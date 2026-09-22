package com.elderlycare.platform.health.domain;

/** 固定测量类型、场景及记录状态，不包含医学判断。 */
public final class HealthTypes {
    private HealthTypes() {}
    public enum Type { BLOOD_PRESSURE, HEART_RATE, WEIGHT, BLOOD_GLUCOSE }
    public enum Scene { FASTING, BEFORE_MEAL, AFTER_MEAL, RANDOM, UNSPECIFIED }
    public enum Status { ACTIVE, VOID }
    public enum Scope { FAMILY_READ, COMMUNITY_ASSIST }
}

