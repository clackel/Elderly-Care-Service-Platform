package com.elderlycare.platform.identity.mapper;

import com.elderlycare.platform.identity.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;
import java.util.List;

/** 后台账号限定查询、版本写入和健康授权撤销统一使用绑定参数的 XML。 */
@Mapper
public interface AdminAccountQueries {
    /** 按社区、姓名或账号字面片段、角色和状态分页读取后台账号。 */
    List<UserAccount> list(@Param("communityId") long communityId, @Param("keyword") String keyword,
            @Param("role") String role, @Param("status") String status, @Param("offset") long offset, @Param("limit") int limit);
    /** 与列表保持相同范围的总数。 */
    long count(@Param("communityId") long communityId, @Param("keyword") String keyword,
            @Param("role") String role, @Param("status") String status);
    /** 仅返回指定社区的后台账号，不暴露移动或跨社区对象。 */
    UserAccount target(@Param("communityId") long communityId, @Param("id") long id);
    /** 持有社区锁时统计仍可登录的管理员，保护最后一个有效管理员。 */
    long activeAdmins(@Param("communityId") long communityId);
    /** 在同一社区与期望版本条件下更新账号，调用方负责角色及状态规则。 */
    int update(@Param("account") UserAccount account, @Param("expected") long expected, @Param("now") Instant now);
    /** 同事务永久撤销目标账号全部未撤销代录授权，包含已经过期的授权。 */
    int revokeAssistance(@Param("communityId") long communityId, @Param("accountId") long accountId,
            @Param("actorId") long actorId, @Param("now") Instant now);
}
