package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.agent.AgentToolTicketService;
import com.tsuk1.itsmygo.entity.Category;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.Order;
import com.tsuk1.itsmygo.entity.OrderItem;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.CategoryMapper;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.OrderItemMapper;
import com.tsuk1.itsmygo.mapper.OrderMapper;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人消费报告：聚合口径、用户隔离、参数校验与内部票据接口。
 *
 * <p>直接插订单/订单项造数（商品、店铺、分类的外键在库里并不强制），
 * 全部在 {@link ApiTestBase} 的事务里回滚。</p>
 */
class ConsumptionReportTest extends ApiTestBase {

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
    private AgentToolTicketService ticketService;

    /** 一次造好一套品类 / 店铺 / 商品，返回它们的 id。 */
    private Catalog seedCatalog() {
        String suffix = String.valueOf(System.nanoTime());

        Category office = new Category();
        office.setParentId(0L);
        office.setName("办公设备" + suffix);
        office.setSort(0);
        office.setStatus(1);
        categoryMapper.insert(office);

        Category digital = new Category();
        digital.setParentId(0L);
        digital.setName("数码配件" + suffix);
        digital.setSort(0);
        digital.setStatus(1);
        categoryMapper.insert(digital);

        Merchant shopOne = merchant("测试店铺一" + suffix);
        Merchant shopTwo = merchant("测试店铺二" + suffix);

        Product productOne = product(office.getId(), shopOne.getId(), "数字万用表" + suffix);
        Product productTwo = product(digital.getId(), shopTwo.getId(), "数据线" + suffix);

        return new Catalog(shopOne.getId(), shopTwo.getId(),
                productOne.getId(), productTwo.getId(),
                office.getName(), digital.getName(),
                shopOne.getName(), shopTwo.getName(),
                productOne.getName(), productTwo.getName());
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
        product.setBrand("");
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

    private long insertOrder(long userId, long merchantId, String status, String total,
                             String refunded, String discount, LocalDateTime paidAt) {
        Order order = new Order();
        order.setOrderNo("RPT" + System.nanoTime());
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setTotalAmount(new BigDecimal(total));
        order.setOriginalAmount(new BigDecimal(total).add(new BigDecimal(discount)));
        order.setDiscountAmount(new BigDecimal(discount));
        order.setRefundedAmount(new BigDecimal(refunded));
        order.setCouponName("");
        order.setStatus(status);
        order.setReceiver("张三");
        order.setPhone("13800000000");
        order.setAddress("杭州市西湖区文三路 1 号");
        order.setTrackingNo("");
        order.setPaidAt(paidAt);
        order.setCreatedAt(paidAt.minusHours(1));
        orderMapper.insert(order);
        return order.getId();
    }

    private void insertItem(long orderId, long productId, String productName,
                            String unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setSkuId(1L);
        item.setProductName(productName);
        item.setProductImage("");
        item.setSpecText("");
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        item.setSubtotal(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        orderItemMapper.insert(item);
    }

    private long userId(String username) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username));
        return user.getId();
    }

    @Test
    void monthlyReportAggregatesRanksAndIsolatesUsers() throws Exception {
        String aName = "rpa" + System.nanoTime();
        String bName = "rpb" + System.nanoTime();
        String aToken = customerToken(aName);
        customerToken(bName);
        long aId = userId(aName);
        long bId = userId(bName);
        Catalog catalog = seedCatalog();

        // A：11 月两笔（一笔部分退款），外加一笔 10 月、一笔已取消（都不进 11 月）
        long orderOne = insertOrder(aId, catalog.shopOneId(), "PAID",
                "199.00", "0.00", "20.00", LocalDateTime.of(2026, 11, 5, 10, 0));
        insertItem(orderOne, catalog.productOneId(), catalog.productOneName(), "99.50", 2);
        long orderTwo = insertOrder(aId, catalog.shopTwoId(), "COMPLETED",
                "159.00", "59.00", "0.00", LocalDateTime.of(2026, 11, 20, 10, 0));
        insertItem(orderTwo, catalog.productTwoId(), catalog.productTwoName(), "159.00", 1);
        long october = insertOrder(aId, catalog.shopOneId(), "PAID",
                "100.00", "0.00", "0.00", LocalDateTime.of(2026, 10, 10, 10, 0));
        insertItem(october, catalog.productOneId(), catalog.productOneName(), "100.00", 1);
        long cancelled = insertOrder(aId, catalog.shopOneId(), "CANCELLED",
                "1000.00", "0.00", "0.00", LocalDateTime.of(2026, 11, 6, 10, 0));
        insertItem(cancelled, catalog.productOneId(), catalog.productOneName(), "1000.00", 1);

        // B：同月一笔更大金额，绝不能混进 A 的报告
        long bOrder = insertOrder(bId, catalog.shopOneId(), "PAID",
                "999.00", "0.00", "0.00", LocalDateTime.of(2026, 11, 7, 10, 0));
        insertItem(bOrder, catalog.productOneId(), catalog.productOneName(), "999.00", 1);

        mockMvc.perform(get("/api/reports/consumption")
                        .header("Authorization", bearer(aToken))
                        .param("period", "month")
                        .param("date", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.label").value("2026年11月"))
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.paidAmount").value("358.00"))
                .andExpect(jsonPath("$.data.refundedAmount").value("59.00"))
                .andExpect(jsonPath("$.data.netAmount").value("299.00"))
                .andExpect(jsonPath("$.data.discountAmount").value("20.00"))
                .andExpect(jsonPath("$.data.avgOrderAmount").value("179.00"))
                .andExpect(jsonPath("$.data.categories.length()").value(2))
                .andExpect(jsonPath("$.data.categories[0].name").value(catalog.officeName()))
                .andExpect(jsonPath("$.data.categories[0].amount").value("199.00"))
                .andExpect(jsonPath("$.data.categories[0].count").value(2))
                .andExpect(jsonPath("$.data.categories[1].name").value(catalog.digitalName()))
                .andExpect(jsonPath("$.data.categories[1].amount").value("159.00"))
                .andExpect(jsonPath("$.data.merchants.length()").value(2))
                .andExpect(jsonPath("$.data.merchants[0].name").value(catalog.shopOneName()))
                .andExpect(jsonPath("$.data.merchants[0].amount").value("199.00"))
                .andExpect(jsonPath("$.data.merchants[1].name").value(catalog.shopTwoName()))
                .andExpect(jsonPath("$.data.merchants[1].amount").value("159.00"))
                .andExpect(jsonPath("$.data.topProducts.length()").value(2))
                .andExpect(jsonPath("$.data.topProducts[0].name").value(catalog.productOneName()))
                .andExpect(jsonPath("$.data.topProducts[0].amount").value("199.00"))
                .andExpect(jsonPath("$.data.topProducts[0].count").value(2))
                .andExpect(jsonPath("$.data.monthly.length()").value(0));
    }

    @Test
    void yearReportReturnsMonthlyTrend() throws Exception {
        String username = "rpy" + System.nanoTime();
        String token = customerToken(username);
        long uid = userId(username);
        Catalog catalog = seedCatalog();

        long january = insertOrder(uid, catalog.shopOneId(), "PAID",
                "50.00", "0.00", "0.00", LocalDateTime.of(2026, 1, 15, 10, 0));
        insertItem(january, catalog.productOneId(), catalog.productOneName(), "50.00", 1);
        long november = insertOrder(uid, catalog.shopTwoId(), "PAID",
                "358.00", "0.00", "0.00", LocalDateTime.of(2026, 11, 8, 10, 0));
        insertItem(november, catalog.productTwoId(), catalog.productTwoName(), "179.00", 2);

        mockMvc.perform(get("/api/reports/consumption")
                        .header("Authorization", bearer(token))
                        .param("period", "year")
                        .param("date", "2026"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.label").value("2026年"))
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.paidAmount").value("408.00"))
                .andExpect(jsonPath("$.data.monthly.length()").value(2))
                .andExpect(jsonPath("$.data.monthly[0].month").value("2026-01"))
                .andExpect(jsonPath("$.data.monthly[0].amount").value("50.00"))
                .andExpect(jsonPath("$.data.monthly[1].month").value("2026-11"))
                .andExpect(jsonPath("$.data.monthly[1].amount").value("358.00"));
    }

    @Test
    void emptyReportReturnsZerosAndEmptyArrays() throws Exception {
        String username = "rpe" + System.nanoTime();
        String token = customerToken(username);

        mockMvc.perform(get("/api/reports/consumption")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderCount").value(0))
                .andExpect(jsonPath("$.data.paidAmount").value("0.00"))
                .andExpect(jsonPath("$.data.refundedAmount").value("0.00"))
                .andExpect(jsonPath("$.data.netAmount").value("0.00"))
                .andExpect(jsonPath("$.data.discountAmount").value("0.00"))
                .andExpect(jsonPath("$.data.avgOrderAmount").value("0.00"))
                .andExpect(jsonPath("$.data.categories.length()").value(0))
                .andExpect(jsonPath("$.data.merchants.length()").value(0))
                .andExpect(jsonPath("$.data.topProducts.length()").value(0))
                .andExpect(jsonPath("$.data.monthly.length()").value(0));
    }

    @Test
    void invalidDateOrPeriodReturns400() throws Exception {
        String username = "rpv" + System.nanoTime();
        String token = customerToken(username);

        mockMvc.perform(get("/api/reports/consumption")
                        .header("Authorization", bearer(token))
                        .param("period", "month")
                        .param("date", "2026-13"))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/reports/consumption")
                        .header("Authorization", bearer(token))
                        .param("period", "month")
                        .param("date", "2026-1"))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/reports/consumption")
                        .header("Authorization", bearer(token))
                        .param("period", "year")
                        .param("date", "2026-11"))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/reports/consumption")
                        .header("Authorization", bearer(token))
                        .param("period", "quarter"))
                .andExpect(jsonPath("$.code").value(400));

        // 未登录走 Spring Security 的 401
        mockMvc.perform(get("/api/reports/consumption"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalReportRequiresTicketAndScopesToTicketUser() throws Exception {
        String aName = "rpa2" + System.nanoTime();
        String bName = "rpb2" + System.nanoTime();
        customerToken(aName);
        customerToken(bName);
        long aId = userId(aName);
        long bId = userId(bName);
        Catalog catalog = seedCatalog();

        long aOrder = insertOrder(aId, catalog.shopOneId(), "PAID",
                "199.00", "0.00", "0.00", LocalDateTime.of(2026, 11, 5, 10, 0));
        insertItem(aOrder, catalog.productOneId(), catalog.productOneName(), "199.00", 1);
        long bOrder = insertOrder(bId, catalog.shopOneId(), "PAID",
                "888.00", "0.00", "0.00", LocalDateTime.of(2026, 11, 6, 10, 0));
        insertItem(bOrder, catalog.productOneId(), catalog.productOneName(), "888.00", 1);

        // 无票据 / 假票据：鉴权先于业务
        mockMvc.perform(get("/internal/agent/reports/consumption")
                        .param("period", "month")
                        .param("date", "2026-11"))
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/internal/agent/reports/consumption")
                        .header("X-Agent-Ticket", "not-a-real-ticket")
                        .param("period", "month")
                        .param("date", "2026-11"))
                .andExpect(jsonPath("$.code").value(401));

        // A 的票据只返回 A 的数据
        mockMvc.perform(get("/internal/agent/reports/consumption")
                        .header("X-Agent-Ticket", ticketService.issue(aId, "sess-report"))
                        .param("period", "month")
                        .param("date", "2026-11"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.label").value("2026年11月"))
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.paidAmount").value("199.00"))
                .andExpect(jsonPath("$.data.topProducts[0].name")
                        .value(catalog.productOneName()));

        // B 的票据拿到的是 B 的金额，不是 A 的
        mockMvc.perform(get("/internal/agent/reports/consumption")
                        .header("X-Agent-Ticket", ticketService.issue(bId, "sess-report"))
                        .param("period", "month")
                        .param("date", "2026-11"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.paidAmount").value("888.00"));
    }

    private record Catalog(long shopOneId, long shopTwoId,
                           long productOneId, long productTwoId,
                           String officeName, String digitalName,
                           String shopOneName, String shopTwoName,
                           String productOneName, String productTwoName) {
    }
}
