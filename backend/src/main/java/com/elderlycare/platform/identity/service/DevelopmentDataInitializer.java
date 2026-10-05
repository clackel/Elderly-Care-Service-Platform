package com.elderlycare.platform.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.Order;

/** 仅在显式启用dev时创建合成社区和运营账号，既有密码不被配置覆盖。 */
@Component
@Profile("dev")
@Order(0)
public class DevelopmentDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;

    /** 装配开发初始化依赖，密码必须通过外部配置提供。 */
    public DevelopmentDataInitializer(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${app.dev-password}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.password = password;
    }

    /** 创建一次性的开发运营账号；初次登录必须修改密码，不授予账号管理权限。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AdminPasswordPolicy.validate(password);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM community WHERE id = 10001", Integer.class);
        if (count != null && count == 0) {
            jdbc.update("INSERT INTO community (id, name) VALUES (10001, ?)", "开发示例社区");
        }
        Integer accounts = jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username = 'dev.operator'", Integer.class);
        if (accounts != null && accounts == 0) {
            jdbc.update("INSERT INTO user_account (id, username, password_hash, display_name, role, community_id, password_change_required) VALUES (?, ?, ?, ?, ?, ?, TRUE)",
                    10001L, "dev.operator", encoder.encode(password), "开发运营账号", "COMMUNITY_OPERATOR", 10001L);
        }
    }
}
