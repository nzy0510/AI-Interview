package com.interview.service.questionbank.build;

import com.interview.config.QuestionBankAccessProperties;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeBase;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.service.AdminRoleService;
import org.springframework.stereotype.Service;

@Service
public class QuestionBankBuildAccessService {
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_PRIVATE = "PRIVATE";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final InterviewPositionMapper positionMapper;
    private final AdminRoleService adminRoleService;
    private final QuestionBankAccessProperties accessProperties;

    public QuestionBankBuildAccessService(KnowledgeBaseMapper knowledgeBaseMapper,
                                          InterviewPositionMapper positionMapper,
                                          AdminRoleService adminRoleService,
                                          QuestionBankAccessProperties accessProperties) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.positionMapper = positionMapper;
        this.adminRoleService = adminRoleService;
        this.accessProperties = accessProperties;
    }

    public KnowledgeBase requireBuildTarget(Long userId, Long knowledgeBaseId) {
        if (userId == null) throw new RuntimeException("未登录：缺少用户身份");
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null || !"ACTIVE".equalsIgnoreCase(knowledgeBase.getStatus())) {
            throw new RuntimeException("无权访问知识库");
        }
        InterviewPosition position = positionMapper.selectById(knowledgeBase.getPositionId());
        if (position == null || !"ACTIVE".equalsIgnoreCase(position.getStatus())
                || !sameScope(knowledgeBase.getScope(), position.getScope())) {
            throw new RuntimeException("无权访问岗位");
        }
        if (SCOPE_PUBLIC.equalsIgnoreCase(knowledgeBase.getScope())) {
            if (!adminRoleService.isAdmin(userId)) throw new RuntimeException("无权访问知识库");
            return knowledgeBase;
        }
        if (!SCOPE_PRIVATE.equalsIgnoreCase(knowledgeBase.getScope())
                || !accessProperties.isUserMaintenanceEnabled()
                || !userId.equals(knowledgeBase.getOwnerUserId())
                || !userId.equals(position.getOwnerUserId())) {
            throw new RuntimeException("无权访问知识库");
        }
        return knowledgeBase;
    }

    private boolean sameScope(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
