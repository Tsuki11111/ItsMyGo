package com.tsuk1.itsmygo.common.cart;

import java.util.Map;

/**
 * 重建角标缓存所需的完整快照：总数量 + 每个 SKU 的数量。
 *
 * <p>存 SKU 明细而不是只存一个总数，是为了让这个 Hash 保持「购物车结构」的形态；
 * 角标本身只读 {@code meta.count}，明细暂时不参与业务。</p>
 */
public record CartCountSnapshot(long count, Map<Long, Integer> skuQuantities) {
}
