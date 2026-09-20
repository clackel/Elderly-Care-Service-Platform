# 养老服务预约接口

本文记录已实现的服务目录、人工预约安排、预约授权、微信账号开通及履约接口。原开发方向见[归档草案](archive/SERVICE_BOOKING_DESIGN.md)，历史草案不作为现行契约。健康授权、支付、自动派单、容量库存、通知和企业独立后台不在本模块内。

## 通用约定与权限

- 前缀为 `/api/v1`，使用现有 `code/message/data/traceId` 响应，成功 HTTP 200。ID 为字符串，金额为整数分，时间为含时区的 ISO 8601，存储按 UTC。
- 分页使用 `page/pageSize`，默认 1/20，上限 100，返回 `items/page/pageSize/total`。本文表格中的路径省略统一前缀。
- 业务接口要求有效会话与有效社区，写请求携带 `/auth/csrf` 返回的动态请求头。预约、人员、账号与授权响应使用 `Cache-Control: no-store`。
- 社区管理角色为 `COMMUNITY_OPERATOR/PLATFORM_ADMIN`；管理员也只能访问账号所属社区。
- `ELDER` 必须具备有效的核验绑定；`FAMILY` 必须有未撤销、未到期的预约授权。`canBook=false` 只允许查询；为 true 时允许创建和取消。`STAFF` 只能读取分配给自己的任务和记录执行，且人员必须启用。
- 无权访问的对象与不存在的对象统一返回 404。权限在每次请求重新检查，客户端提交的角色、社区或老人编号不构成授权。
- 本人、有效授权家属可以查询该老人全部预约，不只查询自己创建的预约。授权撤销、到期或绑定停用后立即停止后续访问，已提交预约继续由社区处理。

## 目录、提供方与人员

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | /services | 当前社区分页目录，可按 category、available 筛选 |
| GET | /services/{id} | 项目详情 |
| POST | /services | 管理角色创建服务 |
| PUT | /services/{id} | 管理角色完整替换服务及上下架状态 |
| GET / POST | /service-providers | 管理角色分页查看 / 创建提供方 |
| PUT | /service-providers/{id} | 管理角色修改提供方 |
| GET | /service-workers | 管理角色分页查看人员能力 |
| PUT | /service-workers/{accountId} | 为本社区已核验 STAFF 账号创建或修改可安排信息 |

类别固定为 `MEAL` 助餐、`CLEANING` 助洁、`ESCORT` 陪诊、`CARE` 护理、`REHABILITATION` 康复。普通用户只能读取上架且提供方启用的项目；管理角色传 `available=true` 可使用相同筛选。

项目写入字段：

| 字段 | 约束 |
|---|---|
| requestId、version | UUID；非负版本，创建从 0 开始 |
| category、name、description | 类别枚举；名称 1—100 字；说明 1—1000 字 |
| durationMinutes | 15—480 的整数分钟 |
| priceFen | 0—10000000 的整数分，仅作参考展示 |
| providerId、serviceArea | 本社区提供方；服务范围 1—80 字 |
| professional、qualification | 专业服务仅适用于护理、康复；专业服务必须提供 1—100 字资质名称，生活服务使用空字符串 |
| enabled | true 上架，false 下架 |

提供方写入 `requestId/version/name/enabled`，名称最多 100 字。创建目录、提供方使用社区内请求编号去重；相同编号不同内容返回 409。更新使用当前版本。项目或提供方停用后不再接受新申请或新安排，历史快照和既有安排保留。

服务范围首期使用社区维护的明确区域名称。每个项目、人员各关联一个提供方及一个区域；安排时提供方、区域名称必须完全一致，人员类别必须包含该项目类别。它不是自动地址解析或地理围栏，社区需核实填写的详细地址位于所选范围内。

人员写入 `version/providerId/categories/serviceArea/qualification/qualificationExpiresAt/enabled`。categories 为上述枚举的非空集合；提供资质时必须同时填写到期时间。安排专业服务时资质名称须与快照要求一致，且覆盖完整服务时段。已有 CONFIRMED 或 IN_PROGRESS 安排的人员不能修改能力或停用，须先改派、取消或终止。具体人员必须明确指定，首期不支持团队安排。

## 账号核验、本人绑定与家属授权

| 方法 | 路径 | 行为 |
|---|---|---|
| GET / POST | /booking-access/members | 社区分页查看 / 核验开通移动账号 |
| POST | /booking-access/members/{id}/enrollment | 社区签发一次性微信开通码 |
| GET / POST | /booking-access/bindings | 社区分页查看 / 建立、停用或恢复本人绑定 |
| GET / POST | /booking-access/grants | 查询授权 / 明确同意后授予或调整预约权限 |
| POST | /booking-access/grants/{id}/revoke | 携带 version 撤销授权 |

开通账号请求：`requestId、displayName、role、verificationReference`。角色只允许 ELDER/FAMILY/STAFF，显示名称最多 50 字，核验依据编号最多 200 字。社区应先核验真实身份，依据字段填写内部记录编号，不采集身份证照片等额外材料。创建请求幂等，账号没有向用户展示的密码，也没有开发角色切换入口。

签发开通码返回 `accountId/token/expiresAt`，有效期 30 分钟、单次使用，重新签发立即废止旧码。原码只在签发响应显示，数据库保存带密钥摘要；已经绑定微信的账号不能重新签发。工作人员资质由社区在人员信息中独立核验，微信登录不代表通过专业资质审核。

本人绑定写入：

- `accountId/elderId/version/active/verificationReference`。
- 双方必须属于同一社区，账号为有效 ELDER；启用时老人档案必须在管。
- 一个账号对应一个档案，一个档案对应一个账号。现有绑定可以停用或恢复，不能静默替换另一账号或档案；首期不提供身份迁移接口。

家属授权写入：

- `elderId/familyId/version/canBook/expiresAt/consentReference`，首次版本 0。
- 账号必须是本社区有效 FAMILY，老人档案必须在管；到期时间在未来 366 天内。
- 仅已核验老人本人或社区管理人员可授予权限。社区须记录明确同意依据；本人在线操作记录为本人明确同意。家属不能自行授予权限。
- 一对老人、家属只保留一条当前授权；调整、续期或重新启用使用原版本更新。
- 老人本人、社区管理人员、该家属均可撤销；撤销增加版本。授权列表保留失效、撤销记录。
- 仅用于预约，不能据此访问健康档案。

## 微信登录与部署配置

`POST /auth/wechat/login` 请求为 `{code, enrollmentToken?}`。code 为小程序 `uni.login` 返回的临时凭证。服务端向微信官方 `https://api.weixin.qq.com/sns/jscode2session` 换取身份，拒绝客户端自报 openid、账号编号或角色。首次登录须提供社区交付的开通码；后续仅提交 code。openid 以带 AppID 命名空间的 HMAC 摘要保存，不向客户端返回微信 session_key。

返回现有 `AccountDto`，成功后轮换服务端会话 ID 和 CSRF，显式保存 Spring Security 上下文。本模块复用服务端会话，不引入未实现的访问令牌或刷新令牌协议。微信小程序仅在内存中携带 `ELDERLY_CARE_SESSION` Cookie，每次写入先获取 CSRF；H5 使用浏览器同源 HttpOnly Cookie，仅支持恢复已有真实会话，微信登录在小程序执行。关闭小程序后可重新通过微信登录恢复账号。

| 配置 | 作用 |
|---|---|
| WECHAT_APP_ID | 服务端正式小程序 AppID |
| WECHAT_APP_SECRET | 服务端 AppSecret，仅放环境或外部安全配置，不能进入前端 |
| frontend/mobile/src/manifest.json 的 mp-weixin.appid | 与服务端一致的小程序 AppID |
| frontend/mobile/.env.local 的 VITE_API_BASE_URL | 已在微信后台配置为 request 合法域名的 HTTPS 地址，包含 /api/v1 |
| ELDER_DATA_KEY | 32 字节 Base64 主密钥；预约与档案使用不同用途的派生密钥 |
| DB_*、REDIS_* 等基础环境 | 沿用默认环境要求；非 dev 使用 Redis 服务端会话 |

缺少 AppID 或 AppSecret 时 `/auth/capabilities` 返回 `wechatLogin=false`，登录返回 503，不生成模拟身份。配置齐全仅表示入口已配置，不代表第三方凭据已被验证。后端需要能够通过 HTTPS 访问微信身份服务。

登录入口保留 CSRF，并按直接连接来源实施单实例一分钟 20 次、最多 10000 个活动来源的内存限流。反向代理和多实例部署应在可信入口配置统一登录限流；应用不信任用户传入的转发地址头。正式管理员认证及 MFA 仍未实现，当前后台 dev 登录仅适用于本地开发，不能因微信入口已实现而把开发后台公开部署。

## 预约申请与查询

| 方法 | 路径 | 行为 |
|---|---|---|
| GET | /bookings/rules | 返回当前 advanceMinutes/horizonDays/cancelBeforeMinutes |
| GET | /bookings/elders | 按 page/pageSize 返回当前可访问老人选项数组 |
| GET | /bookings | 按 page/pageSize/status 查询当前角色可见预约 |
| POST | /bookings | 幂等创建预约申请 |
| GET | /bookings/{id} | 查看详情 |
| GET | /bookings/{id}/history | 分页查看处理记录，版本及编号倒序 |

老人选项包含 `id/name/canBook/address/contactName/contactPhone`。只有已核验本人且可预约时预填地址及档案紧急联系人；家属不通过选项接口读取完整地址或电话，需为当次服务填写联系人。归档档案可保留查询入口，但 `canBook=false`。

创建请求：

~~~json
{
  "requestId": "95fb3bc8-565f-42b1-8b93-3e9a3ed0b26c",
  "application": {
    "elderId": "10001",
    "serviceId": "10002",
    "requestedStart": "2026-10-02T11:00:00+08:00",
    "address": "合成社区测试地址",
    "contactName": "合成联系人",
    "contactPhone": "13900139000",
    "remark": "",
    "specific": {
      "mealDate": "2026-10-02",
      "meal": "LUNCH",
      "portions": 1,
      "dietaryRequirements": ""
    }
  }
}
~~~

示例时间仅说明格式，实际必须符合运行配置的可预约时间窗。address 必填、最多 200 字；contactName 必填、最多 50 字；contactPhone 为大陆手机号或带区号固定电话；remark 使用字符串、最多 300 字。

| 类别 | specific 字段与规则 |
|---|---|
| 助餐 | mealDate、meal、portions 必填；餐次 BREAKFAST/LUNCH/DINNER；1—20 份；dietaryRequirements 最多 200 字 |
| 助洁 | cleaningScope 必填，DAILY/DEEP/KITCHEN/BATHROOM |
| 陪诊 | hospital 1—100 字、appointmentTime、meetingPoint 1—200 字、assistance 均必填；协助 NONE/WALKING/WHEELCHAIR |
| 护理 | careContent 必填，DAILY_LIVING/PERSONAL_CARE/PROFESSIONAL；专业类型必须与项目一致；precautions 最多 300 字 |
| 康复 | location 为 HOME/COMMUNITY_CENTER/PROVIDER_SITE，assessmentRequired 为布尔值，两者必填；precautions 最多 300 字 |

拒绝携带其他类别的非空差异字段。助餐日期按上海时区与期望时间同一天；陪诊就诊时间必须位于期望服务时段内。地址、联系方式、必要饮食要求及注意事项均只服务于本次申请，不用于诊断或处方。

创建编号在 `社区 + 申请账号` 内唯一；相同编号、相同内容返回原预约当前状态，不增加事件；相同编号不同内容返回 409。重试仍需具备当前对象权限。服务下架、档案归档、时间过期等检查在首次创建时执行。前端超时保留原请求和内容供幂等重试，不能把超时当作创建失败后另生成编号。

提交时保存项目完整快照（说明、分类、提供方、范围、时长、资质、参考价）；服务项目后续变更不覆盖历史。详情包含申请资料、服务快照、申请人、安排人员、实际时段、开始及结束时间、结果、异常标志、状态和版本。列表只返回预约、老人编号、服务名称与类别、时间、状态和版本，不解密地址或电话。

## 人工安排与履约

| 方法 | 路径 | 权限与请求 |
|---|---|---|
| POST | /bookings/{id}/arrange | 社区；version/workerId/start/end/coordinationNote |
| POST | /bookings/{id}/reject | 社区；version/note |
| POST | /bookings/{id}/cancel | 本人、具备代办权限的家属或社区；version/note |
| POST | /bookings/{id}/start | 分配给本人的 STAFF；version/note |
| POST | /bookings/{id}/complete | 分配给本人的 STAFF；version/note |
| POST | /bookings/{id}/exception | 分配给本人的 STAFF；version/note |
| POST | /bookings/{id}/resolve | 社区；version/note |
| POST | /bookings/{id}/terminate | 社区；version/note |

所有动作携带读取时的非负 version。note 必填、最多 1000 字；协调说明最多 500 字。状态如下：

| 来源 | 操作 | 结果 |
|---|---|---|
| PENDING 待社区确认 | 确认并安排人员 | CONFIRMED 已确认 |
| PENDING | 拒绝 / 取消 | REJECTED / CANCELLED |
| CONFIRMED | 改派或调整时间 | 状态不变，增加版本及安排记录 |
| CONFIRMED | 开始 / 取消 / 终止 | IN_PROGRESS / CANCELLED / TERMINATED |
| IN_PROGRESS | 完成 / 终止 | COMPLETED / TERMINATED |
| CONFIRMED 或 IN_PROGRESS | 上报 / 处理异常 | 主状态不变，修改 hasException 并记录原因、处理结果 |

确认必须明确人员和起止时间，时长必须与申请快照一致。时间变化及任何重新安排都必须填写协调结果；原安排时间已到后不允许直接改派，须处理异常或终止后重新申请。助餐安排不能跨原用餐日期；陪诊安排必须覆盖原就诊时间。已确认预约不支持用户直接修改内容。

人员安排使用半开区间 `[start,end)`，相邻时段可以安排，重叠时段拒绝。开始服务必须在计划时段内，工作人员有其他 IN_PROGRESS 任务或当前有未处理异常时不能开始。计划结束时间不会自动把超时任务标为完成。

待确认预约可取消；已确认预约的本人、家属取消须严格早于配置截止点，社区可在尚未开始时记录原因取消。服务中不能取消。工作人员提交结果即完成，不另设复核状态；存在未处理异常时禁止完成。社区解决异常只记录处理结果，不代替工作人员完成；无法继续的服务使用 TERMINATED 并保存原因，不显示为已完成。终态不再接受内容或状态修改。

## 业务配置、事务与存储

| 环境变量 | 默认值 | 范围与含义 |
|---|---|---|
| BOOKING_ADVANCE_MINUTES | 60 | 0—10080，申请、确认和改期的最小提前分钟数 |
| BOOKING_HORIZON_DAYS | 30 | 1—365，未来可预约天数；总窗口须大于提前量 |
| BOOKING_CANCEL_BEFORE_MINUTES | 120 | 0—10080，已确认预约的用户取消提前量 |

上述为可配置的开发默认规则，正式运营应按社区服务能力确定配置。服务时长按项目设置；跨类别上限及餐次份数属于本版固定契约。价格不生成支付状态或应收单，助餐份数也不在首期自动计算支付金额。

`V3__service_booking.sql` 新增 service_provider、service_item、service_worker、elder_account_binding、booking_grant、service_booking、booking_event、booking_access_event、mobile_enrollment、wechat_identity。预约直接关联当前工作人员与计划时段，事件保留每次安排，首期不重复维护独立派单主状态。

写事务使用读已提交隔离，先锁定有效 community 行，再检查对象、版本、人员冲突及授权并写入。目录/提供方/人员变更、授权和绑定变更、预约创建与动作、开通码消费及档案归档遵循同一社区锁，作为首期跨应用实例的数据库并发保护；不同社区互不串行。服务人员属于一个社区，不提供跨社区安排。版本过期返回 409，不能自动覆盖新状态。创建唯一约束为数据库最后防线。

老人存在 PENDING/CONFIRMED/IN_PROGRESS 预约时拒绝归档，返回 ELDER_HAS_OPEN_BOOKINGS；先取消、完成或终止再归档。归档后既有终态预约保留，不因档案归档删除历史。

预约申请（地址、电话、必要注意事项）、结果、事件说明、核验与同意依据采用 AES-GCM 加密；使用预约用途的独立派生密钥与随机 IV，附加认证数据绑定用途、社区和对象。服务内容快照不含老人个人资料，以 JSON 保存。请求、微信身份和开通码使用不同用途的 HMAC 摘要。现有加密资料必须继续使用原主密钥，密钥轮换需另行迁移，不能直接改配置覆盖。

详情、历史、老人选项访问及目录、人员、授权、绑定和开通调整记录在 booking_access_event；它仅记录操作者、目标、动作和时间。状态、安排、结果与原因保存在 booking_event，说明加密，不进入日志。工作人员任务进入终态后，详情不再返回地址、联系人和申请注意事项；结果与处理记录保留在其本人任务范围。

## 前端入口与工程边界

管理端：`/services` 维护目录、提供方和人员；`/bookings` 代预约和人工处理；`/fulfillment` 复用同一预约状态及异常处理工作区；`/consent` 管理账号、本人绑定及预约授权。工作台菜单和路由守卫只负责交互，后端始终执行权限检查。

移动端保留首页、服务、健康、我的四个标签。“我的”提供真实微信登录、账号编号及本人/收到的预约授权；“服务”对老人、家属提供目录与预约，对工作人员提供本人任务。请求超时保留原申请，版本冲突刷新详情；401/403 清理会话并卸载个人资料。

管理端和移动端共用 `frontend/shared/booking.ts` 的契约、分类字段及中文显示规则。Vue 页面仅装配功能组件，列表、表单、详情和读取写入状态分别维护。个人信息、草稿、开通码和小程序会话不写入浏览器或小程序持久化存储。

## 常见错误

| HTTP | code 示例 | 处理 |
|---|---|---|
| 400 | INVALID_ARGUMENT / BOOKING_TIME_INVALID / COORDINATION_REQUIRED | 修正必填、类别、时间或协调结果 |
| 401 | UNAUTHENTICATED / WECHAT_CODE_INVALID | 重新获取微信凭证或登录 |
| 403 | FORBIDDEN / ENROLLMENT_REQUIRED / ENROLLMENT_INVALID / ACCOUNT_UNAVAILABLE | 联系社区核验身份、开通码或账号状态 |
| 404 | BOOKING_RESOURCE_NOT_FOUND | 不存在或当前不具备对象权限 |
| 409 | IDEMPOTENCY_CONFLICT / VERSION_CONFLICT | 原请求保持原内容；版本冲突刷新后人工处理 |
| 409 | ELDER_ARCHIVED / ELDER_HAS_OPEN_BOOKINGS | 恢复档案或先结束预约 |
| 409 | SERVICE_UNAVAILABLE / WORKER_UNAVAILABLE / WORKER_SCOPE_MISMATCH / WORKER_QUALIFICATION_INVALID | 调整服务、人员或能力资料 |
| 409 | WORKER_TIME_CONFLICT / WORKER_STILL_SERVING / START_TIME_INVALID | 调整安排或先处理当前任务 |
| 409 | BOOKING_STATE_CONFLICT / CANCELLATION_CLOSED / UNRESOLVED_EXCEPTION | 按最新状态或取消规则处理 |
| 429 | LOGIN_RATE_LIMITED | 稍后重试登录 |
| 502 / 503 | WECHAT_UNAVAILABLE / WECHAT_NOT_CONFIGURED | 微信服务不可用或尚未配置 |
