package com.energy.simulator;

import com.energy.common.protocol.DataPayload;
import com.energy.common.protocol.DeviceFrame;
import com.energy.common.protocol.DeviceFrameEncoder;
import com.energy.common.protocol.ProtocolConstants;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 一台虚拟设备：维持一条 TCP 长连接，周期性上报数据并定时发心跳。
 */
@Slf4j
public class DeviceClient {

    private static final int DECIMAL_SCALE = 100;

    private final EventLoopGroup group;
    private final SimulatorOptions options;
    private final long deviceId;

    /** 每台设备的基准电压/电流略有差异，让大屏曲线更好看，也便于区分设备 */
    private final float baseVoltage;
    private final float baseCurrent;

    private final AtomicLong sentFrames = new AtomicLong();

    private Channel channel;
    private ScheduledFuture<?> dataTask;
    private ScheduledFuture<?> heartbeatTask;

    public DeviceClient(EventLoopGroup group, SimulatorOptions options, long deviceId) {
        this.group = group;
        this.options = options;
        this.deviceId = deviceId;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.baseVoltage = 220f + random.nextFloat(-3f, 3f);
        this.baseCurrent = 5f + random.nextFloat(-1.5f, 1.5f);
    }

    public long getDeviceId() {
        return deviceId;
    }

    public long getSentFrames() {
        return sentFrames.get();
    }

    /** 建立连接并启动定时上报 */
    public void start() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // P1 只做上行，暂不处理服务端下发指令
                    }
                });

        channel = bootstrap.connect(options.getHost(), options.getPort()).sync().channel();
        scheduleTasks();
    }

    private void scheduleTasks() {
        dataTask = channel.eventLoop().scheduleAtFixedRate(
                this::sendDataReport, 0, options.getIntervalMs(), TimeUnit.MILLISECONDS);
        heartbeatTask = channel.eventLoop().scheduleAtFixedRate(
                this::sendHeartbeat, 0, ProtocolConstants.HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void sendDataReport() {
        try {
            if (!isActive()) {
                return;
            }
            writeFrame(DeviceFrame.dataReport(deviceId, generatePayload()));
            sentFrames.incrementAndGet();
        } catch (Exception e) {
            log.warn("设备 {} 上报数据失败: {}", deviceId, e.getMessage());
        }
    }

    private void sendHeartbeat() {
        try {
            if (!isActive()) {
                return;
            }
            writeFrame(DeviceFrame.heartbeat(deviceId));
            log.debug("设备 {} 已发送心跳", deviceId);
        } catch (Exception e) {
            log.warn("设备 {} 发送心跳失败: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 生成一帧模拟数据。
     *
     * <p>5% 概率注入异常电压波动，用于验证后续的告警规则能否被触发——
     * 没有异常数据，告警功能就无法演示。
     */
    private DataPayload generatePayload() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float voltage = baseVoltage + random.nextFloat(-2f, 2f);
        float current = baseCurrent + random.nextFloat(-0.5f, 0.5f);

        if (random.nextFloat() < 0.05f) {
            voltage += random.nextFloat(10f, 30f);
        }

        float power = voltage * current;
        return DataPayload.builder()
                .voltage(round2(voltage))
                .current(round2(current))
                .power(round2(power))
                .collectTime(System.currentTimeMillis())
                .build();
    }

    private static float round2(float value) {
        return Math.round(value * DECIMAL_SCALE) / (float) DECIMAL_SCALE;
    }

    private boolean isActive() {
        return channel != null && channel.isActive();
    }

    private void writeFrame(DeviceFrame frame) {
        ByteBuf buffer = channel.alloc().buffer(DeviceFrameEncoder.estimateLength(frame));
        DeviceFrameEncoder.encode(frame, buffer);
        channel.writeAndFlush(buffer);
    }

    /** 停止上报并断开连接 */
    public void stop() {
        if (dataTask != null) {
            dataTask.cancel(false);
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
        if (channel != null) {
            channel.close();
        }
    }
}
