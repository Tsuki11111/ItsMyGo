package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.dto.ProductPricePeak;
import com.tsuk1.itsmygo.entity.ProductPriceHistory;
import com.tsuk1.itsmygo.mapper.ProductPriceHistoryMapper;
import com.tsuk1.itsmygo.service.ProductPriceHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class ProductPriceHistoryServiceImpl implements ProductPriceHistoryService {

    private final ProductPriceHistoryMapper historyMapper;

    public ProductPriceHistoryServiceImpl(ProductPriceHistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    @Override
    public void recordBaseline(Long productId, Long skuId, BigDecimal price) {
        record(productId, skuId, price, ProductPriceHistory.REASON_BASELINE);
    }

    @Override
    public void recordIfChanged(Long productId, Long skuId, BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null || oldPrice.compareTo(newPrice) == 0) {
            return;
        }
        record(productId, skuId, newPrice, ProductPriceHistory.REASON_UPDATE);
    }

    @Override
    public Map<Long, BigDecimal> maxPricesSince(Collection<Long> productIds, LocalDateTime since) {
        List<Long> ids = productIds == null ? List.of()
                : productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        for (ProductPricePeak peak : historyMapper.selectMaxPricesSince(ids, since)) {
            if (peak.getProductId() != null && peak.getMaxPrice() != null) {
                result.put(peak.getProductId(), peak.getMaxPrice());
            }
        }
        return result;
    }

    private void record(Long productId, Long skuId, BigDecimal price, String reason) {
        if (productId == null || price == null) {
            return;
        }
        ProductPriceHistory history = new ProductPriceHistory();
        history.setProductId(productId);
        history.setSkuId(skuId);
        history.setPrice(price);
        history.setReason(reason);
        history.setChangedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }
}
