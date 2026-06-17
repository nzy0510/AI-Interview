package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.VisiblePositionResponse;
import com.interview.entity.InterviewPosition;
import com.interview.entity.InterviewRecord;
import com.interview.entity.ResumeProfile;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.ResumeProfileMapper;
import com.interview.service.PositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PositionServiceImpl implements PositionService {

    @Autowired
    private InterviewPositionMapper positionMapper;

    @Autowired
    private InterviewRecordMapper recordMapper;

    @Autowired
    private ResumeProfileMapper resumeProfileMapper;

    @Override
    public List<VisiblePositionResponse> getVisiblePositions(Long userId) {
        if (userId == null) {
            throw new RuntimeException("未登录：缺少用户身份");
        }

        // 1. 查询所有 ACTIVE 状态且可见的岗位（公共 + 用户私有）
        LambdaQueryWrapper<InterviewPosition> positionQuery = new LambdaQueryWrapper<>();
        positionQuery.eq(InterviewPosition::getStatus, "ACTIVE")
                .and(wrapper -> wrapper
                        .eq(InterviewPosition::getScope, "PUBLIC")
                        .or(nested -> nested
                                .eq(InterviewPosition::getScope, "PRIVATE")
                                .eq(InterviewPosition::getOwnerUserId, userId)));
        List<InterviewPosition> positions = positionMapper.selectList(positionQuery);

        if (positions.isEmpty()) {
            return List.of();
        }

        // 2. 批量统计每个岗位下当前用户的已评分面试记录数
        List<Long> positionIds = positions.stream().map(InterviewPosition::getId).collect(Collectors.toList());
        Map<Long, Long> historyCountMap = countHistoryByPosition(userId, positionIds);

        // 3. 批量查询每个岗位下当前用户的简历画像状态（Phase 2 按 positionId 隔离）
        Map<Long, ResumeProfile> resumeProfileMap = loadResumeProfilesByPosition(userId, positionIds);

        // 4. 组装响应
        List<VisiblePositionResponse> result = new ArrayList<>();
        for (InterviewPosition pos : positions) {
            VisiblePositionResponse item = new VisiblePositionResponse();
            item.setId(pos.getId());
            item.setName(pos.getName());
            item.setScope(pos.getScope());
            item.setOwnerUserId(pos.getOwnerUserId());
            item.setHistoryCount(historyCountMap.getOrDefault(pos.getId(), 0L).intValue());
            ResumeProfile profile = resumeProfileMap.get(pos.getId());
            item.setHasResumeProfile(profile != null);
            item.setResumeUpdatedAt(profile != null ? profile.getUpdateTime() : null);
            result.add(item);
        }

        return result;
    }

    private Map<Long, Long> countHistoryByPosition(Long userId, List<Long> positionIds) {
        if (positionIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<InterviewRecord> query = new QueryWrapper<>();
        query.select("position_id, COUNT(*) as cnt")
                .eq("user_id", userId)
                .isNotNull("score")
                .in("position_id", positionIds)
                .groupBy("position_id");
        List<Map<String, Object>> rows = recordMapper.selectMaps(query);
        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("position_id")).longValue(),
                row -> ((Number) row.get("cnt")).longValue()
        ));
    }

    private Map<Long, ResumeProfile> loadResumeProfilesByPosition(Long userId, List<Long> positionIds) {
        if (positionIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<ResumeProfile> query = new LambdaQueryWrapper<>();
        query.eq(ResumeProfile::getUserId, userId)
                .in(ResumeProfile::getPositionId, positionIds);
        List<ResumeProfile> profiles = resumeProfileMapper.selectList(query);
        return profiles.stream().collect(Collectors.toMap(
                ResumeProfile::getPositionId,
                p -> p,
                (a, b) -> a.getUpdateTime().isAfter(b.getUpdateTime()) ? a : b
        ));
    }
}
