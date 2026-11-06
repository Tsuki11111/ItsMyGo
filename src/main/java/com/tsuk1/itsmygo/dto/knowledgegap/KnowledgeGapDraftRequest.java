package com.tsuk1.itsmygo.dto.knowledgegap;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 生成 FAQ 草稿请求。
 *
 * 单次最多 20 个缺口：草稿会走一次模型调用，堆太多既慢又容易糊在一起。
 */
public record KnowledgeGapDraftRequest(
        @NotEmpty(message = "请至少选择一个缺口")
        @Size(max = 20, message = "单次最多选择 20 个缺口")
        List<String> gapIds,
        Double days,
        @Size(max = 100, message = "标题最长 100 个字符")
        String title) {

    public KnowledgeGapDraftRequest {
        gapIds = gapIds == null ? List.of() : List.copyOf(gapIds);
        days = days == null ? 7.0 : days;
        title = title == null ? "" : title.trim();
    }
}
