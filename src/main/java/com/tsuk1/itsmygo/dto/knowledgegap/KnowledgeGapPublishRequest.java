package com.tsuk1.itsmygo.dto.knowledgegap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发布草稿：把人工核实过的 Markdown 送进 rag_helper 的导入链路。
 *
 * 只接受 Markdown（必须一级标题开头）；导入进度沿用既有 /api/admin/knowledge/tasks/{taskId}。
 */
public record KnowledgeGapPublishRequest(
        @NotBlank(message = "请填写文档标题")
        @Size(max = 200, message = "标题最长 200 个字符")
        String title,
        @NotBlank(message = "草稿内容不能为空")
        @Size(max = 200000, message = "草稿内容过长")
        String markdown,
        @Size(max = 100, message = "单次最多 100 个缺口")
        List<String> gapIds) {

    public KnowledgeGapPublishRequest {
        title = title == null ? "" : title.trim();
        gapIds = gapIds == null ? List.of() : List.copyOf(gapIds);
    }
}
