package com.elderlycare.platform.identity.api;

import com.elderlycare.platform.identity.domain.UserAccount;

public record AccountDto(String id, String displayName, String role, String communityId, boolean passwordChangeRequired) {
    /** 只返回当前会话所需身份与改密状态，不返回密码或凭证版本。 */
    public static AccountDto from(UserAccount account) {
        return new AccountDto(account.getId().toString(), account.getDisplayName(), account.getRole(),
                account.getCommunityId() == null ? null : account.getCommunityId().toString(), account.isPasswordChangeRequired());
    }
}
