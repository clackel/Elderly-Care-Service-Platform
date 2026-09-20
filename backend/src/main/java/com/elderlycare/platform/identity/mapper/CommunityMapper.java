package com.elderlycare.platform.identity.mapper;

import com.elderlycare.platform.identity.domain.Community;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommunityMapper {
    /** 写事务中锁定有效社区，保证预约安排、权限变更及档案归档互斥。 */
    Community lockActiveById(@Param("id") long id);
    Community findActiveById(@Param("id") long id);
}
