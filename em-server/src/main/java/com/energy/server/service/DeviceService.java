package com.energy.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.server.entity.Device;
import com.energy.server.entity.DeviceData;
import com.energy.server.exception.BusinessException;
import com.energy.server.mapper.DeviceDataMapper;
import com.energy.server.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备查询业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceDataMapper deviceDataMapper;

    /** 设备列表，按设备编码排序 */
    public List<Device> listDevices() {
        return deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().orderByAsc(Device::getDeviceCode));
    }

    public Device getDevice(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException("设备不存在: id=" + id);
        }
        return device;
    }

    /**
     * 查询设备最近若干小时的数据（大屏实时曲线）。
     *
     * <p>传 from 时间而非直接用 LIMIT：走 {@code idx_device_time} 的 range 扫描，
     * 数据量增长时性能不会线性劣化。
     */
    public List<DeviceData> recentData(Long deviceId, int hours, int limit) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        return deviceDataMapper.selectRecentByDevice(deviceId, from, limit);
    }

    /** 分页查询设备历史数据（依赖 MyBatis-Plus 分页插件） */
    public IPage<DeviceData> pageData(Long deviceId, int pageNum, int pageSize) {
        Page<DeviceData> page = new Page<>(pageNum, pageSize);
        return deviceDataMapper.selectPage(page,
                new LambdaQueryWrapper<DeviceData>()
                        .eq(DeviceData::getDeviceId, deviceId)
                        .orderByDesc(DeviceData::getCollectTime));
    }

    /** 每个设备的最新一条数据（大屏概览卡片） */
    public List<DeviceData> latestPerDevice() {
        return deviceDataMapper.selectLatestPerDevice();
    }
}
