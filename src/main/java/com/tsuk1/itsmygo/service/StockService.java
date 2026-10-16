package com.tsuk1.itsmygo.service;

import java.util.Collection;
import java.util.Map;

/**
 * 库存计数器（Redis）。
 *
 * **粒度为 SKU**，不是商品 —— 商品上的 {@code stock} 只是各 SKU 的汇总，不参与判定。
 * 分层和以前一样：
 *   第一层 Redis —— Lua 原子地「判断 + 扣减」，把绝大多数并发挡在数据库之外
 *   第二层 数据库 —— {@code ProductSkuMapper.deductStock} 的 {@code WHERE stock >= ?} 才是真相
 */
public interface StockService {

    /**
     * Redis 预扣。成功返回 true；库存不足或键加载不出来返回 false。
     * 这层只是「挡流量」，**不保证最终正确性** —— 最终防线是数据库的条件更新。
     */
    boolean tryReserve(Long skuId, int quantity);

    /** 回补（下单失败回滚、取消订单时） */
    void release(Long skuId, int quantity);

    /** 把数据库里的当前库存重新灌进 Redis */
    void reload(Long skuId);

    /** 删掉某个 SKU 的库存键（SKU 被移除时；留着会变成读不到的脏数据） */
    void drop(Long skuId);

    /**
     * 当前可售库存 —— 读 Redis 计数器，**不查库**。
     * 反映的是「扣掉已预扣之后还能买多少」，比数据库那份快照更准。
     * SKU 不存在或加载失败时返回 0。
     */
    int currentSkuStock(Long skuId);

    /**
     * 批量版：一次 multiGet 拿一批 SKU 的库存，只有未命中的才逐个回源加载。
     * 商品详情要展示一整个 SKU 列表，逐个读就是 N 次往返。
     */
    Map<Long, Integer> currentSkuStocks(Collection<Long> skuIds);
}
