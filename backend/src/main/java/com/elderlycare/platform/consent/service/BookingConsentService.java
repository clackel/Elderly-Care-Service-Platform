package com.elderlycare.platform.consent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.booking.api.*;
import com.elderlycare.platform.booking.domain.*;
import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.booking.service.*;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.elder.mapper.ElderProfileMapper;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static com.elderlycare.platform.booking.service.BookingAccess.*;

@Service
public class BookingConsentService {
    private final BookingAccess access;
    private final BookingCrypto crypto;
    private final BindingMapper bindings;
    private final GrantMapper grants;
    private final UserAccountMapper accounts;
    private final ElderProfileMapper elders;
    /** 装配预约专用的核验绑定和家属授权，授权不扩展到健康档案。 */
    public BookingConsentService(BookingAccess access, BookingCrypto crypto, BindingMapper bindings,
            GrantMapper grants, UserAccountMapper accounts, ElderProfileMapper elders) {
        this.access = access; this.crypto = crypto; this.bindings = bindings; this.grants = grants; this.accounts = accounts; this.elders = elders;
    }
    /** 社区分页查看已核验的本人绑定，依据正文不作为列表输出。 */
    public PageResponse<BookingResponses.Binding> bindings(Authentication auth, PageQuery page) {
        var a = access.operator(auth);
        var q = new QueryWrapper<BindingRow>().eq("community_id", a.getCommunityId());
        long total = bindings.selectCount(q);
        var rows = bindings.selectList(q.orderByDesc("id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()));
        return new PageResponse<>(rows.stream().map(this::bindingView).toList(), page.page(), page.pageSize(), total);
    }
    /** 社区核验后建立唯一账号档案绑定；解除保留历史，禁止静默替换绑定对象。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Binding bind(Authentication auth, BookingRequests.Binding input) {
        var a = access.operator(auth); access.lock(a);
        var account = accounts.selectById(input.accountId());
        var elder = elders.find(a.getCommunityId(), input.elderId());
        if (account == null || !Objects.equals(account.getCommunityId(), a.getCommunityId())
                || !"ELDER".equals(account.getRole()) || !"ACTIVE".equals(account.getStatus()) || elder == null) throw missing();
        if (input.active() && !"ACTIVE".equals(elder.getStatus())) throw conflict("ELDER_ARCHIVED", "请先恢复老人档案后绑定账号");
        var matches = bindings.selectList(new QueryWrapper<BindingRow>().eq("community_id", a.getCommunityId())
                .and(q -> q.eq("account_id", input.accountId()).or().eq("elder_id", input.elderId())));
        if (matches.size() > 1) throw conflict("BINDING_ALREADY_EXISTS", "账号和老人分别已有其他绑定，请核实原绑定");
        var row = matches.isEmpty() ? null : matches.getFirst();
        boolean create = row == null;
        if (create) {
            version(0, input.version()); row = new BindingRow(); row.id = IdWorker.getId(); row.communityId = a.getCommunityId();
            row.accountId = input.accountId(); row.elderId = input.elderId(); row.version = 0L;
        } else {
            if (!row.accountId.equals(input.accountId()) || !row.elderId.equals(input.elderId()))
                throw conflict("BINDING_ALREADY_EXISTS", "账号或老人已有其他绑定，请由社区核实原绑定");
            version(row.version, input.version()); row.version++;
        }
        row.active = input.active(); row.verifiedBy = a.getId();
        row.evidenceCipher = crypto.encrypt("binding", row.communityId, row.id, input.verificationReference().strip());
        if (create) bindings.insert(row); else bindings.updateById(row);
        access.audit(a, row.id, input.active() ? "BINDING_VERIFY" : "BINDING_REVOKE");
        return bindingView(row);
    }
    /** 返回本社区管理范围、本人授权或家属收到的授权，包含撤销状态便于管理。 */
    public PageResponse<BookingResponses.Grant> grants(Authentication auth, PageQuery page) {
        var a = access.actor(auth);
        var q = new QueryWrapper<GrantRow>().eq("community_id", a.getCommunityId());
        if ("ELDER".equals(a.getRole())) q.apply("elder_id IN (SELECT elder_id FROM elder_account_binding WHERE account_id = {0} AND active = TRUE)", a.getId());
        else if ("FAMILY".equals(a.getRole())) q.eq("family_id", a.getId());
        else access.requireOperator(a);
        long total = grants.selectCount(q);
        var rows = grants.selectList(q.orderByDesc("id").last("LIMIT " + page.pageSize() + " OFFSET " + page.offset()));
        return new PageResponse<>(rows.stream().map(this::grantView).toList(), page.page(), page.pageSize(), total);
    }
    /** 本人在线同意或社区核验明确同意依据后授予预约权限；家庭关系不自动产生权限。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Grant grant(Authentication auth, BookingRequests.Grant input) {
        var a = access.actor(auth); access.lock(a);
        if (!access.isOperator(a)) {
            if (!"ELDER".equals(a.getRole())) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅老人本人或经核验的社区人员可以授予权限");
            access.elder(a, input.elderId(), false);
        }
        var elder = elders.find(a.getCommunityId(), input.elderId());
        var family = accounts.selectById(input.familyId());
        if (elder == null || family == null || !Objects.equals(family.getCommunityId(), a.getCommunityId())
                || !"FAMILY".equals(family.getRole()) || !"ACTIVE".equals(family.getStatus())) throw missing();
        if (!"ACTIVE".equals(elder.getStatus())) throw conflict("ELDER_ARCHIVED", "已归档档案不能新建授权");
        if (!input.expiresAt().isAfter(Instant.now()) || input.expiresAt().isAfter(Instant.now().plusSeconds(366L * 86400)))
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "授权有效期须在未来一年内");
        var row = grants.selectOne(new QueryWrapper<GrantRow>().eq("community_id", a.getCommunityId())
                .eq("elder_id", input.elderId()).eq("family_id", input.familyId()));
        boolean create = row == null;
        if (create) {
            version(0, input.version()); row = new GrantRow(); row.id = IdWorker.getId(); row.communityId = a.getCommunityId();
            row.elderId = input.elderId(); row.familyId = input.familyId(); row.version = 0L;
        } else { version(row.version, input.version()); row.version++; }
        row.canBook = input.canBook(); row.expiresAt = input.expiresAt(); row.revoked = false; row.grantedBy = a.getId();
        row.evidenceCipher = crypto.encrypt("grant", row.communityId, row.id, access.isOperator(a) ? input.consentReference().strip() : "老人本人在线明确同意");
        if (create) grants.insert(row); else grants.updateById(row);
        access.audit(a, row.id, "BOOKING_GRANT");
        return grantView(row);
    }
    /** 本人、获授权家属或社区撤销授权；已有预约保留，后续家属访问立即被阻止。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponses.Grant revoke(Authentication auth, long id, long expected) {
        var a = access.actor(auth); access.lock(a);
        var row = grants.selectById(id);
        if (row == null || !Objects.equals(row.communityId, a.getCommunityId())) throw missing();
        if (!access.isOperator(a)) {
            if ("ELDER".equals(a.getRole())) access.elder(a, row.elderId, false);
            else if (!"FAMILY".equals(a.getRole()) || !Objects.equals(row.familyId, a.getId())) throw missing();
        }
        version(row.version, expected);
        if (row.revoked) throw conflict("GRANT_REVOKED", "该授权已经撤销");
        row.revoked = true; row.version++; grants.updateById(row); access.audit(a, id, "BOOKING_GRANT_REVOKE");
        return grantView(row);
    }
    /** 输出绑定状态与版本，不输出核验依据。 */
    private BookingResponses.Binding bindingView(BindingRow r) { return new BookingResponses.Binding(r.id.toString(), r.accountId.toString(), r.elderId.toString(), r.active, r.version); }
    /** 输出预约权限、到期与撤销状态，不包含健康权限。 */
    private BookingResponses.Grant grantView(GrantRow r) { return new BookingResponses.Grant(r.id.toString(), r.elderId.toString(), r.familyId.toString(), r.canBook, r.expiresAt, r.revoked, r.version); }
}
