package com.tsuk1.itsmygo.dto.knowledgegap;

import java.util.List;

/**
 * 一个知识缺口（聚合后）。
 *
 * gapId 是 rag_helper 侧对「归一化问题」算出的稳定 id，状态（resolved/ignored/published）
 * 挂在这个 id 上，所以前端提交状态时必须原样回传，不能自己按问题文本重新生成。
 */
public record KnowledgeGapView(
        String gapId,
        String question,
        long count,
        String firstAt,
        String lastAt,
        List<String> products,
        long local,
        long web,
        String status,
        String statusNote) {
}
