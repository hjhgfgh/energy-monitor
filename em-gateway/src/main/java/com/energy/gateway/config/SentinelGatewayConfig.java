package com.energy.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentinel 网关限流配置。
 *
 * <p><b>为什么在网关限流，而不是在每个服务里：</b>网关是所有流量的必经之路，
 * 在这里挡掉超额请求，它们就不会打到后面的服务上。放在服务内部限流时，
 * 请求已经消耗了网关转发、负载均衡、连接建立的成本——
 * 「越靠前拦住越省资源」是限流位置选择的基本原则。
 *
 * <p><b>限流维度选择按 API 分组而非按路由：</b>一个路由可能承载多个语义不同的接口
 * （如设备查询与告警查询），它们的容量特征不同，用同一阈值会误伤。
 * 这里把高频查询接口与普通接口分开设阈值。
 *
 * <p><b>被限流时返回结构化响应而非默认错误页：</b>Sentinel 默认返回
 * 一行文本，前端无法按统一格式解析。这里自定义 BlockRequestHandler 返回
 * 与业务一致的 JSON 结构，让「限流」在前端表现为一个可提示的错误码，
 * 而不是一个莫名其妙的 429 空白页。
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelGatewayConfig(List<ViewResolver> viewResolvers,
                                 ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolvers;
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * 覆盖 Sentinel 默认的限流响应。
     *
     * <p><b>@Order(-2) 不能省：</b>网关限流底层是参数流控，超额时抛
     * {@code ParamFlowException}（BlockException 子类）。这个 handler 必须排
     * 在 Spring 的兜底 ExceptionHandlingWebHandler 之前才能接管它——
     * 缺了 Order，异常被兜底处理器吃掉，限流响应变成 HTTP 500，
     * 前端看到的就不是「请求过于频繁」而是「服务器错误」。
     */
    @Bean
    @org.springframework.core.annotation.Order(-2)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    @PostConstruct
    public void init() {
        initBlockHandler();
        // 顺序不能反：规则引用 API 分组名，分组必须先注册
        initApiDefinition();
        initGatewayRules();
    }

    /**
     * 限流规则。
     *
     * <p>阈值取得较小（QPS 5）是为了让限流**可被复现和演示**——
     * 阈值设成几百，压测脚本跑半天也触发不了一次，等于没有验证。
     * 真实容量需要压测后按 P99 能力回填。
     */
    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 设备查询类接口：读库 + 缓存，容量相对有限
        rules.add(new GatewayFlowRule("device-query-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(5)
                .setIntervalSec(1));

        // 认证接口：登录涉及 BCrypt 计算（刻意慢），容量更小
        rules.add(new GatewayFlowRule("auth-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(3)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
        log.info("Sentinel 网关限流规则已加载: device-query-api=5 QPS, auth-api=3 QPS");
    }

    /** API 分组：把路径前缀相同、容量特征相近的接口归为一组 */
    private void initApiDefinition() {
        Set<ApiDefinition> definitions = new HashSet<>();

        Set<ApiPredicateItem> deviceQueryItems = new HashSet<>();
        deviceQueryItems.add(new ApiPathPredicateItem().setPattern("/api/devices/**")
                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
        definitions.add(new ApiDefinition("device-query-api").setPredicateItems(deviceQueryItems));

        Set<ApiPredicateItem> authItems = new HashSet<>();
        authItems.add(new ApiPathPredicateItem().setPattern("/api/auth/**")
                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
        definitions.add(new ApiDefinition("auth-api").setPredicateItems(authItems));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /** 被限流时返回与业务一致的 JSON 结构 */
    private void initBlockHandler() {
        BlockRequestHandler handler = (exchange, throwable) -> {
            String body = "{\"code\":4029,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null,\"timestamp\":"
                    + System.currentTimeMillis() + "}";
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(body));
        };
        GatewayCallbackManager.setBlockHandler(handler);
    }
}
