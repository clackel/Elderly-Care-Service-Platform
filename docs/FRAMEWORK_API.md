# 基础框架接口

本文记录已实现的基础框架接口。老人档案后端见[老人档案接口](ELDER_API.md)；其他规划业务接口见[业务系统设计](SYSTEM_DESIGN.md#7-接口契约)。

| 方法 | 路径 | 当前行为 |
|---|---|---|
| GET | `/api/v1/auth/capabilities` | 公开；返回当前开发登录和微信登录能力标志 |
| GET | `/api/v1/auth/csrf` | 公开；创建或读取当前会话的 CSRF 凭证 |
| POST | `/api/v1/auth/admin/login` | 仅 dev；URL 编码的 username/password，需 CSRF 请求头 |
| GET | `/api/v1/auth/me` | 登录后返回账号 ID、显示名、角色及社区 ID |
| POST | `/api/v1/auth/logout` | 需 CSRF；销毁当前会话并删除 Cookie |
| GET | `/api/v1/system/community` | 后台角色；查询当前账号所属的有效社区 |
| GET | `/actuator/health` | 公开健康检查；遵循 Actuator 自身响应协议 |

Swagger 在 dev 环境的 `/swagger-ui/index.html`，OpenAPI JSON 在 `/v3/api-docs`。登录与注销由 Security Filter 实现，不由 Controller 生成 OpenAPI，契约以本文件为准。

## 登录顺序

1. 请求 `/auth/csrf`，保留响应 Cookie，读取 `data.headerName` 和 `data.token`。
2. 使用该请求头提交 `/auth/admin/login`，Content-Type 为 `application/x-www-form-urlencoded`。
3. 服务端轮换会话 ID；浏览器使用新的 HttpOnly Cookie。
4. 调用 `/auth/me` 恢复身份，调用 `/system/community` 读取当前社区。
5. 注销前重新请求 CSRF，随后 POST `/auth/logout`。

```json
{
  "code": "OK",
  "message": "操作成功",
  "data": {
    "id": "10001",
    "displayName": "开发运营账号",
    "role": "COMMUNITY_OPERATOR",
    "communityId": "10001"
  },
  "traceId": "服务端生成的 UUID"
}
```

身份和社区必须由服务端确定。接口不接受客户端指定角色或指定社区，用户表与社区表均为持久化读取。

## 错误

| 状态 | code | 含义 |
|---|---|---|
| 400 | INVALID_ARGUMENT | 入口校验失败 |
| 401 | UNAUTHENTICATED / INVALID_CREDENTIALS / SESSION_REVOKED | 未登录、密码错误或账号停用 |
| 403 | FORBIDDEN / COMMUNITY_REQUIRED / COMMUNITY_UNAVAILABLE | 权限不足、CSRF 失败或社区不可用 |
| 404 | NOT_FOUND | 已授权请求的资源不存在 |
| 500 | INTERNAL_ERROR | 未预期异常，不向客户端暴露内部内容 |

普通错误响应的 `data` 为 null。未授权访问优先按安全过滤器返回 401/403。409/429 等业务状态将在对应功能实现时补充，现有接口尚不使用这些状态。
