# 后台账号与密码接口

本文记录后台账号模块的现行契约。目标是支持大创项目的后台身份、账号维护和密码生命周期；沿用 Spring Security、服务器会话和单角色模型。多因素认证、自定义权限、跨社区账号调动及公共注册未实现。基础会话接口见[基础框架接口](FRAMEWORK_API.md)，移动账号仍按[预约接口](SERVICE_BOOKING_API.md)核验及微信开通。

## 权限与账号边界

后台角色固定为 `COMMUNITY_OPERATOR`、`DUTY_OFFICER`、`PLATFORM_ADMIN`、`AUDITOR`。后台密码登录不接受 `ELDER`、`FAMILY`、`STAFF`；移动端继续使用微信登录，不通过账号管理切换为后台身份。

`/api/v1/admin/accounts/**` 仅允许当前社区的有效 `PLATFORM_ADMIN`，且已完成强制改密。社区由服务器从操作者账号读取，不接收客户端指定社区。账号目标也必须是本社区后台角色；不存在、跨社区或移动账号均不暴露对象详情。平台管理员角色不产生跨社区权限或健康数据访问权。

账号只有一个角色，用户名及所属社区创建后不可通过本模块修改。状态为 `ACTIVE` 或 `DISABLED`。不提供删除接口，保留账号和历史关联。禁止停用当前操作账号、将自己的平台管理员角色调出，或使社区失去最后一个有效平台管理员。

社区运营账号停用或转为其他后台角色时，在同一社区事务内撤销其收到的全部未撤销 `COMMUNITY_ASSIST` 授权，包含已经到期的授权。恢复启用或重新转为运营角色不会恢复旧授权，须由老人重新授权。此操作不删除代录记录、不改变原始录入人，也不允许管理员代替老人授权。

## 账号接口

前缀为 `/api/v1`。成功 HTTP 200，统一响应 `code/message/data/traceId`；ID 为字符串，版本为整数，时间为含时区 ISO 8601。写入需要当前会话 CSRF。

| 方法 | 路径 | 请求与返回 |
|---|---|---|
| GET | /admin/accounts | `page/pageSize/keyword?/role?/status?`；返回账号分页 |
| POST | /admin/accounts | `requestId/username/displayName/role/password`；返回 `id/version` |
| PUT | /admin/accounts/{id} | `requestId/version/displayName/role/status`；返回 `id/version` |
| POST | /admin/accounts/{id}/reset-password | `requestId/version/password`；返回 `id/version` |
| GET | /admin/accounts/{id}/events | `page/pageSize`；返回操作记录分页 |

分页从 1 开始，默认 20，最大 100，响应为 `items/page/pageSize/total`。`keyword` 去首尾空白后最多 100 字，匹配用户名或显示名称的字面片段；`role` 限定后台角色，`status` 限定账号状态。账号列表按账号编号倒序，不返回密码、密码哈希或凭证版本。

用户名去首尾空白并转为小写，长度 3—64，必须以 ASCII 字母或数字开头，之后可使用字母、数字、点、下划线和连字符，规范化后格式为 `[a-z0-9][a-z0-9._-]{2,63}`。显示名称去首尾空白后为 1—100 字。用户名全局唯一，冲突不提示另一个账号所在社区或身份。

账号项字段：`id、username、displayName、role、communityId、status、version、passwordChangeRequired`。创建账号为启用状态，`passwordChangeRequired=true`。重置密码会重新要求本人改密，并使目标账号既有会话失效；初始密码和重置密码由管理员输入，不通过列表或操作记录回显。

管理员只能重置其他后台账号，自己的密码须通过验证原密码的本人改密接口修改。操作记录按时间和编号倒序；动作包括 `CREATED`、`BOOTSTRAPPED`、`PROFILE_UPDATED`、`ROLE_CHANGED`、`DISABLED`、`ENABLED`、`PASSWORD_RESET`、`PASSWORD_CHANGED`、`ASSISTANCE_REVOKED`。一次编辑可产生多个动作；无变化的编辑不增加版本或操作事件，但仍保存幂等结果。

## 密码与会话

后台密码登录使用 `POST /api/v1/auth/admin/login`，请求为 URL 编码的 `username/password`，各环境均启用。成功返回当前身份，其中 `passwordChangeRequired` 表示是否必须先改密；登录成功并不绕过该限制。能力接口返回 `adminLogin`，保留 `developmentLogin` 和 `wechatLogin`。

本人改密使用 `POST /api/v1/auth/password`，JSON 字段为 `requestId/currentPassword/newPassword`。须校验当前密码，新密码不能与当前密码相同；成功返回 `data=null`，注销当前会话，并使其他旧会话失效，前端随后使用新密码重新登录。密码原文只用于校验、BCrypt 编码和带密钥请求摘要，不保存到历史、日志或前端持久化存储。

初始、重置和新密码要求为 12—64 个字符，UTF-8 编码不超过 72 字节，不能全部为空白；密码不去除首尾空格。不同账号不共享固定默认密码。

登录限流按直接连接来源每分钟 20 次、规范化账号每分钟 10 次实施，成功和失败请求均计数。限流窗口从第一次计数起持续 60 秒，被拒请求不延长窗口，最多保存 20000 个来源与账号摘要项。该计数为单实例内存状态，不读取客户端转发地址头，也不与 Redis 会话共享；反向代理和多实例场景需在可信入口补充统一限制。

每次请求从数据库核验账号状态、角色和登录时的凭证版本，业务服务继续检查有效社区及对象范围。启停、角色调整、改密及密码重置递增凭证版本，旧会话在下一次请求时被拒绝并清理。单纯更新显示名称不撤销会话。部署 V5 前建立的旧会话没有版本化身份，需要重新登录。

V6 新增独立通知版本：启停或角色调整同时使该账号旧通知永久失效，恢复启用或原角色不恢复历史消息；显示名称和密码变化不改变通知周期。此字段不返回账号接口，具体规则见[通知接口](NOTIFICATIONS_API.md)。

## 事务、幂等与存储

账号创建、更新、密码重置及本人改密均携带 UUID `requestId`。幂等唯一键为“社区＋操作者＋操作类型＋请求编号”；相同请求编号和规范化载荷返回原结果，不同载荷返回 409 `IDEMPOTENCY_CONFLICT`。重试必须保留原编号与内容，并重新满足当前账号和操作权限；不会返回原密码或重放敏感正文。

更新与重置使用读取时的 `version`，冲突后重新读取并由使用者确认，不自动覆盖新版本。写事务使用 `READ_COMMITTED`，先锁定社区行，再重新核验操作者、目标及版本，随后写入账号、健康授权撤销、操作事件与幂等结果。账号版本与登录凭证版本职责分离，界面只接收账号版本。

`V5__admin_accounts.sql` 为 `user_account` 增加 `credential_version` 和 `password_change_required`，继续使用已有 `version` 处理编辑冲突；既有后台账号迁移后须先改密，移动账号不受此标志影响。新增 `admin_account_event` 和 `admin_account_mutation_request`，不修改已发布迁移。

账号仅采集用户名、显示名称、角色、社区及状态，不额外收集身份证、手机号或人脸资料。密码使用 BCrypt 哈希；幂等载荷使用现有 `ELDER_DATA_KEY` 派生请求密钥的 HMAC，并通过账号操作用途域隔离。已有数据须继续使用原主密钥。事件只返回 `id/actorId/action/occurredAt`，不保存名称修改前后值、密码、健康正文或核验资料。数据库权限及备份需同时保护账号元数据和密码哈希。

## 首次平台管理员配置

首次管理员由启动时的显式配置开通，不开放匿名 HTTP 注册。配置项如下；也可在外部配置文件使用对应的 `app.bootstrap.*`，不得把实际初始密码提交到仓库。

| 环境变量 | 属性 | 默认值与约束 |
|---|---|---|
| APP_BOOTSTRAP_ENABLED | app.bootstrap.enabled | false；显式设为 true 才执行 |
| APP_BOOTSTRAP_COMMUNITY_ID | app.bootstrap.community-id | 0；开通时须为正整数 |
| APP_BOOTSTRAP_COMMUNITY_NAME | app.bootstrap.community-name | 空；社区不存在时必填，去首尾空白后 1—100 字 |
| APP_BOOTSTRAP_USERNAME | app.bootstrap.username | 空；满足本模块用户名规范 |
| APP_BOOTSTRAP_DISPLAY_NAME | app.bootstrap.display-name | 空；去首尾空白后 1—100 字 |
| APP_BOOTSTRAP_PASSWORD | app.bootstrap.password | 空；满足本模块密码规范，首次登录必须修改 |

社区不存在时同事务创建；已有社区必须有效，名称不会被配置覆盖。在社区锁内检查该社区是否已有任意状态的平台管理员或 `BOOTSTRAPPED` 事件；任一存在则跳过，不重复开通、不覆盖密码，也不提升现有账号。用户名冲突或配置不合法会使启动失败，不能用变更配置重置已有账号。

本地 `dev` 先创建示例社区 `10001` 及 `dev.operator`，再执行可选首次管理员开通；如需管理该示例社区，应使用 `APP_BOOTSTRAP_COMMUNITY_ID=10001` 和独立用户名。`dev.operator` 保留社区运营角色，`APP_DEV_PASSWORD` 只在首次创建时写入，仍须提供满足密码规范的配置。默认环境不创建开发账号。

开通完成后移除 `APP_BOOTSTRAP_ENABLED` 与 `APP_BOOTSTRAP_PASSWORD`，或外部文件中的开启标志和初始密码。后续账号由已完成改密的平台管理员维护。首次开通不是密码恢复或管理员权限修复通道。

## 管理端接入

`/accounts` 仅向本社区平台管理员显示，提供筛选、分页、创建、编辑、重置密码及操作记录。表单将角色或状态变更导致的会话失效明确提示；运营人员失去健康协助授权时提示恢复账号后仍须老人重新授权。角色目录只控制可选项，服务端重复核验权限。

`/password` 为独立页面，强制改密时不挂载后台布局；普通后台账号也可从布局中的改密入口进入。接口返回 `PASSWORD_CHANGE_REQUIRED` 时保留受限身份并转入该页，其余会话或权限失效清理身份。改密成功返回登录页，并清除内存密码。

`useAdminAccounts` 管理读取取消、写入快照和版本冲突；账号编辑与改密均在提交结果不确定时锁定输入，使用原 `requestId` 和载荷重试。关闭表单或离开页面清理密码与重试信息；版本冲突后重新读取账号，不自动覆盖。若改密已经生效而响应丢失，旧会话会失效，应使用新密码重新登录。账号列表、密码、筛选和草稿不写入浏览器持久化存储或 URL。

## 错误

| HTTP | code | 含义与处理 |
|---|---|---|
| 400 | INVALID_ARGUMENT | 字段、旧密码或新旧密码约束不满足，按提示修正 |
| 400 | INVALID_PASSWORD | 新密码长度或 UTF-8 字节限制不满足 |
| 401 | INVALID_CREDENTIALS | 登录凭据无效，不区分账号不存在、停用或角色不适用 |
| 401 | UNAUTHENTICATED / SESSION_REVOKED | 重新登录并恢复当前权限 |
| 403 | PASSWORD_CHANGE_REQUIRED | 先完成本人改密 |
| 403 | FORBIDDEN / COMMUNITY_UNAVAILABLE | 无管理权限、CSRF 失效或社区不可用 |
| 404 | ACCOUNT_NOT_FOUND | 目标不存在或不在本社区后台账号范围 |
| 409 | VERSION_CONFLICT | 刷新后确认最新账号状态 |
| 409 | IDEMPOTENCY_CONFLICT | 请求编号已用于其他内容，核对原请求 |
| 409 | USERNAME_UNAVAILABLE | 用户名已被占用，更换登录名 |
| 409 | SELF_ACCOUNT_CHANGE_FORBIDDEN | 禁止停用自己或移除自己的管理员角色 |
| 409 | SELF_PASSWORD_RESET_FORBIDDEN | 自己的密码须通过本人改密入口修改 |
| 409 | LAST_ADMIN_REQUIRED | 必须保留本社区最后一个有效平台管理员 |
| 429 | LOGIN_RATE_LIMITED | 登录请求过于频繁，一分钟后重试 |
