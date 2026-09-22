/**
 * 亲属关系与分项授权模块的命名空间。
 *
 * <p>本人绑定和预约授权保留原有 booking 持久化对象；独立健康授权使用本模块 domain/mapper。
 * 预约权限不授予健康访问权，健康同意仅由有效绑定本人在线授予，逐请求检查期限、绑定版本及撤销状态。
 */
package com.elderlycare.platform.consent;
