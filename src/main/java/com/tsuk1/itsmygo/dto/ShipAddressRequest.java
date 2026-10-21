package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 商家 / 平台自营的发货地址。全部字段可空，传空即清空。 */
public record ShipAddressRequest(
        @Size(max = 32, message = "省份最长 32 个字符")
        String province,

        @Size(max = 32, message = "城市最长 32 个字符")
        String city,

        @Size(max = 32, message = "区县最长 32 个字符")
        String district,

        @Size(max = 128, message = "详细地址最长 128 个字符")
        String address,

        /** 经度；与纬度必须同时提供，否则视为未定位 */
        @DecimalMin(value = "-180", message = "经度不合法")
        @DecimalMax(value = "180", message = "经度不合法")
        BigDecimal lng,

        /** 纬度；与经度必须同时提供，否则视为未定位 */
        @DecimalMin(value = "-90", message = "纬度不合法")
        @DecimalMax(value = "90", message = "纬度不合法")
        BigDecimal lat) {
}
