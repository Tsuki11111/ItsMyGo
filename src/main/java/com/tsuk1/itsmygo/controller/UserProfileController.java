package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.profile.UserProfileView;
import com.tsuk1.itsmygo.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户画像内部接口：只认票据里的用户，模型无法指定查谁。
 *
 * <p>导购子智能体通过 {@code X-Agent-Ticket} 调用；画像只返回聚合事实，
 * 不返回订单号、地址或聊天原文。</p>
 */
@RestController
@RequestMapping("/internal/agent")
public class UserProfileController {

    private final AgentToolTicketService ticketService;
    private final UserProfileService profileService;

    public UserProfileController(AgentToolTicketService ticketService,
                                 UserProfileService profileService) {
        this.ticketService = ticketService;
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public Result<UserProfileView> profile(
            @RequestHeader(value = "X-Agent-Ticket", required = false) String ticket) {
        AgentToolTicketService.Identity identity = ticketService.resolve(ticket);
        if (identity == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "工具票据无效或已过期");
        }
        return Result.ok(profileService.profile(identity.userId()));
    }
}
