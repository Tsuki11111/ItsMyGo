package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.ProductCleanupReport;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserFavorite;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.UserFavoriteMapper;
import com.tsuk1.itsmygo.service.ProductCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 长期下架商品：dry-run 只报告；执行后归档；无引用商品可物理清理，有引用跳过。 */
class ProductCleanupTest extends ApiTestBase {

    @Autowired
    private ProductCleanupService cleanupService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    private long userIdOf(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        return user.getId();
    }

    private void makeOffShelfLongAgo(long productId) {
        productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, productId)
                .set(Product::getStatus, 0)
                .set(Product::getArchivedAt, null)
                .set(Product::getUpdatedAt, LocalDateTime.now().minusDays(200)));
    }

    private void makeArchivedLongAgo(long productId) {
        productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, productId)
                .set(Product::getStatus, 0)
                .set(Product::getArchivedAt, LocalDateTime.now().minusDays(60)));
    }

    @Test
    void dryRunReportsThenArchiveAndPurgeUnreferencedProduct() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProductForTest(admin, "Cleanup" + System.nanoTime(), "10.00", 5);
        makeOffShelfLongAgo(productId);

        ProductCleanupReport dry = cleanupService.run(true);
        assertThat(dry.dryRun()).isTrue();
        assertThat(dry.archiveCandidates()).isGreaterThanOrEqualTo(1);
        assertThat(dry.archived()).isZero();
        assertThat(productMapper.selectById(productId).getArchivedAt()).isNull();

        ProductCleanupReport executed = cleanupService.run(false);
        assertThat(executed.archived()).isGreaterThanOrEqualTo(1);
        assertThat(productMapper.selectById(productId).getArchivedAt()).isNotNull();

        // 归档满宽限期 + 无任何引用 → 物理清理
        makeArchivedLongAgo(productId);
        ProductCleanupReport purge = cleanupService.run(false);
        assertThat(purge.purged()).isGreaterThanOrEqualTo(1);
        assertThat(productMapper.selectById(productId)).isNull();
        assertThat(productSkuMapper.selectCount(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId))).isZero();
    }

    @Test
    void referencedProductIsArchivedButNotPurged() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String username = "fav" + System.nanoTime();
        customerToken(username);
        long userId = userIdOf(username);
        long productId = createProductForTest(admin, "Keep" + System.nanoTime(), "12.00", 5);

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        userFavoriteMapper.insert(favorite);

        makeOffShelfLongAgo(productId);
        cleanupService.run(false);
        makeArchivedLongAgo(productId);

        ProductCleanupReport report = cleanupService.run(false);
        assertThat(report.skippedReferenced()).isGreaterThanOrEqualTo(1);
        assertThat(productMapper.selectById(productId)).isNotNull();
    }
}
