package com.elderlycare.platform.health.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.health.domain.*;
import com.elderlycare.platform.health.mapper.*;
import com.elderlycare.platform.elder.domain.ElderProfileRow;
import com.elderlycare.platform.booking.domain.BindingRow;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.service.CommunityAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 健康域独立权限入口，预约及管理角色不隐含健康访问权。 */
@Service
public class HealthAccess {
    private final CommunityAccess identities;
    private final CommunityMapper communities;
    private final HealthQueries queries;
    private final HealthEventMapper events;
    /** 装配身份、实时健康范围和元数据审计。 */
    public HealthAccess(CommunityAccess identities, CommunityMapper communities, HealthQueries queries, HealthEventMapper events) {
        this.identities = identities; this.communities = communities; this.queries = queries; this.events = events;
    }
    /** 每次请求重新核验账号、健康域允许角色和有效社区。 */
    public UserAccount actor(Authentication auth) {
        var actor = identities.current(auth);
        if (!Set.of("ELDER", "FAMILY", "COMMUNITY_OPERATOR").contains(actor.getRole()))
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "该角色没有健康记录访问权限");
        if (actor.getCommunityId() == null || communities.findActiveById(actor.getCommunityId()) == null)
            throw error(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "账号未分配有效社区");
        return actor;
    }
    /** 写事务先锁社区再重读身份；跨社区变更时拒绝沿用旧锁。 */
    public UserAccount writer(Authentication auth) {
        var before = actor(auth);
        if (communities.lockActiveById(before.getCommunityId()) == null)
            throw error(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "所属社区不可用");
        var current = actor(auth);
        if (!Objects.equals(before.getCommunityId(), current.getCommunityId())) throw missing();
        return current;
    }
    /** 统一核验老人范围，未授权与不存在都返回404。 */
    public ElderProfileRow elder(UserAccount actor, long id) {
        var row = queries.elder(actor, id, Instant.now());
        if (row == null) throw missing();
        return row;
    }
    /** 仅有效绑定本人可以授予健康权限或查看历史旧值。 */
    public BindingRow owner(UserAccount actor, long elderId) {
        if (!"ELDER".equals(actor.getRole())) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅老人本人可以执行此操作");
        var binding = queries.ownerBinding(actor, elderId);
        if (binding == null) throw missing();
        return binding;
    }
    /** 本人或获授权代录人员可以写入，归档档案只读。 */
    public void writable(UserAccount actor, ElderProfileRow elder) {
        if ("FAMILY".equals(actor.getRole())) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "家属健康授权仅允许查看");
        if (!"ACTIVE".equals(elder.getStatus())) throw conflict("ELDER_ARCHIVED", "老人档案已归档，只能查看历史记录或撤销授权");
    }
    /** 详情与列表共用SQL权限；原代录者在老人接管后无法读取任何正文。 */
    public HealthRecordRow record(UserAccount actor, long id) {
        var row = queries.record(actor, id, Instant.now());
        if (row == null) throw missing();
        return row;
    }
    /** 社区协助不包含完整趋势权限。 */
    public void trend(UserAccount actor) {
        if ("COMMUNITY_OPERATOR".equals(actor.getRole())) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "协助录入权限不包含健康趋势");
    }
    /** 记录敏感读取和写入元数据，不包含测量或原因。 */
    public void audit(UserAccount actor, long target, String action) {
        var row = new HealthEventRow(); row.id = IdWorker.getId(); row.communityId = actor.getCommunityId();
        row.actorId = actor.getId(); row.targetId = target; row.action = action; row.occurredAt = Instant.now();
        if (events.insert(row) != 1) throw new IllegalStateException("健康访问记录保存失败");
    }
    /** 版本过期时要求刷新，禁止覆盖其他用户修改。 */
    public static void version(long actual, long expected) {
        if (actual != expected) throw conflict("VERSION_CONFLICT", "记录已更新，请刷新后重新操作");
    }
    /** 对象不存在和不在权限范围内使用同一响应。 */
    public static BusinessException missing() { return error(HttpStatus.NOT_FOUND, "HEALTH_RESOURCE_NOT_FOUND", "健康资源不存在或不在可访问范围"); }
    /** 生成不包含健康正文的状态冲突。 */
    public static BusinessException conflict(String code, String message) { return error(HttpStatus.CONFLICT, code, message); }
    /** 生成明确中文业务异常。 */
    public static BusinessException error(HttpStatus status, String code, String message) { return new BusinessException(status, code, message); }
}

