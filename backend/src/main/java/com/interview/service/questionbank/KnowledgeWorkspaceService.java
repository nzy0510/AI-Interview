package com.interview.service.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.interview.entity.InterviewPosition;
import com.interview.entity.KnowledgeBase;
import com.interview.entity.KnowledgeSourceFile;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeBaseMapper;
import com.interview.mapper.KnowledgeSourceFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KnowledgeWorkspaceService {

    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_PRIVATE = "PRIVATE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    private final InterviewPositionMapper positionMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeSourceFileMapper sourceFileMapper;

    public KnowledgeWorkspaceService(InterviewPositionMapper positionMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeSourceFileMapper sourceFileMapper) {
        this.positionMapper = positionMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.sourceFileMapper = sourceFileMapper;
    }

    public KnowledgeWorkspaceResponse listWorkspace(Long currentUserId) {
        requireUser(currentUserId);
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

        List<Long> knowledgeBaseIds = basesByPosition.values().stream()
                .map(KnowledgeBase::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<KnowledgeSourceFile>> filesByKnowledgeBase = knowledgeBaseIds.isEmpty()
                ? Map.of()
                : sourceFileMapper.selectList(new QueryWrapper<KnowledgeSourceFile>()
                        .in("knowledge_base_id", knowledgeBaseIds)
                        .and(wrapper -> wrapper
                                .eq("scope", SCOPE_PUBLIC)
                                .or(nested -> nested.eq("scope", SCOPE_PRIVATE).eq("owner_user_id", currentUserId)))
                        .orderByDesc("create_time", "id"))
                .stream()
                .filter(item -> isVisibleScoped(item.getScope(), item.getOwnerUserId(), currentUserId))
                .collect(Collectors.groupingBy(KnowledgeSourceFile::getKnowledgeBaseId, LinkedHashMap::new, Collectors.toList()));

        List<KnowledgePositionResponse> responseItems = new ArrayList<>();
        for (InterviewPosition position : positions) {
            KnowledgeBase knowledgeBase = basesByPosition.get(position.getId());
            responseItems.add(toPositionResponse(position, knowledgeBase,
                    knowledgeBase == null ? List.of() : filesByKnowledgeBase.getOrDefault(knowledgeBase.getId(), List.of()),
                    currentUserId));
        }
        responseItems.sort(Comparator
                .comparing((KnowledgePositionResponse item) -> !SCOPE_PUBLIC.equalsIgnoreCase(item.scope()))
                .thenComparing(KnowledgePositionResponse::id));
        return new KnowledgeWorkspaceResponse(responseItems);
    }

    @Transactional
    public KnowledgePositionResponse createPrivatePosition(Long currentUserId, KnowledgePositionCreateRequest request) {
        requireUser(currentUserId);
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
        return toPositionResponse(position, knowledgeBase, List.of(), currentUserId);
    }

    @Transactional
    public void archivePrivatePosition(Long currentUserId, Long positionId) {
        requireUser(currentUserId);
        InterviewPosition position = positionMapper.selectById(positionId);
        if (position == null
                || !SCOPE_PRIVATE.equalsIgnoreCase(position.getScope())
                || !currentUserId.equals(position.getOwnerUserId())) {
            throw new RuntimeException("无权访问岗位");
        }
        position.setStatus(STATUS_ARCHIVED);
        positionMapper.updateById(position);
        knowledgeBaseMapper.update(null, new UpdateWrapper<KnowledgeBase>()
                .eq("position_id", positionId)
                .eq("scope", SCOPE_PRIVATE)
                .eq("owner_user_id", currentUserId)
                .set("status", STATUS_ARCHIVED));
    }

    private KnowledgePositionResponse toPositionResponse(InterviewPosition position,
                                                         KnowledgeBase knowledgeBase,
                                                         List<KnowledgeSourceFile> sourceFiles,
                                                         Long currentUserId) {
        boolean editable = SCOPE_PRIVATE.equalsIgnoreCase(position.getScope())
                && currentUserId.equals(position.getOwnerUserId())
                && !STATUS_ARCHIVED.equalsIgnoreCase(position.getStatus());
        KnowledgeBaseResponse knowledgeBaseResponse = knowledgeBase == null ? null : new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getScope(),
                knowledgeBase.getOwnerUserId(),
                knowledgeBase.getPositionId(),
                knowledgeBase.getName(),
                knowledgeBase.getStatus(),
                sourceFiles.stream().map(KnowledgeSourceFileResponse::from).toList()
        );
        return new KnowledgePositionResponse(
                position.getId(),
                position.getScope(),
                position.getOwnerUserId(),
                position.getName(),
                position.getDescription(),
                position.getStatus(),
                editable,
                knowledgeBaseResponse
        );
    }

    private void requireUser(Long currentUserId) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }
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
