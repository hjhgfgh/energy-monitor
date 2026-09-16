package com.energy.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.energy.common.dto.Result;
import com.energy.common.entity.SysUser;
import com.energy.common.exception.BusinessException;
import com.energy.common.mapper.SysUserMapper;
import com.energy.common.security.JwtTokenProvider;
import com.energy.web.config.WebSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口。
 *
 * <p>签发放在业务服务而不是网关：网关不应该连业务数据库，
 * 它的职责是「校验令牌」而不是「验证密码」。签发与校验的算法共享在 em-common 中，
 * 两边对同一份令牌的理解因此不会漂移。
 *
 * <p><b>登录失败不区分「用户不存在」与「密码错误」</b>，
 * 统一返回「用户名或密码错误」，避免通过响应差异枚举出系统里有哪些账号。
 * 唯一例外是账号被禁用——让用户知道原因比防枚举更重要。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final SysUserMapper sysUserMapper;
    private final WebSecurityProperties securityProperties;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.password() == null) {
            throw new BusinessException("用户名和密码不能为空");
        }

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username()));

        if (user == null || !ENCODER.matches(request.password(), user.getPassword())) {
            log.warn("登录失败: username={}", request.username());
            throw new BusinessException("用户名或密码错误");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        String token = JwtTokenProvider.issue(
                securityProperties.getSecret(),
                user.getId(),
                user.getRole(),
                user.getNickname(),
                securityProperties.getExpireMinutes());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("tokenType", "Bearer");
        payload.put("expiresInSeconds", securityProperties.getExpireMinutes() * 60);
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("nickname", user.getNickname());
        payload.put("role", user.getRole());

        log.info("登录成功: username={}, role={}", user.getUsername(), user.getRole());
        return Result.ok(payload);
    }

    /** 登录请求体 */
    public record LoginRequest(String username, String password) {
    }
}
