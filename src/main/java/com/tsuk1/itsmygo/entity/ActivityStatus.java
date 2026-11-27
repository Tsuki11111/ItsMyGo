package com.tsuk1.itsmygo.entity;

/** 活动状态：数据库存 code，业务代码用枚举 */
public enum ActivityStatus {

    ACTIVE(1, "进行中"),
    ENDED(2, "已结束");

    private final int code;
    private final String label;

    ActivityStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ActivityStatus fromCode(int code) {
        for (ActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的活动状态: " + code);
    }
}
