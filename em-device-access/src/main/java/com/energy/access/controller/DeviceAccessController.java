package com.energy.access.controller;

import com.energy.access.service.OnlineDeviceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接入服务的内部接口。
 *
 * <p>路径前缀刻意用 {@code /internal/}，且网关**不配置它的路由**——
 * 这类接口只给其他服务通过服务发现调用，不应暴露给外部。
 * 把「内部接口」和「对外接口」在路径上分开，是为了让暴露范围一目了然。
 *
 * <p>在线设备是接入层独有的运行时状态（TCP 连接在哪台机器上），
 * 数据库里没有，所以 em-web-api 必须通过服务调用来获取——
 * 这正是 OpenFeign 在这里存在的理由。
 */
@RestController
@RequestMapping("/internal/devices")
@RequiredArgsConstructor
public class DeviceAccessController {

    private final OnlineDeviceRegistry registry;

    /** 当前在线设备 ID 列表 */
    @GetMapping("/online")
    public List<Long> onlineDevices() {
        return registry.onlineDevices().stream()
                .map(Long::parseLong)
                .sorted()
                .toList();
    }

    /** 在线设备数量概览 */
    @GetMapping("/online/summary")
    public Map<String, Object> onlineSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", registry.onlineCount());
        summary.put("deviceIds", registry.onlineDevices().stream().map(Long::parseLong).sorted().toList());
        return summary;
    }
}
