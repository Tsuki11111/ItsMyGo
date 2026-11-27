package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 管理端创建活动：type 目前只支持 LOTTERY，后续类型用同一个入口扩展 */
public record ActivityRequest(
        @NotBlank(message = "活动类型不能为空")
        @Size(max = 32, message = "活动类型最多 32 个字符")
        String type,

        @NotBlank(message = "活动名称不能为空")
        @Size(max = 100, message = "活动名称最多 100 个字符")
        String name,

        @Size(max = 255, message = "活动副标题最多 255 个字符")
        String description,

        @NotBlank(message = "活动主色调不能为空")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "主色调必须是 #RRGGBB")
        String primaryColor,

        @Size(max = 255, message = "封面图 URL 最多 255 个字符")
        String coverImage,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime startAt,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endAt
) {
}
