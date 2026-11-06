package com.tsuk1.itsmygo.dto.knowledgegap;

/** 发布结果：导入任务已创建（不代表已入库完成） */
public record KnowledgeGapPublishView(
        String taskId,
        String fileTitle,
        boolean statusUpdated,
        String note) {
}
