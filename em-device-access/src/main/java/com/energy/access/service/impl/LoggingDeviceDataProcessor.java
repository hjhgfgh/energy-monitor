package com.energy.access.service.impl;

import com.energy.common.protocol.DeviceFrame;
import com.energy.access.service.DeviceDataProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * P1 阶段数据处理器：只计数与抽样打印，用于先验证「设备 → TCP → 解析」链路是否打通。
 *
 * <p>建库后通过 {@code energy.processor.mode=db} 切换到落库实现，无需改动 Netty 流水线。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "energy.processor.mode", havingValue = "log", matchIfMissing = true)
public class LoggingDeviceDataProcessor implements DeviceDataProcessor {

    private static final long LOG_INTERVAL = 100L;

    private final AtomicLong totalFrames = new AtomicLong();
    private final AtomicLong dataFrames = new AtomicLong();

    @Override
    public void process(DeviceFrame frame) {
        long total = totalFrames.incrementAndGet();

        if (frame.getCommand() == com.energy.common.protocol.CommandType.DATA_REPORT) {
            long dataCount = dataFrames.incrementAndGet();
            if (dataCount % LOG_INTERVAL == 0) {
                log.info("数据帧计数: 累计上报 {} 帧, 总帧数 {} 帧", dataCount, total);
            }
        }
    }
}
