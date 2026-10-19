package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.MerchantView;
import com.tsuk1.itsmygo.service.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开店铺页信息；SecurityConfig 放行匿名 GET */
@RestController
@RequestMapping("/api/shops")
public class ShopController {

    private final MerchantService merchantService;

    public ShopController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/{id}")
    public Result<MerchantView> shop(@PathVariable Long id) {
        return Result.ok(merchantService.publicShop(id));
    }
}
