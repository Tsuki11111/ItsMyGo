package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.FootprintView;
import com.tsuk1.itsmygo.dto.PageResponse;

/** 浏览足迹：按商品去重，每个用户只保留最近 100 条 */
public interface FootprintService {

    void record(Long userId, Long productId);

    PageResponse<FootprintView> page(Long userId, int page, int size);

    void clear(Long userId);
}
