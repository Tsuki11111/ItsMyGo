package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.Review;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserFavorite;
import com.tsuk1.itsmygo.entity.UserFootprint;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ReviewMapper;
import com.tsuk1.itsmygo.mapper.UserFavoriteMapper;
import com.tsuk1.itsmygo.mapper.UserFootprintMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户画像：聚合口径、冷启动、票据隔离与降级。
 *
 * <p>直接插订单/订单项/足迹/收藏/评价造数；缓存按 userId 区分，不跨用例复用。</p>
 */
class UserProfileTest extends ApiTestBase {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserFootprintMapper footprintMapper;

    @Autowired
    private UserFavoriteMapper favoriteMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private AgentToolTicketService ticketService;

    @Test
    void profileAggregatesOrdersCategoriesBrandsAndPriceBand() throws Exception {
        String name = "prof" + System.nanoTime();
        customerToken(name);
        long userId = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, name)).getId();

        Category category = category("办公设备" + name);
        Merchant merchant = merchant("画像店铺" + name);
        Product product = product(category.getId(), merchant.getId(), "画像商品" + name);

        Order first = insertOrder(userId, merchant.getId(), "PAID", "120.00", "20.00",
                LocalDateTime.of(2026, 11, 1, 10, 0));
        Order second = insertOrder(userId, merchant.getId(), "COMPLETED", "80.00", "0.00",
                LocalDateTime.of(2026, 11, 5, 10, 0));
        OrderItem firstItem = insertItem(first.getId(), product, "60.00", "120.00");
        insertItem(second.getId(), product, "40.00", "80.00");

        UserFootprint footprint = new UserFootprint();
        footprint.setUserId(userId);
        footprint.setProductId(product.getId());
        footprint.setViewedAt(LocalDateTime.now());
        footprint.setCreatedAt(LocalDateTime.now());
        footprintMapper.insert(footprint);

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(product.getId());
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);

        Review review = new Review();
        review.setOrderItemId(firstItem.getId());
        review.setOrderId(first.getId());
        review.setMerchantId(merchant.getId());
        review.setSkuId(0L);
        review.setUserId(userId);
        review.setProductId(product.getId());
        review.setRating(5);
        review.setContent("很好");
        review.setStatus(1);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        mockMvc.perform(get("/internal/agent/profile")
                        .header("X-Agent-Ticket", ticketService.issue(userId, "sess-profile")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.coldStart").value(false))
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.topCategories[0].categoryId").value(category.getId()))
                .andExpect(jsonPath("$.data.topCategories[0].name").value(category.getName()))
                .andExpect(jsonPath("$.data.topCategories[0].count").value(2))
                .andExpect(jsonPath("$.data.topBrands[0].name").value("H3C"))
                .andExpect(jsonPath("$.data.priceBand.min").value(40.0))
                .andExpect(jsonPath("$.data.priceBand.max").value(60.0))
                .andExpect(jsonPath("$.data.recentViews[0].categoryId").value(category.getId()))
                .andExpect(jsonPath("$.data.favoriteCategories[0].categoryId").value(category.getId()))
                .andExpect(jsonPath("$.data.avgRating").value(5.0));
    }

    @Test
    void newUserIsColdStart() throws Exception {
        String name = "cold" + System.nanoTime();
        customerToken(name);
        long userId = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, name)).getId();

        mockMvc.perform(get("/internal/agent/profile")
                        .header("X-Agent-Ticket", ticketService.issue(userId, "sess-profile")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.coldStart").value(true))
                .andExpect(jsonPath("$.data.orderCount").value(0))
                .andExpect(jsonPath("$.data.topCategories.length()").value(0));
    }

    @Test
    void profileRequiresTicket() throws Exception {
        mockMvc.perform(get("/internal/agent/profile"))
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/internal/agent/profile")
                        .header("X-Agent-Ticket", "not-a-real-ticket"))
                .andExpect(jsonPath("$.code").value(401));
    }

    private Category category(String name) {
        Category category = new Category();
        category.setParentId(0L);
        category.setName(name);
        category.setSort(0);
        category.setStatus(1);
        categoryMapper.insert(category);
        return category;
    }

    private Merchant merchant(String name) {
        Merchant merchant = new Merchant();
        merchant.setName(name);
        merchant.setLogo("");
        merchant.setDescription("");
        merchant.setContactName("店主");
        merchant.setContactPhone("13800000000");
        merchant.setStatus(Merchant.ACTIVE);
        merchantMapper.insert(merchant);
        return merchant;
    }

    private Product product(long categoryId, long merchantId, String name) {
        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setMerchantId(merchantId);
        product.setName(name);
        product.setBrand("H3C");
        product.setSubtitle("");
        product.setMainImage("");
        product.setPrice(new BigDecimal("99.00"));
        product.setStock(10);
        product.setSales(0);
        product.setHasSpecs(0);
        product.setStatus(1);
        productMapper.insert(product);
        return product;
    }

    private Order insertOrder(long userId, long merchantId, String status,
                              String total, String refunded, LocalDateTime paidAt) {
        Order order = new Order();
        order.setOrderNo("PROF" + System.nanoTime());
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setTotalAmount(new BigDecimal(total));
        order.setOriginalAmount(new BigDecimal(total));
        order.setRefundedAmount(new BigDecimal(refunded));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setStatus(status);
        order.setReceiver("测试用户");
        order.setPhone("13800000000");
        order.setAddress("测试地址");
        order.setPaidAt(paidAt);
        order.setCreatedAt(paidAt);
        orderMapper.insert(order);
        return order;
    }

    private OrderItem insertItem(long orderId, Product product, String unitPrice, String subtotal) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImage("");
        item.setSpecText("");
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal(subtotal));
        item.setCreatedAt(LocalDateTime.now());
        orderItemMapper.insert(item);
        return item;
    }
}
