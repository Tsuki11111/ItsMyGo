package com.tsuk1.itsmygo.dto.knowledgegap;

import java.util.List;

/** 知识缺口列表（按频次降序） */
public record KnowledgeGapListView(double days, long total, List<KnowledgeGapView> gaps) {
}
