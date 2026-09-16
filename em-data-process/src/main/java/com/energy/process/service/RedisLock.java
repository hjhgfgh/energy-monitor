package com.energy.process.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的分布式锁。
 *
 * <p><b>加锁</b>用 {@code SET key token NX PX ttl} 单条命令完成。
 * 这不能拆成「先 SETNX 再 EXPIRE」——两条命令之间进程若崩掉，锁就永不过期，
 * 变成死锁。Redis 2.6.12 起 SET 支持 NX+EX 组合，正是为此。
 *
 * <p><b>解锁</b>必须用 Lua 脚本，因为「校验持有者」和「删除」必须是原子操作。
 * 若写成先 GET 比较、再 DEL：这两步之间锁可能已到期并被其他实例获取，
 * 此时 DEL 删掉的是**别人的锁**，临界区就此失控。
 *
 * <p><b>token 的作用</b>是标识锁的持有者。没有 token 就无法区分
 * 「我加的锁」和「别人加了又被我删掉的锁」。
 *
 * <p><b>已知限制：</b>单节点 Redis 的锁在主从切换瞬间存在失效窗口
 * （主节点写入后尚未同步就宕机，从节点晋升后锁丢失）。
 * 对「避免重复告警」这种允许极小概率重复的场景足够；
 * 若要求绝对互斥，需要 Redlock 或 ZooKeeper 这类多数派方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    /** 校验 value 一致才删除，保证只释放自己持有的锁 */
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final DefaultRedisScript<Long> UNLOCK =
            new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 尝试加锁，不阻塞。
     *
     * @return 成功返回持有者 token（解锁时需回传）；失败返回 {@code null}
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (Exception e) {
            log.warn("加锁失败（Redis 异常）: key={}", key, e);
            return null;
        }
    }

    /**
     * 释放锁。
     *
     * @return true 表示确实释放了自己的锁；false 表示锁已不属于自己（可能已过期）
     */
    public boolean unlock(String key, String token) {
        if (token == null) {
            return false;
        }
        try {
            Long removed = redisTemplate.execute(UNLOCK, Collections.singletonList(key), token);
            return removed != null && removed > 0;
        } catch (Exception e) {
            log.warn("释放锁失败: key={}", key, e);
            return false;
        }
    }
}
