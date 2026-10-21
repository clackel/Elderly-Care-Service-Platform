package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.common.crypto.DataKeyRing;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 后台登录限流；计数保存在可共享的存储中，只使用带密钥摘要，不记录账号或来源原文。 */
@Component
public class AdminLoginThrottle {
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private final byte[] hashKey;
    private final LoginAttemptStore store;

    /** 统计直接连接来源而非可伪造的转发请求头。 */
    public AdminLoginThrottle(DataKeyRing keys, LoginAttemptStore store) {
        this.hashKey = keys.indexKey("identity:login-throttle:v1");
        this.store = store;
    }

    /** 每个来源每分钟20次、每个账号每分钟10次；成功和失败请求均计数。 */
    public void check(String address, String username) {
        String peer = hash("admin-peer", address == null ? "" : address);
        String account = hash("admin-account", username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
        if (!store.tryAcquire(List.of(new LoginAttemptStore.Limit(peer, 20), new LoginAttemptStore.Limit(account, 10)), WINDOW)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "登录请求过于频繁，请一分钟后重试");
        }
    }

    /** 计算计数键摘要，使存储中不出现账号或来源原文。 */
    private String hash(String purpose, String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(DataKeyRing.hmac(hashKey, purpose + ":" + value));
    }
}
