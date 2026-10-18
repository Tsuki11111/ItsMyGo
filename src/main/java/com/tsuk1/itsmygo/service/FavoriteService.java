package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.FavoriteView;
import com.tsuk1.itsmygo.dto.PageResponse;

/** 商品收藏 */
public interface FavoriteService {

    PageResponse<FavoriteView> page(Long userId, int page, int size);

    /** 商品详情页回显：当前用户有没有收藏它 */
    boolean isFavorited(Long userId, Long productId);

    /** 幂等收藏；商品不存在报 404 */
    void add(Long userId, Long productId);

    /** 幂等取消 */
    void remove(Long userId, Long productId);
}
