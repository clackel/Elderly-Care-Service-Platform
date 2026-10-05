package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.common.api.BusinessException;
import com.elderlycare.platform.identity.domain.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CommunityAccess {
    private final IdentityService identities;

    /** 装配实时身份服务，所有业务模块共用版本校验。 */
    public CommunityAccess(IdentityService identities) { this.identities = identities; }

    /** 读取有效账号并检查登录版本，业务写事务获得锁后再次调用可拒绝已撤销身份。 */
    public UserAccount current(Authentication authentication) {
        UserAccount account = authentication == null ? null : identities.find(authentication.getName());
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请重新登录");
        }
        AccountSessions.requireCurrent(authentication, account);
        return account;
    }

    /** 从当前身份取得社区编号，不接受客户端覆盖社区范围。 */
    public long requireCommunity(Authentication authentication) {
        Long communityId = current(authentication).getCommunityId();
        if (communityId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "COMMUNITY_REQUIRED", "账号尚未分配社区");
        }
        return communityId;
    }
}
