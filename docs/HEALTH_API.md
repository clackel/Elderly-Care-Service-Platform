# 健康记录与独立健康授权接口

本文记录健康模块的现行后端契约。健康授权不继承预约授权，页面按同一契约接入。开发方向见健康模块方案，交付后方案归档。

## 通用约定

前缀为 `/api/v1/health`；成功 HTTP 200，统一 `code/message/data/traceId`。ID 为字符串，版本为整数。复用真实会话与 CSRF，响应为 `Cache-Control: no-store`。时间须为含时区 ISO 8601，存储 UTC，界面与日期边界使用 Asia/Shanghai。日期查询为 `[from,to)`。

账号、角色及社区逐请求读取。允许角色仅为 ELDER、FAMILY、COMMUNITY_OPERATOR；平台管理员、STAFF 及其他角色返回403。不可访问对象与不存在对象均返回404 `HEALTH_RESOURCE_NOT_FOUND`。菜单不构成权限。

## 权限与生命周期

| 身份 | 记录读取 | 趋势 | 写入与历史 |
|---|---|---|---|
| 有效绑定本人 | 本人档案当前及作废记录 | 有效原始点 | 在管时新增、更正、作废；本人独享历史旧值和原因 |
| FAMILY_READ 家属 | 全部当前有效记录，含授权前历史记录 | 有效原始点 | 只读，不可读作废及历史旧值 |
| COMMUNITY_ASSIST 指定人员 | 自己原始代录且未由老人接管的记录 | 无 | 当前授权有效时新增、更正、作废自己的记录 |
| 仅预约授权或无健康授权 | 无 | 无 | 无 |

第三方访问同时关联健康授权、未撤销且未到期的期限、授权时的本人绑定编号和版本、当前启用的绑定、有效老人本人账号、有效受权账号及同社区角色。绑定任意更新增加版本后旧授权失效，恢复绑定不会复活旧授权。账号停用或社区不匹配即停止访问，不缓存授权结果。

档案归档在社区锁内撤销所有未撤销健康授权；恢复后需重新同意。有效绑定本人仍可读归档历史和撤销授权，不能新增、更正或作废。

老人更正或作废社区记录后设置不可逆的本人接管标志；原录入人和来源不变。该人员的列表排除该记录，详情、历史及写入都不能绕过该限制。

## 接口

| 方法 | 路径 | 请求/返回 |
|---|---|---|
| GET | /elders | page/pageSize；老人选项分页 |
| GET | /elders/{id}/records | page/pageSize、type?、status?、from?、to?；记录分页 |
| POST | /elders/{id}/records | requestId、measurement；返回 id/version |
| GET | /records/{id} | 当前详情 |
| POST | /records/{id}/corrections | requestId、version、measurement、reason；返回 id/version |
| POST | /records/{id}/void | requestId、version、reason；返回 id/version |
| GET | /records/{id}/revisions | page/pageSize；本人历史分页 |
| GET | /elders/{id}/trends | type、from、to、scene?；按测量时间及编号升序的原始点数组 |
| GET | /grants | page/pageSize；本人授予或当前账号收到的授权事实分页 |
| POST | /grant-recipients/resolve | elderId、recipientId、scope；返回最小账号身份 |
| POST | /grants | requestId、elderId、recipientId、scope、days、accepted、consentVersion |
| POST | /grants/{id}/revoke | requestId、version；返回 id/version |

分页默认1/20，最大100；返回 items/page/pageSize/total。老人选项为 id/name/archived/canWrite/canTrend/canGrant，不含地址或联系方式。列表默认 status=ACTIVE，可按 VOID 查询作废记录；家属始终只返回有效记录。

## 测量与版本

`measurement` 包含 `type、measuredAt` 和该类型的固定字段；其他类型字段必须为空。

| type | 字段 | 固定单位 | 输入存储上限 |
|---|---|---|---|
| BLOOD_PRESSURE | systolic、diastolic，须成对整数 | mmHg | 各1—999 |
| HEART_RATE | heartRate 整数 | 次/分钟 | 1—999 |
| WEIGHT | weight 定点数 | kg | 大于0，最多999.99、两位小数 |
| BLOOD_GLUCOSE | glucose 定点数、glucoseScene | mmol/L | 大于0，最多99.99、两位小数 |

上述上限是输入与存储约束，不是医学正常范围。glucoseScene 为 FASTING（空腹）、BEFORE_MEAL（餐前）、AFTER_MEAL（餐后）、RANDOM（随机）、UNSPECIFIED（未说明）。测量时间不早于1970年且不得晚于当前时间，保存时规范化至毫秒；小数按等值数规范化，不自动四舍五入。

记录详情与列表项均返回 id/elderId/measurement/unit/status/version/originalActorId/entryMode/ownerTakenOver/corrected/createdAt/updatedAt/canWrite/canHistory。来源固定为手工，entryMode 为 SELF 或 COMMUNITY_ASSIST，由服务端决定。corrected 表示已有后续版本；原因仅本人历史接口输出。历史项为 version/measurement/reason/actorId/action/occurredAt，action 为 CREATE/CORRECT/VOID。

初始记录版本1；更正和作废必须携带读取版本，追加不可覆盖版本。reason 去首尾空白后1—300字。指标类型不可更改；录错类型先作废再新增。作废不恢复、也不进入趋势。同时间可以存在多条测量。

趋势单次最长90天，最多2000点，超限返回400 `HEALTH_TREND_TOO_LARGE` 并要求缩小范围，不静默截断。血糖必须传单一 scene，其他指标不得传 scene。无点显示“暂无记录”；单点显示数值，不产生医学结论。

## 独立健康同意

仅有效绑定本人可授予。FAMILY_READ 只适用于本社区有效 FAMILY；COMMUNITY_ASSIST 只适用于本社区有效 COMMUNITY_OPERATOR。受权人主动提供账号编号，本人精确核对，返回 id/displayName/role；不开放全社区目录。账号核对每个老人账号每分钟最多10次，跨实例按社区数据库锁计数，失败核对也记录次数。

同意版本为 `health-v1`；accepted 必须为 true。界面默认30天，可选7、30、90、365天。家属同意说明覆盖授权前的全部当前有效记录和趋势；社区同意说明为“新增、查看、更正及作废该人员自己原始代录且尚未由老人接管的记录”，不包括全档案、趋势或历史旧值。

每次重新授权生成新事实，原同对象同范围的未撤销事实同时撤销；不覆盖原同意记录。授权响应包含 id/elderId/recipientId/scope/createdAt/expiresAt/revoked/effective/version/consentVersion。effective 实时计算；失效授权事实不提供健康正文。本人或该受权人可撤销或放弃，归档本人仍可撤销；无管理员代授权和线下代理。

## 幂等、事务及存储

所有记录和授权写操作必须携带 UUID requestId。以“社区＋操作者＋操作类型＋请求编号”唯一约束去重。同编号同规范化请求返回原 id/version；不同请求内容返回409 IDEMPOTENCY_CONFLICT。重试也先核验当前权限；响应不重放健康旧值。前端必须在网络结果不确定时保留原编号与原载荷，禁止自动生成新编号重复提交。

V4 新增 health_record、health_record_revision、health_grant、health_event、health_mutation_request。写事务使用 READ_COMMITTED，按社区行锁→身份及对象/绑定/授权检查→期望版本更新→历史、事件和幂等结果的顺序执行。档案归档及已有本人绑定变更沿用相同社区锁。数据库唯一约束和记录条件版本更新共同保护一致性。

测量及原因整体采用 AES-256-GCM；沿用 ELDER_DATA_KEY 主密钥，分别以 health:encryption:v1 与 health:request:v1 派生健康密钥。随机12字节 IV，附加认证数据绑定社区、老人、记录及版本。请求摘要使用 HMAC-SHA256，不能用无密钥健康值哈希。既有密文仍依赖原主密钥，密钥更换需专门迁移。

社区、老人、指标、当前状态、测量时间、血糖场景和来源为受权限保护的最少检索元数据；测量数值及更正原因不明文存储。health_event 仅保存操作人、对象、动作及时间，不保存正文。只在权限限定后解密选中页或有界趋势。

健康正文不进入日志、本地存储或URL。注销、切换老人、页面退出与权限失效时清空内存，页面返回前台重新请求身份和授权。已返回终端的信息不能远程收回。

## 错误

| HTTP | code | 含义 |
|---|---|---|
| 400 | INVALID_ARGUMENT | 类型组合、精度、时间、同意或期限不合法 |
| 400 | HEALTH_TREND_TOO_LARGE | 缩小日期范围后重试 |
| 401/403 | UNAUTHENTICATED / FORBIDDEN / COMMUNITY_UNAVAILABLE | 重新核验身份、角色或社区 |
| 404 | HEALTH_RESOURCE_NOT_FOUND | 不存在或无对象访问权限 |
| 409 | VERSION_CONFLICT | 刷新后人工确认，不能覆盖较新内容 |
| 409 | IDEMPOTENCY_CONFLICT | 请求编号已用于其他内容 |
| 409 | HEALTH_TYPE_IMMUTABLE / HEALTH_RECORD_VOID / ELDER_ARCHIVED | 按当前状态处理 |
| 429 | HEALTH_RESOLVE_RATE_LIMITED | 一分钟后再核对账号 |

