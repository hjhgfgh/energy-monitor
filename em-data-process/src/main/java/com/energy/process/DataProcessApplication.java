package com.energy.process;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 数据处理服务。
 *
 * <p>消费 Kafka 中的设备数据，做三件事：
 * <ol>
 *   <li>批量落库（持久化）</li>
 *   <li>更新 Redis 中的设备最新状态（加速大屏读取）</li>
 *   <li>规则判定并写告警，同时通过 WebSocket 推送给前端</li>
 * </ol>
 *
 * <p>这是整个系统里唯一写库的业务服务；em-web-api 只读。
 * 读写分离到不同服务，好处是「写压力」和「读压力」可以各自独立扩容。
 *
 * <p><b>为什么要显式指定 scanBasePackages：</b>默认只扫描启动类所在包，
 * 而 DeviceLatestCache、JwtTokenProvider 等共享组件在 {@code com.energy.common} 下，
 * 不加这一项会报 {@code No qualifying bean of type 'DeviceLatestCache'}。
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.energy.process", "com.energy.common"})
public class DataProcessApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataProcessApplication.class, args);
    }
}
