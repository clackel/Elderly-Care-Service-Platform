package com.elderlycare.platform.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 进程内登录计数，仅供不连接Redis的dev单实例使用；重启清零且多实例之间不共享。 */
@Component
@ConditionalOnProperty(name = "app.login-throttle.store", havingValue = "memory")
public class MemoryLoginAttemptStore implements LoginAttemptStore {
    private static final int MAX_KEYS = 20000;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private record Attempt(Instant until, int count) {}

    /** 默认使用UTC时钟。 */
    @Autowired
    public MemoryLoginAttemptStore() { this(Clock.systemUTC()); }

    /** 注入可控时钟供最小单元测试验证窗口过期。 */
    MemoryLoginAttemptStore(Clock clock) { this.clock = clock; }

    /** 回收过期计数后检查限制；计数键达到内存上限时拒绝新键以限制内存占用。 */
    @Override
    public synchronized boolean tryAcquire(List<Limit> limits, Duration window) {
        var now = clock.instant();
        attempts.entrySet().removeIf(entry -> !entry.getValue().until().isAfter(now));
        long added = limits.stream().filter(limit -> !attempts.containsKey(limit.key())).count();
        if (attempts.size() + added > MAX_KEYS) return false;
        for (var limit : limits) {
            var attempt = attempts.get(limit.key());
            if (attempt != null && attempt.count() >= limit.max()) return false;
        }
        for (var limit : limits) {
            var old = attempts.get(limit.key());
            attempts.put(limit.key(), new Attempt(old == null ? now.plus(window) : old.until(), old == null ? 1 : old.count() + 1));
        }
        return true;
    }
}
