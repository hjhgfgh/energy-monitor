package com.energy.simulator;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 设备模拟器入口。
 *
 * <p>用多台虚拟设备模拟真实电表的上报行为，避免为了演示去采购硬件。
 * 这本身也是一个可以讲的技术点：如何用程序构造可控的、可复现的测试数据源。
 */
@Slf4j
public class DeviceSimulator {

    public static void main(String[] args) {
        SimulatorOptions options = SimulatorOptions.fromArgs(args);
        log.info("设备模拟器启动，参数: {}", options);

        EventLoopGroup group = new NioEventLoopGroup();
        List<DeviceClient> clients = new ArrayList<>();
        int failed = 0;

        for (int i = 0; i < options.getDeviceCount(); i++) {
            long deviceId = options.getStartDeviceId() + i;
            DeviceClient client = new DeviceClient(group, options, deviceId);
            try {
                client.start();
                clients.add(client);
                log.info("设备 {} 连接成功（{}/{}）", deviceId, i + 1, options.getDeviceCount());
                Thread.sleep(50L);
            } catch (Exception e) {
                failed++;
                log.error("设备 {} 连接失败: {}", deviceId, e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            log.error("没有任何设备连接成功。请先启动 em-server，并确认 {}:{} 可访问",
                    options.getHost(), options.getPort());
            group.shutdownGracefully();
            System.exit(1);
        }

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭模拟器，共 {} 台设备...", clients.size());
            clients.forEach(DeviceClient::stop);
            group.shutdownGracefully();
            shutdownLatch.countDown();
        }));

        log.info("模拟器运行中: 成功 {} 台, 失败 {} 台, 上报间隔 {} ms，按 Ctrl+C 退出",
                clients.size(), failed, options.getIntervalMs());

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
