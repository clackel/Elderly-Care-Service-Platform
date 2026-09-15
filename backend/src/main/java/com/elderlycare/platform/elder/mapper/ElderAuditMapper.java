package com.elderlycare.platform.elder.mapper;

import com.elderlycare.platform.elder.domain.ElderAuditRow;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ElderAuditMapper {
    /** 写入不含个人资料正文的访问或变更事件。 */
    @Insert("""
        INSERT INTO elder_profile_event
        (id,elder_id,community_id,actor_id,action,changed_fields,version,occurred_at)
        VALUES (#{id},#{elderId},#{communityId},#{actorId},#{action},#{changedFields},#{version},#{occurredAt})
        """)
    int insert(ElderAuditRow row);

    /** 统计变更事件，详情浏览事件仅保留在审计表。 */
    @Select("""
        SELECT COUNT(*) FROM elder_profile_event
        WHERE community_id=#{communityId} AND elder_id=#{elderId} AND action != 'VIEW'
        """)
    long count(@Param("communityId") long communityId, @Param("elderId") long elderId);

    /** 按版本和编号倒序分页读取指定社区档案的变更历史。 */
    @Select("""
        SELECT id,elder_id,community_id,actor_id,action,changed_fields,version,occurred_at
        FROM elder_profile_event
        WHERE community_id=#{communityId} AND elder_id=#{elderId} AND action != 'VIEW'
        ORDER BY version DESC, id DESC LIMIT #{limit} OFFSET #{offset}
        """)
    List<ElderAuditRow> page(@Param("communityId") long communityId, @Param("elderId") long elderId,
                            @Param("offset") long offset, @Param("limit") int limit);
}
