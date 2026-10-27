package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 回答商品提问：只有买过该商品的用户可以提交 */
public record ProductAnswerRequest(
        @NotBlank(message = "请输入回答")
        @Size(max = 500, message = "回答最长 500 个字符")
        String content) {
}
