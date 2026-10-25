package com.tsuk1.itsmygo.dto.knowledge;

import java.util.List;

/** 导入任务进度：done/running 都是导入服务翻译后的中文阶段名 */
public record KnowledgeTaskStatus(
        String taskId,
        String status,
        List<String> doneList,
        List<String> runningList) {
}
