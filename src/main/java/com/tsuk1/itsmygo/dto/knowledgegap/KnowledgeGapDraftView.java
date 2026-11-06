package com.tsuk1.itsmygo.dto.knowledgegap;

import java.util.List;

/**
 * FAQ 草稿：**未入库**。
 *
 * note 里固定写明「必须人工核实」，发布要再调 /knowledge-gaps/publish。
 */
public record KnowledgeGapDraftView(
        String title,
        String markdown,
        List<String> gapIds,
        String note) {
}
