package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 管理端创建抽奖活动；时间格式用 ISO，例如 2026-12-01T10:00:00 */
public record LotteryActivityRequest(
        @NotBlank(message = "活动名称不能为空")
        @Size(max = 100, message = "活动名称最多 100 个字符")
        String name,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime startAt,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endAt
) {
}
