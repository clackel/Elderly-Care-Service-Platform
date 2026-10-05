package com.elderlycare.platform.identity.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.elderlycare.platform.identity.domain.UserAccount;
import com.elderlycare.platform.identity.domain.AdminAccountEventRow;
import com.elderlycare.platform.identity.mapper.AdminAccountEventMapper;
import com.elderlycare.platform.identity.mapper.AdminBootstrapMapper;
import com.elderlycare.platform.identity.mapper.CommunityMapper;
import com.elderlycare.platform.identity.mapper.UserAccountMapper;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 显式启动配置触发的一次性管理员开通，不覆盖或提升既有账号。 */
@Service
public class AdminBootstrapService {
    private final AdminBootstrapMapper bootstrap;
    private final CommunityMapper communities;
    private final UserAccountMapper accounts;
    private final AdminAccountEventMapper events;
    private final PasswordEncoder encoder;

    /** 装配社区锁、初始化历史和密码编码器。 */
    public AdminBootstrapService(AdminBootstrapMapper bootstrap, CommunityMapper communities, UserAccountMapper accounts,
            AdminAccountEventMapper events, PasswordEncoder encoder) {
        this.bootstrap = bootstrap; this.communities = communities; this.accounts = accounts;
        this.events = events; this.encoder = encoder;
    }

    /** 在社区锁和事务内仅开通一次，指定社区不存在时创建；历史开通过的社区直接跳过。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void initialize(long communityId, String communityName, String username, String displayName, String password) {
        if (communityId <= 0) throw new IllegalStateException("首次管理员开通需要有效的社区编号");
        if (bootstrap.communityExists(communityId) == 0) {
            if (communityName == null || communityName.isBlank() || communityName.trim().length() > 100)
                throw new IllegalStateException("首次创建社区需要1至100字的社区名称");
            try { bootstrap.createCommunity(communityId, communityName.trim()); }
            catch (DuplicateKeyException e) { throw new IllegalStateException("社区已被其他启动进程创建，请重新启动"); }
        }
        if (communities.lockActiveById(communityId) == null)
            throw new IllegalStateException("首次管理员开通失败：指定社区已停用");
        if (bootstrap.initialized(communityId) > 0) return;
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,63}"))
            throw new IllegalStateException("首次管理员账号须为3至64位字母、数字、点、下划线或连字符，首位须为字母或数字");
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 100)
            throw new IllegalStateException("首次管理员显示名须为1至100字");
        AdminPasswordPolicy.validate(password);
        var account = new UserAccount();
        account.setId(IdWorker.getId()); account.setUsername(normalized); account.setDisplayName(displayName.trim());
        account.setPasswordHash(encoder.encode(password)); account.setRole("PLATFORM_ADMIN");
        account.setCommunityId(communityId); account.setStatus("ACTIVE"); account.setPasswordChangeRequired(true);
        try {
            if (accounts.insert(account) != 1) throw new IllegalStateException("首次管理员账号未能保存");
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("首次管理员账号名已被占用，请更换初始化账号名");
        }
        var event = new AdminAccountEventRow(); event.id = IdWorker.getId(); event.communityId = communityId;
        event.accountId = account.getId(); event.actorId = account.getId(); event.action = "BOOTSTRAPPED"; event.occurredAt = Instant.now();
        if (events.insert(event) != 1) throw new IllegalStateException("首次管理员操作记录未能保存");
    }
}
