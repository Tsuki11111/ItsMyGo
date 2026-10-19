package com.tsuk1.itsmygo.dto;

/** 一个用户可能同时有顾客侧和商家侧未读（商家账号也能以顾客身份联系别家店）。 */
public record ChatUnreadView(long customerUnread, long merchantUnread) {
}
