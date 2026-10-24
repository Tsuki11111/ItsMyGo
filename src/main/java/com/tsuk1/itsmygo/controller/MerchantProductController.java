package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.AdjustSkuStockRequest;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.ProductSkuView;
import com.tsuk1.itsmygo.dto.SkuSaveRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.ProductService;
import com.tsuk1.itsmygo.service.ProductSkuService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商家后台：只能操作自己店铺的商品、规格与库存 */
@RestController
@RequestMapping("/api/merchant/products")
public class MerchantProductController {

    private final ProductService productService;
    private final ProductSkuService productSkuService;

    public MerchantProductController(ProductService productService,
                                     ProductSkuService productSkuService) {
        this.productService = productService;
        this.productSkuService = productSkuService;
    }

    @GetMapping
    public Result<PageResponse<Product>> page(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        Long merchantId = requireMerchant(user);
        return Result.ok(productService.page(page, size, categoryId, keyword, null, false,
                merchantId, false));
    }

    @GetMapping("/{id}")
    public Result<Product> detail(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return Result.ok(productService.detailForMerchant(id, requireMerchant(user)));
    }

    @PostMapping
    public Result<Product> create(@AuthenticationPrincipal CurrentUser user,
                                  @Valid @RequestBody ProductRequest request) {
        return Result.ok(productService.create(request, requireMerchant(user)));
    }

    @PutMapping("/{id}")
    public Result<Product> update(@AuthenticationPrincipal CurrentUser user,
                                  @PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return Result.ok(productService.update(id, request, requireMerchant(user)));
    }

    @GetMapping("/{id}/skus")
    public Result<ProductSkuView> skus(@AuthenticationPrincipal CurrentUser user,
                                       @PathVariable Long id) {
        return Result.ok(productSkuService.get(id, requireMerchant(user)));
    }

    @PutMapping("/{id}/skus")
    public Result<ProductSkuView> saveSkus(@AuthenticationPrincipal CurrentUser user,
                                           @PathVariable Long id,
                                           @Valid @RequestBody SkuSaveRequest request) {
        return Result.ok(productSkuService.save(id, request, requireMerchant(user)));
    }

    @PutMapping("/{productId}/skus/{skuId}/stock")
    public Result<ProductSku> adjustStock(@AuthenticationPrincipal CurrentUser user,
                                          @PathVariable Long productId,
                                          @PathVariable Long skuId,
                                          @Valid @RequestBody AdjustSkuStockRequest request) {
        return Result.ok(productSkuService.adjustStock(productId, skuId,
                request.stock(), request.expectedStock(), requireMerchant(user)));
    }

    private Long requireMerchant(CurrentUser user) {
        if (!user.isMerchant()) {
            throw new BizException(ResultCode.FORBIDDEN, "需要商家权限");
        }
        return user.merchantId();
    }
}
