package com.elderlycare.platform.elder.domain;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;

/** 老人基础资料，整体加密保存，禁止写入日志。 */
public record ElderProfileData(
        @NotBlank @Size(max = 50) String name,
        @NotNull Gender gender,
        @NotNull @PastOrPresent LocalDate birthDate,
        @Pattern(regexp = "^$|1[3-9][0-9]{9}|0[0-9]{2,3}-?[0-9]{7,8}") String phone,
        @NotBlank @Size(max = 200) String address,
        @NotNull LivingArrangement livingArrangement,
        @NotBlank @Size(max = 50) String emergencyContactName,
        @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}|0[0-9]{2,3}-?[0-9]{7,8}") String emergencyContactPhone,
        @NotBlank @Size(max = 30) String emergencyContactRelation,
        @Size(max = 500) String remark) {
    /** 规范化入参的首尾空白，后续校验、加密和幂等比较使用同一份资料。 */
    public ElderProfileData {
        name = clean(name); phone = clean(phone); address = clean(address);
        emergencyContactName = clean(emergencyContactName);
        emergencyContactPhone = clean(emergencyContactPhone);
        emergencyContactRelation = clean(emergencyContactRelation);
        remark = clean(remark);
    }

    /** 去除用户输入的首尾空白，不改变正文。 */
    private static String clean(String text) { return text == null ? null : text.strip(); }

    /** 隐去对象的个人信息，防止隐式日志输出。 */
    @Override
    public String toString() { return "ElderProfileData[REDACTED]"; }

    /** 校验出生日期下界，不参与JSON序列化。 */
    @JsonIgnore
    @AssertTrue(message = "出生日期须在1900年及之后")
    public boolean isBirthDateInRange() {
        return birthDate == null || !birthDate.isBefore(LocalDate.of(1900, 1, 1));
    }

    public enum Gender { MALE, FEMALE, UNKNOWN }
    public enum LivingArrangement { ALONE, WITH_FAMILY, INSTITUTION, OTHER, UNKNOWN }
}
