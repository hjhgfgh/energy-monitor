package com.energy.access.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 在线设备注册表。
 *
 * <p><b>为什么用 Redis 而不是进程内 Map：</b>接入服务是可以水平扩展的——
 * 设备连接分散在多个实例上（示例里单实例，但设计上按多实例考虑）。
 * 用进程内 Map 的话，每个实例只知道自己持有的连接，
 * em-web-api 通过 Feign 落到哪个实例就只能看到那部分设备。
 * 放进 Redis 后，任何实例、任何消费方看到的都是完整的在线集合。
 *
 * <p><b>用 Set 而非计数器：</b>设备重连时先 add 再 remove 可能出现顺序交错，
 * 计数器会算错；Set 天然幂等，重复登记不会让数量虚增。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlineDeviceRegistry {

    private static final String KEY_ONLINE_DEVICES = "device:online";

    private final StringRedisTemplate redisTemplate;

    /** 设备连接建立时登记。失败不抛异常——登记不上不该影响设备正常上报 */
    public void markOnline(long deviceId) {
        try {
            redisTemplate.opsForSet().add(KEY_ONLINE_DEVICES, String.valueOf(deviceId));
        } catch (Exception e) {
            log.warn("登记在线设备失败: deviceId={}", deviceId, e);
        }
    }

    /** 设备断开时移除 */
    public void markOffline(long deviceId) {
        try {
            redisTemplate.opsForSet().remove(KEY_ONLINE_DEVICES, String.valueOf(deviceId));
        } catch (Exception e) {
            log.warn("移除在线设备失败: deviceId={}", deviceId, e);
        }
    }

    public Set<String> onlineDevices() {
        try {
            Set<String> members = redisTemplate.opsForSet().members(KEY_ONLINE_DEVICES);
            return members == null ? Set.of() : members;
        } catch (Exception e) {
            log.warn("查询在线设备失败", e);
            return Set.of();
        }
    }

    public long onlineCount() {
        try {
            Long size = redisTemplate.opsForSet().size(KEY_ONLINE_DEVICES);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.warn("统计在线设备失败", e);
            return 0L;
        }
    }
}
