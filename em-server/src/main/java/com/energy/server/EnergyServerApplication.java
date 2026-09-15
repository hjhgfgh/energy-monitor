package com.energy.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智慧能源监控系统 - P1 单体服务入口。
 *
 * <p>同时承载两件事：
 * <ul>
 *   <li>Netty TCP 服务（设备接入，默认 9000 端口）</li>
 *   <li>Spring MVC REST 服务（业务查询，默认 8080 端口）</li>
 * </ul>
 */
@SpringBootApplication
public class EnergyServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnergyServerApplication.class, args);
    }
}
