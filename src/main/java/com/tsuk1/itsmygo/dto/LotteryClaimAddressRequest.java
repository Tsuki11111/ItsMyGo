package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 实物奖品填写收货信息（快照保存，不跟随地址簿后续修改） */
public record LotteryClaimAddressRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 32, message = "收货人最长 32 个字符")
        String receiverName,

        @NotBlank(message = "收货电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
        String receiverPhone,

        @NotBlank(message = "收货地址不能为空")
        @Size(max = 255, message = "收货地址最长 255 个字符")
        String receiverAddress
) {
}
