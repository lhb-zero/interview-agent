package com.interview.agent.web.controller;

import com.interview.agent.common.result.Result;
import com.interview.agent.model.dto.KnowledgeUploadDTO;
import com.interview.agent.model.vo.KnowledgeDocumentVO;
import com.interview.agent.service.knowledge.KnowledgeService;
import com.interview.agent.service.rag.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 知识库管理控制器
 */
@Tag(name = "知识库管理", description = "面试知识库文档管理接口")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final RagService ragService;

    private static final String UPLOAD_DIR = "uploads/";

    @Operation(summary = "上传文档到知识库")
    @PostMapping("/upload")
    public Result<Void> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("domain") String domain
    ) {
        try {
            // 1. 保存文件到本地
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename != null ?
                    originalFilename.substring(originalFilename.lastIndexOf(".")) : ".txt";
            String savedFileName = UUID.randomUUID().toString().replace("-", "") + fileExtension;

            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(savedFileName);
            file.transferTo(filePath.toFile());

            // 2. 导入到知识库（自动分块+Embedding+存储）
            ragService.importDocument(filePath.toString(), domain, title);

            return Result.success();
        } catch (IOException e) {
            return Result.fail("文件上传失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取文档列表")
    @GetMapping("/documents")
    public Result<List<KnowledgeDocumentVO>> listDocuments(
            @RequestParam(required = false) String domain
    ) {
        return Result.success(knowledgeService.listDocuments(domain));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return Result.success();
    }

    @Operation(summary = "获取支持的领域列表")
    @GetMapping("/domains")
    public Result<List<String>> listDomains() {
        return Result.success(knowledgeService.listDomains());
    }
}
