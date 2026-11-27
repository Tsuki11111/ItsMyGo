package com.tsuk1.itsmygo.entity;

import java.util.Locale;

/** 活动类型。抽奖只是第一种，后续加类型只扩展枚举和对应子表 */
public enum ActivityType {

    LOTTERY("抽奖");

    private final String label;

    ActivityType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 找不到时返回 null，由调用方决定是拒绝还是按普通活动展示 */
    public static ActivityType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (ActivityType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        ActivityType type = fromCode(code);
        return type == null ? "活动" : type.label;
    }
}
