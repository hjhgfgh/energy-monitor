package com.energy.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Netty 设备接入相关配置，对应 application.yml 中的 {@code energy.netty.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "energy.netty")
public class NettyProperties {

    /** TCP 监听端口 */
    private int port = 9000;

    /** boss 线程数，通常 1 即可（只负责 accept） */
    private int bossThreads = 1;

    /** worker 线程数，0 表示由 Netty 按 CPU 核数自动决定 */
    private int workerThreads = 0;

    /** 单帧最大长度，防止非法 length 值撑爆内存 */
    private int maxFrameLength = 1024;

    /** 读空闲超时（秒），超过则判定设备掉线 */
    private int readerIdleTimeoutSeconds = 90;

    /** TCP 半连接队列长度 */
    private int backlog = 128;
}
