package com.tsuk1.itsmygo.common.agent;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapDraftRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapDraftView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapListView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapPublishRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapPublishView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapReportView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapStatusRequest;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapStatusView;
import com.tsuk1.itsmygo.dto.knowledgegap.KnowledgeGapView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识缺口代理（rag_helper 8001 的管理端接口）。
 *
 * 浏览器只访问 Java；访问密钥只留在后端配置里。缺口分析、聚类、草稿生成都在
 * rag_helper 侧执行，Java 只做协议转换 —— 与 {@link KnowledgeImportClient} 同一个服务、
 * 同一把密钥（itsmygo.knowledge.*）。
 */
@Slf4j
@Service
public class KnowledgeGapClient {

    private final KnowledgeImportProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public KnowledgeGapClient(KnowledgeImportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // 草稿要调一次模型（数十秒），周报可能要算 embedding；给足读超时
        factory.setReadTimeout(Duration.ofMinutes(3));
        this.client = RestClient.builder()
                .baseUrl(properties.importUrl())
                .requestFactory(factory)
                .build();
    }

    public KnowledgeGapListView list(double days, int minCount) {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri(uriBuilder -> uriBuilder.path("/knowledge-gaps")
                        .queryParam("days", days)
                        .queryParam("min_count", minCount)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        List<KnowledgeGapView> gaps = new ArrayList<>();
        for (JsonNode node : body.path("gaps")) {
            gaps.add(new KnowledgeGapView(
                    text(node, "gap_id"),
                    text(node, "question"),
                    node.path("count").asLong(0),
                    text(node, "first_at"),
                    text(node, "last_at"),
                    strings(node.path("products")),
                    node.path("local").asLong(0),
                    node.path("web").asLong(0),
                    text(node, "status"),
                    text(node, "status_note")));
        }
        return new KnowledgeGapListView(body.path("days").asDouble(days),
                body.path("total").asLong(gaps.size()), gaps);
    }

    public KnowledgeGapReportView report(double days) {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri(uriBuilder -> uriBuilder.path("/knowledge-gaps/report")
                        .queryParam("days", days)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        return new KnowledgeGapReportView(body.path("days").asDouble(days),
                text(body, "markdown"));
    }

    /** 生成 FAQ 草稿：**只返回文本，不入库**（人工核实后才发布） */
    public KnowledgeGapDraftView draft(KnowledgeGapDraftRequest request) {
        requireConfigured();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gap_ids", request.gapIds());
        payload.put("days", request.days());
        payload.put("title", request.title());
        JsonNode body = call(() -> client.post()
                .uri("/knowledge-gaps/draft")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class));
        return new KnowledgeGapDraftView(
                text(body, "title"),
                text(body, "markdown"),
                strings(body.path("gap_ids")),
                text(body, "note"));
    }

    public KnowledgeGapStatusView updateStatus(KnowledgeGapStatusRequest request) {
        requireConfigured();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gap_ids", request.gapIds());
        payload.put("status", request.status());
        payload.put("note", request.note());
        JsonNode body = call(() -> client.post()
                .uri("/knowledge-gaps/status")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class));
        return new KnowledgeGapStatusView(body.path("updated").asInt(0),
                text(body, "status"));
    }

    /** 发布草稿：交给 rag_helper 的导入链路，返回 taskId 供平台轮询进度 */
    public KnowledgeGapPublishView publish(KnowledgeGapPublishRequest request) {
        requireConfigured();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", request.title());
        payload.put("markdown", request.markdown());
        payload.put("gap_ids", request.gapIds());
        JsonNode body = call(() -> client.post()
                .uri("/knowledge-gaps/publish")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class));
        return new KnowledgeGapPublishView(
                text(body, "task_id"),
                text(body, "file_title"),
                body.path("status_updated").asBoolean(false),
                text(body, "note"));
    }

    private JsonNode call(java.util.function.Supplier<JsonNode> action) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            String detail = "";
            try {
                detail = objectMapper.readTree(e.getResponseBodyAsString())
                        .path("detail").asText("");
            } catch (Exception ignored) {
                // 响应体不是 JSON 时保留状态码即可
            }
            ResultCode code = switch (e.getStatusCode().value()) {
                case 400, 409 -> ResultCode.BAD_REQUEST;
                case 404 -> ResultCode.NOT_FOUND;
                default -> ResultCode.INTERNAL_ERROR;
            };
            String message = "知识缺口服务返回错误（" + e.getStatusCode().value() + "）";
            throw new BizException(code,
                    StringUtils.hasText(detail) ? message + "：" + detail : message);
        } catch (ResourceAccessException e) {
            log.warn("知识缺口服务不可达: {}", properties.importUrl(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "知识缺口服务不可用，请确认 rag_helper 的 8001 已启动");
        }
    }

    private List<String> strings(JsonNode nodes) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            values.add(node.asText());
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String bearer() {
        return "Bearer " + properties.key();
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "知识缺口服务未配置访问密钥（AGENT_IMPORT_KEY / AGENT_GRPC_KEY）");
        }
    }
}
