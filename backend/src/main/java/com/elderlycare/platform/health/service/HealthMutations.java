package com.elderlycare.platform.health.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.health.api.HealthResponses.Mutation;
import com.elderlycare.platform.health.domain.HealthMutationRow;
import com.elderlycare.platform.health.mapper.HealthMutationMapper;
import com.elderlycare.platform.identity.domain.UserAccount;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import static com.elderlycare.platform.health.service.HealthAccess.conflict;

/** 仅在持有社区锁的业务事务内使用，调用者必须先重新核验对象权限。 */
@Component
public class HealthMutations {
    private final HealthMutationMapper mapper;
    private final HealthCrypto crypto;
    /** 装配数据库幂等约束和健康专用HMAC。 */
    public HealthMutations(HealthMutationMapper mapper, HealthCrypto crypto) { this.mapper = mapper; this.crypto = crypto; }
    /** 摘要包含社区、操作者和操作域，载荷由业务层规范化。 */
    public String hash(UserAccount actor, String operation, Object payload) {
        return crypto.hash(actor.getCommunityId() + ":" + actor.getId() + ":" + operation, payload);
    }
    /** 同编号同内容返回原标识及版本，不同内容返回409；不返回健康旧正文。 */
    public Mutation replay(UserAccount actor, String operation, UUID requestId, String hash) {
        var row = mapper.selectOne(new QueryWrapper<HealthMutationRow>().eq("community_id", actor.getCommunityId())
                .eq("actor_id", actor.getId()).eq("operation", operation).eq("request_id", requestId.toString()));
        if (row == null) return null;
        if (!hash.equals(row.requestHash)) throw conflict("IDEMPOTENCY_CONFLICT", "该请求编号已用于其他内容，请核对原请求");
        return new Mutation(row.resultId.toString(), row.resultVersion);
    }
    /** 在业务事务中保存原操作结果，唯一约束作为并发去重最后防线。 */
    public Mutation save(UserAccount actor, String operation, UUID requestId, String hash, long id, long version) {
        var row = new HealthMutationRow(); row.id = IdWorker.getId(); row.communityId = actor.getCommunityId();
        row.actorId = actor.getId(); row.operation = operation; row.requestId = requestId.toString(); row.requestHash = hash;
        row.resultId = id; row.resultVersion = version; row.createdAt = Instant.now();
        if (mapper.insert(row) != 1) throw new IllegalStateException("健康写请求保存失败");
        return new Mutation(Long.toString(id), version);
    }
}

