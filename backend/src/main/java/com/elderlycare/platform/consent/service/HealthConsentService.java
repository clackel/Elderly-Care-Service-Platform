package com.elderlycare.platform.consent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.common.api.*;
import com.elderlycare.platform.consent.api.*;
import com.elderlycare.platform.consent.domain.HealthGrantRow;
import com.elderlycare.platform.consent.mapper.*;
import com.elderlycare.platform.health.api.HealthResponses.Mutation;
import com.elderlycare.platform.health.domain.HealthTypes.Scope;
import com.elderlycare.platform.health.mapper.HealthQueries;
import com.elderlycare.platform.health.service.*;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static com.elderlycare.platform.health.service.HealthAccess.*;

/** 老人在线明确同意的独立健康授权，与预约授权无隐式转换。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class HealthConsentService {
    public static final String CONSENT_VERSION = "health-v1";
    private final HealthAccess access;
    private final HealthQueries health;
    private final HealthGrantQueries queries;
    private final HealthGrantMapper grants;
    private final UserAccountMapper accounts;
    private final HealthMutations mutations;
    /** 装配健康授权、本人身份和同事务幂等依赖。 */
    public HealthConsentService(HealthAccess access, HealthQueries health, HealthGrantQueries queries,
            HealthGrantMapper grants, UserAccountMapper accounts, HealthMutations mutations) {
        this.access = access; this.health = health; this.queries = queries; this.grants = grants; this.accounts = accounts; this.mutations = mutations;
    }
    /** 分页显示授权事实与实时有效标志，失效事实不构成老人访问入口。 */
    public PageResponse<HealthConsentResponses.Grant> list(Authentication auth, PageQuery page) {
        var a = access.actor(auth); var now = Instant.now();
        var rows = queries.list(a, page.offset(), page.pageSize()).stream().map(r -> new HealthConsentResponses.Grant(
                r.id.toString(), r.elderId.toString(), r.recipientId.toString(), r.scope, r.createdAt, r.expiresAt,
                r.revoked, queries.effective(r.id, now) > 0, r.version, r.consentVersion)).toList();
        access.audit(a, a.getId(), "GRANTS_READ");
        return new PageResponse<>(rows, page.page(), page.pageSize(), queries.count(a));
    }
    /** 本人按账号编号精确核对；社区锁下限制每账号每分钟10次，失败尝试也保留元数据。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = BusinessException.class)
    public HealthConsentResponses.Recipient resolve(Authentication auth, HealthConsentRequests.Resolve input) {
        var a = access.writer(auth); access.owner(a, input.elderId()); access.writable(a, access.elder(a, input.elderId()));
        if (health.resolveAttempts(a, Instant.now().minusSeconds(60)) >= 10)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "HEALTH_RESOLVE_RATE_LIMITED", "账号核对过于频繁，请一分钟后重试");
        access.audit(a, a.getId(), "RECIPIENT_RESOLVE");
        var recipient = recipient(a, input.recipientId(), input.scope());
        return new HealthConsentResponses.Recipient(recipient.getId().toString(), recipient.getDisplayName(), recipient.getRole());
    }
    /** 本人授予具体账号固定范围，每次重新同意生成新事实并绑定当前本人绑定版本。 */
    public Mutation grant(Authentication auth, HealthConsentRequests.Grant input) {
        var a = access.writer(auth); var binding = access.owner(a, input.elderId());
        access.writable(a, access.elder(a, input.elderId())); recipient(a, input.recipientId(), input.scope());
        if (!input.accepted() || !CONSENT_VERSION.equals(input.consentVersion()) || !Set.of(7, 30, 90, 365).contains(input.days()))
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请核对当前健康同意说明并选择7、30、90或365天");
        String hash = mutations.hash(a, "GRANT", List.of(input.elderId(), input.recipientId(), input.scope(), input.days(), input.consentVersion()));
        var replay = mutations.replay(a, "GRANT", input.requestId(), hash);
        if (replay != null) {
            var old = queries.visible(a, Long.parseLong(replay.id()));
            if (old == null || !old.bindingId.equals(binding.id) || !old.bindingVersion.equals(binding.version)) throw missing();
            return replay;
        }
        var row = new HealthGrantRow(); row.id = IdWorker.getId(); row.communityId = a.getCommunityId();
        row.elderId = input.elderId(); row.recipientId = input.recipientId(); row.scope = input.scope().name();
        row.bindingId = binding.id; row.bindingVersion = binding.version; row.grantedBy = a.getId();
        row.consentVersion = CONSENT_VERSION; row.createdAt = Instant.now(); row.expiresAt = row.createdAt.plusSeconds(input.days() * 86400L);
        queries.supersede(row.communityId, row.elderId, row.recipientId, row.scope, a.getId(), row.createdAt);
        row.revoked = false; row.version = 0L; grants.insert(row); access.audit(a, row.id, "GRANT");
        return mutations.save(a, "GRANT", input.requestId(), hash, row.id, row.version);
    }
    /** 本人或该受权人撤销，归档本人仍可操作，过期及失效授权也可主动撤销。 */
    public Mutation revoke(Authentication auth, long id, HealthConsentRequests.Revoke input) {
        var a = access.writer(auth); var row = queries.visible(a, id); if (row == null) throw missing();
        String hash = mutations.hash(a, "REVOKE", List.of(id, input.version()));
        var replay = mutations.replay(a, "REVOKE", input.requestId(), hash); if (replay != null) return replay;
        version(row.version, input.version());
        if (!row.revoked) {
            row.revoked = true; row.revokedAt = Instant.now(); row.revokedBy = a.getId(); row.version++;
            grants.updateById(row); access.audit(a, id, "GRANT_REVOKE");
        }
        return mutations.save(a, "REVOKE", input.requestId(), hash, id, row.version);
    }
    /** 账号必须同社区、有效且与所选范围角色一致，失败不泄露账号存在性。 */
    private UserAccount recipient(UserAccount actor, long id, Scope scope) {
        var recipient = accounts.selectById(id);
        String role = scope == Scope.FAMILY_READ ? "FAMILY" : "COMMUNITY_OPERATOR";
        if (recipient == null || !Objects.equals(recipient.getCommunityId(), actor.getCommunityId())
                || !"ACTIVE".equals(recipient.getStatus()) || !role.equals(recipient.getRole())) throw missing();
        return recipient;
    }
}
