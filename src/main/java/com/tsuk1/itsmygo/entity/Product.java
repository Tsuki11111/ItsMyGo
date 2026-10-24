package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("products")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商户；存量商品默认是 1（平台自营） */
    private Long merchantId;

    private Long categoryId;

    private String name;

    /** 品牌专区按它筛选；空串表示未归类 */
    private String brand;

    private String subtitle;

    private String mainImage;

    /** 列表页用的 400×400 缩略图；空串表示未生成，前端回落到 mainImage */
    private String thumbImage;

    /** 汇总值：在售 SKU 的最低价。单规格商品就是它自己的价格。真相在 {@code product_skus} */
    private BigDecimal price;

    /** 汇总值：在售 SKU 的库存之和。**库存的真相在 {@code product_skus} 上** */
    private Integer stock;

    /** 汇总值：各 SKU 销量之和 */
    private Integer sales;

    /** 1 有多规格 / 0 单规格。列表页靠它决定要不要显示「¥19.8 起」 */
    private Integer hasSpecs;

    private String detail;

    /** 1 上架 / 0 下架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ── 以下两个不是数据库列，只在商品详情里组装 ──

    /**
     * 规格定义（含各自的值）。**只在详情接口里有值**——
     * 配字段级 NON_NULL 是为了让列表页的 JSON 一个字节都不变。
     */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProductSpec> specs;

    /** SKU 列表，库存已换成 Redis 里的实时值。同上，只在详情里有值 */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProductSku> skus;
}
