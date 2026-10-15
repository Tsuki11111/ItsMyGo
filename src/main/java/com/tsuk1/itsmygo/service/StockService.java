package com.tsuk1.itsmygo.service;

public interface StockService {

    /**
     * Redis 预扣。成功返回 true；库存不足或商品未加载返回 false。
     * 这层只是「挡流量」，**不保证最终正确性** —— 最终防线是数据库的条件更新。
     */
    boolean tryReserve(Long productId, int quantity);

    /** 回补（下单失败回滚、取消订单时） */
    void release(Long productId, int quantity);

    /** 把数据库里的当前库存重新灌进 Redis */
    void reload(Long productId);
}
