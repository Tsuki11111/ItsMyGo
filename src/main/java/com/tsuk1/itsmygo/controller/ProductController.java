package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.StockView;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端：只看上架商品，无需登录 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Result<PageResponse<Product>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            /** 店铺主页用：只看该商户的商品 */
            @RequestParam(required = false) Long merchantId) {
        // 顾客端只展示 ACTIVE 店铺的商品
        return Result.ok(productService.page(page, size, categoryId, keyword, true, merchantId, true));
    }

    @GetMapping("/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        return Result.ok(productService.detailForCustomer(id));
    }

    /** 实时库存：数据来自 Redis 计数器，不查库、也不受商品详情缓存影响 */
    @GetMapping("/{id}/stock")
    public Result<StockView> stock(@PathVariable Long id) {
        return Result.ok(productService.stockForCustomer(id));
    }
}
