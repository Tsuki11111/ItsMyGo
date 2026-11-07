package com.tsuk1.itsmygo.dto.agent;

import java.util.List;
import java.util.Map;

public record AgentHistoryMessage(
        String role,
        String text,
        List<AgentImage> images,
        String ts,
        List<AgentTrace> traces,
        List<AgentProductCard> products,
        List<AgentFormRequest> forms) {

    public record AgentImage(String url, String caption) {
    }

    /** 该轮执行流；老消息没有 trace，返回空列表。 */
    public record AgentTrace(String kind, String agent, String detail) {
    }

    /** 导购商品卡；老消息没有 products，返回空列表。 */
    public record AgentProductCard(String productId, String name, String image, String price,
                                   String stock, String sales, String rating,
                                   String merchantName, String specText, String categoryName) {
    }

    /** 历史里的会话内表单；老消息没有 forms，返回空列表。前端只读展示，不可再提交。 */
    public record AgentFormOption(String value, String label) {
    }

    public record AgentFormField(String name, String label, String type, boolean required,
                                 String placeholder, String defaultValue,
                                 List<AgentFormOption> options) {
    }

    public record AgentFormRequest(String formId, String formType, String title,
                                   String description, List<AgentFormField> fields,
                                   String submitLabel, int expiresInSeconds,
                                   Map<String, String> meta) {
    }
}
