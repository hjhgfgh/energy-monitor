package com.energy.web.config;

import com.energy.common.entity.SysUser;
import com.energy.common.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 默认账号初始化。
 *
 * <p>BCrypt 每次加盐都产生不同哈希，无法在 SQL 脚本里硬编码一个密码。
 * 所以这里在启动时检查并创建默认账号，而不是往 schema.sql 里塞一个
 * 来路不明的哈希值——那样密码到底是什么只有写下它的人知道。
 *
 * <p>只在表里一个用户都没有时才创建，不会覆盖已有数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultUserInitializer implements ApplicationRunner {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final SysUserMapper sysUserMapper;

    @Override
    public void run(ApplicationArguments args) {
        Long existing = sysUserMapper.selectCount(null);
        if (existing != null && existing > 0) {
            log.info("用户表已有 {} 条记录，跳过默认账号初始化", existing);
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(DEFAULT_USERNAME);
        admin.setPassword(new BCryptPasswordEncoder().encode(DEFAULT_PASSWORD));
        admin.setNickname("系统管理员");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        sysUserMapper.insert(admin);

        log.warn("已创建默认管理员账号: {} / {}（请在生产环境中立即修改）",
                DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}
