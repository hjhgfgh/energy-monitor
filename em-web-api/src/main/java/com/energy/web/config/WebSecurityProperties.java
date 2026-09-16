package com.energy.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务服务的 JWT 配置。
 *
 * <p>注意与网关共用同一个 secret 和有效期——两处配置必须一致，
 * 否则会出现「登录成功但访问接口全部 401」的现象。
 */
@Data
@Component
@ConfigurationProperties(prefix = "energy.jwt")
public class WebSecurityProperties {

    private String secret;

    private long expireMinutes = 120;
}
