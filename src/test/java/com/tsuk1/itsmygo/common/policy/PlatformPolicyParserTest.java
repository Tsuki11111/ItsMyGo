package com.tsuk1.itsmygo.common.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformPolicyParserTest {

    @Test
    void parsesMarkdownTitleAndHeadings() {
        String markdown = """
                # 测试政策
                > 生效日期：2026-11-03

                前言正文

                ## 一、总则
                总则正文

                ### 1. 适用范围
                范围正文

                ## 二、退款
                退款正文
                """;

        PlatformPolicyParser.ParsedPolicy parsed =
                PlatformPolicyParser.parse(markdown, "兜底标题");

        assertThat(parsed.title()).isEqualTo("测试政策");
        assertThat(parsed.sections()).extracting(PlatformPolicyParser.ParsedSection::heading)
                .containsExactly("总则", "一、总则", "1. 适用范围", "二、退款");
        assertThat(parsed.sections()).extracting(PlatformPolicyParser.ParsedSection::sectionNo)
                .containsExactly(1, 2, 3, 4);
        assertThat(parsed.sections().get(2).content()).isEqualTo("范围正文");
    }
}
