package com.elderlycare.platform.booking.domain;

/** 五类服务共享状态与明确类型的差异资料。 */
public final class BookingTypes {
    private BookingTypes() {}
    public enum Category { MEAL, CLEANING, ESCORT, CARE, REHABILITATION }
    public enum Status { PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED, TERMINATED }
    public enum Meal { BREAKFAST, LUNCH, DINNER }
    public enum Cleaning { DAILY, DEEP, KITCHEN, BATHROOM }
    public enum Assistance { NONE, WALKING, WHEELCHAIR }
    public enum Care { DAILY_LIVING, PERSONAL_CARE, PROFESSIONAL }
    public enum Place { HOME, COMMUNITY_CENTER, PROVIDER_SITE }
}

