package com.energy.server.service.impl;

import com.energy.common.protocol.CommandType;
import com.energy.common.protocol.DataPayload;
import com.energy.common.protocol.DeviceFrame;
import com.energy.server.config.KafkaTopics;
import com.energy.server.dto.DeviceDataEvent;
import com.energy.server.service.DeviceDataProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 版数据处理器：Netty 收到数据后投递到 topic，由下游消费端决定怎么处理。
 *
 * <p><b>为什么不直接入库，要多一跳 Kafka：</b>
 * <ol>
 *   <li><b>削峰</b>：设备上报是脉冲式的，MySQL 写入能力是恒定的。中间加一层可缓冲的日志，
 *       峰值被摊平，数据库不会被瞬时流量打满。</li>
 *   <li><b>解耦</b>：落库、告警计算、大屏推送是三个独立诉求。没有 Kafka 时，
 *       每加一个消费方都要改接入层代码；有了 Kafka，新增消费方只是多一个消费组，
 *       接入层完全不用动。</li>
 *   <li><b>可重放</b>：消费方宕机或逻辑出错时，offset 回退即可重新消费，
 *       数据不会丢。直连入库一旦写失败，数据就永久没了。</li>
 * </ol>
 *
 * <p><b>关键实现点：</b>用 {@code send()} 的异步回调而非 {@code get()} 同步等待。
 * Netty 的 IO 线程承担着所有设备连接的读写，一旦在这里阻塞等 broker 应答，
 * 整条 EventLoop 上的设备都会被拖慢——这与「IO 线程不做阻塞操作」是同一条纪律。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "energy.processor.mode", havingValue = "kafka")
public class KafkaDeviceDataProducer implements DeviceDataProcessor {

    private final KafkaTemplate<String, DeviceDataEvent> kafkaTemplate;

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    @Override
    public void process(DeviceFrame frame) {
        if (frame.getCommand() != CommandType.DATA_REPORT) {
            return;
        }

        DataPayload payload = frame.asDataPayload();
        DeviceDataEvent event = DeviceDataEvent.builder()
                .deviceId(frame.getDeviceId())
                .voltage(payload.getVoltage())
                .current(payload.getCurrent())
                .power(payload.getPower())
                .collectTime(payload.getCollectTime())
                .build();

        long deviceId = frame.getDeviceId();
        kafkaTemplate.send(KafkaTopics.DEVICE_DATA, String.valueOf(deviceId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        long failCount = failed.incrementAndGet();
                        log.error("投递 Kafka 失败（累计 {} 条）: deviceId={}", failCount, deviceId, ex);
                        return;
                    }
                    long okCount = sent.incrementAndGet();
                    if (okCount % 1000 == 0) {
                        log.info("已投递 {} 条设备数据到 Kafka（失败 {} 条）", okCount, failed.get());
                    }
                });
    }
}
