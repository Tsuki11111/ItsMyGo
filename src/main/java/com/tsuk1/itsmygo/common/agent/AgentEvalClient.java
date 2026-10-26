package com.tsuk1.itsmygo.common.agent;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.eval.AgentEvalCasesView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalCaseCreateRequest;
import com.tsuk1.itsmygo.dto.eval.AgentEvalJobListView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalJobView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalResultListView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalResultView;
import com.tsuk1.itsmygo.dto.eval.AgentEvalRunRequest;
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
 * 智能体评测服务代理（rag_helper 8002）。
 *
 * 浏览器只访问 Java 管理端；评测接口需要 agent 访问密钥，密钥只留在后端。
 * 评测执行与结果文件都在 rag_helper 侧，Java 只做协议转换和任务状态透传。
 */
@Slf4j
@Service
public class AgentEvalClient {

    private final AgentEvalProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public AgentEvalClient(AgentEvalProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // 结果 JSONL 可能包含逐条证据，给大一点的读超时；启动/轮询本身很快
        factory.setReadTimeout(Duration.ofMinutes(2));
        this.client = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(factory)
                .build();
    }

    public AgentEvalCasesView cases() {
        requireConfigured();
        return casesView(call(() -> client.get()
                .uri("/eval/cases")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class)));
    }

    public AgentEvalCasesView.AgentEvalCaseView createCase(
            AgentEvalCaseCreateRequest request) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", request.id());
        body.put("question", request.question());
        body.put("category", request.category());
        body.put("doc", request.doc());
        body.put("context", request.context());
        body.put("source", request.source());
        body.put("gold_chunks", parseGoldChunks(request.goldChunks()));
        body.put("expected_outcome", request.expectedOutcome());
        JsonNode response = call(() -> client.post()
                .uri("/eval/cases")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return caseView(response.path("item"));
    }

    public List<AgentEvalResultListView.AgentEvalResultFileView> results(int limit) {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri(uriBuilder -> uriBuilder.path("/eval/results")
                        .queryParam("limit", limit)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        List<AgentEvalResultListView.AgentEvalResultFileView> items = new ArrayList<>();
        for (JsonNode node : body.path("items")) {
            items.add(new AgentEvalResultListView.AgentEvalResultFileView(
                    text(node, "name"),
                    text(node, "report"),
                    node.path("rows").asLong(0),
                    node.path("size").asLong(0),
                    node.path("mtime").asDouble(0)));
        }
        return items;
    }

    public AgentEvalResultView result(String name) {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri("/eval/results/{name}", name)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        return new AgentEvalResultView(
                text(body, "name"),
                text(body, "kind"),
                jsonList(body.path("rows")),
                body.get("summary"),
                text(body, "report"),
                text(body, "report_content"));
    }

    public AgentEvalJobView start(AgentEvalRunRequest request) {
        requireConfigured();
        JsonNode body = call(() -> client.post()
                .uri("/eval/run")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class));
        return jobView(body);
    }

    public List<AgentEvalJobView> jobs(int limit) {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri(uriBuilder -> uriBuilder.path("/eval/jobs")
                        .queryParam("limit", limit)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        List<AgentEvalJobView> items = new ArrayList<>();
        for (JsonNode node : body.path("items")) {
            items.add(jobView(node));
        }
        return items;
    }

    public AgentEvalJobView job(String id) {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri("/eval/jobs/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        return jobView(body);
    }

    public AgentEvalJobView stop(String id) {
        requireConfigured();
        JsonNode body = call(() -> client.post()
                .uri("/eval/jobs/{id}/stop", id)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        return jobView(body);
    }

    private AgentEvalCasesView casesView(JsonNode body) {
        List<AgentEvalCasesView.AgentEvalCaseView> items = new ArrayList<>();
        for (JsonNode node : body.path("items")) {
            items.add(caseView(node));
        }
        return new AgentEvalCasesView(
                items,
                text(body, "judge_model"),
                strings(body.path("config_errors")),
                strings(body.path("warnings")));
    }

    private AgentEvalCasesView.AgentEvalCaseView caseView(JsonNode node) {
        return new AgentEvalCasesView.AgentEvalCaseView(
                text(node, "id"),
                text(node, "question"),
                text(node, "category"),
                text(node, "doc"),
                text(node, "source"),
                text(node, "expected_outcome"),
                node.path("has_gold").asBoolean(false),
                node.path("custom").asBoolean(false));
    }

    private AgentEvalJobView jobView(JsonNode body) {
        JsonNode params = body.path("params");
        JsonNode resultNode = body.get("result");
        AgentEvalJobView.Result result = null;
        if (resultNode != null && resultNode.isObject()) {
            result = new AgentEvalJobView.Result(
                    text(resultNode, "jsonl"),
                    text(resultNode, "report"),
                    resultNode.get("summary"));
        }
        return new AgentEvalJobView(
                text(body, "id"),
                text(body, "status"),
                body.path("created_at").asDouble(0),
                nullableDouble(body, "started_at"),
                nullableDouble(body, "finished_at"),
                nullableLong(body, "elapsed_ms"),
                new AgentEvalJobView.Params(
                        strings(params.path("case_ids")),
                        params.path("limit").asInt(0),
                        params.path("runs").asInt(1),
                        params.path("web").asBoolean(false),
                        params.path("keep").asBoolean(false),
                        params.path("case_count").asInt(0),
                        text(params, "judge_model")),
                body.path("total").asInt(0),
                body.path("done").asInt(0),
                text(body, "current_case"),
                body.path("percent").asDouble(0),
                result,
                text(body, "error"),
                strings(body.path("warnings")));
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
            ResultCode code = e.getStatusCode().value() == 404
                    ? ResultCode.NOT_FOUND
                    : (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 409
                    ? ResultCode.BAD_REQUEST : ResultCode.INTERNAL_ERROR);
            String message = "智能体评测服务返回错误（" + e.getStatusCode().value() + "）";
            throw new BizException(code,
                    StringUtils.hasText(detail) ? message + "：" + detail : message);
        } catch (ResourceAccessException e) {
            log.warn("智能体评测服务不可达: {}", properties.url(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "智能体评测服务不可用，请确认 rag_helper 的 8002 已启动");
        }
    }

    private List<JsonNode> jsonList(JsonNode nodes) {
        List<JsonNode> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            values.add(node);
        }
        return values;
    }

    private List<Long> parseGoldChunks(List<String> raw) {
        List<Long> values = new ArrayList<>();
        for (String item : raw) {
            String text = item == null ? "" : item.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                long value = Long.parseLong(text);
                if (value <= 0) {
                    throw new NumberFormatException("must be positive");
                }
                values.add(value);
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "gold_chunks 必须是整数切片 id：" + text);
            }
        }
        return values;
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

    private static Double nullableDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String bearer() {
        return "Bearer " + properties.key();
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "智能体评测服务未配置访问密钥（AGENT_EVAL_KEY / AGENT_GRPC_KEY）");
        }
    }
}
