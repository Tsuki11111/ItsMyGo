package com.tsuk1.itsmygo.dto.eval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新建自建评测用例。
 *
 * gold_chunks 用字符串接收：Milvus 主键是 18 位 INT64，超过 JavaScript 安全整数范围；
 * 前端按文本收集，Java 再转 Long，避免精度丢失。
 */
public record AgentEvalCaseCreateRequest(
        @Size(max = 64, message = "用例 id 最长 64 个字符")
        String id,
        @NotBlank(message = "请输入问题")
        @Size(max = 2000, message = "问题最长 2000 个字符")
        String question,
        @NotBlank(message = "请选择用例类型")
        String category,
        @Size(max = 300, message = "文档名最长 300 个字符")
        String doc,
        @Size(max = 2000, message = "上一轮问题最长 2000 个字符")
        String context,
        @Pattern(regexp = "real|constructed", message = "source 只能是 real 或 constructed")
        String source,
        @Size(max = 100, message = "gold_chunks 最多 100 个")
        List<String> goldChunks,
        String expectedOutcome) {

    public AgentEvalCaseCreateRequest {
        id = id == null ? "" : id.trim();
        question = question == null ? "" : question.trim();
        category = category == null ? "" : category.trim();
        doc = doc == null ? "" : doc.trim();
        context = context == null ? "" : context.trim();
        source = source == null || source.isBlank() ? "constructed" : source;
        goldChunks = goldChunks == null ? List.of() : List.copyOf(goldChunks);
        expectedOutcome = expectedOutcome == null ? "answered" : expectedOutcome;
    }
}
