package com.energy.server.service;

import com.energy.server.dto.DeviceDataEvent;
import com.energy.server.dto.RealtimeMessage;
import com.energy.server.entity.Alarm;
import com.energy.server.entity.AlarmRule;
import com.energy.server.entity.Device;
import com.energy.server.mapper.AlarmMapper;
import com.energy.server.mapper.AlarmRuleMapper;
import com.energy.server.mapper.DeviceMapper;
import com.energy.server.websocket.DeviceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警规则引擎。
 *
 * <p><b>规则为什么不硬编码：</b>阈值类需求会频繁变化，且不同设备类型阈值不同。
 * 把规则做成数据表，新增或调整规则只需改数据、不用改代码也不用重启，
 * 这是「配置优于编码」在监控场景的直接体现。
 *
 * <p><b>为什么需要 Redis 锁：</b>落库消费组可以水平扩展（起两个实例提高吞吐）。
 * 此时同一条设备数据可能被两个实例同时处理，若两边的判定都通过，
 * 就会对同一次异常写出两条告警记录。锁把「判定 + 写入」保护成临界区，
 * 保证一条异常只产生一条告警。
 *
 * <p><b>锁的 TTL 就是去重窗口</b>（这里 5 分钟）。成功告警后**不主动释放锁**，
 * 而是让 key 自然过期——这样窗口期内相同规则的重复触发会被 {@code tryLock} 直接挡掉。
 * 这个设计把「互斥」和「去重」合并成一次原子操作，比「加锁-释放 + 另设标记 key」更简洁。
 * 反过来，写库失败时必须释放锁，否则这条异常在窗口期内再也无法补写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEngine {

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(5);
    private static final String LOCK_KEY_PREFIX = "alarm:dedup:";

    /** 规则缓存有效期：规则是低频变更数据，不必每条消息都查库 */
    private static final long RULE_CACHE_MILLIS = 60_000L;

    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmMapper alarmMapper;
    private final DeviceMapper deviceMapper;
    private final RedisLock redisLock;
    private final DeviceWebSocketHandler webSocketHandler;

    private volatile List<AlarmRule> ruleCache = List.of();
    private volatile long ruleLoadedAt = 0L;

    /** deviceId → 设备类型，设备信息极少变动，进程内缓存即可 */
    private final Map<Long, String> deviceTypeCache = new ConcurrentHashMap<>();

    /**
     * 对一条设备数据执行全部规则判定。
     *
     * <p>单条数据判定的异常不能中断整批消费，所以这里吞掉异常只记日志。
     */
    public void evaluate(DeviceDataEvent event) {
        try {
            String deviceType = deviceTypeOf(event.getDeviceId());

            for (AlarmRule rule : enabledRules()) {
                if (rule.getDeviceType() != null && !rule.getDeviceType().equals(deviceType)) {
                    continue;
                }

                BigDecimal value = metricValue(event, rule.getMetric());
                if (value == null) {
                    continue;
                }
                if (!compare(value, rule.getOperator(), rule.getThreshold())) {
                    continue;
                }

                trigger(event, rule, value);
            }
        } catch (Exception e) {
            log.error("告警判定异常: deviceId={}", event.getDeviceId(), e);
        }
    }

    private void trigger(DeviceDataEvent event, AlarmRule rule, BigDecimal value) {
        String lockKey = LOCK_KEY_PREFIX + event.getDeviceId() + ":" + rule.getId();
        String token = redisLock.tryLock(lockKey, DEDUP_WINDOW);
        if (token == null) {
            // 窗口期内该设备该规则已告警过，跳过
            return;
        }

        try {
            Alarm alarm = new Alarm();
            alarm.setDeviceId(event.getDeviceId());
            alarm.setRuleId(rule.getId());
            alarm.setLevel(rule.getLevel());
            alarm.setMetricValue(value);
            alarm.setContent(buildContent(event.getDeviceId(), rule, value));
            alarm.setStatus(0);
            alarmMapper.insert(alarm);

            log.warn("触发告警 | 设备 {} | {} | 实际值 {} 阈值 {} | {}",
                    event.getDeviceId(), rule.getName(), value, rule.getThreshold(), rule.getOperator());

            webSocketHandler.broadcast(RealtimeMessage.alarm(alarm));
        } catch (Exception e) {
            // 写库失败要释放锁，否则这条异常在去重窗口内无法补写
            redisLock.unlock(lockKey, token);
            log.error("告警落库失败，已释放去重锁: deviceId={}, ruleId={}",
                    event.getDeviceId(), rule.getId(), e);
        }
    }

    private static String buildContent(long deviceId, AlarmRule rule, BigDecimal value) {
        return String.format("设备 %d 的 %s 为 %s，%s 阈值 %s",
                deviceId, rule.getMetric(), value, operatorText(rule.getOperator()), rule.getThreshold());
    }

    private static String operatorText(String operator) {
        return switch (operator) {
            case "GT" -> "超过";
            case "LT" -> "低于";
            case "GE" -> "不低于";
            case "LE" -> "不高于";
            default -> operator;
        };
    }

    /** 拉取启用的规则，带 60 秒进程内缓存 */
    private List<AlarmRule> enabledRules() {
        long now = System.currentTimeMillis();
        if (now - ruleLoadedAt > RULE_CACHE_MILLIS) {
            synchronized (this) {
                if (now - ruleLoadedAt > RULE_CACHE_MILLIS) {
                    ruleCache = alarmRuleMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlarmRule>()
                                    .eq(AlarmRule::getEnabled, 1));
                    ruleLoadedAt = now;
                    log.debug("已刷新告警规则缓存，共 {} 条", ruleCache.size());
                }
            }
        }
        return ruleCache;
    }

    private String deviceTypeOf(long deviceId) {
        return deviceTypeCache.computeIfAbsent(deviceId, id -> {
            Device device = deviceMapper.selectById(id);
            return device == null ? null : device.getType();
        });
    }

    private static BigDecimal metricValue(DeviceDataEvent event, String metric) {
        return switch (metric) {
            // 必须经 Float.toString 中转。BigDecimal.valueOf(float) 会先隐式转成 double，
            // 把 240.73 变成 240.72999572753906——既影响阈值比较，也让告警文案失真
            case "voltage" -> toDecimal(event.getVoltage());
            case "electric_current" -> toDecimal(event.getCurrent());
            case "power" -> toDecimal(event.getPower());
            default -> null;
        };
    }

    private static BigDecimal toDecimal(float value) {
        return new BigDecimal(Float.toString(value));
    }

    private static boolean compare(BigDecimal value, String operator, BigDecimal threshold) {
        int cmp = value.compareTo(threshold);
        return switch (operator) {
            case "GT" -> cmp > 0;
            case "LT" -> cmp < 0;
            case "GE" -> cmp >= 0;
            case "LE" -> cmp <= 0;
            default -> false;
        };
    }
}
