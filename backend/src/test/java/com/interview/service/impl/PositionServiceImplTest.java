package com.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.dto.VisiblePositionResponse;
import com.interview.entity.InterviewPosition;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.ResumeProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PositionServiceImpl — 岗位选择可见性")
class PositionServiceImplTest {

    private InterviewPositionMapper positionMapper;
    private InterviewRecordMapper recordMapper;
    private ResumeProfileMapper resumeProfileMapper;
    private QuestionBankAccessProperties accessProperties;

    @BeforeEach
    void setUp() {
        positionMapper = mock(InterviewPositionMapper.class);
        recordMapper = mock(InterviewRecordMapper.class);
        resumeProfileMapper = mock(ResumeProfileMapper.class);
        accessProperties = new QuestionBankAccessProperties();
        when(recordMapper.selectMaps(any())).thenReturn(List.of());
        when(resumeProfileMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("用户题库维护关闭时岗位选择只返回公共 starter 岗位")
    void shouldOnlyExposePublicPositionsWhenMaintenanceDisabled() {
        accessProperties.setUserMaintenanceEnabled(false);
        when(positionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                position(1L, "PUBLIC", null, "Java 后端开发"),
                position(2L, "PRIVATE", 7L, "我的私有岗位")
        ));
        PositionServiceImpl service = new PositionServiceImpl(
                positionMapper, recordMapper, resumeProfileMapper, accessProperties);

        List<VisiblePositionResponse> result = service.getVisiblePositions(7L);

        assertThat(result)
                .extracting(VisiblePositionResponse::getScope)
                .containsExactly("PUBLIC");
    }

    @Test
    @DisplayName("开关开启时保留公共岗位和当前用户私有岗位")
    void shouldPreserveOwnedPrivatePositionsWhenMaintenanceEnabled() {
        accessProperties.setUserMaintenanceEnabled(true);
        when(positionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                position(1L, "PUBLIC", null, "Java 后端开发"),
                position(2L, "PRIVATE", 7L, "我的私有岗位")
        ));
        PositionServiceImpl service = new PositionServiceImpl(
                positionMapper, recordMapper, resumeProfileMapper, accessProperties);

        List<VisiblePositionResponse> result = service.getVisiblePositions(7L);

        assertThat(result)
                .extracting(VisiblePositionResponse::getScope)
                .containsExactly("PUBLIC", "PRIVATE");
    }

    private InterviewPosition position(Long id, String scope, Long ownerUserId, String name) {
        InterviewPosition position = new InterviewPosition();
        position.setId(id);
        position.setScope(scope);
        position.setOwnerUserId(ownerUserId);
        position.setName(name);
        position.setStatus("ACTIVE");
        return position;
    }
}
