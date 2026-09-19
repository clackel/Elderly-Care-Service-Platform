# 社区养老服务平台

2026 大创项目。当前交付为前后端分离、模块化单体的基础框架，项目暂未确定品牌名称。

## 当前范围

- Spring Boot：统一响应、异常处理、请求追踪、开发环境会话登录、CSRF、角色和社区权限、Flyway 迁移。
- 老人档案后端：建档、精确检索、分页、详情、版本更新、归档恢复、加密存储及操作历史。
- Vue 管理后台：登录、工作台、模块导航、路由守卫；老人档案查询、建档、编辑、详情、归档恢复与变更记录。
- uni-app 小程序：四个底部导航页面、字号切换、公共请求和状态结构、电话求助入口。
- 工程配套：版本锁定、ESLint、严格 TypeScript、集成测试、CI、基础设施配置。

预约、健康档案、支付、短信、微信认证等业务尚未实现。所有预留页面明确展示待开发状态；老人档案管理位于管理后台 `/elders`。

## 目录

```text
backend/                Java 21 + Spring Boot，包名 com.elderlycare.platform
frontend/admin-web/     Vue 3 + TypeScript + Element Plus + Vite
frontend/mobile/        uni-app + Vue 3 + TypeScript
infra/                  MySQL、Redis、RabbitMQ 和 Nginx 配置示例
docs/                   业务设计、开发规范及接口契约
```

## 本地启动（PowerShell）

需要 JDK 21、Maven 3.9+、Node.js 22.12+。三个终端分别在仓库根目录执行。

**后端**（开发模式无需 Docker）：

```powershell
cd backend
$env:APP_DEV_PASSWORD = '替换为你自己的12至64位开发密码'
mvn spring-boot:run '-Dspring-boot.run.profiles=dev'
```

开发账号：`dev.operator`。首次启动以环境变量中的密码创建账号；后续启动不会覆盖已有密码。开发数据库位于 `backend/.data/`，仅允许合成数据。开发服务只监听 `127.0.0.1`。

**使用本机 MySQL，并保存配置**：

先创建独立测试库 `elderly_care`，连接账号需有该库的建表、修改表、索引及增删改查权限；Flyway 会自动创建表结构。在仓库根目录首次执行：

```powershell
Copy-Item backend/config/application-local.yml.example backend/config/application-local.yml
```

编辑 `backend/config/application-local.yml`，填写 MySQL 地址、账号、密码，以及 12～64 位的 `app.dev-password`。以后从仓库根目录执行：

```powershell
cd backend
mvn spring-boot:run '-Dspring-boot.run.profiles=dev,local'
```

必须以 `backend` 为工作目录并按顺序启用 `dev,local`，才能自动加载本机 MySQL 配置，同时保留开发登录、回环监听和进程内会话；不需要 Redis 或 RabbitMQ。只启用 `dev` 会使用 H2 文件数据库，不会读取 `application-local.yml`。实际配置被 Git 忽略，位于源码资源目录之外，不会打包进 JAR。配置密码可重复使用，但修改 `app.dev-password` 不会重置已有账号密码。仅使用合成测试数据；已有加密数据需继续使用原密钥。

IDE 手动调试：主类为 `com.elderlycare.platform.CareApplication`，工作目录设为仓库的 `backend` 目录，Active profiles 设为 `dev,local`（或在 Program arguments 中填写 `--spring.profiles.active=dev,local`）。不需要启动脚本。

启动日志应显示 `dev` 和 `local` 两个环境，以及 Flyway 的 MySQL 连接记录。若显示 `jdbc:h2:file:`，说明实际仍在使用 H2；若 MySQL 连接、权限或迁移失败，应先修复启动错误。Flyway 创建表，不负责创建 MySQL 数据库本身。连接同一个库执行 `SHOW TABLES`，应包含 `community`、`user_account`、`elder_profile`、`elder_profile_event` 和 `flyway_schema_history`。登录校验数据库中的密码哈希，配置密码仅初始化新账号，因此旧账号密码不会随配置文件变更。

如果之前在终端设置过 `SPRING_DATASOURCE_*` 等环境变量，请打开新终端运行，避免高优先级环境变量覆盖文件配置。无需再次复制模板，以免覆盖已填写的参数。

**管理后台**：

```powershell
cd frontend/admin-web
npm ci
npm run dev
```

访问 [管理后台](http://127.0.0.1:5173)，使用上述账号和你设置的密码登录。

**小程序 H5 预览**：

```powershell
cd frontend/mobile
npm ci
npm run dev:h5
```

访问 [移动端预览](http://127.0.0.1:5174)。微信小程序执行 `npm run build:mp-weixin`，在微信开发者工具中导入 `dist/build/mp-weixin`。真机接入前需配置 `src/manifest.json` 的 AppID 和 `.env.local` 中的 HTTPS 接口域名。

## 检查命令

```powershell
# backend/
mvn -B -ntp verify

# frontend/admin-web/
npm run lint
npm run format:check
npm test
npm run build

# frontend/mobile/
npm run type-check
npm run build:h5
npm run build:mp-weixin
```

开发接口文档：[Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)。

## 开发文档

- [开发文档索引](docs/README.md)
- [开发规范](docs/DEVELOPMENT.md)
- [Git 提交消息规范](docs/GIT_COMMIT_CONVENTIONS.md)
- [基础框架接口](docs/FRAMEWORK_API.md)
- [老人档案接口](docs/ELDER_API.md)
- [依赖与参考资料](docs/REFERENCES.md)

默认配置面向 MySQL、Redis 和 RabbitMQ，必须提供外部环境变量。正式管理员认证、多因素认证和小程序令牌会话尚未接入，因此非 `dev` 环境没有开发密码登录入口。基础框架尚不具备公开上线条件。
