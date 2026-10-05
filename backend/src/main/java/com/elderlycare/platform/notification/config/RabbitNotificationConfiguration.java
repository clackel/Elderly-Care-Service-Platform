package com.elderlycare.platform.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 沿用现有 RabbitMQ，为预约通知声明独立持久队列及死信队列。 */
@Configuration
@EnableRabbit
@EnableScheduling
@ConditionalOnProperty(name = "app.notification.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitNotificationConfiguration {
    public static final String EXCHANGE = "elderly-care.notifications.v1";
    public static final String QUEUE = EXCHANGE + ".inbox";
    public static final String DEAD_EXCHANGE = EXCHANGE + ".dead";
    public static final String DEAD_QUEUE = DEAD_EXCHANGE + ".inbox";
    private static final Logger LOG = LoggerFactory.getLogger(RabbitNotificationConfiguration.class);

    /** 声明持久交换机、队列及拒绝消息的死信路由，不自动重放死信。 */
    @Bean public Declarables notificationTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var dead = new DirectExchange(DEAD_EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey("failed").build();
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(exchange, dead, queue, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with("booking"), BindingBuilder.bind(deadQueue).to(dead).with("failed"));
    }

    /** mandatory 使无法路由的消息返回发布器，防止仅收到代理确认就遗漏无队列情形。 */
    @Bean public RabbitTemplate notificationRabbitTemplate(ConnectionFactory connectionFactory) {
        var template = new RabbitTemplate(connectionFactory); template.setMandatory(true); return template;
    }

    /** 消费事务返回后自动确认；异常不立即重入，连接恢复后由出箱租约继续调度。 */
    @Bean public SimpleRabbitListenerContainerFactory notificationListenerContainerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO); factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1); factory.setPrefetchCount(10); factory.setMissingQueuesFatal(false);
        factory.setRecoveryInterval(5000L);
        factory.setErrorHandler(exception -> {
            LOG.warn("通知消费未完成，等待数据库发送记录恢复");
            throw new AmqpRejectAndDontRequeueException("通知消费失败，已停止立即重试");
        });
        return factory;
    }
}
