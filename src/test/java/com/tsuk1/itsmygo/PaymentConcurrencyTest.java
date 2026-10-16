package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.dto.CreateOrderRequest;
import com.tsuk1.itsmygo.entity.Address;
import com.tsuk1.itsmygo.entity.CartItem;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.Payment;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.PaymentMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import com.tsuk1.itsmygo.service.OrderService;
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
 * 并发下的资金安全：**同一个订单被同时操作多次，只能生效一次**。
 *
 * 两个场景守的是**两道不同的防线**，缺一不可：
 *
 * 1. {@link #concurrentPayOnlySucceedsOnce} —— 守 `payments.uk_order_id` 唯一索引。
 *    但**光靠它不够**：把订单状态机的原子性去掉，这条测试照样通过（唯一索引会兜住），
 *    也就是说它根本测不出状态机的问题。
 *
 * 2. {@link #concurrentCancelRestoresStockOnlyOnce} —— 守**状态机的原子性**。
 *    这个场景没有唯一索引兜底：五个并发取消请求若都通过「读出来判断」那一关，
 *    就会执行五次回滚库存，**库存凭空多出来** —— 实打实的资损。
 *
 * 刻意不加 @Transactional：并发要的是真实提交，包在一个事务里其他线程根本看不到数据。
 */
@SpringBootTest
class PaymentConcurrencyTest {

    private static final int CONCURRENCY = 5;
    private static final int STOCK = 100;

    @Autowired
    private OrderService orderService;
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
    private CartItemMapper cartItemMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long createdCategoryId;
    private Long createdProductId;
    /** 库存和下单都按 SKU 走，所以得记住商品的默认 SKU */
    private Long createdSkuId;
    private Long orderId;
    private Long buyerId;

    /** 建一个「已下单、待支付」的订单，返回前把 orderId / buyerId / createdProductId 记下来 */
    private void preparePendingOrder() {
        String stamp = String.valueOf(System.nanoTime());

        Category category = new Category();
        category.setParentId(0L);
        category.setName("PayConc" + stamp);
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        createdCategoryId = category.getId();

        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setName("Payable " + stamp);
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(STOCK);
        product.setSales(0);
        product.setStatus(1);
        productMapper.insert(product);
        createdProductId = product.getId();
        createdSkuId = insertDefaultSku(product.getId(), product.getPrice(), product.getStock());

        User user = new User();
        user.setUsername("payconc" + stamp);
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setNickname("payer");
        user.setPhone("");
        user.setStatus(1);
        user.setRole(Roles.CUSTOMER);
        userMapper.insert(user);
        createdUserIds.add(user.getId());
        buyerId = user.getId();

        Address address = new Address();
        address.setUserId(buyerId);
        address.setReceiver("R");
        address.setPhone("13800000000");
        address.setProvince("P");
        address.setCity("C");
        address.setDistrict("D");
        address.setDetail("Detail");
        address.setIsDefault(1);
        addressMapper.insert(address);

        CartItem cartItem = new CartItem();
        cartItem.setUserId(buyerId);
        cartItem.setProductId(createdProductId);
        cartItem.setSkuId(createdSkuId);
        cartItem.setQuantity(1);
        cartItem.setSelected(1);
        cartItemMapper.insert(cartItem);

        orderId = orderService.create(buyerId,
                new CreateOrderRequest(address.getId(), orderService.issueToken(buyerId), null))
                .order().getId();
    }

    /**
     * 直插库的商品**不会**自动有 SKU（那是管理端 create 接口做的事），
     * 而库存的真相和下单都挂在 SKU 上 —— 测试自己造数据就得自己补。
     */
    private Long insertDefaultSku(Long productId, BigDecimal price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecValueIds("");
        sku.setSpecText("");
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setSales(0);
        sku.setImage("");
        sku.setStatus(1);
        skuMapper.insert(sku);
        return sku.getId();
    }

    /** 让 N 个线程卡在起跑线上，一声令下同时执行；返回成功次数 */
    private int runConcurrently(Runnable action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    action.run();
                    succeeded.incrementAndGet();
                } catch (Exception ignored) {
                    // 被拒是预期结果之一
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        return succeeded.get();
    }

    @Test
    void concurrentPayOnlySucceedsOnce() throws Exception {
        preparePendingOrder();

        int succeeded = runConcurrently(() -> orderService.pay(buyerId, orderId));

        assertThat(succeeded).isEqualTo(1);
        long paymentCount = paymentMapper.selectCount(
                Wrappers.<Payment>lambdaQuery().eq(Payment::getOrderId, orderId));
        assertThat(paymentCount).isEqualTo(1);
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo("PAID");
    }

    /**
     * 五个并发取消：**库存只能被回滚一次**。
     * 没有唯一索引能兜住这件事，全看状态流转是不是原子的。
     */
    @Test
    void concurrentCancelRestoresStockOnlyOnce() throws Exception {
        preparePendingOrder();
        // 下单扣了 1 件
        assertThat(productMapper.selectById(createdProductId).getStock()).isEqualTo(STOCK - 1);

        int succeeded = runConcurrently(() -> orderService.cancel(buyerId, orderId));

        assertThat(succeeded).isEqualTo(1);

        Product after = productMapper.selectById(createdProductId);
        // 回滚五次的话这里会变成 STOCK + 4
        assertThat(after.getStock()).isEqualTo(STOCK);
        // 销量也要只退一次（restoreStock 会连同 sales 一起退）
        assertThat(after.getSales()).isZero();
        assertThat(orderMapper.selectById(orderId).getStatus()).isEqualTo("CANCELLED");
    }

    /**
     * ⚠️ **只删本次测试造出来的那些行。**
     *
     * 这里原来写的是 `paymentMapper.delete(null)`、`orderMapper.delete(null)` 这种 ——
     * MyBatis-Plus 里 **Wrapper 传 null 等于没有 where 条件**，也就是 `DELETE FROM 整张表`。
     * 本地开发库和测试库是同一个，跑一次测试就会把开发时下的订单、支付流水、
     * 加过的购物车、存过的收货地址一并抹掉，而且很难往「是测试干的」上想。
     */
    @AfterEach
    void cleanup() {
        if (!createdUserIds.isEmpty()) {
            List<Long> orderIds = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                            .in(Order::getUserId, createdUserIds))
                    .stream().map(Order::getId).toList();
            if (!orderIds.isEmpty()) {
                paymentMapper.delete(Wrappers.<Payment>lambdaQuery().in(Payment::getOrderId, orderIds));
                orderItemMapper.delete(Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId, orderIds));
                orderMapper.delete(Wrappers.<Order>lambdaQuery().in(Order::getId, orderIds));
            }
            cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().in(CartItem::getUserId, createdUserIds));
            addressMapper.delete(Wrappers.<Address>lambdaQuery().in(Address::getUserId, createdUserIds));
        }
        if (createdSkuId != null) {
            skuMapper.deleteById(createdSkuId);
            createdSkuId = null;
        }
        if (createdProductId != null) {
            productMapper.deleteById(createdProductId);
            createdProductId = null;
        }
        if (createdCategoryId != null) {
            categoryMapper.deleteById(createdCategoryId);
            createdCategoryId = null;
        }
        for (Long userId : createdUserIds) {
            userMapper.deleteById(userId);
        }
        createdUserIds.clear();
        orderId = null;
        buyerId = null;
    }
}
