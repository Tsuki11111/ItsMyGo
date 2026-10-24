package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.StockView;
import com.tsuk1.itsmygo.entity.Product;

import java.util.List;

public interface ProductService {

    /**
     * 商品分页。
     *
     * @param onlyOnSale          顾客端传 true（只看上架），管理端传 false
     * @param merchantId          非空时只看该商户；null 表示不按商户过滤
     * @param onlyActiveMerchant  顾客端传 true（店铺必须 ACTIVE），管理端传 false
     */
    PageResponse<Product> page(int page, int size, Long categoryId, String keyword,
                               boolean onlyOnSale, Long merchantId, boolean onlyActiveMerchant);

    /** 兼容旧调用：不按商户过滤、不校验店铺状态（平台管理端语义） */
    default PageResponse<Product> page(int page, int size, Long categoryId, String keyword,
                                       boolean onlyOnSale) {
        return page(page, size, categoryId, keyword, onlyOnSale, null, false);
    }

    /** 管理端商品运营列表：支持上架状态与低库存筛选（低库存按 SKU stock <= 10）。 */
    PageResponse<Product> pageForAdmin(int page, int size, Long categoryId, String keyword,
                                       Long merchantId, Integer status, boolean lowStock);

    /** 批量上架 / 下架；返回实际更新的商品数。 */
    int updateStatusBatch(List<Long> ids, boolean onSale);

    Product detailForCustomer(Long id);

    /** 实时库存，走 Redis 计数器而非商品缓存 */
    StockView stockForCustomer(Long id);

    /** 商家后台用：商品必须是本店的 */
    Product detailForMerchant(Long id, Long merchantId);

    Product create(ProductRequest request, Long merchantId);

    /** 平台管理端：默认建到平台自营商户 */
    default Product create(ProductRequest request) {
        return create(request, 1L);
    }

    /** merchantId 非空时校验商品归属；null 表示平台管理员 */
    Product update(Long id, ProductRequest request, Long merchantId);

    default Product update(Long id, ProductRequest request) {
        return update(id, request, null);
    }

    void delete(Long id, Long merchantId);

    default void delete(Long id) {
        delete(id, null);
    }
}
