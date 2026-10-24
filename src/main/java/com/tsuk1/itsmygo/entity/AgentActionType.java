package com.tsuk1.itsmygo.entity;

/** 动作类型。高危动作必须用户确认；以后催办类低危动作可直接复用同一张表。 */
public enum AgentActionType {
    CREATE_ORDER,
    APPLY_REFUND,
    /** 催发货：确认后以买小购（AGENT）身份给商家发一条站内消息 */
    URGE_SHIPMENT,
    /** 催退款：确认后以买小购（AGENT）身份提醒商家尽快处理退款 */
    URGE_REFUND
}
