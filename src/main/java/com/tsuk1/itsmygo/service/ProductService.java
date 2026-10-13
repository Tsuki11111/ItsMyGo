package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.PageResponse;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.Product;

public interface ProductService {

    /** @param onlyOnSale 顾客端传 true（只看上架），管理端传 false */
    PageResponse<Product> page(int page, int size, Long categoryId, String keyword, boolean onlyOnSale);

    Product detailForCustomer(Long id);

    Product create(ProductRequest request);

    Product update(Long id, ProductRequest request);

    void delete(Long id);
}
