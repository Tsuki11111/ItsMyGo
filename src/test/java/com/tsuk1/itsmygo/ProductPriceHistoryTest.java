package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.dto.SkuSaveRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductPriceHistory;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductPriceHistoryMapper;
import com.tsuk1.itsmygo.service.ProductService;
import com.tsuk1.itsmygo.service.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 价格历史：新建时 baseline，商品/规格改价后只在真的变化时追加 update。 */
class ProductPriceHistoryTest extends ApiTestBase {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductSkuService productSkuService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductPriceHistoryMapper historyMapper;

    private List<ProductPriceHistory> history(long productId) {
        return historyMapper.selectList(Wrappers.<ProductPriceHistory>lambdaQuery()
                .eq(ProductPriceHistory::getProductId, productId)
                .orderByAsc(ProductPriceHistory::getId));
    }

    private ProductRequest request(Product product, String price) {
        return new ProductRequest(product.getCategoryId(), product.getName(),
                product.getSubtitle(), product.getMainImage(), new BigDecimal(price),
                product.getStock(), product.getDetail(), true);
    }

    @Test
    void createAndProductPriceUpdateRecordHistory() throws Exception {
        String admin = adminToken("ph" + System.nanoTime());
        long productId = createProductForTest(admin, "PH" + System.nanoTime(), "100.00", 10);

        List<ProductPriceHistory> rows = history(productId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getReason()).isEqualTo(ProductPriceHistory.REASON_BASELINE);
        assertThat(rows.get(0).getPrice()).isEqualByComparingTo("100.00");
        assertThat(rows.get(0).getSkuId()).isNull();

        Product product = productMapper.selectById(productId);
        productService.update(productId, request(product, "80.00"));

        rows = history(productId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getReason()).isEqualTo(ProductPriceHistory.REASON_UPDATE);
        assertThat(rows.get(1).getPrice()).isEqualByComparingTo("80.00");

        // 价格没变不该再记一条
        productService.update(productId, request(productMapper.selectById(productId), "80.00"));
        assertThat(history(productId)).hasSize(2);
    }

    @Test
    void skuPriceUpdateRecordsHistoryWithSkuId() throws Exception {
        String admin = adminToken("ph" + System.nanoTime());
        long productId = createProductForTest(admin, "PH" + System.nanoTime(), "100.00", 10);
        long skuId = defaultSkuId(productId);

        productSkuService.save(productId, new SkuSaveRequest(List.of(),
                List.of(new SkuSaveRequest.SkuPayload(List.of(), new BigDecimal("70.00"), 10, ""))));

        List<ProductPriceHistory> rows = history(productId);
        ProductPriceHistory last = rows.get(rows.size() - 1);
        assertThat(last.getReason()).isEqualTo(ProductPriceHistory.REASON_UPDATE);
        assertThat(last.getPrice()).isEqualByComparingTo("70.00");
        assertThat(last.getSkuId()).isEqualTo(skuId);
    }
}
