package com.tsuk1.itsmygo.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductServiceImpl implements ProductService {

    private static final int ON_SALE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public PageResponse<Product> page(int page, int size, Long categoryId, String keyword, boolean onlyOnSale) {
        Page<Product> pageRequest = new Page<>(normalizePage(page), normalizeSize(size));

        var wrapper = Wrappers.<Product>lambdaQuery()
                .eq(onlyOnSale, Product::getStatus, ON_SALE)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                // 关键词目前用 LIKE；数据量上万后再考虑 FULLTEXT(ngram) 或搜索引擎
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Product::getName, keyword)
                        .or().like(Product::getSubtitle, keyword))
                .orderByDesc(Product::getId);

        return PageResponse.of(productMapper.selectPage(pageRequest, wrapper));
    }

    /** 顾客端详情：已下架的商品一律当不存在 */
    @Override
    public Product detailForCustomer(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !Integer.valueOf(ON_SALE).equals(product.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        return product;
    }

    @Override
    public Product create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        product.setSales(0);
        productMapper.insert(product);
        return product;
    }

    @Override
    public Product update(Long id, ProductRequest request) {
        Product product = requireExists(id);
        apply(product, request);
        productMapper.updateById(product);
        return product;
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        productMapper.deleteById(id);
    }

    private void apply(Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setSubtitle(request.subtitle() == null ? "" : request.subtitle());
        product.setMainImage(request.mainImage() == null ? "" : request.mainImage());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setDetail(request.detail());
        product.setStatus(Boolean.TRUE.equals(request.onSale()) ? ON_SALE : 0);
    }

    private Product requireExists(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
