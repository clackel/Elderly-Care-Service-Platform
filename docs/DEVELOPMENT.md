# 开发规范

## 1. 工程边界

当前实现包括工程基础、后台账号密码认证与管理、角色与社区验证、老人档案、服务目录、预约与人工安排、履约、本人绑定、预约专用家属授权、微信登录、四类手工健康测量和独立健康授权，以及预约站内消息与待办。数据库结构以 V1—V6 Flyway 迁移、[后台账号接口](ADMIN_ACCOUNTS_API.md)、[预约接口](SERVICE_BOOKING_API.md)、[健康接口](HEALTH_API.md)及[通知接口](NOTIFICATIONS_API.md)为准；设备、医疗附件、求助投递、短信和微信订阅消息仍为规划能力。

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

新增或修改的功能方法必须编写中文注释，说明职责、参数、返回值及必要的权限、事务和状态约束；纯字段访问器不要求重复注释。主动抛出的异常使用中文消息，错误码保持稳定，异常内容不得包含个人信息、凭据或SQL参数。

## 3. 认证与数据边界

- 后台账号密码认证采用 Spring Security 表单处理器；前端提交 URL 编码的账号密码，响应为统一 JSON。该入口只接受后台角色，移动账号继续使用微信登录。
- 会话 Cookie 为 HttpOnly、SameSite=Lax；默认 Secure，只有回环地址开发模式关闭 Secure。
- 保留 Spring Security 默认的会话 CSRF 与会话固定攻击防护。写请求先取 `/auth/csrf`，登录、注销后重新取凭证。
- `AccountSessionFilter` 每次请求重新核验账号状态、角色及凭证版本；停用、角色调整及密码变化使既有会话失效。初始与重置密码账号必须先改密，服务端拦截未完成改密的业务请求。
- 社区编号从当前账号读取，客户端传入的社区编号不参与授权；平台管理员无自动跨社区权限。
- 当前模型一个账号只有一个角色；后台账号由本社区平台管理员管理，角色固定为 COMMUNITY_OPERATOR、DUTY_OFFICER、PLATFORM_ADMIN、AUDITOR。登录限流、改密与重置后的旧会话撤销已接入；多角色、自定义菜单按钮权限及 MFA 未实现。
- 账号所属社区不可经后台账号接口变更。禁止自停用或自降权，并保护本社区最后一个有效平台管理员。运营账号停用或转出角色时，必须在社区写事务内永久撤销其健康协助授权；恢复账号不能恢复旧授权。
- 社区范围校验不等于健康数据授权。健康域使用独立 HealthAccess，逐请求检查本人绑定或授权、绑定版本、期限、撤销状态及原始录入归属；禁止沿用预约管理角色权限。履约和家属访问同样必须校验具体对象和当前业务关系。
- 前端菜单和路由守卫只改善交互体验，不能替代后端鉴权。

## 4. 接口、时间和数据

前缀统一为 `/api/v1`。返回 `code/message/data/traceId`；过滤器生成追踪编号，并写入 `X-Trace-Id`。异常响应不包含 SQL、堆栈和敏感正文。

对外 ID 使用字符串，金额统一整数分。后续时间字段使用 `Instant` 或 `OffsetDateTime`，接口返回含时区 ISO 8601。应用默认 UTC；数据库连接按 UTC 配置。

分页请求使用 `@Valid PageQuery`，从第 1 页开始，默认 20、最大 100，返回 `PageResponse<T>`。校验必须位于入口，不能只依赖客户端。

Flyway 迁移位于 `backend/src/main/resources/db/migration/`；已发布迁移禁止直接修改，新增下一个未使用版本号的迁移。H2 用于本地开发与快速集成测试，MySQL 语义必须在真实 MySQL 验证。

## 5. 前端规范

管理后台使用 Vue 3 Composition API 与严格 TypeScript。按 `api / views / components / router / stores / styles` 划分，路由按需加载，Element Plus 按组件引入。所有 HTTP 请求统一经过封装，错误保留追踪编号；禁止在页面中散落请求实现。

会话信息只在 Pinia 内存中保存，页面刷新向服务端恢复。401/403 时清空会话并卸载当前页面，避免保留失效数据；403 `PASSWORD_CHANGE_REQUIRED` 保留受限身份并转入独立改密页面，不继续挂载业务布局。注销请求失败应提示重试，不宣称注销成功。

后台账号路由 `/accounts` 只向本社区平台管理员开放，由 `AdminAccountsView` 装配独立账号表格、筛选、编辑及操作记录组件；`useAdminAccounts` 管理分页取消、幂等写入快照和版本冲突。本人改密使用独立 `/password`，初始和重置密码账号优先进入该路由；成功后重新登录。密码只在输入及待确认请求的短期内存中保留，退出或关闭时清除，不能写入日志、URL 或持久化存储。

老人档案路由 `/elders` 由 `EldersView` 装配功能容器，`components/elders` 内拆分筛选、列表、编辑、详情及历史。`useElderRecords` 管理读取取消、草稿生命周期、版本冲突和写入状态；`api/elders.ts` 与后端契约对应。档案不得写入 localStorage、sessionStorage 或 URL；筛选条件和草稿随页面卸载清理。交互约定见[老人档案接口](ELDER_API.md#管理端集成)。

管理端 `npm test` 使用 Node 内置测试运行器及类型擦除执行 TypeScript 用例，不依赖新增测试框架；表单模型测试位于 `tests/elderModel.test.ts`。接口交互与权限由后端集成测试覆盖（AI 不新增集成测试）；界面表现和实际操作体验由人工在真实浏览器验收。

移动端使用官方 uni-app Vite TypeScript 结构，四个 tab 页面共用 `PageFrame` 和求助区域。默认正文 20px、特大字号 26px、按钮最小高度 56px。本地存储只保存字号偏好；健康数据和令牌不得持久化。当前无客户端角色切换和模拟微信登录。

健康入口采用独立功能组件和 useHealthRecords；两端共享 frontend/shared/health.ts 及 healthApi.ts 的纯 TypeScript 字段、时间和接口语义。健康视图隐藏或退出时卸载正文，返回前台重新核验身份及授权；未知写入结果保留原载荷与 requestId，重试成功后同样清理撤销授权涉及的内容。

通知两端共用 `frontend/shared/notifications.ts` 的类型、动作标题和时间规则，标题只使用固定泛化模板。移动端在“我的”进入独立消息页，隐藏时卸载消息及待办，返回前台重新恢复身份。消息打开预约只使用绑定账号的一次性内存编号，由服务页重新取得当前详情；禁止将通知列表、业务资料或导航目标放入 URL 和持久化存储。

## 6. 基础设施与环境

`dev`：H2 文件库、进程内会话、无 Redis 连接需求，仅监听回环地址；手动提供 `APP_DEV_PASSWORD` 及 RabbitMQ 连接配置。通知投递依赖真实 RabbitMQ，不使用内存消息替代；配置和失败处理见[通知接口](NOTIFICATIONS_API.md)。

本机 MySQL 测试需手动激活 `dev,local`。将 `backend/config/application-local.yml.example` 复制为同目录的 `application-local.yml`，在文件中覆盖数据源 URL、账号、密码及 MySQL 驱动，并提供 `app.dev-password`。以 `backend` 为工作目录，执行 `mvn spring-boot:run '-Dspring-boot.run.profiles=dev,local'`；IDE 调试使用相同工作目录和 Active profiles。Spring Boot 从外部 `config/` 目录加载配置；只启用 dev 不会加载 local 文件。实际文件被 Git 忽略且不进入 JAR。环境变量仍优先于文件，已有数据库必须沿用原加密密钥。此方式保留开发账号初始化及进程内会话，仅用于本机合成数据测试；配置登录密码仅初始化新账号，不重置已有账号密码。

默认环境：MySQL、Redis 会话、RabbitMQ 连接配置；后台密码登录启用，开发账号初始化器不启用。默认禁用 Swagger，只公开健康状态。必需配置 `ELDER_DATA_KEY` 及密钥管理要求见[老人档案接口](ELDER_API.md#存储与配置)。首次平台管理员使用显式开启的 `APP_BOOTSTRAP_*` 或 `app.bootstrap` 配置，具体字段和一次性语义见[后台账号接口](ADMIN_ACCOUNTS_API.md#首次平台管理员配置)。

本地基础设施配置：

```powershell
cd infra
Copy-Item .env.example .env
# 编辑 .env 为自己的本地密码后执行
docker compose up -d
```

后端需要独立设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`RABBITMQ_HOST`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`。Compose 的 `.env` 不会自动传给 Maven 启动的进程。

Compose 使用固定镜像版本，端口仅映射回环地址，数据通过命名卷保存。Nginx 文件只是路由示例；非 dev 使用 HTTPS 并保留 Secure Cookie。后台登录限流为单实例内存计数，多实例或代理部署需在可信入口统一限制。大创阶段未实现 MFA，真实社区部署另行确定运营和接入范围。

微信 AppID/AppSecret 用于真实小程序身份交换；缺少配置时关闭登录能力。小程序复用服务器会话及 CSRF，Cookie 只保存在运行内存。RabbitMQ 负责预约站内通知投递；对象存储和外部通知供应商字段仍为预留，短信、电话和微信订阅消息尚未接入。预约规则、开通码及部署要求见[预约接口](SERVICE_BOOKING_API.md)。

## 7. 提交检查

AI 交付前只做最小验证：为本次改动补充并运行相关的最小单元测试，并执行一次编译或构建校验，确认改动可编译；不执行完整 `mvn -B -ntp verify`，不执行 lint、格式检查和双端完整构建，也不新增集成测试和端到端测试。完整检查命令仍列在[仓库README](../README.md)，由人工按需执行。提交消息遵循[提交规范](GIT_COMMIT_CONVENTIONS.md)。

AI 不承担验收职责，验收仅在用户明确要求时进行，其余情况由人工自行安排。浏览器页面检查、微信开发者工具及真机验证、端到端验收、性能压测和部署演练由人工执行；数据库变更的 MySQL 兼容性及并发行为由人工在专用测试库验证，H2 结果不作为 MySQL 结论。交付说明只陈述实际执行过的命令与结果，不以自动化检查通过代替验收结论。

`.github/workflows/verify.yml` 提供后端构建及 MySQL 档案集成测试、管理端 lint/格式检查/测试/build、小程序类型检查及双端编译。CI 结果与验收结论由人工确认。

AI 生成的开发文档不包含验收章节、验收用例和人工验证清单，除非用户明确要求。
