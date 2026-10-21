package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.common.api.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisLoginAttemptStoreTest {
    /** 计数键带统一前缀并传递窗口及上限，脚本结果决定是否放行。 */
    @Test @SuppressWarnings("unchecked") void passesKeysAndLimitsToAtomicScript() {
        var redis = mock(StringRedisTemplate.class);
        var store = new RedisLoginAttemptStore(redis);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L, 0L);
        var limits = List.of(new LoginAttemptStore.Limit("peer", 20), new LoginAttemptStore.Limit("account", 10));
        assertThat(store.tryAcquire(limits, Duration.ofSeconds(60))).isTrue();
        assertThat(store.tryAcquire(limits, Duration.ofSeconds(60))).isFalse();
        verify(redis, times(2)).execute(any(RedisScript.class),
                eq(List.of(RedisLoginAttemptStore.PREFIX + "peer", RedisLoginAttemptStore.PREFIX + "account")),
                eq("60000"), eq("20"), eq("10"));
    }

    /** Redis不可用时返回明确的503业务错误，不放行也不暴露底层异常。 */
    @Test @SuppressWarnings("unchecked") void redisFailureBecomesServiceUnavailable() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new QueryTimeoutException("synthetic"));
        assertThatThrownBy(() -> new RedisLoginAttemptStore(redis).tryAcquire(List.of(new LoginAttemptStore.Limit("k", 1)), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("LOGIN_THROTTLE_UNAVAILABLE"));
    }
}
