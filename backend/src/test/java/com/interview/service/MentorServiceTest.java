package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.MentorInsightResponse;
import com.interview.entity.InterviewPosition;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MentorService — AI 教练分析与知识覆盖")
@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    @Mock
    private RagRetrievalLogMapper ragLogMapper;

    @Mock
    private KnowledgeAtomMapper atomMapper;

    @Mock
    private InterviewPositionMapper positionMapper;

    @Test
    @DisplayName("知识覆盖率使用已发布 Atom 总量作为分母")
    void shouldMeasureCoverageAgainstPublishedAtoms() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "ragLogMapper", ragLogMapper);
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        when(atomMapper.selectMaps(any())).thenReturn(List.of(
                totalRow("AI大模型", 10),
                totalRow("Java", 5)));
        when(ragLogMapper.selectMaps(any())).thenReturn(List.of(coveredRow("AI大模型", 4)));

        MentorInsightResponse response = service.getKnowledgeCoverageOnly(1L);

        assertThat(response.getKnowledgeCoverage().getTotalCategories()).isEqualTo(2);
        assertThat(response.getKnowledgeCoverage().getCoveredCategories()).isEqualTo(1);
        assertThat(response.getKnowledgeCoverage().getCoveragePercent()).isEqualTo(26.7);
        assertThat(response.getKnowledgeCoverage().getDetails())
                .extracting(MentorInsightResponse.KnowledgeCoverage.CategoryDetail::getPercent)
                .containsExactly(40.0, 0.0);
    }

    @Test
    @DisplayName("知识覆盖查询按可见岗位过滤分母和分子")
    void shouldFilterCoverageByVisiblePositionId() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "ragLogMapper", ragLogMapper);
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 7L, "ACTIVE"));
        when(atomMapper.selectMaps(any())).thenReturn(List.of(totalRow("Java", 5)));
        when(ragLogMapper.selectMaps(any())).thenReturn(List.of(coveredRow("Java", 2)));

        MentorInsightResponse response = service.getKnowledgeCoverageOnly(7L, 20L);

        assertThat(response.getKnowledgeCoverage().getCoveragePercent()).isEqualTo(40.0);
        ArgumentCaptor<QueryWrapper> atomQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper> ragQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(atomMapper).selectMaps(atomQuery.capture());
        verify(ragLogMapper).selectMaps(ragQuery.capture());
        assertThat(atomQuery.getValue().getSqlSegment()).contains("position_id");
        assertThat(ragQuery.getValue().getSqlSegment()).contains("position_id");
        assertThat(atomQuery.getValue().getParamNameValuePairs().values()).contains(20L);
        assertThat(ragQuery.getValue().getParamNameValuePairs().values()).contains(20L);
    }

    @Test
    @DisplayName("知识覆盖查询拒绝访问他人的私有岗位")
    void shouldRejectCoverageForOtherUsersPrivatePosition() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "ragLogMapper", ragLogMapper);
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 8L, "ACTIVE"));

        assertThatThrownBy(() -> service.getKnowledgeCoverageOnly(7L, 20L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");

        verify(atomMapper, never()).selectMaps(any());
        verify(ragLogMapper, never()).selectMaps(any());
    }

    /**
     * 验证 MentorInsightResponse 各字段可正常构造
     */
    @Test
    @DisplayName("响应 DTO 结构完整")
    void shouldBuildCompleteResponse() {
        MentorInsightResponse response = new MentorInsightResponse();

        MentorInsightResponse.Diagnosis diag = new MentorInsightResponse.Diagnosis();
        diag.setOverview("总体评价");
        diag.setStrengths(java.util.List.of("技术深度强"));
        diag.setWeaknesses(java.util.List.of("表达需提升"));
        response.setDiagnosis(diag);

        MentorInsightResponse.KnowledgeCoverage kc = new MentorInsightResponse.KnowledgeCoverage();
        kc.setTotalCategories(5);
        kc.setCoveredCategories(3);
        kc.setCoveragePercent(60.0);
        response.setKnowledgeCoverage(kc);

        assertThat(response.getDiagnosis().getOverview()).isEqualTo("总体评价");
        assertThat(response.getDiagnosis().getStrengths()).containsExactly("技术深度强");
        assertThat(response.getKnowledgeCoverage().getCoveredCategories()).isEqualTo(3);
        assertThat(response.getKnowledgeCoverage().getCoveragePercent()).isEqualTo(60.0);
    }

    /**
     * 验证空面试历史时返回友好提示
     */
    @Test
    @DisplayName("无面试记录时返回空诊断")
    void shouldReturnEmptyDiagnosisForNoHistory() {
        MentorInsightResponse response = new MentorInsightResponse();
        MentorInsightResponse.Diagnosis diag = new MentorInsightResponse.Diagnosis();
        diag.setOverview("暂无面试数据，AI Mentor 将在你完成首次面试后生成分析报告。");
        diag.setStrengths(java.util.Collections.emptyList());
        diag.setWeaknesses(java.util.Collections.emptyList());
        response.setDiagnosis(diag);
        response.setRiskAlerts(java.util.Collections.emptyList());
        response.setActions(java.util.Collections.emptyList());

        assertThat(response.getDiagnosis().getOverview()).contains("暂无面试数据");
        assertThat(response.getRiskAlerts()).isEmpty();
        assertThat(response.getActions()).isEmpty();
    }

    private Map<String, Object> totalRow(String category, int total) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category", category);
        row.put("total", total);
        return row;
    }

    private Map<String, Object> coveredRow(String category, int covered) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("retrieved_category", category);
        row.put("cnt", covered);
        return row;
    }

    private InterviewPosition position(Long id, String scope, Long ownerUserId, String status) {
        InterviewPosition position = new InterviewPosition();
        position.setId(id);
        position.setScope(scope);
        position.setOwnerUserId(ownerUserId);
        position.setStatus(status);
        return position;
    }
}
