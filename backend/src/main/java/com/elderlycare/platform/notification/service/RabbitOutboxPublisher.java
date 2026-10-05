package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.notification.config.RabbitNotificationConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 等待 RabbitMQ 发布确认及路由退回结果，不把代理确认当作站内消息生成成功。 */
@Component
@ConditionalOnProperty(name = "app.notification.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitOutboxPublisher implements OutboxPublisher {
    private final RabbitTemplate template;

    /** 使用专属模板，保证通知发送启用 mandatory 及关联确认。 */
    public RabbitOutboxPublisher(@Qualifier("notificationRabbitTemplate") RabbitTemplate template) { this.template = template; }

    /** 只发送 ASCII 事件编号；失败返回稳定码，不向日志或接口泄露中间件异常正文。 */
    @Override public String publish(long eventId, String token) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        properties.setContentEncoding("US-ASCII");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(Long.toString(eventId));
        var correlation = new CorrelationData(token);
        try {
            template.send(RabbitNotificationConfiguration.EXCHANGE, "booking",
                    new Message(Long.toString(eventId).getBytes(StandardCharsets.US_ASCII), properties), correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (correlation.getReturned() != null) return "UNROUTABLE";
            return confirm.isAck() ? null : "BROKER_REJECTED";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); return "CONFIRM_TIMEOUT";
        } catch (TimeoutException exception) { return "CONFIRM_TIMEOUT";
        } catch (Exception exception) { return "BROKER_UNAVAILABLE"; }
    }
}
