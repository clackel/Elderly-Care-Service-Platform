package com.elderlycare.platform.booking.api;

import com.elderlycare.platform.booking.domain.BookingTypes.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class BookingRequests {
    private BookingRequests() {}
    public record Provider(@NotNull UUID requestId, @NotNull @Min(0) Long version,
                           @NotBlank @Size(max=100) String name, boolean enabled) {}
    public record Catalog(@NotNull UUID requestId, @NotNull @Min(0) Long version,
            @NotNull Category category, @NotBlank @Size(max=100) String name,
            @NotBlank @Size(max=1000) String description, @Min(15) @Max(480) int durationMinutes,
            @Min(0) @Max(10000000) int priceFen, @NotBlank @Size(max=80) String serviceArea,
            @NotNull @Positive Long providerId, boolean professional, @NotNull @Size(max=100) String qualification,
            boolean enabled) {}
    public record Worker(@NotNull @Min(0) Long version, @NotNull @Positive Long providerId,
            @NotEmpty @Size(max=5) Set<@NotNull Category> categories,
            @NotBlank @Size(max=80) String serviceArea, @NotNull @Size(max=100) String qualification,
            Instant qualificationExpiresAt, boolean enabled) {}
    public record Specific(LocalDate mealDate, Meal meal, @Min(1) @Max(20) Integer portions,
            @Size(max=200) String dietaryRequirements, Cleaning cleaningScope,
            @Size(max=100) String hospital, Instant appointmentTime, @Size(max=200) String meetingPoint,
            Assistance assistance, Care careContent, @Size(max=300) String precautions,
            Place location, Boolean assessmentRequired) {}
    public record Application(
            @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
            @NotNull @Positive Long elderId,
            @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
            @NotNull @Positive Long serviceId,
            @NotNull Instant requestedStart, @NotBlank @Size(max=200) String address,
            @NotBlank @Size(max=50) String contactName,
            @NotBlank @Pattern(regexp="1[3-9][0-9]{9}|0[0-9]{2,3}-?[0-9]{7,8}") String contactPhone,
            @NotNull @Size(max=300) String remark, @NotNull @Valid Specific specific) {
        /** 避免日志隐式输出预约个人资料。 */
        @Override public String toString() { return "Application[REDACTED]"; }
    }
    public record Create(@NotNull UUID requestId, @NotNull @Valid Application application) {}
    public record Action(@NotNull @Min(0) Long version, @NotBlank @Size(max=1000) String note) {}
    public record Arrange(@NotNull @Min(0) Long version, @NotNull @Positive Long workerId,
            @NotNull Instant start, @NotNull Instant end, @NotNull @Size(max=500) String coordinationNote) {}
    public record Member(@NotNull UUID requestId, @NotBlank @Size(max=50) String displayName,
            @Pattern(regexp="ELDER|FAMILY|STAFF") @NotNull String role,
            @NotBlank @Size(max=200) String verificationReference) {}
    public record Binding(@NotNull @Positive Long accountId, @NotNull @Positive Long elderId,
            @NotNull @Min(0) Long version, boolean active, @NotBlank @Size(max=200) String verificationReference) {}
    public record Grant(@NotNull @Positive Long elderId, @NotNull @Positive Long familyId,
            @NotNull @Min(0) Long version, boolean canBook, @NotNull Instant expiresAt,
            @NotBlank @Size(max=200) String consentReference) {}
    public record Version(@NotNull @Min(0) Long version) {}
    public record Wechat(@NotBlank @Size(max=200) String code, @Size(max=100) String enrollmentToken) {}
}
