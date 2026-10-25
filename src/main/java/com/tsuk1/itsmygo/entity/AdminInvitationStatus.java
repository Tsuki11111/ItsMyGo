package com.tsuk1.itsmygo.entity;

public final class AdminInvitationStatus {

    /** 已发起，等待另一位管理员批准 */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    /** 已批准，等待被邀请人接受 */
    public static final String APPROVED = "APPROVED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REVOKED = "REVOKED";
    public static final String EXPIRED = "EXPIRED";

    private AdminInvitationStatus() {
    }
}
