package com.elderlycare.platform.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 默认为关闭的首次管理员开通入口，秘密配置只在启动时读取。 */
@Component
@Order(100)
public class AdminBootstrapInitializer implements ApplicationRunner {
    private final AdminBootstrapService service;
    private final boolean enabled;
    private final long communityId;
    private final String communityName;
    private final String username;
    private final String displayName;
    private final String password;

    /** 从外部配置读取首次开通参数，不设置固定密码或默认管理员账号。 */
    public AdminBootstrapInitializer(AdminBootstrapService service,
            @Value("${app.bootstrap.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.community-id:0}") long communityId,
            @Value("${app.bootstrap.community-name:}") String communityName,
            @Value("${app.bootstrap.username:}") String username,
            @Value("${app.bootstrap.display-name:}") String displayName,
            @Value("${app.bootstrap.password:}") String password) {
        this.service = service; this.enabled = enabled; this.communityId = communityId;
        this.communityName = communityName; this.username = username; this.displayName = displayName; this.password = password;
    }

    /** 开启初始化时通过事务服务执行，完成后应移除开启标志及初始密码配置。 */
    @Override public void run(ApplicationArguments args) {
        if (enabled) service.initialize(communityId, communityName, username, displayName, password);
    }
}
