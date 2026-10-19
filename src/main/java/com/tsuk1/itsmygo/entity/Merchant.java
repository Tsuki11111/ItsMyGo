package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商户/店铺。默认商户 id=1 为平台自营，owner_user_id 为 NULL。 */
@Data
@TableName("merchants")
public class Merchant {

    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String REJECTED = "REJECTED";
    public static final String DISABLED = "DISABLED";

    /** 平台自营默认商户 */
    public static final long PLATFORM_MERCHANT_ID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String logo;

    private String description;

    private String contactName;

    private String contactPhone;

    private String status;

    /** 店主用户；默认商户为 NULL */
    private Long ownerUserId;

    private String rejectReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
