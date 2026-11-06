package com.tsuk1.itsmygo.dto.knowledgegap;

/** 知识缺口周报（Markdown，平台管理端直接渲染） */
public record KnowledgeGapReportView(double days, String markdown) {
}
