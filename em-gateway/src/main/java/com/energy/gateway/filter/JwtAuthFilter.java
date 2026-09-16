package com.energy.gateway.filter;

import com.energy.common.security.JwtTokenProvider;
import com.energy.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 网关全局 JWT 鉴权过滤器。
 *
 * <p><b>为什么把鉴权放在网关：</b>如果每个业务服务各自实现一遍，
 * 三个服务就是三份重复代码，且任何一处漏配都会变成安全缺口——
 * 这是「安全逻辑集中在一处」的典型场景。业务服务因此只需信任网关注入的
 * {@code X-User-*} 头，不必解析令牌。
 *
 * <p><b>注意这些头只在网关之后可信：</b>内部服务必须不对外暴露端口（或放在内网），
 * 否则外部请求可以伪造 {@code X-User-Id} 绕过鉴权。
 * 本项目的 {@code /internal/**} 接口因此不配置网关路由。
 *
 * <p><b>为什么返回 401 而不是让请求继续：</b>WebFlux 是响应式的，
 * 一旦没有 {@code return} 就会继续往下走。这里的每个拒绝分支都显式返回
 * {@code Mono<Void>} 并结束响应，避免出现「校验失败但请求照常转发」的严重漏洞。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtProperties jwtProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        String token = JwtTokenProvider.resolveToken(
                exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return reject(exchange, "缺少认证令牌，请先登录");
        }

        try {
            Claims claims = JwtTokenProvider.parse(jwtProperties.getSecret(), token);

            // 令牌合法：把用户信息以请求头形式透传给下游，业务服务无需重复解析
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(headers -> JwtTokenProvider.toHeaders(claims)
                            .forEach(headers::set))
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException e) {
            log.warn("令牌校验失败: path={}, reason={}", path, e.getMessage());
            return reject(exchange, "令牌无效或已过期，请重新登录");
        }
    }

    /** 顺序设为 -100，确保先于路由与 Sentinel 过滤器执行 */
    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhiteListed(String path) {
        return jwtProperties.getWhiteList().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":4010,\"message\":\"" + message
                + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
