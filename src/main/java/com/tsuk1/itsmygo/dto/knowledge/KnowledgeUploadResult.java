package com.tsuk1.itsmygo.dto.knowledge;

import java.util.List;

/** 知识库文档上传结果：受理的任务、重复文件、失败文件 */
public record KnowledgeUploadResult(
        String message,
        List<String> taskIds,
        List<Duplicate> duplicates,
        List<FailedFile> failed) {

    public record Duplicate(
            String filename,
            String fileTitle,
            String itemName,
            long chunkCount,
            String status,
            String importedAt) {
    }

    public record FailedFile(String filename, String reason) {
    }
}
