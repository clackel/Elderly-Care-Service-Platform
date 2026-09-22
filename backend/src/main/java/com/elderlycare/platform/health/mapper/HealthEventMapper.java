package com.elderlycare.platform.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.health.domain.HealthEventRow;
import org.apache.ibatis.annotations.Mapper;

/** health_event 基础持久化接口。 */
@Mapper
public interface HealthEventMapper extends BaseMapper<HealthEventRow> {}

