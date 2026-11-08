package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.ProductCleanupReport;

/** 长期下架商品：自动归档 + 无引用商品物理清理。 */
public interface ProductCleanupService {

    /**
     * 执行一次扫描。
     *
     * @param dryRun true 只统计不落库；false 真正归档/删除
     */
    ProductCleanupReport run(boolean dryRun);
}
