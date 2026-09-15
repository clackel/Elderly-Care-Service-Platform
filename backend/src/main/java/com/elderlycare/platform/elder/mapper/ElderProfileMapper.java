package com.elderlycare.platform.elder.mapper;

import com.elderlycare.platform.elder.domain.ElderProfileRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ElderProfileMapper {
    /** 按可信社区范围读取单个档案，不存在时返回null。 */
    ElderProfileRow find(@Param("communityId") long communityId, @Param("id") long id);
    /** 查询同社区幂等请求已经创建的档案。 */
    ElderProfileRow findRequest(@Param("communityId") long communityId, @Param("requestId") String requestId);
    /** 统计满足状态和精确检索摘要的档案数。 */
    long count(@Param("communityId") long communityId, @Param("status") String status, @Param("keywordHash") String keywordHash);
    /** 按创建时间和编号倒序分页；所有条件均使用参数绑定。 */
    List<ElderProfileRow> page(@Param("communityId") long communityId, @Param("status") String status,
                              @Param("keywordHash") String keywordHash, @Param("offset") long offset, @Param("limit") int limit);
    /** 插入加密档案，由数据库唯一约束处理并发建档重复。 */
    int insert(ElderProfileRow row);
    /** 按社区、版本和在管状态更新资料，返回实际影响行数。 */
    int update(ElderProfileRow row);
    /** 按预期状态及版本完成归档或恢复，返回实际影响行数。 */
    int transition(@Param("communityId") long communityId, @Param("id") long id,
                   @Param("version") long version, @Param("from") String from, @Param("to") String to,
                   @Param("updatedAt") java.time.Instant updatedAt);
}
