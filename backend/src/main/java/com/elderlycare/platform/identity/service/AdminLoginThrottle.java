package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.booking.service.BookingCrypto;
import com.elderlycare.platform.common.api.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 单实例后台登录限流；仅保存带密钥摘要，不记录账号或来源原文。 */
@Component
public class AdminLoginThrottle {
    private final BookingCrypto crypto;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private record Attempt(Instant until, int count) {}

    /** 默认使用UTC时钟，统计直接连接来源而非可伪造的转发请求头。 */
    @Autowired
    public AdminLoginThrottle(BookingCrypto crypto) { this(crypto, Clock.systemUTC()); }

    /** 注入可控时钟供最小单元测试验证窗口过期。 */
    AdminLoginThrottle(BookingCrypto crypto, Clock clock) { this.crypto = crypto; this.clock = clock; }

    /** 每个来源每分钟20次、每个账号每分钟10次；成功和失败请求均计数。 */
    public synchronized void check(String address, String username) {
        var now = clock.instant();
        attempts.entrySet().removeIf(entry -> !entry.getValue().until().isAfter(now));
        String peer = crypto.hash("admin-login-peer", address == null ? "" : address);
        String account = crypto.hash("admin-login-account", username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
        if (limited(peer, 20) || limited(account, 10)
                || attempts.size() + (attempts.containsKey(peer) ? 0 : 1) + (attempts.containsKey(account) ? 0 : 1) > 20000) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "登录请求过于频繁，请一分钟后重试");
        }
        record(peer, now); record(account, now);
    }

    /** 按固定窗口检查计数，不因被拒请求延长锁定时间。 */
    private boolean limited(String key, int limit) {
        var attempt = attempts.get(key);
        return attempt != null && attempt.count() >= limit;
    }

    /** 在同步边界内递增通过限流的请求计数。 */
    private void record(String key, Instant now) {
        var old = attempts.get(key);
        attempts.put(key, new Attempt(old == null ? now.plusSeconds(60) : old.until(), old == null ? 1 : old.count() + 1));
    }
}
