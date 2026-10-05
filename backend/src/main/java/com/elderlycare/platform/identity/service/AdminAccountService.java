package com.elderlycare.platform.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.booking.service.BookingCrypto;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.common.api.PageQuery;
import com.elderlycare.platform.common.api.PageResponse;
import com.elderlycare.platform.identity.api.AdminAccountRequests;
import com.elderlycare.platform.identity.api.AdminAccountResponses.*;
import com.elderlycare.platform.identity.domain.*;
import com.elderlycare.platform.identity.mapper.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 本社区后台账号生命周期；社区锁覆盖权限重读、账号更新、授权撤销及操作记录。 */
@Service
public class AdminAccountService {
    public static final Set<String> ROLES = Set.of("COMMUNITY_OPERATOR", "DUTY_OFFICER", "PLATFORM_ADMIN", "AUDITOR");
    private final CommunityAccess access;
    private final CommunityMapper communities;
    private final UserAccountMapper accounts;
    private final AdminAccountQueries queries;
    private final AdminAccountEventMapper events;
    private final AdminAccountMutationMapper mutations;
    private final PasswordEncoder passwords;
    private final BookingCrypto crypto;

    /** 装配实时账号权限、事务查询、密码哈希和仅用于幂等摘要的带密钥运算。 */
    public AdminAccountService(CommunityAccess access, CommunityMapper communities, UserAccountMapper accounts,
            AdminAccountQueries queries, AdminAccountEventMapper events, AdminAccountMutationMapper mutations,
            PasswordEncoder passwords, BookingCrypto crypto) {
        this.access = access; this.communities = communities; this.accounts = accounts; this.queries = queries;
        this.events = events; this.mutations = mutations; this.passwords = passwords; this.crypto = crypto;
    }

    /** 平台管理员分页读取本社区后台账号；关键词按字面片段匹配，不接受客户端社区范围。 */
    public PageResponse<Item> list(Authentication authentication, PageQuery page, String keyword, String role, String status) {
        var actor = current(authentication, true);
        keyword = optional(keyword); role = optional(role); status = optional(status);
        if (keyword != null && keyword.length() > 100) throw invalid("查询内容不能超过100个字符");
        if (role != null) requireRole(role);
        if (status != null) requireStatus(status);
        long total = queries.count(actor.getCommunityId(), keyword, role, status);
        var items = queries.list(actor.getCommunityId(), keyword, role, status, page.offset(), page.pageSize());
        return new PageResponse<>(items.stream().map(Item::from).toList(), page.page(), page.pageSize(), total);
    }

    /** 创建所属社区固定的后台账号；同编号同内容重试返回原结果，初始密码必须更换。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Mutation create(Authentication authentication, AdminAccountRequests.Create input) {
        var actor = writer(authentication, true);
        String username = normalizeUsername(input.username());
        String name = displayName(input.displayName());
        String role = input.role().strip(); requireRole(role);
        AdminPasswordPolicy.validate(input.password());
        String hash = hash(actor, "CREATE", List.of(username, name, role, input.password()));
        var replay = replay(actor, "CREATE", input.requestId(), hash);
        if (replay != null) return replay;
        var account = new UserAccount(); account.setId(IdWorker.getId()); account.setUsername(username);
        account.setCommunityId(actor.getCommunityId()); account.setDisplayName(name); account.setRole(role);
        account.setStatus("ACTIVE"); account.setPasswordHash(passwords.encode(input.password())); account.setPasswordChangeRequired(true);
        try {
            if (accounts.insert(account) != 1) throw new IllegalStateException("后台账号保存失败");
        } catch (DuplicateKeyException exception) {
            throw conflict("USERNAME_UNAVAILABLE", "该登录账号已被使用，请更换账号名称");
        }
        event(actor, account, "CREATED");
        return save(actor, "CREATE", input.requestId(), hash, account);
    }

    /** 按期望版本编辑本社区后台账号；停用或离开运营角色时永久撤销原代录授权。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Mutation update(Authentication authentication, long id, AdminAccountRequests.Update input) {
        var actor = writer(authentication, true); var account = target(actor, id);
        String name = displayName(input.displayName()); String role = input.role().strip(); String status = input.status().strip();
        requireRole(role); requireStatus(status);
        String hash = hash(actor, "UPDATE", List.of(id, input.version(), name, role, status));
        var replay = replay(actor, "UPDATE", input.requestId(), hash);
        if (replay != null) return replay;
        requireVersion(account, input.version());
        boolean roleChanged = !role.equals(account.getRole()); boolean statusChanged = !status.equals(account.getStatus());
        boolean nameChanged = !name.equals(account.getDisplayName());
        if (Objects.equals(actor.getId(), account.getId()) && (roleChanged || !"ACTIVE".equals(status)))
            throw conflict("SELF_ACCOUNT_CHANGE_FORBIDDEN", "不能停用自己的账号或降低自己的管理员权限，请由其他管理员处理");
        if ("PLATFORM_ADMIN".equals(account.getRole()) && "ACTIVE".equals(account.getStatus())
                && (!"PLATFORM_ADMIN".equals(role) || !"ACTIVE".equals(status)) && queries.activeAdmins(actor.getCommunityId()) <= 1)
            throw conflict("LAST_ADMIN_REQUIRED", "本社区必须保留至少一个有效的平台管理员");
        if (!roleChanged && !statusChanged && !nameChanged) return save(actor, "UPDATE", input.requestId(), hash, account);
        boolean revokeAssistance = "COMMUNITY_OPERATOR".equals(account.getRole())
                && (!"COMMUNITY_OPERATOR".equals(role) || (statusChanged && "DISABLED".equals(status)));
        account.setDisplayName(name); account.setRole(role); account.setStatus(status);
        if (roleChanged || statusChanged) {
            account.setCredentialVersion(account.getCredentialVersion() + 1);
            account.setNotificationVersion(account.getNotificationVersion() + 1);
        }
        advance(account, input.version());
        if (revokeAssistance && queries.revokeAssistance(actor.getCommunityId(), account.getId(), actor.getId(), Instant.now()) > 0)
            event(actor, account, "ASSISTANCE_REVOKED");
        if (nameChanged) event(actor, account, "PROFILE_UPDATED");
        if (roleChanged) event(actor, account, "ROLE_CHANGED");
        if (statusChanged) event(actor, account, "ACTIVE".equals(status) ? "ENABLED" : "DISABLED");
        return save(actor, "UPDATE", input.requestId(), hash, account);
    }

    /** 管理员只能重置其他后台账号；要求下次改密并撤销其全部旧会话。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Mutation resetPassword(Authentication authentication, long id, AdminAccountRequests.ResetPassword input) {
        var actor = writer(authentication, true); var account = target(actor, id);
        if (Objects.equals(actor.getId(), account.getId()))
            throw conflict("SELF_PASSWORD_RESET_FORBIDDEN", "修改自己的密码需要验证原密码，请使用修改密码入口");
        AdminPasswordPolicy.validate(input.password());
        String hash = hash(actor, "RESET_PASSWORD", List.of(id, input.version(), input.password()));
        var replay = replay(actor, "RESET_PASSWORD", input.requestId(), hash);
        if (replay != null) return replay;
        requireVersion(account, input.version());
        account.setPasswordHash(passwords.encode(input.password())); account.setPasswordChangeRequired(true);
        account.setCredentialVersion(account.getCredentialVersion() + 1); advance(account, input.version());
        event(actor, account, "PASSWORD_RESET");
        return save(actor, "RESET_PASSWORD", input.requestId(), hash, account);
    }

    /** 本人验证旧密码后改密；事务提交后控制器销毁当前会话，其他会话按凭证版本失效。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changePassword(Authentication authentication, AdminAccountRequests.ChangePassword input) {
        var actor = writer(authentication, false);
        if (input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw invalid("原密码不正确，请核对后重新输入");
        AdminPasswordPolicy.validate(input.newPassword());
        String hash = hash(actor, "CHANGE_PASSWORD", List.of(input.currentPassword(), input.newPassword()));
        if (replay(actor, "CHANGE_PASSWORD", input.requestId(), hash) != null) return;
        if (!passwords.matches(input.currentPassword(), actor.getPasswordHash()))
            throw invalid("原密码不正确，请核对后重新输入");
        if (passwords.matches(input.newPassword(), actor.getPasswordHash()))
            throw invalid("新密码不能与原密码相同，请设置新的密码");
        long version = actor.getVersion();
        actor.setPasswordHash(passwords.encode(input.newPassword())); actor.setPasswordChangeRequired(false);
        actor.setCredentialVersion(actor.getCredentialVersion() + 1); advance(actor, version);
        event(actor, actor, "PASSWORD_CHANGED");
        save(actor, "CHANGE_PASSWORD", input.requestId(), hash, actor);
    }

    /** 平台管理员仅能读取本社区后台账号的操作元数据，不返回密码或请求摘要。 */
    public PageResponse<Event> events(Authentication authentication, long id, PageQuery page) {
        var actor = current(authentication, true); target(actor, id);
        var filter = new QueryWrapper<AdminAccountEventRow>().eq("community_id", actor.getCommunityId()).eq("account_id", id);
        long count = events.selectCount(filter);
        var rows = events.selectList(filter.orderByDesc("occurred_at", "id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()));
        return new PageResponse<>(rows.stream().map(row -> new Event(row.id.toString(), row.actorId.toString(), row.action, row.occurredAt)).toList(),
                page.page(), page.pageSize(), count);
    }

    /** 逐请求检查登录版本、后台角色及有效社区，管理接口仅向已完成改密的平台管理员开放。 */
    private UserAccount current(Authentication authentication, boolean adminRequired) {
        var actor = access.current(authentication); AccountSessions.requireCurrent(authentication, actor);
        if (!ROLES.contains(actor.getRole()) || (adminRequired && !"PLATFORM_ADMIN".equals(actor.getRole())))
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "此操作需要平台管理员权限或有效后台账号");
        if (actor.getCommunityId() == null || communities.findActiveById(actor.getCommunityId()) == null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "账号未分配有效社区，请联系管理员");
        if (adminRequired && actor.isPasswordChangeRequired())
            throw new BusinessException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", "请先修改初始或重置密码");
        return actor;
    }

    /** 先锁社区再重新校验操作者与登录版本，阻止等待锁期间已被停用或改权的请求继续写入。 */
    private UserAccount writer(Authentication authentication, boolean adminRequired) {
        var before = current(authentication, adminRequired);
        long communityId = before.getCommunityId(); long actorId = before.getId();
        if (communities.lockActiveById(communityId) == null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "所属社区不可用，请联系管理员");
        var current = current(authentication, adminRequired);
        if (current.getCommunityId() != communityId || current.getId() != actorId)
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "账号身份已变更，请重新登录");
        return current;
    }

    /** 同一错误隐藏跨社区账号、移动账号和不存在对象的差别。 */
    private UserAccount target(UserAccount actor, long id) {
        var account = queries.target(actor.getCommunityId(), id);
        if (account == null) throw new BusinessException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在或不在可管理范围");
        return account;
    }

    /** 以版本条件写入，失败时不覆盖其他更新，外围事务同时回滚记录与授权撤销。 */
    private void advance(UserAccount account, long expected) {
        account.setVersion(expected + 1);
        if (queries.update(account, expected, Instant.now()) != 1)
            throw conflict("VERSION_CONFLICT", "账号已被更新，请刷新后重新操作");
    }

    /** 原始请求版本与当前不一致时拒绝覆盖。 */
    private static void requireVersion(UserAccount account, long expected) {
        if (account.getVersion() != expected) throw conflict("VERSION_CONFLICT", "账号已被更新，请刷新后重新操作");
    }

    /** 记录安全事件，不持久化账号密码、请求摘要或健康正文。 */
    private void event(UserAccount actor, UserAccount account, String action) {
        var row = new AdminAccountEventRow(); row.id = IdWorker.getId(); row.communityId = actor.getCommunityId();
        row.actorId = actor.getId(); row.accountId = account.getId(); row.action = action; row.occurredAt = Instant.now();
        if (events.insert(row) != 1) throw new IllegalStateException("账号操作记录保存失败");
    }

    /** 使用账号专用用途域生成 HMAC，密码参与一致性判断但不保存正文或无密钥摘要。 */
    private String hash(UserAccount actor, String operation, Object payload) {
        return crypto.hash("admin-account:" + actor.getCommunityId() + ":" + actor.getId() + ":" + operation, payload);
    }

    /** 已重新校验当前权限后返回同请求原结果；换用内容必须提供新请求编号。 */
    private Mutation replay(UserAccount actor, String operation, UUID requestId, String hash) {
        var row = mutations.selectOne(new QueryWrapper<AdminAccountMutationRow>().eq("community_id", actor.getCommunityId())
                .eq("actor_id", actor.getId()).eq("operation", operation).eq("request_id", requestId.toString()));
        if (row == null) return null;
        if (!hash.equals(row.requestHash)) throw conflict("IDEMPOTENCY_CONFLICT", "该请求编号已用于其他内容，请核对原请求");
        return new Mutation(row.resultId.toString(), row.resultVersion);
    }

    /** 与账号变更同事务保留原标识和版本，唯一约束保证重复提交不会重复创建账号。 */
    private Mutation save(UserAccount actor, String operation, UUID requestId, String hash, UserAccount account) {
        var row = new AdminAccountMutationRow(); row.id = IdWorker.getId(); row.communityId = actor.getCommunityId(); row.actorId = actor.getId();
        row.operation = operation; row.requestId = requestId.toString(); row.requestHash = hash;
        row.resultId = account.getId(); row.resultVersion = account.getVersion(); row.createdAt = Instant.now();
        if (mutations.insert(row) != 1) throw new IllegalStateException("账号请求记录保存失败");
        return new Mutation(account.getId().toString(), account.getVersion());
    }

    /** 登录名统一去首尾空白并转为小写，只接受3至64位字母数字及点、下划线和连字符。 */
    private static String normalizeUsername(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,63}")) throw invalid("登录账号须为3至64位，字母或数字开头，可包含点、下划线和连字符");
        return normalized;
    }

    /** 显示名称去除首尾空白并限制为1至100字，不代替真实身份核验。 */
    private static String displayName(String value) {
        String name = value.strip();
        if (name.isEmpty() || name.length() > 100) throw invalid("显示名称须为1至100个字符");
        return name;
    }

    /** 可选筛选空白转为空值，避免无意义的空字符串查询。 */
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    /** 单角色枚举排除移动角色和客户端自定义权限。 */
    private static void requireRole(String role) { if (!ROLES.contains(role)) throw invalid("请选择有效的后台角色"); }

    /** 状态只支持启用和停用，不通过删除账号绕过保留操作历史。 */
    private static void requireStatus(String status) { if (!Set.of("ACTIVE", "DISABLED").contains(status)) throw invalid("请选择启用或停用状态"); }

    /** 生成不包含输入值或凭据正文的参数错误。 */
    private static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message); }

    /** 生成可据提示刷新或调整请求的业务冲突。 */
    private static BusinessException conflict(String code, String message) { return new BusinessException(HttpStatus.CONFLICT, code, message); }
}
