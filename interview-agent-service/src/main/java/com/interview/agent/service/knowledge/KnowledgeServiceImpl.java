package com.interview.agent.service.knowledge;

import com.interview.agent.dao.mapper.KnowledgeDocumentMapper;
import com.interview.agent.model.entity.KnowledgeDocument;
import com.interview.agent.model.vo.KnowledgeDocumentVO;
import com.interview.agent.service.rag.RagService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeDocumentMapper documentMapper;
    private final RagService ragService;

    @Override
    public List<KnowledgeDocumentVO> listDocuments(String domain) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        if (domain != null && !domain.isEmpty()) {
            wrapper.eq(KnowledgeDocument::getDomain, domain);
        }
        wrapper.eq(KnowledgeDocument::getStatus, "ACTIVE")
               .orderByDesc(KnowledgeDocument::getCreatedAt);

        return documentMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listDomains() {
        return Arrays.asList("java", "python", "ai", "frontend", "database", "system");
    }

    @Override
    public void deleteDocument(Long id) {
        ragService.deleteDocument(id);
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument doc) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setDomain(doc.getDomain());
        vo.setFileType(doc.getFileType());
        vo.setChunkCount(doc.getChunkCount());
        vo.setStatus(doc.getStatus());
        vo.setCreatedAt(doc.getCreatedAt());
        return vo;
    }
}
