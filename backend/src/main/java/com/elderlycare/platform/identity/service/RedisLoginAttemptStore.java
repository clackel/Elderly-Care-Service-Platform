package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.common.api.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 基于Redis的共享登录计数，多实例部署和应用重启后限流仍然有效；正式环境默认使用。 */
@Component
@ConditionalOnProperty(name = "app.login-throttle.store", havingValue = "redis", matchIfMissing = true)
public class RedisLoginAttemptStore implements LoginAttemptStore {
    static final String PREFIX = "elderly-care:login-attempt:";
    // 先检查全部键再统一计数，保证被拒请求不计数，且只在首次计数时设置过期时间。
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
              if tonumber(redis.call('GET', key) or '0') >= tonumber(ARGV[i + 1]) then return 0 end
            end
            for _, key in ipairs(KEYS) do
              if redis.call('INCR', key) == 1 then redis.call('PEXPIRE', key, ARGV[1]) end
            end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;

    /** 使用应用已配置的Redis连接。 */
    public RedisLoginAttemptStore(StringRedisTemplate redis) { this.redis = redis; }

    /** 原子执行检查与计数；Redis不可用时拒绝登录并返回503，避免限流失效。 */
    @Override
    public boolean tryAcquire(List<Limit> limits, Duration window) {
        var keys = new ArrayList<String>();
        var args = new ArrayList<Object>();
        args.add(String.valueOf(window.toMillis()));
        for (var limit : limits) {
            keys.add(PREFIX + limit.key());
            args.add(String.valueOf(limit.max()));
        }
        try {
            return Long.valueOf(1).equals(redis.execute(SCRIPT, keys, args.toArray()));
        } catch (DataAccessException e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "LOGIN_THROTTLE_UNAVAILABLE", "登录服务暂不可用，请稍后重试");
        }
    }
}
