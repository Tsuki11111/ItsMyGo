package com.tsuk1.itsmygo.entity;

/**
 * 动作状态机。
 *
 * <pre>
 * PENDING_CONFIRM ──确认──> EXECUTING ──成功──> SUCCEEDED
 *        │                      └────失败──> FAILED
 *        ├──用户取消──> CANCELLED
 *        └──超时──────> EXPIRED
 * </pre>
 */
public enum AgentActionStatus {
    PENDING_CONFIRM,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED
}
