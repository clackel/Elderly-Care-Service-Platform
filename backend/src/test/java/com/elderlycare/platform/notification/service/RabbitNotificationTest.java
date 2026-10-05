package com.elderlycare.platform.notification.service;

import com.elderlycare.platform.notification.config.RabbitNotificationConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitNotificationTest {
    @Test void publisherSendsPersistentIdOnlyAndRejectsReturnedAck() {
        var template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            Message message = call.getArgument(2); CorrelationData correlation = call.getArgument(3);
            assertEquals("123", new String(message.getBody(), StandardCharsets.US_ASCII));
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", RabbitNotificationConfiguration.EXCHANGE, "booking"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null)); return null;
        }).when(template).send(eq(RabbitNotificationConfiguration.EXCHANGE), eq("booking"), any(Message.class), any(CorrelationData.class));
        assertEquals("UNROUTABLE", new RabbitOutboxPublisher(template).publish(123, "claim"));
    }

    @Test void publisherRequiresPositiveConfirmAndReturnsSafeFailureCode() {
        var template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker details must remain private")); return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertEquals("BROKER_REJECTED", new RabbitOutboxPublisher(template).publish(123, "claim"));
    }

    @Test void listenerRejectsMalformedInputWithoutInvokingBusinessService() {
        var service = mock(NotificationDeliveryService.class); var listener = new RabbitNotificationListener(service);
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message("{\"eventId\":123}")));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message("0")));
        verifyNoInteractions(service);
    }

    @Test void listenerPropagatesSafeRejectionAfterTransactionFailure() {
        var service = mock(NotificationDeliveryService.class); var listener = new RabbitNotificationListener(service);
        doThrow(new IllegalStateException("private database details")).when(service).deliver(123L);
        var failure = assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message("123")));
        assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private"));
    }

    /** 构造队列原始载荷，仅用于测试输入边界。 */
    private Message message(String text) { return new Message(text.getBytes(StandardCharsets.US_ASCII), new MessageProperties()); }
}
