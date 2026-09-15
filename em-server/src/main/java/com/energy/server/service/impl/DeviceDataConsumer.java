package com.energy.server.service.impl;

import com.energy.server.config.KafkaTopics;
import com.energy.server.dto.DeviceDataEvent;
import com.energy.server.dto.RealtimeMessage;
import com.energy.server.entity.DeviceData;
import com.energy.server.mapper.DeviceDataMapper;
import com.energy.server.service.DeviceLatestCache;
import com.energy.server.websocket.DeviceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备数据消费端：批量落库 + 更新缓存 + 推送大屏。
 *
 * <p><b>手动提交 offset</b>（{@code enable-auto-commit=false}）是这里的核心决策。
 * 自动提交的语义是「消息被取到就记为已消费」，一旦入库失败，offset 已经前移，
 * 这批数据就永久丢失。手动提交把「提交」和「业务处理成功」绑在一起，
 * 处理失败就不提交，Kafka 会重新投递。
 *
 * <p>代价是必须处理重复：重投意味着同一条消息可能被处理两次，
 * 所以落库走 {@code ON DUPLICATE KEY UPDATE} 依赖唯一索引保证幂等。
 * 这是「至少一次投递」下的标准组合拳。
 *
 * <p><b>已知限制</b>：若某条消息因数据本身有问题而永远处理失败，会无限重投阻塞分区。
 * 生产环境需要引入重试上限 + 死信队列（DLQ），本项目暂未实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "energy.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceDataConsumer {

    private final DeviceDataMapper deviceDataMapper;
    private final DeviceLatestCache latestCache;
    private final DeviceWebSocketHandler webSocketHandler;

    private final AtomicLong persistedRows = new AtomicLong();
    private final AtomicLong batches = new AtomicLong();

    @KafkaListener(topics = KafkaTopics.DEVICE_DATA, groupId = KafkaTopics.GROUP_DATA_PERSIST)
    public void onMessage(List<DeviceDataEvent> events, Acknowledgment ack) {
        if (events == null || events.isEmpty()) {
            ack.acknowledge();
            return;
        }

        try {
            List<DeviceData> entities = events.stream().map(DeviceDataConsumer::toEntity).toList();
            deviceDataMapper.insertBatchIgnoreDuplicate(entities);
            ack.acknowledge();

            refreshCacheAndPush(events);

            long total = persistedRows.addAndGet(entities.size());
            long batchNo = batches.incrementAndGet();
            if (batchNo % 20 == 0) {
                log.info("落库进度：已处理 {} 批 / {} 条数据（当前批 {} 条）",
                        batchNo, total, entities.size());
            }
        } catch (Exception e) {
            // 不 ack：让这批消息重新投递。重复消费由唯一索引兜底
            log.error("批量入库失败，本批 {} 条不提交 offset，等待重投", events.size(), e);
        }
    }

    /**
     * 更新最新状态缓存，并把数据推给大屏。
     *
     * <p>推送做了一层降采样：缓存更新逐条进行（内存操作，代价极低），
     * 但 WebSocket 只推每台设备在本批中的**最后一条**。
     * 若不降采样，消费积压时单批 500 条会触发 500 次广播——
     * 而大屏只需要知道「现在是多少」，中间过程对展示毫无价值。
     */
    private void refreshCacheAndPush(List<DeviceDataEvent> events) {
        Map<Long, DeviceDataEvent> latestPerDevice = new LinkedHashMap<>();

        for (DeviceDataEvent event : events) {
            latestCache.update(event);
            // 同设备后写覆盖先写，最终保留本批最后一条
            latestPerDevice.put(event.getDeviceId(), event);
        }

        latestPerDevice.values().forEach(
                latest -> webSocketHandler.broadcast(RealtimeMessage.data(latest)));
    }

    private static DeviceData toEntity(DeviceDataEvent event) {
        DeviceData entity = new DeviceData();
        entity.setDeviceId(event.getDeviceId());
        entity.setVoltage(toDecimal(event.getVoltage()));
        entity.setElectricCurrent(toDecimal(event.getCurrent()));
        entity.setPower(toDecimal(event.getPower()));
        entity.setCollectTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.getCollectTime()), ZoneId.systemDefault()));
        return entity;
    }

    /** float → BigDecimal，必须经 Float.toString 中转，否则会带入二进制浮点误差 */
    private static BigDecimal toDecimal(float value) {
        return new BigDecimal(Float.toString(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
