package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.ProductSkuView;
import com.tsuk1.itsmygo.dto.SkuSaveRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;

/** 商品的规格与 SKU */
public interface ProductSkuService {

    /** 商家后台回显：校验商品归属；merchantId 为 null 表示平台管理员 */
    ProductSkuView get(Long productId, Long merchantId);

    /** 管理端回显某商品的规格与 SKU */
    default ProductSkuView get(Long productId) {
        return get(productId, null);
    }

    ProductSkuView save(Long productId, SkuSaveRequest request, Long merchantId);

    /** 整组覆盖保存（规格 + 规格值 + SKU），返回保存后的结果 */
    default ProductSkuView save(Long productId, SkuSaveRequest request) {
        return save(productId, request, null);
    }

    /**
     * 商品新建时给它一条**默认 SKU**（无规格），价格库存先落在那里。
     *
     * ⚠️ 每个商品都至少要有 SKU，否则详情页会显示总库存 0、
     * 下单永远报「库存不足」——看着像库存 bug，其实是数据没铺开。
     */
    void createDefaultSku(Product product);

    /**
     * 单规格商品改价改库存时同步到它那条默认 SKU。
     * 配了多规格的商品**不动** —— 那种要商户走管理端的分规格接口。
     */
    void syncDefaultSku(Product product);

    /**
     * 管理端调整某个 SKU 的库存（条件更新）。
     *
     * 整组保存 SKU 时**故意不动已有行的库存**（库存该由下单/取消来改），
     * 所以补货、盘库得走这里。
     *
     * @param expectedStock 表单提交时看到的库存；与库里不一致说明期间被并发改过
     * @return 调整后的 SKU
     */
    ProductSku adjustStock(Long productId, Long skuId, Integer stock, Integer expectedStock,
                           Long merchantId);

    default ProductSku adjustStock(Long productId, Long skuId, Integer stock, Integer expectedStock) {
        return adjustStock(productId, skuId, stock, expectedStock, null);
    }
}
