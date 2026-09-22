package com.elderlycare.platform.consent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.consent.domain.HealthGrantRow;
import org.apache.ibatis.annotations.Mapper;

/** health_grant 基础持久化接口。 */
@Mapper
public interface HealthGrantMapper extends BaseMapper<HealthGrantRow> {}

