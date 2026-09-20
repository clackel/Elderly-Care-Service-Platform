package com.elderlycare.platform.booking.mapper;

import com.elderlycare.platform.booking.domain.*;
import com.elderlycare.platform.elder.domain.ElderProfileRow;
import com.elderlycare.platform.identity.domain.UserAccount;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;

@Mapper
public interface BookingQueries {
    /** 分页查询服务端限定的预约范围，家属逐次检查有效授权。 */
    List<BookingRow> list(@Param("actor") UserAccount actor, @Param("status") String status,
                         @Param("offset") long offset, @Param("limit") int limit, @Param("now") Instant now);
    /** 使用与列表相同的对象权限统计数量。 */
    long count(@Param("actor") UserAccount actor, @Param("status") String status, @Param("now") Instant now);
    /** 查询单笔可见预约，跨社区或越权视同不存在。 */
    BookingRow visible(@Param("actor") UserAccount actor, @Param("id") long id, @Param("now") Instant now);
    /** 查询账号当前可访问的老人档案；管理员也限定社区并支持分页。 */
    List<ElderProfileRow> elders(@Param("actor") UserAccount actor, @Param("offset") long offset,
            @Param("limit") int limit, @Param("now") Instant now);
    /** 判断本人绑定或家属预约权限；write为真时要求代办授权。 */
    long elderAccess(@Param("actor") UserAccount actor, @Param("elderId") long elderId,
                     @Param("write") boolean write, @Param("now") Instant now);
    /** 社区锁内检查人员时间相交，半开区间允许相邻预约。 */
    long conflicts(@Param("community") long community, @Param("worker") long worker,
            @Param("except") long except, @Param("start") Instant start, @Param("end") Instant end);
    /** 归档前统计未结束预约，调用方必须先持有社区锁。 */
    long openForElder(@Param("community") long community, @Param("elderId") long elderId);
    /** 人员停用或能力修改前统计未完成安排。 */
    long openForWorker(@Param("community") long community, @Param("workerId") long workerId);
}

