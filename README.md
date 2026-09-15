# 社区养老服务平台

2026 大创项目。当前交付为前后端分离、模块化单体的基础框架，项目暂未确定品牌名称。

## 当前范围

- Spring Boot：统一响应、异常处理、请求追踪、开发环境会话登录、CSRF、角色和社区权限、Flyway 迁移。
- 老人档案后端：建档、精确检索、分页、详情、版本更新、归档恢复、加密存储及操作历史。
- Vue 管理后台：登录、工作台、模块导航、路由守卫、Pinia 和请求封装。
- uni-app 小程序：四个底部导航页面、字号切换、公共请求和状态结构、电话求助入口。
- 工程配套：版本锁定、ESLint、严格 TypeScript、集成测试、CI、基础设施配置。

老人档案页面、预约、健康档案、支付、短信、微信认证等业务尚未实现。所有预留页面明确展示待开发状态。

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
