package com.tsuk1.itsmygo.common.agent;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeDocumentList;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeDocumentView;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeTaskStatus;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeUploadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 知识库导入服务代理（rag_helper 8001）。
 *
 * 浏览器永远不直连 agent：管理员上传到 Java，Java 带访问密钥转发。
 * 导入服务自己会做重复检测、后台跑图、进度落 Redis；这里只做协议转换。
 */
@Slf4j
@Service
public class KnowledgeImportClient {

    private final KnowledgeImportProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public KnowledgeImportClient(KnowledgeImportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // 用 JDK HttpClient 流式转发，避免把 200MB 的 PDF 整个读进内存
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.client = RestClient.builder()
                .baseUrl(properties.importUrl())
                .requestFactory(factory)
                .build();
    }

    public KnowledgeUploadResult upload(List<MultipartFile> files, boolean force) {
        requireConfigured();
        if (files == null || files.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择要导入的 PDF / Markdown 文件");
        }
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        int nonEmpty = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            nonEmpty++;
            builder.part("files", file.getResource())
                    .filename(Objects.requireNonNullElse(file.getOriginalFilename(), "unnamed"))
                    .contentType(mediaType(file.getContentType()));
        }
        if (nonEmpty == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件为空");
        }
        builder.part("force", Boolean.toString(force));

        JsonNode body = call(() -> client.post()
                .uri("/upload")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(JsonNode.class));
        return mapUpload(body);
    }

    public KnowledgeTaskStatus status(String taskId) {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri("/status/{taskId}", taskId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        return new KnowledgeTaskStatus(
                body.path("task_id").asText(taskId),
                body.path("status").asText(""),
                strings(body.path("done_list")),
                strings(body.path("running_list")));
    }

    public KnowledgeDocumentList documents() {
        requireConfigured();
        JsonNode body = call(() -> client.get()
                .uri("/documents")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        return new KnowledgeDocumentList(
                body.path("total").asLong(0),
                documents(body.path("documents")));
    }

    public void revoke(String fileTitle) {
        requireConfigured();
        JsonNode body = call(() -> client.delete()
                .uri(uriBuilder -> uriBuilder.path("/documents/{fileTitle}")
                        .queryParam("confirm", true)
                        .build(fileTitle))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class));
        JsonNode result = body.path("result");
        if (result.has("success") && !result.path("success").asBoolean()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "撤回存在失败项，请查看导入服务日志");
        }
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
            String message = "知识库导入服务返回错误（" + e.getStatusCode().value() + "）";
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    StringUtils.hasText(detail) ? message + "：" + detail : message);
        } catch (ResourceAccessException e) {
            log.warn("知识库导入服务不可达: {}", properties.importUrl(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "知识库导入服务不可用，请确认 8001 已启动");
        }
    }

    private KnowledgeUploadResult mapUpload(JsonNode body) {
        List<KnowledgeUploadResult.Duplicate> duplicates = new ArrayList<>();
        for (JsonNode node : body.path("duplicates")) {
            duplicates.add(new KnowledgeUploadResult.Duplicate(
                    node.path("filename").asText(""),
                    node.path("file_title").asText(""),
                    node.path("item_name").asText(""),
                    node.path("chunk_count").asLong(0),
                    node.path("status").asText(""),
                    node.path("imported_at").asText("")));
        }
        List<KnowledgeUploadResult.FailedFile> failed = new ArrayList<>();
        for (JsonNode node : body.path("failed")) {
            failed.add(new KnowledgeUploadResult.FailedFile(
                    node.path("filename").asText(""),
                    node.path("reason").asText("")));
        }
        return new KnowledgeUploadResult(
                body.path("message").asText(""),
                strings(body.path("task_ids")),
                duplicates,
                failed);
    }

    private List<KnowledgeDocumentView> documents(JsonNode nodes) {
        List<KnowledgeDocumentView> documents = new ArrayList<>();
        for (JsonNode node : nodes) {
            documents.add(new KnowledgeDocumentView(
                    node.path("file_title").asText(""),
                    node.path("item_name").asText(""),
                    node.path("chunk_count").asLong(0),
                    node.path("imported_at").asText(""),
                    node.path("status").asText(""),
                    node.path("kg_entities").asLong(0)));
        }
        return documents;
    }

    private List<String> strings(JsonNode nodes) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            values.add(node.asText());
        }
        return values;
    }

    private MediaType mediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String bearer() {
        return "Bearer " + properties.key();
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new BizException(ResultCode.INTERNAL_ERROR,
                    "知识库导入服务未配置访问密钥（AGENT_IMPORT_KEY / AGENT_GRPC_KEY）");
        }
    }
}
