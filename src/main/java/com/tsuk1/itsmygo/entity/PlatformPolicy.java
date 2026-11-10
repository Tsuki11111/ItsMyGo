package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台政策主表：政策原文以本表为准，向量库只做章节召回。
 */
@Data
@TableName("platform_policy")
public class PlatformPolicy {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 政策短标识，如 terms/privacy/refund/merchant */
    private String policyKey;

    private String title;

    private String category;

    private String version;

    /** ACTIVE / ARCHIVED */
    private String status;

    private String contentMd;

    private LocalDateTime effectiveAt;

    private LocalDateTime updatedAt;
}
