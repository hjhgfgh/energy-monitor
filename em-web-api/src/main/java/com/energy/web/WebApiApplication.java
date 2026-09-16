package com.energy.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 业务查询服务。
 *
 * <p>对外提供设备与告警的查询接口。本服务只读数据库，
 * 写路径由 em-data-process 负责——这样读接口的扩展不会影响写入吞吐。
 *
 * <p>{@code @EnableFeignClients} 启用声明式服务调用：
 * 查询「在线设备」需要接入层的实时状态，而那份状态只存在于 em-device-access 的内存里，
 * 数据库中没有。这是服务间调用最自然的落点——跨服务获取「另一个服务才有的运行时状态」。
 *
 * <p><b>为什么要显式指定 scanBasePackages：</b>DeviceLatestCache、JwtTokenProvider
 * 等共享组件位于 {@code com.energy.common} 下，不在启动类所在包内，
 * 不声明就会报 {@code No qualifying bean}。
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.energy.web", "com.energy.common"})
public class WebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApiApplication.class, args);
    }
}
