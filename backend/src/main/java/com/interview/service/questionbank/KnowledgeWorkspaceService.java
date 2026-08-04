package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.dto.QuestionBankCapabilitiesResponse;
import com.interview.entity.AppJob;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeAtom;
import com.interview.entity.KnowledgeAtomVersion;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.RagRetrievalLog;
import com.interview.dto.questionbank.QuestionBankAtomListItem;
import com.interview.dto.questionbank.QuestionBankAtomQueryRequest;
import com.interview.dto.questionbank.QuestionBankBulkAtomRequest;
import com.interview.dto.questionbank.QuestionBankImportPreviewResponse;
import com.interview.dto.questionbank.QuestionBankImportRequest;
import com.interview.dto.questionbank.QuestionBankImportResult;
import com.interview.dto.questionbank.QuestionBankPageResponse;
import com.interview.dto.MentorInsightResponse.KnowledgeCoverage;
import com.interview.dto.MentorInsightResponse.KnowledgeCoverage.CategoryDetail;
import com.interview.mapper.AppJobMapper;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.KnowledgeAtomReviewMapper;
import com.interview.mapper.KnowledgeAtomVersionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import com.interview.service.AdminRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeWorkspaceService {

    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_PRIVATE = "PRIVATE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    private final InterviewPositionMapper positionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeAtomMapper atomMapper;
    private final KnowledgeAtomVersionMapper versionMapper;
    private final KnowledgeAtomReviewMapper reviewMapper;
    private final AppJobMapper appJobMapper;
    private final AdminRoleService adminRoleService;
    private final QuestionBankService questionBankService;
    private final QdrantVectorService qdrantVectorService;
    private final RagRetrievalLogMapper ragLogMapper;
    private final QuestionBankAccessProperties accessProperties;

    public KnowledgeWorkspaceService(InterviewPositionMapper positionMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeAtomMapper atomMapper,
                                     KnowledgeAtomVersionMapper versionMapper,
                                     KnowledgeAtomReviewMapper reviewMapper,
                                     AppJobMapper appJobMapper,
                                     AdminRoleService adminRoleService,
                                     QuestionBankService questionBankService,
                                     QdrantVectorService qdrantVectorService,
                                     RagRetrievalLogMapper ragLogMapper,
                                     QuestionBankAccessProperties accessProperties) {
        this.positionMapper = positionMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.atomMapper = atomMapper;
        this.versionMapper = versionMapper;
        this.reviewMapper = reviewMapper;
        this.appJobMapper = appJobMapper;
        this.adminRoleService = adminRoleService;
        this.questionBankService = questionBankService;
        this.qdrantVectorService = qdrantVectorService;
        this.ragLogMapper = ragLogMapper;
        this.accessProperties = accessProperties;
    }

    public KnowledgeWorkspaceResponse listWorkspace(Long currentUserId) {
        requireWorkspaceAccess(currentUserId);
        List<InterviewPosition> positions = positionMapper.selectList(new QueryWrapper<InterviewPosition>()
                .and(wrapper -> wrapper
                        .eq("scope", SCOPE_PUBLIC)
                        .or(nested -> nested.eq("scope", SCOPE_PRIVATE).eq("owner_user_id", currentUserId)))
                .orderByAsc("scope", "id"));
        if (positions.isEmpty()) {
            return new KnowledgeWorkspaceResponse(List.of());
        }

        List<Long> positionIds = positions.stream().map(InterviewPosition::getId).toList();
        Map<Long, KnowledgeBase> basesByPosition = knowledgeBaseMapper.selectList(new QueryWrapper<KnowledgeBase>()
                        .in("position_id", positionIds)
                        .and(wrapper -> wrapper
                                .eq("scope", SCOPE_PUBLIC)
                                .or(nested -> nested.eq("scope", SCOPE_PRIVATE).eq("owner_user_id", currentUserId))))
                .stream()
                .filter(item -> isVisibleScoped(item.getScope(), item.getOwnerUserId(), currentUserId))
                .collect(Collectors.toMap(KnowledgeBase::getPositionId, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<KnowledgePositionResponse> responseItems = new ArrayList<>();
        for (InterviewPosition position : positions) {
            KnowledgeBase knowledgeBase = basesByPosition.get(position.getId());
            responseItems.add(toPositionResponse(position, knowledgeBase, currentUserId));
        }
        responseItems.sort(Comparator
                .comparing((KnowledgePositionResponse item) -> !SCOPE_PUBLIC.equalsIgnoreCase(item.scope()))
                .thenComparing(KnowledgePositionResponse::id));
        return new KnowledgeWorkspaceResponse(responseItems);
    }

    @Transactional
    public KnowledgePositionResponse createPrivatePosition(Long currentUserId, KnowledgePositionCreateRequest request) {
        requireWorkspaceAccess(currentUserId);
        String name = cleanName(request != null ? request.name() : null);
        String description = cleanDescription(request != null ? request.description() : null);
        LocalDateTime now = LocalDateTime.now();

        InterviewPosition position = new InterviewPosition();
        position.setScope(SCOPE_PRIVATE);
        position.setOwnerUserId(currentUserId);
        position.setName(name);
        position.setDescription(description);
        position.setStatus(STATUS_ACTIVE);
        position.setCreatedBy(currentUserId);
        position.setCreateTime(now);
        position.setUpdateTime(now);
        positionMapper.insert(position);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setScope(SCOPE_PRIVATE);
        knowledgeBase.setOwnerUserId(currentUserId);
        knowledgeBase.setPositionId(position.getId());
        knowledgeBase.setName(name + " 默认知识库");
        knowledgeBase.setStatus(STATUS_ACTIVE);
        knowledgeBase.setCreatedBy(currentUserId);
        knowledgeBase.setCreateTime(now);
        knowledgeBase.setUpdateTime(now);
        knowledgeBaseMapper.insert(knowledgeBase);

        position.setDefaultKnowledgeBaseId(knowledgeBase.getId());
        positionMapper.updateById(position);
        return toPositionResponse(position, knowledgeBase, currentUserId);
    }

    public Map<String, Integer> publishAllDrafts(Long currentUserId, Long knowledgeBaseId) {
        requireUser(currentUserId);
        QuestionBankImportScope scope = importScopeFor(currentUserId, knowledgeBaseId);
        return questionBankService.publishAllDrafts(scope);
    }

    public Map<String, Integer> archiveAllAtoms(Long currentUserId, Long knowledgeBaseId) {
        requireUser(currentUserId);
        QuestionBankImportScope scope = importScopeFor(currentUserId, knowledgeBaseId);
        return questionBankService.archiveAll(scope);
    }

    public KnowledgeCoverage getPositionCoverage(Long currentUserId, Long positionId) {
        requireWorkspaceAccess(currentUserId);
        InterviewPosition position = positionMapper.selectById(positionId);
        if (position == null) {
            throw new RuntimeException("岗位不存在");
        }
        boolean privateOwner = SCOPE_PRIVATE.equalsIgnoreCase(position.getScope())
                && currentUserId.equals(position.getOwnerUserId());
        boolean publicAdmin = SCOPE_PUBLIC.equalsIgnoreCase(position.getScope())
                && adminRoleService.isAdmin(currentUserId);
        if (!privateOwner && !publicAdmin) {
            throw new RuntimeException("无权查看该岗位的覆盖数据");
        }

        KnowledgeCoverage kc = new KnowledgeCoverage();
        List<Map<String, Object>> totalRows = atomMapper.selectMaps(
                new QueryWrapper<KnowledgeAtom>()
                        .select("category, COUNT(*) as total")
                        .eq("status", "PUBLISHED")
                        .eq("position_id", positionId)
                        .groupBy("category"));
        List<Map<String, Object>> coveredRows = ragLogMapper.selectMaps(
                new QueryWrapper<RagRetrievalLog>()
                        .select("retrieved_category, COUNT(DISTINCT retrieved_atom_id) as cnt")
                        .eq("user_id", currentUserId)
                        .eq("position_id", positionId)
                        .eq("context_selected", true)
                        .groupBy("retrieved_category"));
        java.util.Map<String, Integer> coveredByCategory = new java.util.HashMap<>();
        for (Map<String, Object> row : coveredRows) {
            String cat = (String) row.get("retrieved_category");
            Number cnt = (Number) row.get("cnt");
            if (cat != null && cnt != null) coveredByCategory.put(cat, cnt.intValue());
        }

        int coveredCats = 0;
        int coveredTotal = 0;
        int publishedTotal = 0;
        List<CategoryDetail> details = new ArrayList<>();
        for (Map<String, Object> row : totalRows) {
            String cat = (String) row.get("category");
            Number total = (Number) row.get("total");
            if (cat == null || total == null) continue;
            int categoryTotal = total.intValue();
            int covered = Math.min(coveredByCategory.getOrDefault(cat, 0), categoryTotal);
            CategoryDetail detail = new CategoryDetail();
            detail.setCategory(cat);
            detail.setCovered(covered);
            detail.setTotal(categoryTotal);
            detail.setPercent(categoryTotal > 0 ? Math.round((double) covered / categoryTotal * 1000) / 10.0 : 0.0);
            details.add(detail);
            if (covered > 0) coveredCats++;
            coveredTotal += covered;
            publishedTotal += categoryTotal;
        }
        kc.setTotalCategories(details.size());
        kc.setCoveredCategories(coveredCats);
        kc.setCoveragePercent(publishedTotal > 0
                ? Math.round((double) coveredTotal / publishedTotal * 1000) / 10.0 : 0.0);
        kc.setDetails(details);
        return kc;
    }

    @Transactional
    public void deletePrivatePosition(Long currentUserId, Long positionId) {
        requireWorkspaceAccess(currentUserId);
        InterviewPosition position = positionMapper.selectById(positionId);
        if (position == null
                || !SCOPE_PRIVATE.equalsIgnoreCase(position.getScope())
                || !currentUserId.equals(position.getOwnerUserId())) {
            throw new RuntimeException("无权访问岗位");
        }
        List<KnowledgeAtom> publishedAtoms = atomMapper.selectList(
                new QueryWrapper<KnowledgeAtom>()
                        .eq("position_id", positionId)
                        .eq("status", "PUBLISHED"));
        List<String> publishedAtomIds = publishedAtoms.stream()
                .map(KnowledgeAtom::getAtomId)
                .collect(Collectors.toList());
        for (String atomId : publishedAtomIds) {
            try {
                qdrantVectorService.delete(atomId);
            } catch (Exception e) {
                log.warn("Qdrant delete failed for atom {} during position delete: {}", atomId, e.getMessage());
            }
        }
        List<KnowledgeAtom> allAtoms = atomMapper.selectList(
                new QueryWrapper<KnowledgeAtom>().eq("position_id", positionId));
        List<String> allAtomIds = allAtoms.stream()
                .map(KnowledgeAtom::getAtomId)
                .collect(Collectors.toList());
        if (!allAtomIds.isEmpty()) {
            versionMapper.delete(new QueryWrapper<KnowledgeAtomVersion>().in("atom_id", allAtomIds));
            atomMapper.delete(new QueryWrapper<KnowledgeAtom>().eq("position_id", positionId));
        }
        appJobMapper.delete(new QueryWrapper<AppJob>().eq("position_id", positionId));
        knowledgeBaseMapper.delete(new QueryWrapper<KnowledgeBase>().eq("position_id", positionId));
        positionMapper.deleteById(positionId);
    }

    public QuestionBankImportPreviewResponse previewImportPackage(Long currentUserId,
                                                                  Long knowledgeBaseId,
                                                                  QuestionBankImportRequest request) {
        QuestionBankImportScope scope = importScopeFor(currentUserId, knowledgeBaseId);
        QuestionBankImportRequest safeRequest = forceDraft(request);
        return questionBankService.previewImport(safeRequest, scope);
    }

    public QuestionBankImportResult importPackage(Long currentUserId,
                                                  Long knowledgeBaseId,
                                                  QuestionBankImportRequest request) {
        QuestionBankImportScope scope = importScopeFor(currentUserId, knowledgeBaseId);
        QuestionBankImportRequest safeRequest = forceDraft(request);
        return questionBankService.importBatch(safeRequest, scope);
    }

    public QuestionBankPageResponse<QuestionBankAtomListItem> listAtoms(Long currentUserId,
                                                                        Long knowledgeBaseId,
                                                                        QuestionBankAtomQueryRequest request) {
        return questionBankService.listAtoms(request, importScopeFor(currentUserId, knowledgeBaseId));
    }

    public java.util.Map<String, Integer> archiveAtoms(Long currentUserId,
                                                       Long knowledgeBaseId,
                                                       QuestionBankBulkAtomRequest request) {
        return questionBankService.archiveAtoms(request == null ? List.of() : request.getAtomIds(),
                importScopeFor(currentUserId, knowledgeBaseId));
    }

    public java.util.Map<String, Integer> publishAtoms(Long currentUserId,
                                                       Long knowledgeBaseId,
                                                       QuestionBankBulkAtomRequest request) {
        return questionBankService.publishAtoms(request == null ? List.of() : request.getAtomIds(),
                importScopeFor(currentUserId, knowledgeBaseId));
    }

    public java.util.Map<String, Integer> reindexAtoms(Long currentUserId,
                                                       Long knowledgeBaseId,
                                                       QuestionBankBulkAtomRequest request) {
        return questionBankService.reindexAtoms(request == null ? List.of() : request.getAtomIds(),
                importScopeFor(currentUserId, knowledgeBaseId));
    }

    public QuestionBankCapabilitiesResponse getCapabilities(Long currentUserId) {
        requireUser(currentUserId);
        boolean admin = adminRoleService.isAdmin(currentUserId);
        boolean userMaintenanceEnabled = accessProperties.isUserMaintenanceEnabled();
        return new QuestionBankCapabilitiesResponse(
                userMaintenanceEnabled,
                admin,
                admin || userMaintenanceEnabled
        );
    }

    private KnowledgePositionResponse toPositionResponse(InterviewPosition position,
                                                         KnowledgeBase knowledgeBase,
                                                         Long currentUserId) {
        boolean editable = SCOPE_PRIVATE.equalsIgnoreCase(position.getScope())
                && currentUserId.equals(position.getOwnerUserId())
                && !STATUS_ARCHIVED.equalsIgnoreCase(position.getStatus());
        boolean active = STATUS_ACTIVE.equalsIgnoreCase(position.getStatus())
                && knowledgeBase != null
                && STATUS_ACTIVE.equalsIgnoreCase(knowledgeBase.getStatus());
        boolean privateOwner = active
                && SCOPE_PRIVATE.equalsIgnoreCase(position.getScope())
                && currentUserId.equals(position.getOwnerUserId());
        boolean publicAdmin = active
                && SCOPE_PUBLIC.equalsIgnoreCase(position.getScope())
                && adminRoleService.isAdmin(currentUserId);
        boolean canMaintain = privateOwner || publicAdmin;
        boolean canPublish = privateOwner || publicAdmin;
        boolean canReindex = privateOwner || publicAdmin;
        KnowledgeBaseResponse knowledgeBaseResponse = knowledgeBase == null ? null : new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getScope(),
                knowledgeBase.getOwnerUserId(),
                knowledgeBase.getPositionId(),
                knowledgeBase.getName(),
                knowledgeBase.getStatus()
        );
        return new KnowledgePositionResponse(
                position.getId(),
                position.getScope(),
                position.getOwnerUserId(),
                position.getName(),
                position.getDescription(),
                position.getStatus(),
                editable,
                canMaintain,
                canMaintain,
                canPublish,
                canReindex,
                canMaintain,
                knowledgeBaseResponse
        );
    }

    private void requireUser(Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
    }

    private void requireWorkspaceAccess(Long currentUserId) {
        requireUser(currentUserId);
        if (!accessProperties.isUserMaintenanceEnabled() && !adminRoleService.isAdmin(currentUserId)) {
            throw new RuntimeException("无权访问题库工作台");
        }
    }

    private QuestionBankImportScope importScopeFor(Long currentUserId, Long knowledgeBaseId) {
        requireWorkspaceAccess(currentUserId);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null || !canManageKnowledgeBase(knowledgeBase, currentUserId)) {
            throw new RuntimeException("无权访问知识库");
        }
        return new QuestionBankImportScope(
                knowledgeBase.getScope(),
                SCOPE_PUBLIC.equalsIgnoreCase(knowledgeBase.getScope()) ? null : knowledgeBase.getOwnerUserId(),
                knowledgeBase.getPositionId(),
                knowledgeBase.getId(),
                currentUserId,
                false
        );
    }

    private boolean canManageKnowledgeBase(KnowledgeBase knowledgeBase, Long currentUserId) {
        if (knowledgeBase == null || STATUS_ARCHIVED.equalsIgnoreCase(knowledgeBase.getStatus())) {
            return false;
        }
        if (SCOPE_PRIVATE.equalsIgnoreCase(knowledgeBase.getScope())
                && currentUserId.equals(knowledgeBase.getOwnerUserId())) {
            return true;
        }
        return SCOPE_PUBLIC.equalsIgnoreCase(knowledgeBase.getScope()) && adminRoleService.isAdmin(currentUserId);
    }

    private QuestionBankImportRequest forceDraft(QuestionBankImportRequest request) {
        QuestionBankImportRequest safe = request == null ? new QuestionBankImportRequest() : request;
        safe.setMode("DRAFT");
        return safe;
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("岗位名称不能为空");
        }
        String cleaned = name.trim();
        if (cleaned.length() > 80) {
            throw new IllegalArgumentException("岗位名称不能超过 80 个字符");
        }
        return cleaned;
    }

    private String cleanDescription(String description) {
        if (description == null) {
            return "";
        }
        String cleaned = description.trim();
        if (cleaned.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("岗位说明不能超过 300 个字符");
        }
        return cleaned;
    }

    private boolean isVisibleScoped(String scope, Long ownerUserId, Long currentUserId) {
        return SCOPE_PUBLIC.equalsIgnoreCase(scope)
                || (SCOPE_PRIVATE.equalsIgnoreCase(scope) && currentUserId.equals(ownerUserId));
    }
}
