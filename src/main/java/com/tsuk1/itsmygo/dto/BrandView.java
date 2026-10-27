package com.tsuk1.itsmygo.dto;

import lombok.Data;

/** 品牌专区：一个品牌名 + 在售商品数 + 由后端生成的可访问 Logo URL */
@Data
public class BrandView {
    private String name;
    private Long count;
    /** 空串表示该品牌没有配置 Logo，前端回落到首字色块 */
    private String logoUrl;
}
