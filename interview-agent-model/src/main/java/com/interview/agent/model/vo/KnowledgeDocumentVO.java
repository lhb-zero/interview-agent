package com.interview.agent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档 VO
 */
@Data
public class KnowledgeDocumentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String domain;

    private String fileType;

    private Integer chunkCount;

    private String status;

    private LocalDateTime createdAt;
}
