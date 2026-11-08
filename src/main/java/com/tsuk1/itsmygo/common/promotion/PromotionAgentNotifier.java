package com.tsuk1.itsmygo.common.promotion;

import agent.v1.Event;
import com.tsuk1.itsmygo.common.agent.AgentGateway;
import com.tsuk1.itsmygo.entity.AgentSession;
import com.tsuk1.itsmygo.entity.Product;
import com.tsuk1.itsmygo.mapper.AgentSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 智能推广的投放器：**写进「客服智能体」会话历史**，不是顾客—店铺聊天。
 *
 * 实现：Java 以内部事件 {@code promotion-push} 调 agent（不跑主模型），
 * agent 往该用户最近一个 agent 会话里追加一条 assistant 消息（带商品卡）；
 * 前端 AgentChatPanel 的历史本来就能渲染商品卡，所以用户打开客服对话就能看到。
 *
 * 没有历史会话时新建一个（Java 先登记归属，agent 侧建 Mongo 会话），
 * 保证会话列表里能列出、且不会跨用户串历史。
 */
@Slf4j
@Component
public class PromotionAgentNotifier {

    private static final String EVENT = "promotion-push";
    private static final Duration DEADLINE = Duration.ofSeconds(10);

    private final AgentGateway agentGateway;
    private final AgentSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    public PromotionAgentNotifier(AgentGateway agentGateway, AgentSessionMapper sessionMapper,
                                  ObjectMapper objectMapper) {
        this.agentGateway = agentGateway;
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    /** @return true=已写入 agent 会话；false=agent 不可用/发送失败（不标记当天已推） */
    public boolean deliver(long userId, Product product, String reasonText) {
        if (!agentGateway.configured() || product == null) {
            log.warn("智能推广跳过：agent 未配置或商品为空，userId={}", userId);
            return false;
        }
        try {
            String sessionId = resolveSessionId(userId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", reasonText);
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("productId", String.valueOf(product.getId()));
            card.put("name", product.getName() == null ? "" : product.getName());
            card.put("image", product.getMainImage() == null ? "" : product.getMainImage());
            card.put("price", product.getPrice() == null ? "" : product.getPrice().toPlainString());
            card.put("stock", product.getStock() == null ? "" : String.valueOf(product.getStock()));
            card.put("sales", product.getSales() == null ? "" : String.valueOf(product.getSales()));
            card.put("rating", "");
            card.put("merchantName", "");
            card.put("specText", "");
            card.put("categoryName", "");
            payload.put("product", card);

            Iterator<Event> events = agentGateway.internalEvent(userId, sessionId, EVENT,
                    objectMapper.writeValueAsString(payload), "", DEADLINE);
            while (events.hasNext()) {
                Event event = events.next();
                if (event.hasError()) {
                    log.warn("智能推广写入 agent 会话失败：userId={}, err={}",
                            userId, event.getError().getMessage());
                    return false;
                }
            }
            sessionMapper.touch(sessionId, userId);
            log.info("智能推广已写入客服会话：userId={}，sessionId={}，商品={}",
                    userId, sessionId, product.getId());
            return true;
        } catch (RuntimeException e) {
            log.warn("智能推广写入 agent 会话异常：userId={}, err={}", userId, e.getMessage());
            return false;
        }
    }

    private String resolveSessionId(long userId) {
        AgentSession latest = sessionMapper.selectLatestByUser(userId);
        if (latest != null && latest.getSessionId() != null) {
            return latest.getSessionId();
        }
        // 没有历史会话（用户还没和客服聊过）：新建一个，归属先落在 Java 侧
        String sessionId = "promo-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
        sessionMapper.insertSession(userId, sessionId);
        return sessionId;
    }
}
