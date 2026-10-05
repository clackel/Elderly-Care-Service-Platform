package com.elderlycare.platform.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.identity.domain.AdminAccountEventRow;
import org.apache.ibatis.annotations.Mapper;

/** 账号操作记录基础持久化。 */
@Mapper
public interface AdminAccountEventMapper extends BaseMapper<AdminAccountEventRow> {}
