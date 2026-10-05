package com.elderlycare.platform.notification.mapper;

import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.notification.domain.*;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 通知投递、展示、计数和已读共用同一套快照及实时权限条件。 */
@Mapper
public interface NotificationQueries {
    /** 与预约事件同事务保存待投递事项，主键使同一事件不会重复创建。 */
    int insertOutbox(@Param("row") OutboxRow row);
    /** 在持有社区锁时读取当前接收范围，排除操作者，不补发过去事件。 */
    List<RecipientRow> snapshots(@Param("communityId") long communityId, @Param("elderId") long elderId,
            @Param("workerId") Long workerId, @Param("assignmentVersion") long assignmentVersion,
            @Param("actorId") long actorId, @Param("now") Instant now);
    /** 查找被改派的有效工作人员，仅创建无业务正文的移交通知。 */
    RecipientRow transferred(@Param("communityId") long communityId, @Param("accountId") long accountId);
    /** 保存事件发生时的权限周期，禁止投递时重新扩大接收范围。 */
    int insertRecipient(@Param("row") RecipientRow row);
    /** 投递前读取所属社区，仅供后续加锁，不据此返回正文。 */
    OutboxRow outbox(@Param("eventId") long eventId);
    /** 社区锁内重读并锁定投递状态，与站内消息一同提交。 */
    OutboxRow lockOutbox(@Param("eventId") long eventId);
    /** 以当前账号、角色、绑定、授权和安排周期筛选仍有权限的事件接收人。 */
    List<RecipientRow> deliverable(@Param("eventId") long eventId, @Param("now") Instant now);
    /** 社区锁内查询去重键，处理消息中间件重复投递。 */
    Long messageId(@Param("eventId") long eventId, @Param("accountId") long accountId);
    /** 仅创建安全站内消息引用，不复制业务说明或个人资料。 */
    int insertMessage(@Param("id") long id, @Param("eventId") long eventId, @Param("accountId") long accountId,
            @Param("communityId") long communityId, @Param("now") Instant now);
    /** 投递成功与消息写入同事务提交，清除发送占用及历史错误。 */
    int delivered(@Param("eventId") long eventId);
    /** 分页读取当前账号仍有权限的通知，转交消息的预约编号为空。 */
    List<MessageRow> list(@Param("actor") UserAccount actor, @Param("unreadOnly") boolean unreadOnly,
            @Param("now") Instant now, @Param("offset") long offset, @Param("limit") int limit);
    /** 与通知列表使用相同权限过滤条件计算总数或未读数。 */
    long count(@Param("actor") UserAccount actor, @Param("unreadOnly") boolean unreadOnly, @Param("now") Instant now);
    /** 标记已读前重新核验单条消息的当前权限。 */
    MessageRow visible(@Param("actor") UserAccount actor, @Param("id") long id, @Param("now") Instant now);
    /** 已经通过同事务权限校验的消息按账号标记已读，重复请求保留原已读时间。 */
    int markRead(@Param("actor") UserAccount actor, @Param("id") long id, @Param("now") Instant now);
    /** 按当前预约及对象权限派生待办，不解密申请正文。 */
    List<TodoRow> todos(@Param("actor") UserAccount actor, @Param("type") String type,
            @Param("now") Instant now, @Param("offset") long offset, @Param("limit") int limit);
    /** 与待办列表使用同一派生条件统计数量。 */
    long todoCount(@Param("actor") UserAccount actor, @Param("type") String type, @Param("now") Instant now);
}
