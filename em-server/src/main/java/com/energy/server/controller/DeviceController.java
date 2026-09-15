package com.energy.server.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.energy.server.dto.Result;
import com.energy.server.entity.Device;
import com.energy.server.entity.DeviceData;
import com.energy.server.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备查询接口。
 *
 * <p>路径变量写成 {@code {id:\d+}} 而非 {@code {id}}：
 * 否则 {@code /api/devices/latest-data} 会先命中 {@code /{id}}，
 * 再因 "latest-data" 无法转成 Long 而抛类型转换异常（400）。
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /** 设备列表 */
    @GetMapping
    public Result<List<Device>> list() {
        return Result.ok(deviceService.listDevices());
    }

    /**
     * 缓存命中统计。
     *
     * <p>暴露这个接口是为了让「缓存有没有真的起作用」可被验证——
     * 否则加了缓存也只有靠猜，命中率无法量化验证。
     */
    @GetMapping("/cache-stats")
    public Result<Map<String, Object>> cacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("hits", deviceService.getCacheHits());
        stats.put("misses", deviceService.getCacheMisses());
        stats.put("hitRate", deviceService.cacheHitRate());
        return Result.ok(stats);
    }

    /** 各设备最新一条数据（大屏概览卡片） */
    @GetMapping("/latest-data")
    public Result<List<DeviceData>> latestData() {
        return Result.ok(deviceService.latestPerDevice());
    }

    /** 设备详情 */
    @GetMapping("/{id:\\d+}")
    public Result<Device> detail(@PathVariable Long id) {
        return Result.ok(deviceService.getDevice(id));
    }

    /** 设备最近若干小时的实时曲线数据 */
    @GetMapping("/{id:\\d+}/recent")
    public Result<List<DeviceData>> recent(@PathVariable Long id,
                                           @RequestParam(defaultValue = "24") int hours,
                                           @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(deviceService.recentData(id, hours, limit));
    }

    /** 设备历史数据分页 */
    @GetMapping("/{id:\\d+}/page")
    public Result<IPage<DeviceData>> page(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(deviceService.pageData(id, page, size));
    }
}
