package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.ProductSpec;
import com.tsuk1.itsmygo.entity.ProductSpecValue;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecValueMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.OrderService;
import com.tsuk1.itsmygo.service.StockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SKU 粒度的防超卖。
 *
 * **本类刻意不加 @Transactional** —— 并发要的是真实提交，整个测试包在一个事务里的话
 * 其他线程根本看不到数据，也就测不出竞态。代价是自己清理（见 {@link #cleanup()}）。
 *
 * 两条测试守的是不同的东西：
 *   1. {@link #tenBuyersCannotOversellOneSku} —— 单个 SKU 的库存不会被抢穿
 *   2. {@link #twoSkusOfTheSameProductDoNotInterfere} —— **一款售罄不影响另一款**。
 *      这是 SKU 化新引入的风险：计数器要是还按商品建，两款就共用一个数字了。
 */
@SpringBootTest
class SkuStockConcurrencyTest {

    private static final int BUYERS = 10;

    @Autowired
    private OrderService orderService;
    @Autowired
    private StockService stockService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AddressMapper addressMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSkuMapper skuMapper;
    @Autowired
    private ProductSpecMapper specMapper;
    @Autowired
    private ProductSpecValueMapper specValueMapper;
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long createdCategoryId;
    private Long createdProductId;
    private Long blackSkuId;
    private Long whiteSkuId;

    @Test
    void tenBuyersCannotOversellOneSku() throws Exception {
        int stock = 5;
        createProductWithTwoSkus(stock, 100);

        List<Long> buyers = new ArrayList<>();
        List<Long> addresses = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            addBuyer("b" + i, blackSkuId, buyers, addresses);
        }

        int succeeded = runConcurrently(buyers, addresses);

        // 10 个人抢 5 件 —— 恰好 5 个成功
        assertThat(succeeded).isEqualTo(stock);

        ProductSku black = skuMapper.selectById(blackSkuId);
        assertThat(black.getStock()).isZero();
        assertThat(black.getSales()).isEqualTo(stock);
        // Redis 计数器要和数据库一致
        assertThat(stockService.currentSkuStock(blackSkuId)).isZero();
        // 没抢到的那款纹丝不动
        assertThat(skuMapper.selectById(whiteSkuId).getStock()).isEqualTo(100);
        // 订单数对得上
        assertThat(orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .in(Order::getUserId, createdUserIds))).isEqualTo(stock);
    }

    /**
     * 同一件商品的两款各抢各的：**一款售罄不该影响另一款**。
     *
     * 计数器如果还按商品建，两款就共用一个数字 —— 黑色抢光之后白色也买不了，
     * 或者两边各自看到 8 件、加起来超卖。
     */
    @Test
    void twoSkusOfTheSameProductDoNotInterfere() throws Exception {
        int blackStock = 3;
        int whiteStock = 10;
        createProductWithTwoSkus(blackStock, whiteStock);

        List<Long> buyers = new ArrayList<>();
        List<Long> addresses = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            addBuyer("b" + i, blackSkuId, buyers, addresses);
        }
        for (int i = 0; i < 8; i++) {
            addBuyer("w" + i, whiteSkuId, buyers, addresses);
        }

        int succeeded = runConcurrently(buyers, addresses);

        // 黑色只有 3 件 → 3 个成功；白色 10 件、来了 8 个人 → 8 个全中
        assertThat(succeeded).isEqualTo(blackStock + 8);

        assertThat(skuMapper.selectById(blackSkuId).getStock()).isZero();
        assertThat(skuMapper.selectById(whiteSkuId).getStock()).isEqualTo(whiteStock - 8);
        assertThat(stockService.currentSkuStock(blackSkuId)).isZero();
        assertThat(stockService.currentSkuStock(whiteSkuId)).isEqualTo(whiteStock - 8);

        // 商品上的汇总 = 两款之和
        assertThat(productMapper.selectById(createdProductId).getStock())
                .isEqualTo((blackStock - blackStock) + (whiteStock - 8));
    }

    // ── 造数据 ──

    /** 建分类 + 商品 + 一个「颜色」规格（黑 / 白两个值）+ 两条 SKU */
    private void createProductWithTwoSkus(int blackStock, int whiteStock) {
        String stamp = String.valueOf(System.nanoTime());

        Category category = new Category();
        category.setParentId(0L);
        category.setName("SkuConc" + stamp);
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        createdCategoryId = category.getId();

        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setName("SkuScarce " + stamp);
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("19.90"));
        product.setStock(blackStock + whiteStock);
        product.setSales(0);
        product.setHasSpecs(1);
        product.setStatus(1);
        productMapper.insert(product);
        createdProductId = product.getId();

        ProductSpec spec = new ProductSpec();
        spec.setProductId(product.getId());
        spec.setName("颜色");
        spec.setSort(0);
        specMapper.insert(spec);

        Long blackValueId = insertSpecValue(spec.getId(), "黑色");
        Long whiteValueId = insertSpecValue(spec.getId(), "白色");

        blackSkuId = insertSku(product.getId(), blackValueId, "颜色:黑色", "19.90", blackStock);
        whiteSkuId = insertSku(product.getId(), whiteValueId, "颜色:白色", "29.90", whiteStock);
    }

    private Long insertSpecValue(Long specId, String value) {
        ProductSpecValue specValue = new ProductSpecValue();
        specValue.setSpecId(specId);
        specValue.setValue(value);
        specValue.setSort(0);
        specValueMapper.insert(specValue);
        return specValue.getId();
    }

    private Long insertSku(Long productId, Long specValueId, String specText,
                           String price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecValueIds(String.valueOf(specValueId));
        sku.setSpecText(specText);
        sku.setPrice(new BigDecimal(price));
        sku.setStock(stock);
        sku.setSales(0);
        sku.setImage("");
        sku.setStatus(1);
        skuMapper.insert(sku);
        return sku.getId();
    }

    /** 一个买家：账号 + 地址 + 购物车里的那一件（指定某个 SKU） */
    private void addBuyer(String name, Long skuId, List<Long> buyers, List<Long> addresses) {
        User user = new User();
        user.setUsername("skuc" + System.nanoTime() + "_" + name);
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setNickname("buyer");
        user.setPhone("");
        user.setStatus(1);
        user.setRole(Roles.CUSTOMER);
        userMapper.insert(user);
        createdUserIds.add(user.getId());
        buyers.add(user.getId());

        Address address = new Address();
        address.setUserId(user.getId());
        address.setReceiver("R");
        address.setPhone("13800000000");
        address.setProvince("P");
        address.setCity("C");
        address.setDistrict("D");
        address.setDetail("Detail");
        address.setIsDefault(1);
        addressMapper.insert(address);
        addresses.add(address.getId());

        CartItem cartItem = new CartItem();
        cartItem.setUserId(user.getId());
        cartItem.setProductId(createdProductId);
        cartItem.setSkuId(skuId);
        cartItem.setQuantity(1);
        cartItem.setSelected(1);
        cartItemMapper.insert(cartItem);
    }

    /** 所有人卡在起跑线上，一声令下同时下单；返回成功次数 */
    private int runConcurrently(List<Long> userIds, List<Long> addressIds) throws Exception {
        int n = userIds.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    // 每个买家各领一个令牌 —— 这测的是并发抢库存，不是重复提交
                    orderService.create(userIds.get(idx), new CreateOrderRequest(
                            addressIds.get(idx), orderService.issueToken(userIds.get(idx)), null));
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {
                    // 库存不足是预期结果之一
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        if (!done.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发下单超时");
        }
        pool.shutdown();
        return succeeded.get();
    }

    /** ⚠️ 只删本次测试造出来的那些行（`delete(null)` 是清空整张表，见 HANDOFF §5.19） */
    @AfterEach
    void cleanup() {
        if (!createdUserIds.isEmpty()) {
            List<Long> orderIds = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                            .in(Order::getUserId, createdUserIds))
                    .stream().map(Order::getId).toList();
            if (!orderIds.isEmpty()) {
                orderItemMapper.delete(Wrappers.<OrderItem>lambdaQuery()
                        .in(OrderItem::getOrderId, orderIds));
                orderMapper.delete(Wrappers.<Order>lambdaQuery().in(Order::getId, orderIds));
            }
            cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                    .in(CartItem::getUserId, createdUserIds));
            addressMapper.delete(Wrappers.<Address>lambdaQuery()
                    .in(Address::getUserId, createdUserIds));
        }
        if (createdProductId != null) {
            for (ProductSku sku : skuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                    .eq(ProductSku::getProductId, createdProductId))) {
                // Redis 键不参与事务，删 SKU 时顺手清掉
                stockService.drop(sku.getId());
                skuMapper.deleteById(sku.getId());
            }
            List<Long> specIds = specMapper.selectList(Wrappers.<ProductSpec>lambdaQuery()
                            .eq(ProductSpec::getProductId, createdProductId))
                    .stream().map(ProductSpec::getId).toList();
            if (!specIds.isEmpty()) {
                specValueMapper.delete(Wrappers.<ProductSpecValue>lambdaQuery()
                        .in(ProductSpecValue::getSpecId, specIds));
            }
            specMapper.delete(Wrappers.<ProductSpec>lambdaQuery()
                    .eq(ProductSpec::getProductId, createdProductId));
            productMapper.deleteById(createdProductId);
        }
        if (createdCategoryId != null) {
            categoryMapper.deleteById(createdCategoryId);
        }
        for (Long userId : createdUserIds) {
            userMapper.deleteById(userId);
        }
        createdUserIds.clear();
    }
}
