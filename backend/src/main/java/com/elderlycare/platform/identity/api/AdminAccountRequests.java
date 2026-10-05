package com.elderlycare.platform.identity.api;

import jakarta.validation.constraints.*;
import java.util.UUID;

/** 后台账号写请求；密码仅参与校验及带密钥摘要，不进入操作历史。 */
public final class AdminAccountRequests {
    private AdminAccountRequests() {}

    public record Create(@NotNull UUID requestId, @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 100) String displayName, @NotBlank String role,
            @NotBlank @Size(max = 64) String password) {}

    public record Update(@NotNull UUID requestId, @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 100) String displayName, @NotBlank String role, @NotBlank String status) {}

    public record ResetPassword(@NotNull UUID requestId, @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 64) String password) {}

    public record ChangePassword(@NotNull UUID requestId, @NotBlank @Size(max = 64) String currentPassword,
            @NotBlank @Size(max = 64) String newPassword) {}
}
