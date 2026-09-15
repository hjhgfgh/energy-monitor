package com.energy.server.service.impl;

import com.energy.common.protocol.CommandType;
import com.energy.common.protocol.DataPayload;
import com.energy.common.protocol.DeviceFrame;
import com.energy.server.entity.DeviceData;
import com.energy.server.mapper.DeviceDataMapper;
import com.energy.server.service.DeviceDataProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 落库版数据处理器。
 *
 * <p><b>核心设计：Netty IO 线程绝不执行 JDBC。</b>
 * JDBC 是阻塞调用，若直接在 {@code channelRead0} 里写库，一旦数据库变慢，
 * 阻塞的就是整条 EventLoop 上的所有设备连接——一个慢 SQL 能拖垮全部设备。
 *
 * <p>因此这里做职责分离：
 * <pre>
 * Netty IO 线程   --offer-->  有界队列  --poll-->  独立消费线程  --JDBC-->  MySQL
 *   （非阻塞）                 （削峰）              （可阻塞）
 * </pre>
 *
 * <p>队列满时选择丢弃并计数，而不是阻塞 IO 线程。
 * 监控数据允许少量丢失，但不能因为存储故障导致设备全部掉线——这是业务取舍。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "energy.processor.mode", havingValue = "db")
public class PersistingDeviceDataProcessor implements DeviceDataProcessor {

    /** 队列容量：按 5000 QPS 估算，可缓冲约 20 秒的峰值流量 */
    private static final int QUEUE_CAPACITY = 100_000;

    /** 单批最多处理条数，避免队列长期积压时单批过大 */
    private static final int BATCH_SIZE = 200;

    private static final long POLL_TIMEOUT_MS = 1000L;

    private final DeviceDataMapper deviceDataMapper;

    private final BlockingQueue<DeviceData> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    private ExecutorService consumer;
    private final CountDownLatch stopped = new CountDownLatch(1);

    @PostConstruct
    void start() {
        consumer = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "device-data-persister");
            thread.setDaemon(true);
            return thread;
        });
        consumer.submit(this::consumeLoop);
        log.info("设备数据落库线程已启动，队列容量 {}", QUEUE_CAPACITY);
    }

    @PreDestroy
    void stop() {
        log.info("正在停止设备数据落库线程，剩余队列 {} 条...", queue.size());
        consumer.shutdownNow();
        try {
            if (!consumer.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("落库线程未能在 5 秒内退出，已入库 {} 条，丢弃 {} 条",
                        persisted.get(), dropped.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        stopped.countDown();
    }

    @Override
    public void process(DeviceFrame frame) {
        if (frame.getCommand() != CommandType.DATA_REPORT) {
            return;
        }
        DeviceData entity = toEntity(frame);
        if (!queue.offer(entity)) {
            long total = dropped.incrementAndGet();
            if (total % 1000 == 1) {
                log.warn("落库队列已满，累计丢弃 {} 条数据", total);
            }
        }
    }

    /** 消费循环：批量取、逐条插，异常不退出循环 */
    private void consumeLoop() {
        List<DeviceData> batch = new ArrayList<>(BATCH_SIZE);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DeviceData first = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);

                for (DeviceData data : batch) {
                    deviceDataMapper.insert(data);
                }

                long total = persisted.addAndGet(batch.size());
                if (total % 1000 < BATCH_SIZE) {
                    log.info("已入库 {} 条设备数据（队列余 {}）", total, queue.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 单批失败不能终止整个消费循环，否则后续数据全部堆积
                log.error("批量入库失败，丢弃本批 {} 条数据", batch.size(), e);
            } finally {
                batch.clear();
            }
        }
        log.info("设备数据落库线程已退出，累计入库 {} 条，丢弃 {} 条", persisted.get(), dropped.get());
    }

    private DeviceData toEntity(DeviceFrame frame) {
        DataPayload payload = frame.asDataPayload();
        DeviceData entity = new DeviceData();
        entity.setDeviceId(frame.getDeviceId());
        entity.setVoltage(toDecimal(payload.getVoltage()));
        entity.setElectricCurrent(toDecimal(payload.getCurrent()));
        entity.setPower(toDecimal(payload.getPower()));
        entity.setCollectTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(payload.getCollectTime()), ZoneId.systemDefault()));
        return entity;
    }

    /**
     * float → BigDecimal。
     *
     * <p>必须经 {@link Float#toString} 中转：直接用 {@code new BigDecimal(float)} 会展开成
     * 二进制浮点的完整小数（如 217.43 变成 217.43000030517578），落库后值就变了。
     */
    private static BigDecimal toDecimal(float value) {
        return new BigDecimal(Float.toString(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
