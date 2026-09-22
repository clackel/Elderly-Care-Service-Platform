package com.elderlycare.platform.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.health.domain.HealthRevisionRow;
import org.apache.ibatis.annotations.Mapper;

/** health_record_revision 基础持久化接口。 */
@Mapper
public interface HealthRevisionMapper extends BaseMapper<HealthRevisionRow> {}

