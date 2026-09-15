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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备查询业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceDataMapper deviceDataMapper;
    private final DeviceLatestCache latestCache;

    /** 缓存命中统计，用于验证缓存确实在起作用（而不是摆设） */
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();

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
     * <p>传 from 时间而非直接用 LIMIT：走 {@code uk_device_time} 的 range 扫描，
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

    /**
     * 每个设备的最新一条数据（大屏概览卡片）。
     *
     * <p>读路径：先逐个查缓存；对未命中的设备，用一条 SQL 批量回源
     * （{@code selectLatestPerDevice}），而不是每台设备查一次库——
     * 后者在设备数量增长后会变成 N 次查询，即典型的 N+1 问题。
     * 回源结果顺手回填缓存，下次请求即命中。
     */
    public List<DeviceData> latestPerDevice() {
        List<Device> devices = listDevices();
        List<DeviceData> result = new ArrayList<>(devices.size());
        Set<Long> cachedIds = new HashSet<>();

        for (Device device : devices) {
            Optional<DeviceData> cached = latestCache.find(device.getId());
            if (cached.isPresent()) {
                result.add(cached.get());
                cachedIds.add(device.getId());
                cacheHits.incrementAndGet();
            } else {
                cacheMisses.incrementAndGet();
            }
        }

        if (cachedIds.size() < devices.size()) {
            for (DeviceData fromDb : deviceDataMapper.selectLatestPerDevice()) {
                if (!cachedIds.contains(fromDb.getDeviceId())) {
                    result.add(fromDb);
                    latestCache.put(fromDb);
                }
            }
        }

        result.sort(Comparator.comparing(DeviceData::getDeviceId));
        return result;
    }

    /** 缓存命中率统计，供 /api/devices/cache-stats 暴露 */
    public String cacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        if (total == 0) {
            return "0/0 (尚无请求)";
        }
        return String.format("%d/%d = %.1f%%", hits, total, hits * 100.0 / total);
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /** 供测试或运维清空统计 */
    public void resetCacheStats() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }
}
