package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AdminInvitationView;
import com.tsuk1.itsmygo.security.CurrentUser;

import java.util.List;

public interface AdminInvitationService {

    /** 管理员发起邀请；返回的 view 里带一次性明文 token */
    AdminInvitationView create(CurrentUser actor, String username);

    List<AdminInvitationView> list(String status);

    /** 另一位管理员批准邀请；发起人不能批准自己的邀请 */
    AdminInvitationView approve(CurrentUser actor, Long id);

    AdminInvitationView revoke(CurrentUser actor, Long id);

    /** 被邀请人接受：要求当前登录用户就是 target，且邀请已批准 */
    void accept(CurrentUser user, String token);
}
