/**
 * 站内通知、预约待办及可靠投递模块。
 *
 * <p>预约事件同事务保存发件箱及接收权限快照，消费及展示时重新检查授权；
 * 待办从预约当前状态派生。事务置于 Service，通知不复制个人资料或事件正文。
 */
package com.elderlycare.platform.notification;
