package com.tsuk1.itsmygo.entity;

/** 动作类型。高危动作必须用户确认；以后催办类低危动作可直接复用同一张表。 */
public enum AgentActionType {
    CREATE_ORDER,
    APPLY_REFUND
}
