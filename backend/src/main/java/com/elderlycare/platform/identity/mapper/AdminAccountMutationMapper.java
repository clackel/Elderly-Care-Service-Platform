package com.elderlycare.platform.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.identity.domain.AdminAccountMutationRow;
import org.apache.ibatis.annotations.Mapper;

/** 账号写请求幂等凭据基础持久化。 */
@Mapper
public interface AdminAccountMutationMapper extends BaseMapper<AdminAccountMutationRow> {}
