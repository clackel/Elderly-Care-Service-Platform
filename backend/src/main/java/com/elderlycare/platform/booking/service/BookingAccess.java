package com.elderlycare.platform.booking.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.booking.domain.AccessEventRow;
import com.elderlycare.platform.booking.mapper.*;
import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.service.CommunityAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Set;

@Service
public class BookingAccess {
    private final CommunityAccess access;
    private final CommunityMapper communities;
    private final BookingQueries queries;
    private final AccessEventMapper events;
    /** 装配身份、社区与预约对象权限依赖。 */
    public BookingAccess(CommunityAccess access, CommunityMapper communities, BookingQueries queries, AccessEventMapper events) {
        this.access = access; this.communities = communities; this.queries = queries; this.events = events;
    }
    /** 核验真实账号、允许的业务角色和有效社区。 */
    public UserAccount actor(Authentication auth) {
        UserAccount a = access.current(auth);
        if (!Set.of("ELDER","FAMILY","STAFF","COMMUNITY_OPERATOR","PLATFORM_ADMIN").contains(a.getRole()))
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "该账号无权访问养老服务预约");
        if (a.getCommunityId() == null || communities.findActiveById(a.getCommunityId()) == null)
            throw error(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "账号未分配有效社区，请联系社区人员");
        return a;
    }
    /** 核验本社区管理角色，平台管理员不豁免社区边界。 */
    public UserAccount operator(Authentication auth) {
        UserAccount a = actor(auth); requireOperator(a); return a;
    }
    /** 判断当前账号是否为允许的管理角色。 */
    public boolean isOperator(UserAccount a) { return Set.of("COMMUNITY_OPERATOR","PLATFORM_ADMIN").contains(a.getRole()); }
    /** 非管理人员不得维护目录、绑定或人工安排。 */
    public void requireOperator(UserAccount a) {
        if (!isOperator(a)) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "此操作需要社区管理权限");
    }
    /** 在写事务中获取数据库社区行锁，序列化预约、安排、授权和归档。 */
    public void lock(UserAccount a) {
        if (communities.lockActiveById(a.getCommunityId()) == null)
            throw error(HttpStatus.FORBIDDEN, "COMMUNITY_UNAVAILABLE", "所属社区不可用");
    }
    /** 校验老人对象及预约代办范围，授权撤销或到期立即拒绝。 */
    public void elder(UserAccount a, long id, boolean write) {
        if (queries.elderAccess(a, id, write, Instant.now()) == 0) throw missing();
    }
    /** 记录敏感详情读取或权限调整的元数据，不记录敏感正文。 */
    public void audit(UserAccount a, long target, String action) {
        var row = new AccessEventRow(); row.id = IdWorker.getId(); row.communityId = a.getCommunityId();
        row.actorId = a.getId(); row.targetId = target; row.action = action; row.occurredAt = Instant.now();
        events.insert(row);
    }
    /** 校验乐观版本；不自动覆盖其他操作的结果。 */
    public static void version(long actual, long expected) {
        if (actual != expected) throw conflict("VERSION_CONFLICT", "资料已更新，请刷新后重新操作");
    }
    /** 返回不泄露跨社区对象是否存在的统一异常。 */
    public static BusinessException missing() { return error(HttpStatus.NOT_FOUND, "BOOKING_RESOURCE_NOT_FOUND", "资源不存在或不在可访问范围"); }
    /** 返回需要调整状态或资料后重试的业务冲突。 */
    public static BusinessException conflict(String code, String message) { return error(HttpStatus.CONFLICT, code, message); }
    /** 返回不包含敏感正文的业务异常。 */
    public static BusinessException error(HttpStatus status, String code, String message) { return new BusinessException(status, code, message); }
}

