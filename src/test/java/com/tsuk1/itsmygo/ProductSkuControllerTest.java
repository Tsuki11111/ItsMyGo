package com.tsuk1.itsmygo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.dto.CategoryRequest;
import com.tsuk1.itsmygo.dto.ProductRequest;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.entity.ProductSku;
import com.tsuk1.itsmygo.entity.ProductSpec;
import com.tsuk1.itsmygo.entity.ProductSpecValue;
import com.tsuk1.itsmygo.mapper.ProductMapper;
import com.tsuk1.itsmygo.mapper.ProductSkuMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecMapper;
import com.tsuk1.itsmygo.mapper.ProductSpecValueMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理端的规格与 SKU 编辑接口（整组覆盖） */
class ProductSkuControllerTest extends ApiTestBase {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper skuMapper;

    @Autowired
    private ProductSpecMapper specMapper;

    @Autowired
    private ProductSpecValueMapper valueMapper;

    private long createProduct(String adminToken, String name, String price, int stock) throws Exception {
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
                                categoryId, name, "sub", "", new BigDecimal(price), stock, "", true))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private List<ProductSku> skusOf(long productId) {
        return skuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
    }

    private void saveSkus(String adminToken, long productId, String json) throws Exception {
        mockMvc.perform(put("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 键鼠套装那种：颜色（黑/白）× 版本（标准/静音）。两个规格都是新建 */
    private String twoSpecJson(String colorValues, String versionValues, String skusJson) {
        return """
                {
                  "specs": [
                    {"name": "颜色", "values": [%s]},
                    {"name": "版本", "values": [%s]}
                  ],
                  "skus": [%s]
                }
                """.formatted(colorValues, versionValues, skusJson);
    }

    /**
     * 同上，但带上已有规格的 id —— **二次保存时必须带**。
     * 不带的话服务端会认为「请求里没有这两个规格」，把它们连同规格值一起删掉重建，
     * 于是值的 id 全变，SKU 也就匹配不到原来那行了（库存跟着没）。
     */
    private String twoSpecJson(long colorSpecId, String colorValues,
                               long versionSpecId, String versionValues, String skusJson) {
        return """
                {
                  "specs": [
                    {"id": %d, "name": "颜色", "values": [%s]},
                    {"id": %d, "name": "版本", "values": [%s]}
                  ],
                  "skus": [%s]
                }
                """.formatted(colorSpecId, colorValues, versionSpecId, versionValues, skusJson);
    }

    /**
     * 每个商品都自带一条**默认 SKU**（无规格）—— 这是「库存的真相在 SKU 上」这条规矩的前提。
     * 少了它，详情页会显示总库存 0、下单永远报「库存不足」。
     */
    @Test
    void newProductAlwaysGetsADefaultSku() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProduct(admin, "默认Sku", "10.00", 7);

        List<ProductSku> skus = skusOf(productId);
        assertThat(skus).hasSize(1);
        assertThat(skus.get(0).getSpecValueIds()).isEmpty();
        assertThat(skus.get(0).getStock()).isEqualTo(7);
    }

    /** 新建的规格值用负数临时 id 提交，服务端插入后要把 SKU 的引用改写过来 */
    @Test
    void saveSpecsAndSkusWithTemporaryIds() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProduct(admin, "双规格", "100.00", 0);

        saveSkus(admin, productId, twoSpecJson(
                """
                {"id": -1, "value": "黑色"}, {"id": -2, "value": "白色"}""",
                """
                {"id": -3, "value": "标准版"}, {"id": -4, "value": "静音版"}""",
                """
                {"specValueIds": [-1, -3], "price": 129.00, "stock": 5, "image": ""},
                {"specValueIds": [-2, -4], "price": 149.00, "stock": 3, "image": ""}"""));

        // 规格值真的落库了，而且两条 SKU 引用的是不同的值
        List<ProductSpec> specs = specMapper.selectList(Wrappers.<ProductSpec>lambdaQuery()
                .eq(ProductSpec::getProductId, productId).orderByAsc(ProductSpec::getSort));
        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).getName()).isEqualTo("颜色");
        assertThat(specs.get(1).getName()).isEqualTo("版本");

        long valueCount = valueMapper.selectCount(Wrappers.<ProductSpecValue>lambdaQuery()
                .in(ProductSpecValue::getSpecId, specs.stream().map(ProductSpec::getId).toList()));
        assertThat(valueCount).isEqualTo(4);

        List<ProductSku> skus = skusOf(productId);
        assertThat(skus).hasSize(2);
        // 组合键是升序拼接的；临时 id 已经被换成真实 id
        assertThat(skus.get(0).getSpecValueIds()).matches("\\d+,\\d+");
        assertThat(skus.get(0).getSpecValueIds()).doesNotContain("-");
        assertThat(skus.get(0).getSpecText()).isEqualTo("颜色:黑色;版本:标准版");
        assertThat(skus.get(1).getSpecText()).isEqualTo("颜色:白色;版本:静音版");
    }

    /** 商品的 price / stock / has_specs 是 SKU 的汇总，存完要跟着重算 */
    @Test
    void productAggregatesFollowTheSkus() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProduct(admin, "汇总", "100.00", 0);

        saveSkus(admin, productId, """
                {
                  "specs": [{"name": "长度", "values": [{"id": -1, "value": "1米"}, {"id": -2, "value": "2米"}]}],
                  "skus": [
                    {"specValueIds": [-1], "price": 29.00, "stock": 200, "image": ""},
                    {"specValueIds": [-2], "price": 39.00, "stock": 150, "image": ""}
                  ]
                }
                """);

        Product product = productMapper.selectById(productId);
        assertThat(product.getPrice()).isEqualByComparingTo("29.00");   // 最低价
        assertThat(product.getStock()).isEqualTo(350);                  // 求和
        assertThat(product.getHasSpecs()).isEqualTo(1);                 // 列表页据此显示「起」
    }

    /**
     * **这条是整组覆盖里最容易出事的地方**：商户改完价格再保存一次，
     * 请求里带的 stock 是表单上的旧数字，**不能**拿它去覆盖真实库存 ——
     * 否则一次改价就把库存打回解放前。
     */
    @Test
    void resavingKeepsExistingStock() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProduct(admin, "保库存", "100.00", 0);

        // ① 配好规格：黑 5 件、白 3 件
        saveSkus(admin, productId, twoSpecJson(
                """
                {"id": -1, "value": "黑色"}, {"id": -2, "value": "白色"}""",
                """
                {"id": -3, "value": "均码"}""",
                """
                {"specValueIds": [-1, -3], "price": 129.00, "stock": 5, "image": ""},
                {"specValueIds": [-2, -3], "price": 139.00, "stock": 3, "image": ""}"""));

        List<ProductSku> afterCreate = skusOf(productId);
        assertThat(afterCreate).hasSize(2);

        // ② 再存一次：价格全改，stock 故意填一个离谱的值（模拟表单里那份没跟着下单更新的旧快照）
        long black = specValueId(productId, "颜色", "黑色");
        long white = specValueId(productId, "颜色", "白色");
        long oneSize = specValueId(productId, "版本", "均码");
        saveSkus(admin, productId, twoSpecJson(
                specId(productId, "颜色"),
                """
                {"id": %d, "value": "黑色"}, {"id": %d, "value": "白色"}""".formatted(black, white),
                specId(productId, "版本"),
                """
                {"id": %d, "value": "均码"}""".formatted(oneSize),
                """
                {"specValueIds": [%d, %d], "price": 299.00, "stock": 9999, "image": ""},
                {"specValueIds": [%d, %d], "price": 309.00, "stock": 9999, "image": ""}"""
                        .formatted(black, oneSize, white, oneSize)));

        // ③ 价格变了、SKU 没有重建（id 不变）、**库存一分没动**
        List<ProductSku> afterResave = skusOf(productId);
        assertThat(afterResave).hasSize(2);
        assertThat(afterResave.get(0).getId()).isEqualTo(afterCreate.get(0).getId());
        assertThat(afterResave.get(1).getId()).isEqualTo(afterCreate.get(1).getId());
        assertThat(afterResave.get(0).getPrice()).isEqualByComparingTo("299.00");
        assertThat(afterResave.get(0).getStock()).isEqualTo(5);
        assertThat(afterResave.get(1).getStock()).isEqualTo(3);
    }

    /** 同一个规格组合提交两次 —— 唯一索引兜底之前，先在内存里挡掉并给出人话 */
    @Test
    void duplicateCombinationIsRejected() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProduct(admin, "重复组合", "10.00", 0);

        mockMvc.perform(put("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "specs": [{"name": "颜色", "values": [{"id": -1, "value": "黑"}]}],
                                  "skus": [
                                    {"specValueIds": [-1], "price": 10.00, "stock": 1, "image": ""},
                                    {"specValueIds": [-1], "price": 20.00, "stock": 1, "image": ""}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("存在重复的规格组合"));
    }

    /** 每个 SKU 必须为每组规格各选一个值，少选了要拦住 */
    @Test
    void skuMustPickOneValuePerSpec() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        long productId = createProduct(admin, "漏选", "10.00", 0);

        mockMvc.perform(put("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoSpecJson(
                                """
                                {"id": -1, "value": "黑色"}""",
                                """
                                {"id": -2, "value": "均码"}""",
                                """
                                {"specValueIds": [-1], "price": 10.00, "stock": 1, "image": ""}""")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("每个 SKU 要为每组规格各选一个值"));
    }

    /** 顾客不能碰管理端的规格接口 */
    @Test
    void customerCannotEditSkus() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        String customer = customerToken("c" + System.nanoTime());
        long productId = createProduct(admin, "越权", "10.00", 5);

        mockMvc.perform(put("/api/admin/products/" + productId + "/skus")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"specs": [], "skus": [{"specValueIds": [], "price": 1.00, "stock": 1, "image": ""}]}
                                """))
                .andExpect(status().isForbidden());
    }

    /** 查某个商品某个规格的 id */
    private long specId(long productId, String name) {
        return specMapper.selectList(Wrappers.<ProductSpec>lambdaQuery()
                        .eq(ProductSpec::getProductId, productId)
                        .eq(ProductSpec::getName, name))
                .stream().findFirst().map(ProductSpec::getId).orElseThrow();
    }

    /** 查某个商品某规格下某个值的 id —— 二次提交时用来回填真实 id，避免测试里写死自增 id */
    private long specValueId(long productId, String specName, String value) {
        return valueMapper.selectList(Wrappers.<ProductSpecValue>lambdaQuery()
                        .eq(ProductSpecValue::getSpecId, specId(productId, specName))
                        .eq(ProductSpecValue::getValue, value))
                .stream().findFirst().map(ProductSpecValue::getId).orElseThrow();
    }
}
