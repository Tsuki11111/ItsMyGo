package com.tsuk1.itsmygo.entity;

/** 虚拟物流状态。 */
public enum ShipmentStatus {
    PICKED_UP("已揽收"),
    IN_TRANSIT("运输中"),
    OUT_FOR_DELIVERY("派送中"),
    DELIVERED("已送达"),
    CANCELLED("已取消");

    private final String label;

    ShipmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
