package com.energy.web.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>两个关键点，都是容易踩的坑：
 * <ol>
 *   <li>分页插件必须显式指定 {@link DbType}。不指定时，框架需要自行探测数据库类型，
 *       在某些代理数据源或连接池延迟初始化场景下会探测失败，导致分页静默失效——
 *       SQL 照常执行，但不带 LIMIT，返回全表数据。</li>
 *   <li>必须用 {@code mybatis-plus-spring-boot3-starter} 坐标。Spring Boot 3 换到了
 *       jakarta 命名空间，老的 {@code mybatis-plus-boot-starter} 依赖 javax，
 *       启动时会因找不到 Servlet 相关类而失败。</li>
 * </ol>
 */
@Configuration
@MapperScan("com.energy.common.mapper")
public class MybatisPlusConfig {

    /** 单页最大条数，防止前端传入超大 size 拖垮数据库 */
    private static final long MAX_PAGE_LIMIT = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_LIMIT);
        pagination.setOverflow(false);

        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
