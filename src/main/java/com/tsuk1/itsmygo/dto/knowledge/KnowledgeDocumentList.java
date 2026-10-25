package com.tsuk1.itsmygo.dto.knowledge;

import java.util.List;

public record KnowledgeDocumentList(long total, List<KnowledgeDocumentView> documents) {
}
