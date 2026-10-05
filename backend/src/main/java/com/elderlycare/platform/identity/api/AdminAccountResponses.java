package com.elderlycare.platform.identity.api;

import com.elderlycare.platform.identity.domain.UserAccount;
import java.time.Instant;

/** 后台账号响应不包含密码哈希、凭证版本和核验资料。 */
public final class AdminAccountResponses {
    private AdminAccountResponses() {}

    public record Item(String id, String username, String displayName, String role, String communityId,
            String status, long version, boolean passwordChangeRequired) {
        /** 将已通过社区与后台角色校验的账号转换为安全摘要。 */
        public static Item from(UserAccount account) {
            return new Item(account.getId().toString(), account.getUsername(), account.getDisplayName(), account.getRole(),
                    account.getCommunityId().toString(), account.getStatus(), account.getVersion(), account.isPasswordChangeRequired());
        }
    }
    public record Mutation(String id, long version) {}
    public record Event(String id, String actorId, String action, Instant occurredAt) {}
}
