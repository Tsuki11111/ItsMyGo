package com.tsuk1.itsmygo.dto.eval;

import java.util.List;

/** output/eval 下的历史结果文件（mtime 为 epoch 秒）。 */
public record AgentEvalResultListView(List<AgentEvalResultFileView> items) {

    public record AgentEvalResultFileView(
            String name,
            String report,
            long rows,
            long size,
            double mtime) {
    }
}
