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

    @org.springframework.beans.factory.annotation.Value("${app.upload.dir:${user.dir}/uploads}")
    private String uploadDir;

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

            // 使用绝对路径，避免 file.transferTo() 基于 Tomcat 临时目录解析
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(savedFileName);
            file.transferTo(filePath.toFile());

            // 2. 识别文件类型（不带点号）
            String fileType = fileExtension.replace(".", "").toLowerCase();
            if (fileType.equals("markdown")) fileType = "md";

            // 3. 导入到知识库（自动分块 + Embedding + 向量存储）
            ragService.importDocument(filePath.toString(), domain, title, fileType);

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
