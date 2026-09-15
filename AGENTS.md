# 项目开发规范

适用范围：社区养老服务平台的功能开发、缺陷修复和代码审查。开发前阅读根目录README及docs中的接口、工程约定；以当前源码和依赖锁文件为准。

## 开发环境

- 后端：Java 21、Maven、Spring Boot、Spring Security、MyBatis-Plus；复杂查询使用MyBatis XML。
- 管理后台：Vue 3、TypeScript、Element Plus、Vite；移动端：uni-app、Vue 3、TypeScript。
- 前端依赖使用各工程的npm锁文件和npm ci安装，不引入无关包管理器。
- 后端按api、service、mapper、domain分层，事务放在Service；Controller不直接访问数据库。
- 延续前后端分离、模块化单体架构；不得为局部功能擅自升级主要依赖或拆分微服务。

## 方法注释与异常

- 新增或修改的功能方法必须编写中文注释。Java使用Javadoc，TypeScript使用JSDoc或紧邻方法的说明。
- 注释说明职责、关键参数含义、返回结果以及必要的权限、事务或状态约束；避免逐行翻译实现。纯字段访问器不要求重复注释。
- 主动抛出的异常使用明确的中文消息，说明失败原因或可执行的处理方式；稳定错误码保持英文。
- 业务异常使用现有BusinessException和恰当HTTP状态；不得用成功响应掩盖失败。
- 系统异常的中文消息不得包含密码、密钥、个人信息、SQL参数或健康正文。不得直接向客户端暴露底层异常堆栈。
- 不为满足中文要求而覆盖第三方异常类型；在业务边界进行必要转换，由统一异常处理器生成响应。

## 功能实现

- 接口必须落实服务端身份、角色、社区及对象权限校验，不能依赖前端菜单。
- 写操作完成参数校验、并发控制与事务处理；重复请求不得产生重复业务结果。
- 涉及个人信息时明确最小采集范围、访问规则和加密方式；测试仅使用合成数据。
- 新建数据库结构通过后续Flyway迁移实施，不修改已发布迁移。
- 保留用户现有改动，不提交无关重构或自动生成文件。

## 测试与检查

- 后端在backend目录执行mvn -B -ntp verify。
- 管理端在frontend/admin-web执行npm run lint、npm run format:check和npm run build。
- 移动端在frontend/mobile执行npm run format:check、npm run type-check、npm run build:h5和npm run build:mp-weixin。
- 对业务变更补充正常流程、参数边界、权限隔离和失败场景测试；不为简单访问器或纯格式变更堆砌测试。
- 数据库变更及并发行为需使用真实MySQL验证，H2检查不能代替MySQL兼容性验证。
- 修改界面时执行实际页面检查；仅修改后端时不扩展前端实现范围。
- 修复相关测试失败后再交付。明确区分已执行检查、未验证项和环境限制，不将配置存在表述为运行通过。
- CI以.github/workflows中的实际配置为准。

## 文档与提交

- docs面向开发者，仅维护架构、接口、开发约定和部署要求；不添加基础知识教学、个人过程记录或VERIFICATION.md。
- 接口、配置及数据库契约变更同步更新对应文档，规划能力与当前实现分开标注。
- 提交消息遵循docs/GIT_COMMIT_CONVENTIONS.md；PR描述交代问题、最终行为及验证结果。
- 未获明确要求，不主动提交、推送或合并代码。
