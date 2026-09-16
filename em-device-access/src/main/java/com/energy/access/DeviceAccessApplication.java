package com.energy.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 设备接入服务。
 *
 * <p>两个端口承担两件事：
 * <ul>
 *   <li><b>9000/TCP</b> —— Netty 监听设备长连接，解析二进制协议后投递 Kafka</li>
 *   <li><b>8081/HTTP</b> —— 对内提供在线设备查询，供 em-web-api 通过 OpenFeign 调用</li>
 * </ul>
 *
 * <p>本服务不连数据库：它只负责「把设备数据可靠地投出去」，
 * 数据的存储与加工属于 em-data-process。职责越单一，越容易水平扩展
 * （接入层是无状态的，加机器即可承接更多设备连接）。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class DeviceAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceAccessApplication.class, args);
    }
}
