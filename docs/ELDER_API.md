# 老人档案接口

本模块实现社区管理端的老人基础资料管理。预约所需的本人绑定、家属授权和微信认证见[预约接口](SERVICE_BOOKING_API.md)，它们不授予健康记录访问权限。

## 权限与响应

- 前缀：`/api/v1/elders`。
- 角色：`COMMUNITY_OPERATOR`或`PLATFORM_ADMIN`，且账号必须属于有效社区。
- 社区从当前账号读取，不接受客户端指定社区；跨社区编号与不存在的编号均返回404。
- 使用现有会话认证，写请求必须携带CSRF凭证，流程见[基础框架接口](FRAMEWORK_API.md)。
- 成功状态为HTTP 200，响应格式为`code/message/data/traceId`，档案响应使用`Cache-Control: no-store`。
- ID为字符串，时间戳使用ISO 8601，出生日期使用YYYY-MM-DD。

## 接口

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| GET | /elders | page、pageSize、keyword、status | 档案摘要分页 |
| POST | /elders | requestId、profile | 档案详情 |
| GET | /elders/{id} | — | 档案详情 |
| PUT | /elders/{id} | version、profile | 更新后详情 |
| POST | /elders/{id}/archive | version | 已归档详情 |
| POST | /elders/{id}/restore | version | 恢复后详情 |
| GET | /elders/{id}/history | page、pageSize | 修改历史分页 |

表内路径相对于`/api/v1`。当前不提供物理删除、批量导出、健康信息写入或用户账号绑定接口。

## 档案字段

| profile字段 | 类型 | 校验 |
|---|---|---|
| name | string | 必填，去首尾空白后1—50字符 |
| gender | enum | MALE、FEMALE、UNKNOWN |
| birthDate | date | 必填，1900-01-01至当前日期 |
| phone | string/null | 可空；大陆手机号或0开头固定电话 |
| address | string | 必填，1—200字符 |
| livingArrangement | enum | ALONE、WITH_FAMILY、INSTITUTION、OTHER、UNKNOWN |
| emergencyContactName | string | 必填，1—50字符 |
| emergencyContactPhone | string | 必填，与phone使用相同号码格式 |
| emergencyContactRelation | string | 必填，1—30字符 |
| remark | string/null | 最多500字符，不用于记录病史 |

手机号为11位且以1[3-9]开头；固定电话由3—4位区号和7—8位号码组成，允许区号后一个连字符。服务端校验长度和格式。

### 建档

requestId为客户端生成的UUID。同社区相同编号及相同规范化资料重复提交，返回既有档案当前状态，不新增档案或创建事件；同编号不同资料返回409。网络失败重试沿用原编号，新建档使用新编号。

~~~json
{
  "requestId": "95fb3bc8-565f-42b1-8b93-3e9a3ed0b26c",
  "profile": {
    "name": "测试老人",
    "gender": "FEMALE",
    "birthDate": "1950-01-01",
    "phone": "13800138000",
    "address": "测试路1号",
    "livingArrangement": "WITH_FAMILY",
    "emergencyContactName": "测试联系人",
    "emergencyContactPhone": "13900139000",
    "emergencyContactRelation": "子女",
    "remark": null
  }
}
~~~

同社区下“姓名＋出生日期＋联系电话”组合唯一，电话优先使用老人电话，缺少时使用紧急联系人电话；电话去除区号连字符后比较。不同姓名或出生日期可共用电话。归档后仍保留此约束，应恢复原档案。

详情包含`id、communityId、profile、status、version、createdAt、updatedAt`，不返回密文、索引和请求摘要。

### 修改与状态

PUT为完整替换profile，必填字段不能省略；顶层version必须为读取详情时获得的版本。无字段变化时不增加版本或历史。

~~~json
{
  "version": 0,
  "profile": {
    "name": "测试老人",
    "gender": "FEMALE",
    "birthDate": "1950-01-01",
    "phone": "13800138000",
    "address": "测试路2号",
    "livingArrangement": "WITH_FAMILY",
    "emergencyContactName": "测试联系人",
    "emergencyContactPhone": "13900139000",
    "emergencyContactRelation": "子女",
    "remark": null
  }
}
~~~

新档案为ACTIVE、版本0。归档和恢复请求体为`{"version": 1}`，分别执行ACTIVE→ARCHIVED和ARCHIVED→ACTIVE。实际修改或状态变化增加版本；版本过期或状态不匹配返回409。归档资料可查询、不可编辑，归档不等于删除个人信息。

存在待确认、已确认或服务中的预约时禁止归档，返回 409 `ELDER_HAS_OPEN_BOOKINGS`，须先取消、完成或终止预约。归档与预约创建在同一社区数据库行锁下协调，既有终态预约保留。

归档同时撤销该老人所有未撤销的独立健康授权；恢复不会重新启用这些授权。有效绑定本人可只读健康历史及撤销授权，具体规则见[健康接口](HEALTH_API.md)。

### 列表与历史

- page默认1，pageSize默认20、最大100，返回items/page/pageSize/total。
- keyword最多50字符，支持完整姓名或完整老人电话精确匹配，不支持模糊检索或紧急联系人检索。
- status可为ACTIVE或ARCHIVED，不传则查询两种状态。
- 列表按创建时间及编号倒序，包含id、name、gender、age、maskedPhone、livingArrangement、status、version、updatedAt。年龄按上海时区当前日期计算，不返回地址及联系人。
- 修改历史按版本和编号倒序，包含id、actorId、action、changedFields、version、occurredAt。只保存字段名，不保存旧值或新值正文。
- 详情访问记录VIEW审计事件，历史接口仅返回CREATE、UPDATE、ARCHIVE和RESTORE。

## 错误码

| HTTP | code | 客户端处理 |
|---|---|---|
| 400 | INVALID_ARGUMENT | 修正字段、枚举、分页或版本 |
| 401 | UNAUTHENTICATED / SESSION_REVOKED | 重新认证 |
| 403 | FORBIDDEN | 检查角色或CSRF凭证 |
| 403 | COMMUNITY_REQUIRED / COMMUNITY_UNAVAILABLE | 配置有效社区 |
| 404 | ELDER_NOT_FOUND | 不存在或不属于当前社区 |
| 409 | ELDER_ALREADY_EXISTS | 核对重复档案，包括归档资料 |
| 409 | IDEMPOTENCY_CONFLICT | 不复用已绑定其他资料的requestId |
| 409 | ELDER_VERSION_CONFLICT | 重新读取详情，再确认修改 |
| 409 | ELDER_ARCHIVED | 先恢复，再编辑 |
| 409 | INVALID_ELDER_STATE | 刷新状态 |
| 500 | INTERNAL_ERROR | 保留traceId排查，不展示底层异常 |

## 管理端集成

- 路由 `/elders`；侧栏、工作台入口和路由守卫限定为具有社区归属的 `COMMUNITY_OPERATOR`、`PLATFORM_ADMIN`。服务端继续逐请求核验角色、社区状态和对象归属。
- 默认查询在册档案，每页20条，可切换10/20/50条；支持全部状态、归档状态及精确检索。列表仅使用摘要，打开详情或编辑时再读取完整资料。
- 表单包含基础资料、紧急联系人及服务备注。出生日期按上海日期校验；可选空值转为null。归档资料只读，恢复后可编辑。
- 建档首次有效提交生成UUID；同一载荷重试沿用该编号。网络失败或无法确认响应时锁定资料，允许原载荷重试；关闭会提示先核对列表。请求快照不持久化，刷新后不会自动重放写请求。
- 修改、归档和恢复携带最近读取的version。冲突时保留草稿和中文提示，用户确认放弃草稿后读取最新资料，不自动重试覆盖更新。
- 列表、详情和变更记录读取均支持取消旧请求；失败状态提供重试。操作完成刷新当前筛选页；最后一页为空时自动回退。变更记录每页5条，只显示动作、变更字段、操作账号和时间。
- 离开页面或关闭编辑器检查未保存修改；会话失效优先清空页面。档案和搜索词仅保存在内存中，不写入地址栏或浏览器持久化存储。

## 存储与配置

迁移V2__elder_profiles.sql新增elder_profile与elder_profile_event。写入和审计同事务提交，数据库唯一约束负责并发去重，条件更新负责版本冲突。

资料整体使用AES-256-GCM加密，每次写入生成随机nonce，并绑定社区及档案编号。姓名、电话和重复身份检索使用带社区范围的HMAC摘要。事件表仅存元数据，个人资料不进入日志。

非dev环境必须提供ELDER_DATA_KEY：32个随机字节的Base64编码。缺失、格式错误或长度错误将阻止启动。通过部署密钥管理设施提供并单独备份，不得提交仓库。

dev环境未配置时使用公开固定的合成数据密钥。已有资料依赖写入时的密钥，不能直接更换。当前未提供在线轮换工具，轮换需同时迁移密文、检索索引及幂等摘要。

## 检查

本节命令由人工或 CI 执行；AI 交付只运行与改动相关的最小单元测试和一次编译校验，见[开发规范](DEVELOPMENT.md#7-提交检查)。

后端目录执行：

~~~powershell
mvn -B -ntp verify
~~~

集成测试默认使用H2；同一套老人档案用例可连接专用MySQL测试库：

~~~powershell
$env:ELDER_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3306/elder_module_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:ELDER_TEST_DB_USERNAME = '测试库账号'
$env:ELDER_TEST_DB_PASSWORD = '测试库密码'
$env:ELDER_TEST_DB_DRIVER = 'com.mysql.cj.jdbc.Driver'
mvn -B -ntp '-Dtest=ElderProfileIntegrationTest' test
~~~

测试会清空档案及事件表并重置合成账号，只能连接专用测试库。用例覆盖真实HTTP会话与CSRF、生命周期、字段校验、社区与角色隔离、幂等、并发修改、加密和审计失败回滚。
