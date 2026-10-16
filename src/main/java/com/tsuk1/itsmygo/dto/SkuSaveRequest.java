package com.tsuk1.itsmygo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端保存商品的规格与 SKU —— **整组覆盖**。
 *
 * 为什么不是逐个 CRUD：规格值一改，SKU 组合的集合就变了（加一个「颜色」规格，
 * SKU 数量直接翻倍），逐个接口表达不了这件事；商户在后台也是一次填完一整张表再提交。
 *
 * ⚠️ **新建的规格值请用负数当临时 id**（如 -1、-2）。
 * 因为新值在提交时还没有数据库 id，而 SKU 又要引用它 ——
 * 服务端插入后会建立「临时 id → 真实 id」的映射，把 SKU 里的引用一并改写。
 * 已有的规格和规格值请传**真实 id**：传对了，服务端才能按规格值组合匹配到原来那行 SKU，
 * **把库存保住**；传错了（当成新建）就会删旧建新，库存归零。
 */
public record SkuSaveRequest(
        @NotNull(message = "规格列表不能为空")
        @Valid
        List<SpecPayload> specs,

        @NotNull(message = "SKU 列表不能为空")
        @Valid
        List<SkuPayload> skus
) {
    public record SpecPayload(
            /** 已有规格传真实 id；新建传 null */
            Long id,

            @NotBlank(message = "规格名不能为空")
            @Size(max = 32, message = "规格名最长 32 个字符")
            String name,

            @NotEmpty(message = "每组规格至少要有一个值")
            @Valid
            List<ValuePayload> values
    ) {
    }

    public record ValuePayload(
            /** 已有值传真实 id；新建传**负数临时 id**（见类注释） */
            @NotNull(message = "规格值 id 不能为空，新建的请传负数临时 id")
            Long id,

            @NotBlank(message = "规格值不能为空")
            @Size(max = 64, message = "规格值最长 64 个字符")
            String value
    ) {
    }

    public record SkuPayload(
            /**
             * 规格值 id，**每组规格恰好一个**；新值用负数临时 id 引用。
             * 商品没有规格时传**空数组**（那就只剩一条默认 SKU）。
             */
            @NotNull(message = "SKU 的规格值组合不能为 null，无规格时请传空数组")
            List<Long> specValueIds,

            @NotNull(message = "SKU 价格不能为空")
            @DecimalMin(value = "0.00", message = "价格不能为负数")
            BigDecimal price,

            /**
             * **只在新建这个 SKU 时生效**。
             * 已存在的 SKU 会忽略这个值 —— 否则商户改个价格就把库存覆盖了，
             * 而库存该由下单和取消来动。
             */
            @NotNull(message = "库存不能为空")
            @Min(value = 0, message = "库存不能为负数")
            Integer stock,

            @Size(max = 512, message = "规格图地址最长 512 个字符")
            String image
    ) {
    }
}
