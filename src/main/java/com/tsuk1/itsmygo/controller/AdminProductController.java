package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.ProductBloomFilter;
import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.ProductSkuView;
import com.tsuk1.itsmygo.dto.SkuSaveRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.service.ProductService;
import com.tsuk1.itsmygo.service.ProductSkuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端：含下架商品，需 ADMIN */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;
    private final ProductSkuService productSkuService;
    private final ProductBloomFilter bloomFilter;

    public AdminProductController(ProductService productService, ProductSkuService productSkuService,
                                  ProductBloomFilter bloomFilter) {
        this.productService = productService;
        this.productSkuService = productSkuService;
        this.bloomFilter = bloomFilter;
    }

    @GetMapping
    public Result<PageResponse<Product>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(productService.page(page, size, categoryId, keyword, false));
    }

    @PostMapping
    public Result<Product> create(@Valid @RequestBody ProductRequest request) {
        return Result.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return Result.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }

    /** 回显某商品当前的规格与 SKU，供管理端编辑表单用 */
    @GetMapping("/{id}/skus")
    public Result<ProductSkuView> skus(@PathVariable Long id) {
        return Result.ok(productSkuService.get(id));
    }

    /**
     * 保存规格与 SKU —— **整组覆盖**（规格值一改，SKU 组合集合就变了，逐个 CRUD 表达不了）。
     *
     * 已存在的 SKU 会按规格值组合匹配到原来那行，**库存保持不动**；
     * 新建的 SKU 用请求里带的 stock 作为初始值。
     * 新建的规格值请传负数临时 id，服务端插入后会改写引用 —— 详见 {@link SkuSaveRequest}。
     */
    @PutMapping("/{id}/skus")
    public Result<ProductSkuView> saveSkus(@PathVariable Long id,
                                           @Valid @RequestBody SkuSaveRequest request) {
        return Result.ok(productSkuService.save(id, request));
    }

    /**
     * 手工重建商品布隆过滤器。
     * 用途：有商品**没走 create 接口**入库（数据迁移、手工 SQL、导入脚本）时，
     * 它们不在过滤器里会被判为「不存在」，调一次这个接口即可修复
     * （应用重启也会自动预热，这里是不重启的补救手段）。
     */
    @PostMapping("/rebuild-bloom")
    public Result<Integer> rebuildBloomFilter() {
        return Result.ok(bloomFilter.rebuild());
    }
}
