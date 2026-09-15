package com.elderlycare.platform.elder.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.elder.api.*;
import com.elderlycare.platform.elder.api.ElderResponses.*;
import com.elderlycare.platform.elder.domain.*;
import com.elderlycare.platform.elder.mapper.*;
import com.elderlycare.platform.identity.service.CommunityAccess;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.domain.UserAccount;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ElderProfileService {
    private final CommunityAccess access;
    private final CommunityMapper communities;
    private final ElderProfileMapper profiles;
    private final ElderAuditMapper events;
    private final ElderCrypto crypto;

    /** 装配档案持久化、权限、加密和审计组件。 */
    public ElderProfileService(CommunityAccess access, CommunityMapper communities,
                               ElderProfileMapper profiles, ElderAuditMapper events, ElderCrypto crypto) {
        this.access = access; this.communities = communities;
        this.profiles = profiles; this.events = events; this.crypto = crypto;
    }

    /** 分页查询当前社区档案，按状态或完整姓名、联系电话筛选；列表仅返回脱敏摘要。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<Summary> list(Authentication auth, PageQuery page, String keyword, Status status) {
        long communityId = operator(auth).getCommunityId();
        String hash = keyword == null || keyword.isBlank() ? null : crypto.index(communityId, normalizeSearch(keyword));
        String state = status == null ? null : status.name();
        long total = profiles.count(communityId, state, hash);
        var items = profiles.page(communityId, state, hash, page.offset(), page.pageSize()).stream()
                .map(row -> {
                    var data = data(row);
                    return new Summary(row.getId().toString(), data.name(), data.gender(),
                            Period.between(data.birthDate(), LocalDate.now(ZoneId.of("Asia/Shanghai"))).getYears(),
                            maskPhone(data.phone()), data.livingArrangement(), Status.valueOf(row.getStatus()),
                            row.getVersion(), row.getUpdatedAt());
                }).toList();
        return new PageResponse<>(items, page.page(), page.pageSize(), total);
    }

    /** 读取同社区档案详情，并记录敏感信息访问事件。 */
    @Transactional
    public Detail detail(Authentication auth, long id) {
        UserAccount actor = operator(auth);
        var row = require(actor.getCommunityId(), id);
        Detail result = detail(row);
        audit(actor, row, "VIEW", List.of());
        return result;
    }

    /** 校验社区范围并创建档案；请求编号幂等，资料和创建事件在同一事务保存。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Detail create(Authentication auth, ElderRequests.Create input) {
        UserAccount actor = operator(auth);
        long communityId = actor.getCommunityId();
        String requestHash = crypto.fingerprint(communityId, input.profile());
        var existing = profiles.findRequest(communityId, input.requestId().toString());
        if (existing != null) return replay(existing, requestHash);

        var row = new ElderProfileRow();
        row.setId(IdWorker.getId()); row.setCommunityId(communityId);
        row.setRequestId(input.requestId().toString()); row.setRequestHash(requestHash);
        row.setStatus("ACTIVE"); row.setVersion(0L);
        row.setCreatedAt(Instant.now()); row.setUpdatedAt(row.getCreatedAt());
        populate(row, input.profile());
        try { profiles.insert(row); }
        catch (DuplicateKeyException e) {
            // 读已提交隔离级别确保能读取并发事务已经提交的相同请求。
            existing = profiles.findRequest(communityId, input.requestId().toString());
            if (existing != null) return replay(existing, requestHash);
            throw duplicate();
        }
        audit(actor, row, "CREATE", fieldNames());
        return detail(row);
    }

    /** 按版本替换在管档案资料，防止并发覆盖；只记录实际变化的字段名。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Detail update(Authentication auth, long id, ElderRequests.Update input) {
        UserAccount actor = operator(auth);
        var row = require(actor.getCommunityId(), id);
        checkVersion(row, input.version());
        if (!"ACTIVE".equals(row.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ELDER_ARCHIVED", "档案已归档，请先恢复后编辑");
        }
        var changed = changedFields(data(row), input.profile());
        if (changed.isEmpty()) return detail(row);
        populate(row, input.profile()); row.setUpdatedAt(Instant.now());
        try {
            if (profiles.update(row) != 1) throw stale();
        } catch (DuplicateKeyException e) { throw duplicate(); }
        row.setVersion(row.getVersion() + 1);
        audit(actor, row, "UPDATE", changed);
        return detail(row);
    }

    /** 按预期版本归档当前社区档案，保留资料及操作历史。 */
    @Transactional
    public Detail archive(Authentication auth, long id, long version) {
        return transition(auth, id, version, "ACTIVE", "ARCHIVED", "ARCHIVE");
    }

    /** 按预期版本恢复已归档档案。 */
    @Transactional
    public Detail restore(Authentication auth, long id, long version) {
        return transition(auth, id, version, "ARCHIVED", "ACTIVE", "RESTORE");
    }

    /** 分页查询同社区档案的变更历史，不返回个人信息旧值或浏览事件。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<History> history(Authentication auth, long id, PageQuery page) {
        UserAccount actor = operator(auth);
        require(actor.getCommunityId(), id);
        long total = events.count(actor.getCommunityId(), id);
        var items = events.page(actor.getCommunityId(), id, page.offset(), page.pageSize()).stream()
                .map(e -> new History(e.id().toString(), e.actorId().toString(), e.action(),
                        e.changedFields().isEmpty() ? List.of() : List.of(e.changedFields().split(",")),
                        e.version(), e.occurredAt())).toList();
        return new PageResponse<>(items, page.page(), page.pageSize(), total);
    }

    /** 以版本和来源状态执行原子状态更新，失败时返回冲突。 */
    private Detail transition(Authentication auth, long id, long version, String from, String to, String action) {
        UserAccount actor = operator(auth);
        var row = require(actor.getCommunityId(), id);
        checkVersion(row, version);
        if (!from.equals(row.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_ELDER_STATE", "档案状态已变化，请刷新后重试");
        }
        Instant now = Instant.now();
        if (profiles.transition(actor.getCommunityId(), id, version, from, to, now) != 1) throw stale();
        row.setStatus(to); row.setVersion(version + 1); row.setUpdatedAt(now);
        audit(actor, row, action, List.of("status"));
        return detail(row);
    }

    /** 核验操作人角色和有效社区，平台管理员也不得绕过社区限制。 */
    private UserAccount operator(Authentication auth) {
        UserAccount actor = access.current(auth);
        if (!Set.of("COMMUNITY_OPERATOR", "PLATFORM_ADMIN").contains(actor.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权管理老人档案");
        }
        if (actor.getCommunityId() == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "COMMUNITY_REQUIRED", "账号尚未分配社区");
        }
        if (communities.findActiveById(actor.getCommunityId()) == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "所属社区不可用");
        }
        return actor;
    }

    /** 按档案编号和可信社区范围读取；不存在或跨社区统一返回404。 */
    private ElderProfileRow require(long communityId, long id) {
        var row = profiles.find(communityId, id);
        // 跨社区和不存在的编号使用相同响应，避免泄露档案是否存在。
        if (row == null) throw new BusinessException(HttpStatus.NOT_FOUND, "ELDER_NOT_FOUND", "老人档案不存在");
        return row;
    }

    /** 更新加密载荷及检索、去重摘要，电话不能单独作为个人身份。 */
    private void populate(ElderProfileRow row, ElderProfileData input) {
        long community = row.getCommunityId();
        row.setPayloadCipher(crypto.encrypt(row.getId(), community, input));
        row.setNameHash(crypto.index(community, input.name()));
        row.setPhoneHash(input.phone() == null || input.phone().isEmpty() ? null : crypto.index(community, normalizeSearch(input.phone())));
        // 家庭成员可能共用电话，不能仅凭电话号码判断重复档案。
        String contact = input.phone() == null || input.phone().isEmpty() ? input.emergencyContactPhone() : input.phone();
        row.setIdentityHash(crypto.index(community, input.name() + "\u0000" + input.birthDate() + "\u0000" + normalizeSearch(contact)));
    }

    /** 解密当前档案，确保密文绑定的编号与社区一致。 */
    private ElderProfileData data(ElderProfileRow row) {
        return crypto.decrypt(row.getId(), row.getCommunityId(), row.getPayloadCipher());
    }

    /** 将持久化档案转换为详情响应，不暴露密文、索引或请求摘要。 */
    private Detail detail(ElderProfileRow row) {
        return new Detail(row.getId().toString(), row.getCommunityId().toString(), data(row),
                Status.valueOf(row.getStatus()), row.getVersion(), row.getCreatedAt(), row.getUpdatedAt());
    }

    /** 复用同请求编号的建档结果，载荷不一致时拒绝复用。 */
    private Detail replay(ElderProfileRow row, String hash) {
        if (!row.getRequestHash().equals(hash)) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "该请求编号已用于其他建档内容");
        }
        return detail(row);
    }

    /** 记录操作者、变更字段及版本，不保存明文资料。 */
    private void audit(UserAccount actor, ElderProfileRow row, String action, List<String> changed) {
        int inserted = events.insert(new ElderAuditRow(IdWorker.getId(), row.getId(), row.getCommunityId(), actor.getId(),
                action, String.join(",", changed), row.getVersion(), Instant.now()));
        if (inserted != 1) throw new IllegalStateException("老人档案审计记录保存失败");
    }

    /** 返回档案可编辑字段名，用于创建事件。 */
    private static List<String> fieldNames() {
        return Arrays.stream(ElderProfileData.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
    }

    /** 比较规范化资料，返回发生变化的字段名。 */
    private static List<String> changedFields(ElderProfileData before, ElderProfileData after) {
        List<String> changed = new ArrayList<>();
        try {
            for (var component : ElderProfileData.class.getRecordComponents()) {
                if (!Objects.equals(component.getAccessor().invoke(before), component.getAccessor().invoke(after))) {
                    changed.add(component.getName());
                }
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("老人档案字段比较失败"); }
        return changed;
    }

    /** 检查客户端提交版本，拒绝覆盖较新的档案。 */
    private static void checkVersion(ElderProfileRow row, long version) {
        if (row.getVersion() != version) throw stale();
    }
    /** 构造可供客户端提示刷新重试的版本冲突异常。 */
    private static BusinessException stale() {
        return new BusinessException(HttpStatus.CONFLICT, "ELDER_VERSION_CONFLICT", "档案已被其他操作更新，请刷新后重试");
    }
    /** 构造同社区档案重复异常，提示核对或恢复已有档案。 */
    private static BusinessException duplicate() {
        return new BusinessException(HttpStatus.CONFLICT, "ELDER_ALREADY_EXISTS", "同名、同出生日期和同联系电话的档案已存在，请核对或恢复原档案");
    }
    /** 统一检索输入，电话去除连字符，姓名保留原字符。 */
    private static String normalizeSearch(String value) {
        String stripped = value.strip();
        return stripped.matches("[0-9-]+") ? stripped.replace("-", "") : stripped;
    }
    /** 保留电话首三位和末四位，其余位替换为星号。 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String normalized = normalizeSearch(phone);
        return normalized.substring(0, 3) + "*".repeat(normalized.length() - 7) + normalized.substring(normalized.length() - 4);
    }
}
