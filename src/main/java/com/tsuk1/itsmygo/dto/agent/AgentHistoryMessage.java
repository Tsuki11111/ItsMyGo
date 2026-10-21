package com.tsuk1.itsmygo.dto.agent;

import java.util.List;

public record AgentHistoryMessage(
        String role,
        String text,
        List<AgentImage> images,
        String ts,
        List<AgentTrace> traces,
        List<AgentProductCard> products) {

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
}
