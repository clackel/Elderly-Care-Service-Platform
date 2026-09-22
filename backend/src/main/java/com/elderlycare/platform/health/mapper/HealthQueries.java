package com.elderlycare.platform.health.mapper;

import com.elderlycare.platform.health.domain.*;
import com.elderlycare.platform.elder.domain.ElderProfileRow;
import com.elderlycare.platform.booking.domain.BindingRow;
import com.elderlycare.platform.identity.domain.UserAccount;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;

/** 健康对象范围统一由 XML 关联查询限定，禁止复用预约权限。 */
@Mapper
public interface HealthQueries {
    /** 分页读取本人或有效健康授权的老人。 */
    List<ElderProfileRow> elders(@Param("actor") UserAccount actor, @Param("now") Instant now, @Param("offset") long offset, @Param("limit") int limit);
    /** 统计同一权限条件下的老人。 */
    long elderCount(@Param("actor") UserAccount actor, @Param("now") Instant now);
    /** 返回当前可见老人，归档只对本人保留读取。 */
    ElderProfileRow elder(@Param("actor") UserAccount actor, @Param("elderId") long elderId, @Param("now") Instant now);
    /** 当前本人绑定同时检查绑定及账号有效性。 */
    BindingRow ownerBinding(@Param("actor") UserAccount actor, @Param("elderId") long elderId);
    /** 分页或有界趋势读取；先在数据库限定对象和代录归属，再解密。 */
    List<HealthRecordRow> records(@Param("actor") UserAccount actor, @Param("elderId") long elderId,
            @Param("type") String type, @Param("status") String status, @Param("scene") String scene,
            @Param("from") Instant from, @Param("to") Instant to, @Param("now") Instant now,
            @Param("offset") long offset, @Param("limit") int limit, @Param("ascending") boolean ascending);
    /** 统计与列表完全相同范围的记录。 */
    long recordCount(@Param("actor") UserAccount actor, @Param("elderId") long elderId,
            @Param("type") String type, @Param("status") String status, @Param("scene") String scene,
            @Param("from") Instant from, @Param("to") Instant to, @Param("now") Instant now);
    /** 详情共用列表权限，老人接管后原代录人不能读取。 */
    HealthRecordRow record(@Param("actor") UserAccount actor, @Param("id") long id, @Param("now") Instant now);
    /** 以期望版本更新当前指针；版本、历史和事件由同一事务提交。 */
    int advance(@Param("row") HealthRecordRow row, @Param("expected") long expected);
    /** 按社区账号统计最近精确账号核对次数。 */
    long resolveAttempts(@Param("actor") UserAccount actor, @Param("since") Instant since);
}

