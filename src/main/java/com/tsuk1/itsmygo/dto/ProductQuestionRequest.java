package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发布商品提问：任何登录用户可发 */
public record ProductQuestionRequest(
        @NotBlank(message = "请输入问题")
        @Size(max = 500, message = "提问最长 500 个字符")
        String content) {
}
