package com.energy.process.service.impl;

import com.energy.common.config.KafkaTopics;
import com.energy.common.dto.DeviceDataEvent;
import com.energy.process.service.AlarmEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 告警计算消费端。
 *
 * <p><b>与落库消费者用不同的消费组</b>（{@code em-alarm} vs {@code em-data-persist}）。
 * 这是 Kafka 的核心价值所在：同一个 topic 的同一份数据，可以被多个消费组各自独立消费。
 * 新增一个「告警计算」需求时，接入层（Netty）和落库逻辑都不用改，
 * 只需加一个消费者——这正是「解耦」的具体含义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "energy.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class AlarmConsumer {

    private final AlarmEngine alarmEngine;

    @KafkaListener(topics = KafkaTopics.DEVICE_DATA, groupId = KafkaTopics.GROUP_ALARM)
    public void onMessage(List<DeviceDataEvent> events, Acknowledgment ack) {
        if (events == null || events.isEmpty()) {
            ack.acknowledge();
            return;
        }

        for (DeviceDataEvent event : events) {
            // 逐条判定；单条失败不影响同批其他数据，也不阻断 offset 提交
            alarmEngine.evaluate(event);
        }
        ack.acknowledge();
    }
}
