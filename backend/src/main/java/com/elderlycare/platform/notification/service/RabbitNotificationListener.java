package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.notification.config.RabbitNotificationConfiguration;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 数据库事务成功返回后才由容器确认消费，重复事件交由投递服务幂等处理。 */
@Component
@ConditionalOnProperty(name = "app.notification.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitNotificationListener {
    private final NotificationDeliveryService service;

    /** 装配事务投递服务，队列消息不直接提供账号或业务正文。 */
    public RabbitNotificationListener(NotificationDeliveryService service) { this.service = service; }

    /** 非法或消费失败事件进入死信队列；数据库出箱租约负责有界重投，避免无限立即重入。 */
    @RabbitListener(queues = RabbitNotificationConfiguration.QUEUE, containerFactory = "notificationListenerContainerFactory")
    public void receive(Message message) {
        try {
            byte[] body = message.getBody();
            if (body.length == 0 || body.length > 19) throw new IllegalArgumentException("通知事件编号长度无效");
            for (byte value : body) if (value < '0' || value > '9') throw new IllegalArgumentException("通知事件编号格式无效");
            long eventId = Long.parseLong(new String(body, StandardCharsets.US_ASCII));
            if (eventId <= 0) throw new IllegalArgumentException("通知事件编号必须为正数");
            service.deliver(eventId);
        } catch (RuntimeException exception) {
            throw new AmqpRejectAndDontRequeueException("通知暂时无法处理，请查看发送异常记录");
        }
    }
}
