package com.elderlycare.platform.consent.mapper;

import com.elderlycare.platform.consent.domain.HealthGrantRow;
import com.elderlycare.platform.identity.domain.UserAccount;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;

/** 健康同意事实与有效性查询。 */
@Mapper
public interface HealthGrantQueries {
    /** 查询本人授予或当前账号收到的事实，保留到期、撤销记录。 */
    List<HealthGrantRow> list(@Param("actor") UserAccount actor, @Param("offset") long offset, @Param("limit") int limit);
    /** 统计同权限范围授权。 */
    long count(@Param("actor") UserAccount actor);
    /** 读取有权管理的授权事实；无对象权限时返回空。 */
    HealthGrantRow visible(@Param("actor") UserAccount actor, @Param("id") long id);
    /** 按绑定版本、账号状态、社区及时间计算当前授权是否有效。 */
    long effective(@Param("id") long id, @Param("now") Instant now);
    /** 重新同意时撤销同对象同范围的旧事实，防止出现难以撤销的重叠授权。 */
    int supersede(@Param("community") long community, @Param("elderId") long elderId,
            @Param("recipientId") long recipientId, @Param("scope") String scope,
            @Param("actorId") long actorId, @Param("now") Instant now);
    /** 档案归档持有社区锁时，撤销所有尚未撤销的第三方健康授权。 */
    int revokeForArchive(@Param("community") long community, @Param("elderId") long elderId,
            @Param("actorId") long actorId, @Param("now") Instant now);
}
