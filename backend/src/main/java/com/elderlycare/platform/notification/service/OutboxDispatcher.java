package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.notification.domain.OutboxRow;
import com.elderlycare.platform.notification.mapper.OutboxMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 数据库出箱发送器；进程退出、代理断连或消费失败均可通过到期租约恢复。 */
@Service
@ConditionalOnProperty(name = "app.notification.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {
    static final int MAX_ATTEMPTS = 5;
    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxMapper mapper;
    private final OutboxPublisher publisher;
    private final Clock clock;

    /** 装配数据库和发送边界；网络调用不处于预约事务内。 */
    @Autowired
    public OutboxDispatcher(OutboxMapper mapper, OutboxPublisher publisher) { this(mapper, publisher, Clock.systemUTC()); }

    /** 测试可传固定时钟，校验租约及退避而不等待真实时间。 */
    OutboxDispatcher(OutboxMapper mapper, OutboxPublisher publisher, Clock clock) {
        this.mapper = mapper; this.publisher = publisher; this.clock = clock;
    }

    /** 每轮最多处理二十个到期事件，个别失败不阻塞其他事件，日志不记录异常正文。 */
    @Scheduled(fixedDelayString = "${app.notification.dispatch-delay-ms:5000}")
    public void dispatch() {
        try {
            for (var row : mapper.due(clock.instant(), 20)) {
                try { send(row); }
                catch (RuntimeException exception) { LOG.warn("通知发送状态暂时无法更新，将在租约到期后重试"); }
            }
        } catch (RuntimeException exception) { LOG.warn("通知发送任务暂时无法读取，请检查数据库连接"); }
    }

    /** 比较版本占用一次发送；代理确认后仍保留租约，只有消费者能标记完成。 */
    private void send(OutboxRow row) {
        Instant now = clock.instant();
        if (row.attempts >= MAX_ATTEMPTS) { mapper.exhausted(row.eventId, row.version, now); return; }
        String token = UUID.randomUUID().toString();
        if (mapper.claim(row.eventId, row.version, token, now, now.plusSeconds(120), MAX_ATTEMPTS) != 1) return;
        String error;
        try { error = publisher.publish(row.eventId, token); }
        catch (RuntimeException exception) { error = "BROKER_UNAVAILABLE"; }
        if (error == null) return;
        int attempt = row.attempts + 1;
        mapper.failed(row.eventId, token, attempt >= MAX_ATTEMPTS ? "FAILED" : "READY",
                clock.instant().plusSeconds(5L << (attempt - 1)), error);
    }
}
