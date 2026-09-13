# 开发规范

## 1. 工程边界

当前实现包括工程基础、开发认证、角色与社区范围验证、前端页面框架。数据库仅创建 `community` 和 `user_account`。其余业务模块用 `package-info.java` 说明职责，尚无业务 Controller 或模拟成功接口。

工程标识：`elderly-care-service-platform`；Java 根包：`com.elderlycare.platform`。用户界面采用“社区养老服务平台”，不使用尚未确认的品牌名。

## 2. 分层与命名

后端按业务模块划分，再按 `api / service / mapper / domain` 分层。`common` 只放跨模块的技术基础，不承载业务规则。

| 层 | 职责 | 约束 |
|---|---|---|
| Controller / api | 路由、参数绑定、响应 DTO | 不直接访问数据库 |
| Service | 权限校验、业务协调、事务边界 | 构造器注入；有写操作时明确事务 |
| Mapper | 持久化与查询 | 普通 CRUD 用 MyBatis-Plus；关联、复杂筛选用 XML |
| domain | 实体、枚举、内部投影 | 不直接输出到客户端 |

参考调用链：`SystemController → CommunityService → CommunityAccess / CommunityMapper → CommunityMapper.xml → community`。

简单 Service 不创建只有一份实现的同名接口；出现可替换实现或稳定跨模块契约时再提取接口。禁止循环依赖、字段注入、拼接用户输入的 SQL 和 Controller 返回持久化实体。

必要注释解释安全约束、状态变化和设计原因，避免逐行重复代码含义。公共契约、事务边界和集成适配器需要说明使用限制。

## 3. 认证与数据边界

- 当前开发认证采用 Spring Security 表单处理器；前端提交 URL 编码的账号密码，响应为统一 JSON。
- 会话 Cookie 为 HttpOnly、SameSite=Lax；默认 Secure，只有回环地址开发模式关闭 Secure。
- 保留 Spring Security 默认的会话 CSRF 与会话固定攻击防护。写请求先取 `/auth/csrf`，登录、注销后重新取凭证。
- `AccountSessionFilter` 每次请求重新核验账号状态和角色，停用和降权对既有会话立即生效。
- 社区编号从当前账号读取，客户端传入的社区编号不参与授权；平台管理员无自动跨社区权限。
- 当前模型一个账号只有一个角色。多角色、菜单按钮权限、凭证重置后全会话撤销、登录限流及 MFA 在正式认证阶段实现。
- 社区范围校验不等于健康数据授权。后续健康、家属、订单接口必须继续核验对象归属、授权期限、撤销状态及当前业务关系。
- 前端菜单和路由守卫只改善交互体验，不能替代后端鉴权。

## 4. 接口、时间和数据

前缀统一为 `/api/v1`。返回 `code/message/data/traceId`；过滤器生成追踪编号，并写入 `X-Trace-Id`。异常响应不包含 SQL、堆栈和敏感正文。

对外 ID 使用字符串，金额统一整数分。后续时间字段使用 `Instant` 或 `OffsetDateTime`，接口返回含时区 ISO 8601。应用默认 UTC；数据库连接按 UTC 配置。

分页请求使用 `@Valid PageQuery`，从第 1 页开始，默认 20、最大 100，返回 `PageResponse<T>`。校验必须位于入口，不能只依赖客户端。

Flyway 迁移位于 `backend/src/main/resources/db/migration/`；已发布迁移禁止直接修改，新增下一个未使用版本号的迁移。H2 用于本地开发与快速集成测试，MySQL 语义必须在真实 MySQL 验证。

## 5. 前端规范

管理后台使用 Vue 3 Composition API 与严格 TypeScript。按 `api / views / components / router / stores / styles` 划分，路由按需加载，Element Plus 按组件引入。所有 HTTP 请求统一经过封装，错误保留追踪编号；禁止在页面中散落请求实现。

会话信息只在 Pinia 内存中保存，页面刷新向服务端恢复。401/403 时清空会话并卸载当前页面，避免保留失效数据。注销请求失败应提示重试，不宣称注销成功。

移动端使用官方 uni-app Vite TypeScript 结构，四个 tab 页面共用 `PageFrame` 和求助区域。默认正文 20px、特大字号 26px、按钮最小高度 56px。本地存储只保存字号偏好；健康数据和令牌不得持久化。当前无客户端角色切换和模拟微信登录。

## 6. 基础设施与环境

`dev`：H2 文件库、进程内会话、无 Redis/RabbitMQ 连接需求，仅监听回环地址；手动提供 `APP_DEV_PASSWORD`。

默认环境：MySQL、Redis 会话、RabbitMQ 连接配置；开发账号初始化器和开发登录均不启用。默认禁用 Swagger，只公开健康状态。

本地基础设施配置：

```powershell
cd infra
Copy-Item .env.example .env
# 编辑 .env 为自己的本地密码后执行
docker compose up -d
```

后端需要独立设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`RABBITMQ_HOST`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`。Compose 的 `.env` 不会自动传给 Maven 启动的进程。

Compose 使用固定镜像版本，端口仅映射回环地址，数据通过命名卷保存。Nginx 文件只是路由示例；上线需在受信代理配置 HTTPS，保留 Secure Cookie。当前未实现生产认证，不支持直接公开部署。

配置中的微信、对象存储和通知字段仅预留命名，不会调用供应商。短信、支付、消息投递和外部数据交换尚未实现。

## 7. 提交检查

提交前执行[仓库README](../README.md)中的检查命令，并遵循[提交规范](GIT_COMMIT_CONVENTIONS.md)。后端测试应通过真实过滤器、事务和数据库验证权限边界；无需为简单 getter 编写重复测试。界面变更还要检查真实浏览器，微信专有能力要在开发者工具及真机验证。

`.github/workflows/verify.yml` 提供后端构建、管理端 lint/build、小程序类型检查及双端编译。检查结果以对应提交的 GitHub Actions 运行记录为准。
