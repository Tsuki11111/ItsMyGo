package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.agent.KnowledgeImportClient;
import com.tsuk1.itsmygo.common.audit.AdminAudit;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeDocumentList;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeTaskStatus;
import com.tsuk1.itsmygo.dto.knowledge.KnowledgeUploadResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * 管理端知识库导入：代理 rag_helper 的 8001 导入服务。
 * 浏览器只访问 Java；访问密钥只留在后端配置里。
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeImportClient importClient;

    public AdminKnowledgeController(KnowledgeImportClient importClient) {
        this.importClient = importClient;
    }

    /** 上传 PDF / Markdown；重复检测由导入服务按文件哈希完成 */
    @RateLimit(scene = "knowledge-upload", limit = 5, windowSeconds = 60, byUser = true)
    @PostMapping("/upload")
    @AdminAudit(action = "KNOWLEDGE_UPLOAD", label = "导入知识库文档", targetType = "KNOWLEDGE")
    public Result<KnowledgeUploadResult> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "false") boolean force) {
        for (MultipartFile file : files) {
            String name = file == null ? "" : file.getOriginalFilename();
            String extension = name == null ? "" : name.substring(name.lastIndexOf('.') + 1)
                    .toLowerCase(Locale.ROOT);
            if (!"pdf".equals(extension) && !"md".equals(extension)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "只支持导入 PDF / Markdown 文件：" + name);
            }
        }
        return Result.ok(importClient.upload(files, force));
    }

    @GetMapping("/documents")
    public Result<KnowledgeDocumentList> documents() {
        return Result.ok(importClient.documents());
    }

    @GetMapping("/tasks/{taskId}")
    public Result<KnowledgeTaskStatus> task(@PathVariable String taskId) {
        return Result.ok(importClient.status(taskId));
    }

    /** 撤回文档：删除 Milvus 切片、图谱、MinIO 对象，不可恢复 */
    @DeleteMapping("/documents/{fileTitle}")
    @AdminAudit(action = "KNOWLEDGE_REVOKE", label = "撤回知识库文档", targetType = "KNOWLEDGE")
    public Result<Void> revoke(@PathVariable String fileTitle) {
        importClient.revoke(fileTitle);
        return Result.ok();
    }
}
