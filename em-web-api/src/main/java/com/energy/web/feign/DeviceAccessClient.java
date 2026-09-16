package com.energy.web.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

/**
 * 调用 em-device-access 的声明式客户端。
 *
 * <p><b>为什么这里真的需要服务间调用：</b>「设备当前是否在线」取决于 TCP 连接存在与否，
 * 这份状态只存在于接入服务的运行时（以及它写入的 Redis），数据库里查不到。
 * 业务服务要展示这个信息，只能去问接入服务——这正是 Feign 的适用场景：
 * <b>获取另一个服务独有的运行时状态</b>。
 *
 * <p>如果只是查数据库就能拿到的数据，硬拆成服务间调用反而是反模式
 * （多一次网络往返、多一个故障点）。
 *
 * <p>{@code name} 填服务名而非具体地址：实例列表由 Nacos 提供，
 * 接入服务扩容或迁移时这里一行都不用改。
 */
@FeignClient(name = "em-device-access", path = "/internal/devices")
public interface DeviceAccessClient {

    /** 当前在线设备 ID 列表 */
    @GetMapping("/online")
    List<Long> onlineDevices();

    /** 在线设备概览（数量 + ID 列表） */
    @GetMapping("/online/summary")
    Map<String, Object> onlineSummary();
}
