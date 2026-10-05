package com.elderlycare.platform.identity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 首个管理员开通专用持久化入口，不提供公开接口。 */
@Mapper
public interface AdminBootstrapMapper {
    /** 检查社区是否存在，区分尚未建立与已被停用的社区。 */
    int communityExists(@Param("id") long id);
    /** 仅在指定社区不存在时创建，唯一主键阻止并发重复创建。 */
    int createCommunity(@Param("id") long id, @Param("name") String name);
    /** 已有管理员或历史开通记录时禁止再次建立初始化账号。 */
    int initialized(@Param("communityId") long communityId);
}
