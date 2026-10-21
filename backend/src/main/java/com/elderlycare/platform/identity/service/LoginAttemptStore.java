package com.elderlycare.platform.identity.service;

import java.time.Duration;
import java.util.List;

/** 登录请求固定窗口计数存储；计数键必须是带密钥摘要，不保存账号或来源原文。 */
public interface LoginAttemptStore {
    /** 单个计数键及其窗口内允许的最大请求数。 */
    record Limit(String key, int max) {}

    /**
     * 原子检查并计数。
     *
     * @param limits 需要同时满足的计数限制
     * @param window 首次计数时开始的固定窗口，被拒请求不延长窗口
     * @return 全部未达上限时计数并返回true；任一达到上限时不计数并返回false
     */
    boolean tryAcquire(List<Limit> limits, Duration window);
}
