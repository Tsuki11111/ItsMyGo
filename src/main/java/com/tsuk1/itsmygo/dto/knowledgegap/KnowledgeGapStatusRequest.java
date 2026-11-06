package com.tsuk1.itsmygo.dto.knowledgegap;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 回写缺口状态：resolved（已补）/ ignored（不补）/ published（草稿已提交导入） */
public record KnowledgeGapStatusRequest(
        @NotEmpty(message = "请至少选择一个缺口")
        @Size(max = 100, message = "单次最多 100 个缺口")
        List<String> gapIds,
        @NotEmpty(message = "请选择状态")
        @Pattern(regexp = "pending|published|resolved|ignored", message = "状态取值非法")
        String status,
        @Size(max = 200, message = "备注最长 200 个字符")
        String note) {

    public KnowledgeGapStatusRequest {
        gapIds = gapIds == null ? List.of() : List.copyOf(gapIds);
        note = note == null ? "" : note.trim();
    }
}
