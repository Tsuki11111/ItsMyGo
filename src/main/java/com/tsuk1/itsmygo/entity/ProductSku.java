package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU：一个规格组合一行，**价格和库存的真相都在这里**。
 * {@code products} 上的 price / stock 只是它的冗余汇总。
 */
@Data
@TableName("product_skus")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /**
     * 升序逗号分隔的规格值 id，如 {@code "11,12"}。
     * 空串表示无规格商品的**默认 SKU**（唯一约束保证每个商品只有一条）。
     */
    private String specValueIds;

    /** 如「款式:云宝;颜色:白」。下单时快照进订单，规格改名或删除都不影响历史订单 */
    private String specText;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    /** 规格图，空则回落到商品主图 */
    private String image;

    /** 规格图的列表缩略图；空串表示未生成，前端回落到 image */
    private String thumbImage;

    /** 1 在售 / 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
