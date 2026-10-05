# 通知与待办接口

本文记录预约站内通知、当前待办和 RabbitMQ 投递的现行契约。复用[预约接口](SERVICE_BOOKING_API.md)的身份、社区及对象权限；健康授权保持独立。本模块不包含短信、电话外呼、微信订阅消息或求助投递。

## 范围与角色

| 角色 | 消息范围 | 待办范围 |
|---|---|---|
| COMMUNITY_OPERATOR / PLATFORM_ADMIN | 事件发生时属于本社区的接收账号，当前身份与通知周期仍有效 | 社区共享的待确认、异常和超时预约 |
| ELDER | 事件时有效绑定本人，当前绑定编号、版本仍有效 | 空列表 |
| FAMILY | 事件时具有有效预约授权，当前授权仍处于同一有效周期 | 空列表 |
| STAFF | 当前仍分配给本人且分配周期一致的任务消息；已移交只保留无详情提示 | 本人待开始和进行中任务 |

DUTY_OFFICER、AUDITOR 不通过本模块取得预约访问权。所有查询从会话读取账号及社区，不接受客户端自报接收人、角色或社区。列表、计数、已读写入、待办和预约详情分别执行服务端授权检查。

消息接收人由业务事件发生时确定，排除该次操作的操作者，不因其后新增账号、绑定、授权或任务分配而补发历史。投递时、列表读取时、未读计数和标已读时重新检查当前权限。家属授权撤销或到期后旧消息立即不可见；重新授权进入新通知周期，只接收此后新事件。有效期内的授权调整沿用当前通知周期。

本人绑定版本变化使旧消息失效，恢复绑定不恢复旧消息。后台账号启停或角色调整递增独立通知版本，恢复账号或原角色不恢复旧通知；修改显示名或密码不改变通知周期。工作人员每次改派到另一人员时更换任务分配周期，旧人员不能继续查看旧任务消息，重新分配给该人员也不恢复旧周期消息。

改派旧人员可收到 `TASK_TRANSFERRED`，仅说明任务已移交，`bookingId=null`，不提供旧任务详情入口。消息不含老人姓名、电话、地址、健康正文或预约说明；客户端只按固定动作模板显示标题和时间。

## 接口

前缀 `/api/v1`。成功 HTTP 200，统一 `code/message/data/traceId`，ID 为字符串；时间使用含时区 ISO 8601，存储 UTC，界面显示 Asia/Shanghai。分页从 1 开始、默认 20、最大 100，返回 `items/page/pageSize/total`。接口设置 `Cache-Control: no-store`；写入需要当前会话及 CSRF。

| 方法 | 路径 | 请求与返回 |
|---|---|---|
| GET | /notifications | `page/pageSize/unreadOnly`，未读筛选默认 false；通知分页 |
| GET | /notifications/unread-count | 返回 `{count}`，只计当前仍有权读取的未读消息 |
| PUT | /notifications/{id}/read | 空请求体；幂等标记自己的单条消息，返回 null |
| GET | /todos | `page/pageSize/type?`；当前待办分页 |
| GET | /notifications/deliveries | 本社区管理人员查询失败投递分页 |
| POST | /notifications/deliveries/{eventId}/retry | `{version}`；将失败事件重新排队，返回 null |

通知项为 `id/action/bookingId/occurredAt/read`，按业务事件时间及消息编号倒序。`occurredAt` 为业务发生时间，不是队列消费时间。动作包括 `CREATE`、`CONFIRM`、`RESCHEDULE`、`REJECT`、`CANCEL`、`START`、`COMPLETE`、`EXCEPTION`、`RESOLVE`、`TERMINATE`、`TASK_TRANSFERRED`。

标已读只改变当前接收人的阅读状态，不完成待办，也不改变预约。重复请求已读消息仍成功；不存在、属于其他账号或已无访问权限的消息统一返回 404 `NOTIFICATION_NOT_FOUND`。打开详情时仍经预约接口重新鉴权，消息链接本身不产生权限。

## 当前待办

待办项为 `bookingId/type/status/scheduledStart/scheduledEnd/hasException/overdue`，不复制姓名、地址或电话。待办直接从预约当前状态派生，不建立可单独勾选完成的任务表，不依赖消息是否到达或已读。

| type | 派生规则 |
|---|---|
| PENDING_CONFIRMATION | 本社区状态为 PENDING，等待人工确认 |
| EXCEPTION | 本社区 CONFIRMED/IN_PROGRESS 且有未处理异常，优先于超时类型 |
| OVERDUE | 本社区 CONFIRMED/IN_PROGRESS，计划结束时间已到，且未归为异常类型 |
| WAITING_START | STAFF 本人 CONFIRMED 任务 |
| IN_PROGRESS | STAFF 本人 IN_PROGRESS 任务 |

社区运营与平台管理员共用同一社区待办池，一人的已读状态不影响其他人员的待办。工作人员始终按待开始或进行中分组，异常和超时通过 `hasException/overdue` 同时提示；老人及家属只显示通知。时间到达不自动完成服务。待办按异常、超时优先，再按计划开始时间及预约编号排序；同一预约在当前角色范围只占一条待办。

## 事务与投递

预约操作在原有社区锁和业务事务内同时保存 `booking_event`、通知发件箱与接收人权限快照。预约成功只表示业务和事件已持久化，不承诺通知已送达。RabbitMQ 网络发送在业务事务之外执行，队列不可用不会丢弃已保存事件，也不会把通知失败伪装为业务未发生。

发件箱状态为 `READY → IN_FLIGHT → DELIVERED`，耗尽重试后为 `FAILED`。调度器默认每轮间隔 5 秒、最多取 20 个到期事件，通过版本条件和 120 秒租约认领，支持进程退出后恢复。每个事件在一个自动重试周期内最多尝试发送 5 次；明确发布失败按 5、10、20、40 秒退避，消费未完成则在租约到期后重新发送，重试耗尽进入失败列表。

代理确认、消息未退回只代表 RabbitMQ 接收成功，不把发件箱改为已送达。消费者以事件编号重新读取数据库，在社区锁事务内检查接收快照和当前权限、写入站内消息、标记 `DELIVERED`；提交后才完成消费。当前已无合格接收人也可完成事件，不为失效权限强行投递。

站内消息使用“业务事件＋接收账号”唯一约束，重复发布、确认丢失或重复消费不会产生重复消息。消费者异常拒绝重新入原队列，转入死信队列，后续由发件箱执行有界重投；不自动消费死信队列。已在队列中的迟到消息仍可能完成此前标为失败的事件，管理操作始终以数据库最新状态为准。

投递失败项为 `eventId/action/occurredAt/attempts/errorCode/version`，仅 COMMUNITY_OPERATOR、PLATFORM_ADMIN 可管理本社区记录，不含预约正文和底层异常。人工重试携带当前版本，将 FAILED 恢复为 READY、次数归零；仍使用原事件接收人快照，并在投递时重新检查当前权限，不重新选取接收人。重复点击已经排队或完成的记录无副作用；若下一轮又进入失败状态，旧版本不能覆盖新失败，需要刷新后确认。成功响应只表示已接受重试。

## RabbitMQ 与运行配置

| 配置 | 默认值与用途 |
|---|---|
| NOTIFICATIONS_ENABLED / app.notification.enabled | true；启用发布和消费 |
| app.notification.dispatch-delay-ms | 5000；两轮调度之间的间隔毫秒数 |
| RABBITMQ_HOST / RABBITMQ_PORT | localhost / 5672 |
| RABBITMQ_USERNAME / RABBITMQ_PASSWORD | 使用运行环境配置的账号及密码 |

各环境包括 `dev` 的通知投递均需可用 RabbitMQ。`dev` 仍使用进程内会话，不要求 Redis。可显式设 `NOTIFICATIONS_ENABLED=false` 暂停通知发布与消费；业务事务继续累计发件箱事件，这不表示通知已送达。恢复启用后按到期事件继续处理，读取端仍可查询已落库消息及当前待办。

发送启用 correlated publisher confirms、returns 和 mandatory，连接超时为 3 秒，单次发布最多等待确认 5 秒。所用持久化拓扑：

| 类型 | 名称 | 路由 |
|---|---|---|
| direct exchange | elderly-care.notifications.v1 | booking |
| queue | elderly-care.notifications.v1.inbox | 绑定 booking |
| dead-letter exchange | elderly-care.notifications.v1.dead | failed |
| dead-letter queue | elderly-care.notifications.v1.dead.inbox | 绑定 failed |

队列消息仅包含事件编号，不携带接收人、姓名、电话、地址、健康内容或事件说明。RabbitMQ 账号需具备配置上述交换机、队列和绑定及发布消费的权限。部署配置与凭据放在环境变量或已忽略的外部配置，不进入前端或源码。

## 数据库与页面

`V6__notifications_and_todos.sql` 新增 `notification_outbox`、`notification_recipient`、`notification_message`，分别保存投递状态、事件时接收范围、个人阅读状态。`user_account.notification_version`、`booking_grant.notification_epoch`、`service_booking.assignment_version` 用于阻止账号恢复、重新授权或再次改派后复活旧消息。迁移不为旧预约事件补建历史通知，已有未完成预约仍可进入实时待办。数据库仍是投递状态和站内消息的最终依据；RabbitMQ 消息不代替数据库业务记录。

管理端 `/notifications` 提供消息、社区待办和发送异常，处理入口打开现有预约详情。移动端保留四个 tab，从“我的”进入 `pages/notifications/index`；ELDER/FAMILY 查看消息，STAFF 另有本人待办。隐藏或退出页面时卸载消息数据，返回前台先恢复身份，迟到的旧会话响应不得回填。

两端共用 `frontend/shared/notifications.ts` 的固定标题、类型和时间语义。消息点击后只传一次性内存预约编号，由现有预约工作区重新读取详情；不通过 URL 或持久化存储保存业务正文或导航意图。移动端切换到服务页后真正打开目标详情；TASK_TRANSFERRED 始终无详情跳转。

## 错误

| HTTP | code | 处理 |
|---|---|---|
| 400 | INVALID_ARGUMENT | 修正分页、待办类型或版本输入 |
| 401 / 403 | UNAUTHENTICATED / SESSION_REVOKED / FORBIDDEN / COMMUNITY_UNAVAILABLE | 重新核验当前身份及社区权限 |
| 404 | NOTIFICATION_NOT_FOUND | 消息不存在或已无权访问，刷新列表 |
| 404 | DELIVERY_NOT_FOUND | 投递记录不存在或不在本社区 |
| 409 | VERSION_CONFLICT | 失败记录已变化，刷新后确认再重试 |

投递记录中的稳定 `errorCode` 为 `BROKER_UNAVAILABLE`、`BROKER_REJECTED`、`UNROUTABLE`、`CONFIRM_TIMEOUT`、`DELIVERY_TIMEOUT`，不返回代理连接异常正文。发送异常与用户读取接口错误分别处理，消息未读不代表发送失败。
