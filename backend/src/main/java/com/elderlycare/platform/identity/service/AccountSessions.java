package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.UserAccount;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** 登录时固定凭证版本，密码或权限变更后旧会话不能恢复使用。 */
public final class AccountSessions {
    public static final Set<String> ADMIN_ROLES = Set.of("COMMUNITY_OPERATOR", "DUTY_OFFICER", "PLATFORM_ADMIN", "AUDITOR");

    private AccountSessions() {}

    /** 仅保存服务器确定的用户名及登录时凭证版本，不包含密码或个人资料。 */
    public record Identity(String username, long credentialVersion) implements Principal, Serializable {
        /** 返回不可由客户端指定的账号名称，供 Spring Security 识别当前身份。 */
        @Override public String getName() { return username; }
    }

    /** 表单认证使用的密码校验快照；密码由 Spring 在认证成功后擦除。 */
    public static final class LoginUser extends User {
        private static final long serialVersionUID = 1L;
        private final long credentialVersion;

        /** 保存认证查询时的版本，避免密码重置与登录并发时提升旧凭据权限。 */
        public LoginUser(UserAccount account) {
            super(account.getUsername(), account.getPasswordHash(), true, true, true, true,
                    List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())));
            credentialVersion = account.getCredentialVersion();
        }
        public long credentialVersion() { return credentialVersion; }
    }

    /** 生成无密码的版本化身份；仅限服务器已完成认证后调用。 */
    public static Authentication authentication(UserAccount account) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new Identity(account.getUsername(), account.getCredentialVersion()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())));
    }

    /** 重新核验账号状态及登录版本，写事务取得社区锁后也必须调用。 */
    public static void requireCurrent(Authentication authentication, UserAccount account) {
        long version = -1;
        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication.getPrincipal() instanceof Identity identity) version = identity.credentialVersion();
            else if (authentication.getPrincipal() instanceof LoginUser user) version = user.credentialVersion();
        }
        if (account == null || !"ACTIVE".equals(account.getStatus()) || authentication == null
                || !account.getUsername().equals(authentication.getName()) || version != account.getCredentialVersion()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "账号或登录凭证已变更，请重新登录");
        }
    }
}
