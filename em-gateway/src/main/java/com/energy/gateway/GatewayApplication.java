package com.energy.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关。
 *
 * <p>职责边界：
 * <ul>
 *   <li>统一入口 —— 外部只访问网关，不直接访问业务服务</li>
 *   <li>路由转发 —— 按路径前缀把请求分发到对应服务</li>
 *   <li>JWT 鉴权 —— 校验集中在此，业务服务不重复实现</li>
 *   <li>限流 —— Sentinel 在网关维度按路由限流</li>
 * </ul>
 *
 * <p><b>明确不管的事</b>：TCP 设备接入不经网关。
 * 网关基于 WebFlux 处理 HTTP，二进制长连接走不了这条路，
 * em-device-access 是独立暴露 9000 端口的接入服务。这是设计边界，不是疏漏。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
