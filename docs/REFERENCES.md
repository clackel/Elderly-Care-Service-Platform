# 依赖与参考资料

参考资料记录日期：2026-09-09。版本以`backend/pom.xml`及两个前端工程的`package.json`和`package-lock.json`为准。

| 来源 | 用途 | 本项目落地 |
|---|---|---|
| [vuejs/create-vue](https://github.com/vuejs/create-vue) | Vue 官方 Vite 工程组织及 TypeScript、Router、Pinia 组合 | 管理后台的独立入口、路由、状态和构建配置 |
| [dcloudio/uni-preset-vue 的 vite-ts 分支](https://github.com/dcloudio/uni-preset-vue/tree/vite-ts) | 官方 Vue 3 + TypeScript 模板与依赖约束 | mobile 的 main.ts、manifest.json、pages.json、Vite 插件和双端脚本 |
| [spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic) | Spring Boot 本地开发数据库、配置隔离与测试的组织方式 | 显式 dev 模式、H2 快速验证和 MySQL 配置；持久化遵从本项目的 MyBatis 要求 |

uni-app 配套版本来自[官方模板 package.json](https://github.com/dcloudio/uni-preset-vue/blob/vite-ts/package.json)：锁定 `3.0.0-5020420260813003` 和 Vite `5.2.8`，Vue `3.4.21` 与其内部运行时保持一致。管理端与移动端分别管理 package-lock，避免强行共享构建链版本。

后端锁定 Java 21、Spring Boot 3.5.11、MyBatis-Plus 3.5.12、springdoc 2.8.16。依据 [Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis-Plus 官方安装方式](https://baomidou.com/en/getting-started/install/)及 [springdoc 兼容矩阵](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)选择同一兼容系列。

认证实现遵循 [Spring Security CSRF 文档](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)的会话凭证与登录后刷新原则，没有关闭 CSRF。

所有直接 npm 依赖使用精确版本并提交锁文件，Maven 依赖由指定父 BOM 和显式版本管理。版本锁定用于可复现构建，不代表依赖没有安全问题；正式发布前还需做依赖安全评估。uni-app 模板的部分传递依赖存在上游弃用提示，后续应整体升级兼容的官方发行组合。

若后续直接引入第三方源码、模板资源或图标，应按对应仓库许可证保留版权声明。第三方项目能力不属于本仓库的实现范围。
