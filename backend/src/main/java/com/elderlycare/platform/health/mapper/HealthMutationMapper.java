package com.elderlycare.platform.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.health.domain.HealthMutationRow;
import org.apache.ibatis.annotations.Mapper;

/** health_mutation_request 基础持久化接口。 */
@Mapper
public interface HealthMutationMapper extends BaseMapper<HealthMutationRow> {}

