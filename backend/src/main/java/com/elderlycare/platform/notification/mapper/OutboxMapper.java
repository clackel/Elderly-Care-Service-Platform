package com.elderlycare.platform.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.notification.domain.OutboxRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 使用发送租约和比较更新协调多个发布进程，不持有网络调用期间的数据库事务。 */
@Mapper
public interface OutboxMapper extends BaseMapper<OutboxRow> {
    /** 只选到期且未投递完成的事件；实际占用必须再比较更新。 */
    List<OutboxRow> due(@Param("now") Instant now, @Param("limit") int limit);
    /** 版本仍匹配时取得发送租约并累计一次尝试，返回零表示已被其他进程处理。 */
    int claim(@Param("id") long id, @Param("version") long version, @Param("token") String token,
            @Param("now") Instant now, @Param("until") Instant until, @Param("maxAttempts") int maxAttempts);
    /** 达到次数上限且租约过期后标记发送异常，不覆盖已完成状态。 */
    int exhausted(@Param("id") long id, @Param("version") long version, @Param("now") Instant now);
    /** 仅本次租约持有人能记录发送失败，不能覆盖消费者已经提交的完成状态。 */
    int failed(@Param("id") long id, @Param("token") String token, @Param("status") String status,
            @Param("next") Instant next, @Param("code") String code);
    /** 按社区分页列出重试耗尽的事件，只提供安全的处理元数据。 */
    List<OutboxRow> failures(@Param("communityId") long communityId, @Param("offset") long offset, @Param("limit") int limit);
    /** 与失败列表使用相同社区和状态条件。 */
    long failureCount(@Param("communityId") long communityId);
    /** 社区管理重试时加行锁，和消费完成及发布状态更新互斥。 */
    OutboxRow lockFailure(@Param("communityId") long communityId, @Param("id") long id);
    /** 只将指定版本的失败事项重新排队；不重建接收范围，不重发已完成通知。 */
    int retry(@Param("communityId") long communityId, @Param("id") long id, @Param("version") long version, @Param("now") Instant now);
}
