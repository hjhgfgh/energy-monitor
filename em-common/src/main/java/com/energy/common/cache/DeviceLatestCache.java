package com.energy.common.cache;

import com.energy.common.dto.DeviceDataEvent;
import com.energy.common.entity.DeviceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 设备最新状态缓存。
 *
 * <p><b>缓存什么、为什么缓存这个：</b>大屏概览需要「每台设备当前电压/电流/功率」。
 * 直接查 {@code device_data} 要走「按设备分组取最大采集时间再回表」，
 * 在千万级数据下代价很高，而这份数据每台设备只占一行、且被高频读取——
 * 典型的「读多写少 + 可容忍秒级延迟」场景，正是缓存的适用面。
 *
 * <p><b>数据结构选 Hash 而非 String+JSON：</b>Hash 支持按字段读写，
 * 未来若只需更新某个指标（如额外加入温度），不必反序列化整个对象。
 * 同时 Hash 的字段名不会被重复存储，多设备场景下内存占用更优。
 *
 * <p><b>失效策略：</b>Cache Aside —— 写路径先更新数据库、再更新缓存；
 * 读路径未命中则回源并回填。这里不引入延迟双删，因为监控数据的业务语义
 * 允许秒级不一致，为它增加复杂度不划算（这个取舍要能说清）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceLatestCache {

    public static final String KEY_PREFIX = "device:latest:";

    /** 过期时间：设备离线后，其状态不应无限期占用内存 */
    private static final Duration TTL = Duration.ofMinutes(30);

    private static final String FIELD_VOLTAGE = "voltage";
    private static final String FIELD_CURRENT = "current";
    private static final String FIELD_POWER = "power";
    private static final String FIELD_COLLECT_TIME = "collectTime";

    private final StringRedisTemplate redisTemplate;

    /**
     * 写入设备最新状态。
     *
     * <p>整体包在 try-catch 里：缓存是加速手段而非数据源，
     * 它挂掉不能连累主链路。Redis 抖动时业务应当降级为直接查库。
     */
    public void update(DeviceDataEvent event) {
        String key = KEY_PREFIX + event.getDeviceId();
        try {
            Map<String, String> values = new HashMap<>(8);
            values.put(FIELD_VOLTAGE, String.valueOf(event.getVoltage()));
            values.put(FIELD_CURRENT, String.valueOf(event.getCurrent()));
            values.put(FIELD_POWER, String.valueOf(event.getPower()));
            values.put(FIELD_COLLECT_TIME, String.valueOf(event.getCollectTime()));

            redisTemplate.opsForHash().putAll(key, values);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("更新设备最新状态缓存失败: deviceId={}", event.getDeviceId(), e);
        }
    }

    /** 读取设备最新状态，未命中返回 empty 由调用方回源 */
    public Optional<DeviceData> find(long deviceId) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(KEY_PREFIX + deviceId);
            if (entries.isEmpty()) {
                return Optional.empty();
            }

            DeviceData data = new DeviceData();
            data.setDeviceId(deviceId);
            data.setVoltage(new BigDecimal(string(entries.get(FIELD_VOLTAGE))));
            data.setElectricCurrent(new BigDecimal(string(entries.get(FIELD_CURRENT))));
            data.setPower(new BigDecimal(string(entries.get(FIELD_POWER))));
            long millis = Long.parseLong(string(entries.get(FIELD_COLLECT_TIME)));
            data.setCollectTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
            return Optional.of(data);
        } catch (Exception e) {
            log.warn("读取设备最新状态缓存失败，降级为查库: deviceId={}", deviceId, e);
            return Optional.empty();
        }
    }

    /** 回填缓存（读路径未命中、从库中取到数据后调用） */
    public void put(DeviceData data) {
        try {
            String key = KEY_PREFIX + data.getDeviceId();
            Map<String, String> values = new HashMap<>(8);
            values.put(FIELD_VOLTAGE, String.valueOf(data.getVoltage()));
            values.put(FIELD_CURRENT, String.valueOf(data.getElectricCurrent()));
            values.put(FIELD_POWER, String.valueOf(data.getPower()));
            values.put(FIELD_COLLECT_TIME, String.valueOf(
                    data.getCollectTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));

            redisTemplate.opsForHash().putAll(key, values);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("回填设备最新状态缓存失败: deviceId={}", data.getDeviceId(), e);
        }
    }

    private static String string(Object value) {
        return value == null ? "0" : value.toString();
    }
}
