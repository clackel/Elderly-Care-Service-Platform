package com.elderlycare.platform.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderlycare.platform.booking.domain.BookingRow;
import org.apache.ibatis.annotations.Mapper;

/** service_booking基础持久化；写入由服务层事务与社区锁保护。 */
@Mapper
public interface BookingMapper extends BaseMapper<BookingRow> {}

