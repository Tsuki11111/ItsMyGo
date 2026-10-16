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
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.AddressMapper;
import com.tsuk1.itsmygo.mapper.CartItemMapper;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
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
 * 库存并发：**本类刻意不加 @Transactional** —— 并发要的是真实提交，
 * 若整个测试包在一个事务里，其他线程根本看不到数据，也就测不出竞态。
 * 代价是数据不会自动回滚，所以自己做清理（见 {@link #cleanup()}）。
 */
@SpringBootTest
class StockConcurrencyTest {

    private static final int STOCK = 5;
    private static final int BUYERS = 10;

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

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        String stamp = String.valueOf(System.nanoTime());

        Category category = new Category();
        category.setParentId(0L);
        category.setName("Conc" + stamp);
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        createdCategoryId = category.getId();

        Product product = new Product();
        product.setCategoryId(category.getId());
        product.setName("Scarce " + stamp);
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(STOCK);
        product.setSales(0);
        product.setStatus(1);
        productMapper.insert(product);
        createdProductId = product.getId();

        // 每个买家一份：账号 + 地址 + 购物车（1 件）
        List<Long> addressIds = new ArrayList<>(BUYERS);
        for (int i = 0; i < BUYERS; i++) {
            User user = new User();
            user.setUsername("conc" + stamp + "_" + i);
            user.setPassword(passwordEncoder.encode("secret123"));
            user.setNickname("buyer");
            user.setPhone("");
            user.setStatus(1);
            user.setRole(Roles.CUSTOMER);
            userMapper.insert(user);
            createdUserIds.add(user.getId());

            Address address = new Address();
            address.setUserId(user.getId());
            address.setReceiver("R" + i);
            address.setPhone("13800000000");
            address.setProvince("P");
            address.setCity("C");
            address.setDistrict("D");
            address.setDetail("Detail");
            address.setIsDefault(1);
            addressMapper.insert(address);
            addressIds.add(address.getId());

            CartItem cartItem = new CartItem();
            cartItem.setUserId(user.getId());
            cartItem.setProductId(product.getId());
            cartItem.setQuantity(1);
            cartItem.setSelected(1);
            cartItemMapper.insert(cartItem);
        }

        // 所有人卡在起跑线上，一声令下同时下单
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(BUYERS);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < BUYERS; i++) {
            Long userId = createdUserIds.get(i);
            Long addressId = addressIds.get(i);
            pool.submit(() -> {
                try {
                    start.await();
                    // 每个买家各领一个令牌 —— 这测的是并发抢库存，不是重复提交
                    String submitToken = orderService.issueToken(userId);
                    orderService.create(userId, new CreateOrderRequest(addressId, submitToken, null));
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    // 库存不足是预期结果之一
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        Product after = productMapper.selectById(product.getId());

        // 核心断言：卖的 + 剩的 == 初始库存，且库存没有变成负数
        assertThat(succeeded.get()).isEqualTo(STOCK);
        assertThat(rejected.get()).isEqualTo(BUYERS - STOCK);
        assertThat(after.getStock()).isZero();
        assertThat(after.getSales()).isEqualTo(STOCK);

        // 数据库里的订单数也要对得上。
        // 只数本次测试这些买家的 —— 数全表的话，库里只要有别的订单这条就恒真，等于没测
        long orderCount = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .in(Order::getUserId, createdUserIds));
        assertThat(orderCount).isGreaterThanOrEqualTo(STOCK);
    }

    /**
     * ⚠️ **只删本次测试造出来的那些行。**
     *
     * 这里原来写的是 `orderMapper.delete(null)`、`cartItemMapper.delete(null)` 这种 ——
     * MyBatis-Plus 里 **Wrapper 传 null 等于没有 where 条件**，也就是 `DELETE FROM 整张表`。
     * 本地开发库和测试库是同一个，跑一次测试就会把开发时下的订单、加过的购物车、
     * 存过的收货地址一并抹掉，而且很难往「是测试干的」上想。
     */
    @AfterEach
    void cleanup() {
        if (!createdUserIds.isEmpty()) {
            List<Long> orderIds = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                            .in(Order::getUserId, createdUserIds))
                    .stream().map(Order::getId).toList();
            if (!orderIds.isEmpty()) {
                orderItemMapper.delete(Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId, orderIds));
                orderMapper.delete(Wrappers.<Order>lambdaQuery().in(Order::getId, orderIds));
            }
            cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().in(CartItem::getUserId, createdUserIds));
            addressMapper.delete(Wrappers.<Address>lambdaQuery().in(Address::getUserId, createdUserIds));
        }
        if (createdProductId != null) {
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
