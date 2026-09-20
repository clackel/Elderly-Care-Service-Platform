package com.elderlycare.platform.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.booking.domain.GrantRow;
import org.apache.ibatis.annotations.Mapper;

/** booking_grant基础持久化；写入由服务层事务与社区锁保护。 */
@Mapper
public interface GrantMapper extends BaseMapper<GrantRow> {}

