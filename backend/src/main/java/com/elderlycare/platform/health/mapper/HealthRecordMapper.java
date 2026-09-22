package com.elderlycare.platform.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.health.domain.HealthRecordRow;
import org.apache.ibatis.annotations.Mapper;

/** health_record 基础持久化接口。 */
@Mapper
public interface HealthRecordMapper extends BaseMapper<HealthRecordRow> {}

