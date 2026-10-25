package com.tsuk1.itsmygo.dto.knowledge;

/** 已导入知识库文档（与 rag_helper /documents 对齐） */
public record KnowledgeDocumentView(
        String fileTitle,
        String itemName,
        long chunkCount,
        String importedAt,
        String status,
        long kgEntities) {
}
