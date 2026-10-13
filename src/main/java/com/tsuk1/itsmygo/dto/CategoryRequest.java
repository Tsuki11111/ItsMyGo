package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        /** null 或 0 表示顶层分类 */
        Long parentId,

        @NotBlank(message = "分类名不能为空")
        @Size(max = 32, message = "分类名最长 32 个字符")
        String name,

        Integer sort,

        Boolean enabled
) {
}
