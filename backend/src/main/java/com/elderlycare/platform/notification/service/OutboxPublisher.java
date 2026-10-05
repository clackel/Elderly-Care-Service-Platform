package com.elderlycare.platform.notification.service;

/** 持久化事件的发送边界，发布失败只返回稳定错误码。 */
public interface OutboxPublisher {
    /** 发送事件编号并等待代理确认；返回空值表示代理确认且未退回，仍需消费者完成数据库事务。 */
    String publish(long eventId, String token);
}
