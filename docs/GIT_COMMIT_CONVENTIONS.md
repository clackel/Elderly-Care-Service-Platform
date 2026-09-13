# 提交规范

提交格式：`<type>(<scope>): <subject>`。type、scope使用小写英文，subject使用中文，冒号后保留一个空格；跨模块变更可省略scope。

## 类型

| type | 适用变更 |
|---|---|
| feat | 新增功能或接口 |
| fix | 修复行为缺陷 |
| refactor | 不改变外部行为的重构 |
| perf | 性能改进 |
| docs | 文档 |
| test | 测试 |
| style | 不改变逻辑的格式调整 |
| build | 依赖、构建和打包配置 |
| ci | 自动化工作流 |
| chore | 不属于以上类型的工程维护 |
| revert | 撤销提交 |

## 范围

| scope | 对应模块 |
|---|---|
| auth | 身份、会话和权限 |
| admin | 管理后台 |
| mobile | uni-app移动端 |
| backend | 后端公共代码及配置 |
| infra | 基础设施及部署 |

新增业务模块可使用其稳定模块名作为scope。

## 提交要求

- 一个提交对应一个完整目的，配套测试与功能或修复一并提交。
- subject描述具体行为变化，避免使用“更新代码”“优化”等缺少对象的表述。
- CSS变更按目的选择feat、fix或refactor；style仅用于格式调整。
- 不兼容变更使用`!`，并在正文以`BREAKING CHANGE:`记录影响及迁移方式。
- 复杂变更在正文说明原因、影响和验证结果；撤销提交记录目标提交标识。
- 不提交凭据、本地数据库、依赖目录及构建产物。

```text
fix(auth): 拒绝已停用账号的现有会话

docs: 更新本地开发配置

build(mobile): 更新uni-app配套依赖
```
