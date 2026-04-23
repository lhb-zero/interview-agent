package com.interview.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文档上传 DTO
 */
@Data
public class KnowledgeUploadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档标题 */
    @NotBlank(message = "文档标题不能为空")
    private String title;

    /** 技术领域 */
    @NotBlank(message = "技术领域不能为空")
    private String domain;
}
