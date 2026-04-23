package com.interview.agent.service.knowledge;

import com.interview.agent.model.vo.KnowledgeDocumentVO;

import java.util.List;

/**
 * 知识库管理服务接口
 */
public interface KnowledgeService {

    /**
     * 获取所有文档列表
     */
    List<KnowledgeDocumentVO> listDocuments(String domain);

    /**
     * 获取支持的领域列表
     */
    List<String> listDomains();

    /**
     * 删除文档
     */
    void deleteDocument(Long id);
}
