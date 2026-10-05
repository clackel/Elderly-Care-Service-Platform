package com.elderlycare.platform.identity.service;

import com.elderlycare.platform.common.api.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/** 统一初始、重置及本人修改密码的长度约束，避免 BCrypt 字节截断。 */
public final class AdminPasswordPolicy {
    private AdminPasswordPolicy() {}

    /** 密码须为12至64个字符且UTF-8不超过72字节，保留用户输入的空格。 */
    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "密码须为12至64个字符，且UTF-8长度不能超过72字节");
        }
    }
}
