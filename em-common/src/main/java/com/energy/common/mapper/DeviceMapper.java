package com.energy.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.energy.common.entity.Device;

/**
 * 设备 Mapper。
 *
 * <p>继承 {@code BaseMapper} 即获得单表 CRUD 能力，无需编写 XML。
 * 复杂多表查询仍建议手写 SQL——这是 MyBatis-Plus 的适用边界。
 */
public interface DeviceMapper extends BaseMapper<Device> {
}
