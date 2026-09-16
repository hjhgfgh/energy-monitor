package com.energy.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT 与鉴权白名单配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "energy.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求不少于 32 字节 */
    private String secret;

    /** 令牌有效期（分钟） */
    private long expireMinutes = 120;

    /** 免鉴权路径：登录接口本身不能被鉴权挡住，否则无法登录 */
    private List<String> whiteList = new ArrayList<>();
}
