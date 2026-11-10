package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 政策章节：按 Markdown 的 ## / ### 切分，供精确引用与向量召回。
 */
@Data
@TableName("platform_policy_section")
public class PlatformPolicySection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long policyId;

    private Integer sectionNo;

    private String heading;

    private String content;
}
