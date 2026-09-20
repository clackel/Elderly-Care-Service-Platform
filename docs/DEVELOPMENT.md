# 开发规范

## 1. 工程边界

当前实现包括工程基础、开发认证、角色与社区验证、老人档案，以及服务目录、预约与人工安排、履约、本人绑定、预约专用家属授权和微信登录入口。数据库结构以 V1—V3 Flyway 迁移及[预约接口](SERVICE_BOOKING_API.md)为准；健康、求助投递等仍为规划能力。

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

- 当前开发认证采用 Spring Security 表单处理器；前端提交 URL 编码的账号密码，响应为统一 JSON。
- 会话 Cookie 为 HttpOnly、SameSite=Lax；默认 Secure，只有回环地址开发模式关闭 Secure。
- 保留 Spring Security 默认的会话 CSRF 与会话固定攻击防护。写请求先取 `/auth/csrf`，登录、注销后重新取凭证。
- `AccountSessionFilter` 每次请求重新核验账号状态和角色，停用和降权对既有会话立即生效。
- 社区编号从当前账号读取，客户端传入的社区编号不参与授权；平台管理员无自动跨社区权限。
- 当前模型一个账号只有一个角色。多角色、菜单按钮权限、凭证重置后全会话撤销、登录限流及 MFA 在正式认证阶段实现。
- 社区范围校验不等于健康数据授权。后续健康、家属、履约接口必须继续核验对象归属、授权期限、撤销状态及当前业务关系。
- 前端菜单和路由守卫只改善交互体验，不能替代后端鉴权。

## 4. 接口、时间和数据

前缀统一为 `/api/v1`。返回 `code/message/data/traceId`；过滤器生成追踪编号，并写入 `X-Trace-Id`。异常响应不包含 SQL、堆栈和敏感正文。

对外 ID 使用字符串，金额统一整数分。后续时间字段使用 `Instant` 或 `OffsetDateTime`，接口返回含时区 ISO 8601。应用默认 UTC；数据库连接按 UTC 配置。

分页请求使用 `@Valid PageQuery`，从第 1 页开始，默认 20、最大 100，返回 `PageResponse<T>`。校验必须位于入口，不能只依赖客户端。

Flyway 迁移位于 `backend/src/main/resources/db/migration/`；已发布迁移禁止直接修改，新增下一个未使用版本号的迁移。H2 用于本地开发与快速集成测试，MySQL 语义必须在真实 MySQL 验证。

## 5. 前端规范

管理后台使用 Vue 3 Composition API 与严格 TypeScript。按 `api / views / components / router / stores / styles` 划分，路由按需加载，Element Plus 按组件引入。所有 HTTP 请求统一经过封装，错误保留追踪编号；禁止在页面中散落请求实现。

会话信息只在 Pinia 内存中保存，页面刷新向服务端恢复。401/403 时清空会话并卸载当前页面，避免保留失效数据。注销请求失败应提示重试，不宣称注销成功。

老人档案路由 `/elders` 由 `EldersView` 装配功能容器，`components/elders` 内拆分筛选、列表、编辑、详情及历史。`useElderRecords` 管理读取取消、草稿生命周期、版本冲突和写入状态；`api/elders.ts` 与后端契约对应。档案不得写入 localStorage、sessionStorage 或 URL；筛选条件和草稿随页面卸载清理。交互约定见[老人档案接口](ELDER_API.md#管理端集成)。

管理端 `npm test` 使用 Node 内置测试运行器及类型擦除执行 TypeScript 用例，不依赖新增测试框架；表单模型测试位于 `tests/elderModel.test.ts`。接口交互与权限由后端集成测试覆盖（AI 不新增集成测试）；界面表现和实际操作体验由人工在真实浏览器验收。

移动端使用官方 uni-app Vite TypeScript 结构，四个 tab 页面共用 `PageFrame` 和求助区域。默认正文 20px、特大字号 26px、按钮最小高度 56px。本地存储只保存字号偏好；健康数据和令牌不得持久化。当前无客户端角色切换和模拟微信登录。

## 6. 基础设施与环境

`dev`：H2 文件库、进程内会话、无 Redis/RabbitMQ 连接需求，仅监听回环地址；手动提供 `APP_DEV_PASSWORD`。

本机 MySQL 测试需手动激活 `dev,local`。将 `backend/config/application-local.yml.example` 复制为同目录的 `application-local.yml`，在文件中覆盖数据源 URL、账号、密码及 MySQL 驱动，并提供 `app.dev-password`。以 `backend` 为工作目录，执行 `mvn spring-boot:run '-Dspring-boot.run.profiles=dev,local'`；IDE 调试使用相同工作目录和 Active profiles。Spring Boot 从外部 `config/` 目录加载配置；只启用 dev 不会加载 local 文件。实际文件被 Git 忽略且不进入 JAR。环境变量仍优先于文件，已有数据库必须沿用原加密密钥。此方式保留 dev 认证及进程内会话，仅用于本机合成数据测试；配置登录密码仅初始化新账号，不重置已有账号密码。

默认环境：MySQL、Redis 会话、RabbitMQ 连接配置；开发账号初始化器和开发登录均不启用。默认禁用 Swagger，只公开健康状态。新增必需配置 `ELDER_DATA_KEY` 及密钥管理要求见[老人档案接口](ELDER_API.md#存储与配置)。

本地基础设施配置：

```powershell
cd infra
Copy-Item .env.example .env
# 编辑 .env 为自己的本地密码后执行
docker compose up -d
```

后端需要独立设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`RABBITMQ_HOST`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`。Compose 的 `.env` 不会自动传给 Maven 启动的进程。

Compose 使用固定镜像版本，端口仅映射回环地址，数据通过命名卷保存。Nginx 文件只是路由示例；上线需在受信代理配置 HTTPS，保留 Secure Cookie。当前未实现生产认证，不支持直接公开部署。

微信 AppID/AppSecret 用于真实小程序身份交换；缺少配置时关闭登录能力。小程序复用服务器会话及 CSRF，Cookie 只保存在运行内存。对象存储和通知字段仍为预留，短信和消息投递尚未实现。预约规则、开通码及部署要求见[预约接口](SERVICE_BOOKING_API.md)。

## 7. 提交检查

AI 交付前只做最小验证：为本次改动补充并运行相关的最小单元测试，并执行一次编译或构建校验，确认改动可编译；不执行完整 `mvn -B -ntp verify`，不执行 lint、格式检查和双端完整构建，也不新增集成测试和端到端测试。完整检查命令仍列在[仓库README](../README.md)，由人工按需执行。提交消息遵循[提交规范](GIT_COMMIT_CONVENTIONS.md)。

AI 不承担验收职责，验收仅在用户明确要求时进行，其余情况由人工自行安排。浏览器页面检查、微信开发者工具及真机验证、端到端验收、性能压测和部署演练由人工执行；数据库变更的 MySQL 兼容性及并发行为由人工在专用测试库验证，H2 结果不作为 MySQL 结论。交付说明只陈述实际执行过的命令与结果，不以自动化检查通过代替验收结论。

`.github/workflows/verify.yml` 提供后端构建及 MySQL 档案集成测试、管理端 lint/格式检查/测试/build、小程序类型检查及双端编译。CI 结果与验收结论由人工确认。

AI 生成的开发文档不包含验收章节、验收用例和人工验证清单，除非用户明确要求。
