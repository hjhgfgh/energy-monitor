package com.energy.server.netty;

import com.energy.server.config.NettyProperties;
import com.energy.server.service.DeviceDataProcessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Netty TCP 设备接入服务。
 *
 * <p>在 Spring 容器启动完成后（ApplicationRunner）绑定端口并开始接收设备连接。
 * 用 ApplicationRunner 而非 {@code @PostConstruct}，是为了让失败信息落在应用启动阶段，
 * 端口被占用时能直接让进程启动失败，而不是留下一个「HTTP 起来了但设备连不上」的半死状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTcpServer implements ApplicationRunner {

    private final NettyProperties properties;
    private final DeviceDataProcessor processor;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        bossGroup = new NioEventLoopGroup(properties.getBossThreads());
        workerGroup = new NioEventLoopGroup(properties.getWorkerThreads());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, properties.getBacklog())
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new DeviceChannelInitializer(properties, processor));

            serverChannel = bootstrap.bind(properties.getPort()).sync().channel();
            log.info("设备 TCP 接入服务启动成功，监听端口 {}（读空闲超时 {} 秒）",
                    properties.getPort(), properties.getReaderIdleTimeoutSeconds());
        } catch (Exception e) {
            log.error("设备 TCP 接入服务启动失败，端口 {}", properties.getPort(), e);
            shutdown();
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭设备 TCP 接入服务...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
