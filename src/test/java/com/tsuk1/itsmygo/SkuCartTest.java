package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.CartAddRequest;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.ProductSku;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 购物车挂到 SKU 之后的行为：同商品不同规格要能各占一行，跨商品引用要被挡住 */
class SkuCartTest extends ApiTestBase {

    private record ProductWithSkus(long productId, long blackSkuId, long whiteSkuId) {
    }

    private long createProduct(String adminToken, String name) throws Exception {
        String catBody = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest(null, "C" + name, 0, true))))
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(catBody).path("data").path("id").asLong();

        String body = mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                categoryId, name, "sub", "", new BigDecimal("10.00"), 0, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    /** 建一个「颜色：黑 / 白」两规格的商品，黑 5 件 19.90、白 3 件 29.90 */
    private ProductWithSkus createProductWithTwoSkus(String admin, String name) throws Exception {
        long productId = createProduct(admin, name);

        mockMvc.perform(put("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "specs": [{"name": "颜色",
                                             "values": [{"id": -1, "value": "黑色"}, {"id": -2, "value": "白色"}]}],
                                  "skus": [
                                    {"specValueIds": [-1], "price": 19.90, "stock": 5, "image": ""},
                                    {"specValueIds": [-2], "price": 29.90, "stock": 3, "image": ""}
                                  ]
                                }
                                """))
                .andExpect(jsonPath("$.code").value(200));

        List<ProductSku> skus = productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId));
        return new ProductWithSkus(productId,
                skuIdOf(skus, "黑色"), skuIdOf(skus, "白色"));
    }

    private long skuIdOf(List<ProductSku> skus, String color) {
        return skus.stream().filter(s -> s.getSpecText().contains(color))
                .findFirst().orElseThrow().getId();
    }

    private void addToCart(String token, long productId, long skuId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(productId, skuId, quantity))))
                .andExpect(status().isOk());
    }

    /**
     * 唯一键从「用户 + 商品」换成了「用户 + SKU」之后，**同一件商品的不同规格要能各占一行** ——
     * 这正是多规格购物车和以前单规格最大的区别。
     */
    @Test
    void differentSkusOfSameProductGetSeparateRows() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "两规格");

        addToCart(token, p.productId(), p.blackSkuId(), 1);
        addToCart(token, p.productId(), p.whiteSkuId(), 2);

        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // 同一个规格再加购 → 还是两行，只是那一行数量累加
        // （黑色变成 4 件：4×19.90 + 2×29.90 = 139.40）
        addToCart(token, p.productId(), p.blackSkuId(), 3);
        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.selectedTotal").value(139.40));
    }

    /** 每一行的小计要按**它自己那个规格**的价格算，不能都用商品的最低价 */
    @Test
    void subtotalUsesTheSkuPriceNotTheProductPrice() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "价格不同");

        addToCart(token, p.productId(), p.blackSkuId(), 2);   // 19.90 × 2 = 39.80
        addToCart(token, p.productId(), p.whiteSkuId(), 1);   // 29.90 × 1 = 29.90

        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.selectedTotal").value(69.70));
    }

    /** 拿别的商品的 skuId 来加购 —— 必须当「不存在」挡住，不能把两件商品拼在一起 */
    @Test
    void skuFromAnotherProductIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus first = createProductWithTwoSkus(admin, "甲");
        ProductWithSkus second = createProductWithTwoSkus(admin, "乙");

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(second.productId(), first.blackSkuId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 规格被删掉之后，购物车那一行**不隐藏**，而是标成不可购买 —— 让用户看得见、自己删 */
    @Test
    void cartRowBecomesUnavailableWhenItsSkuIsGone() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "删规格");

        addToCart(token, p.productId(), p.whiteSkuId(), 1);

        // 直接删库：模拟商户后来把「白色」这个规格值去掉了
        productSkuMapper.deleteById(p.whiteSkuId());

        mockMvc.perform(get("/api/cart").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].available").value(false))
                .andExpect(jsonPath("$.data.items[0].onSale").value(false))
                .andExpect(jsonPath("$.data.selectedTotal").value(0));
    }

    /** 停售的规格同样不能买（商品在售、但这一款停了） */
    @Test
    void stoppedSkuCannotBeAddedToCart() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String token = customerToken("c" + System.nanoTime());
        ProductWithSkus p = createProductWithTwoSkus(admin, "停售款");

        ProductSku white = productSkuMapper.selectById(p.whiteSkuId());
        white.setStatus(0);
        productSkuMapper.updateById(white);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CartAddRequest(p.productId(), p.whiteSkuId(), 1))))
                .andExpect(jsonPath("$.code").value(400));
    }
}
