package com.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.interview.dto.MentorInsightResponse;
import com.interview.config.QuestionBankAccessProperties;
import com.interview.entity.InterviewPosition;
import com.interview.entity.InterviewRecord;
import com.interview.entity.InterviewTurn;
import com.interview.entity.KnowledgeAtom;
import com.interview.mapper.InterviewPositionMapper;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.InterviewTurnMapper;
import com.interview.mapper.KnowledgeAtomMapper;
import com.interview.mapper.RagRetrievalLogMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private InterviewTurnMapper interviewTurnMapper;

    @Mock
    private InterviewRecordMapper recordMapper;

    @Mock
    private UserLlmConfigService userLlmConfigService;

    @Mock
    private UserLlmModelFactory userLlmModelFactory;

    @Mock
    private OpenAiChatModel chatLanguageModel;

    @Test
    @DisplayName("Mentor 洞察只聚合当前用户当前岗位的已评分历史")
    void shouldFilterMentorHistoryByUserAndPosition() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 7L, "ACTIVE"));
        when(atomMapper.selectMaps(any())).thenReturn(List.of());
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectList(any())).thenReturn(List.of());

        MentorInsightResponse response = service.getInsight(7L, 20L, false);

        assertThat(response.getDiagnosis().getOverview()).contains("暂无面试数据");
        ArgumentCaptor<QueryWrapper> historyQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(recordMapper).selectList(historyQuery.capture());
        assertThat(historyQuery.getValue().getSqlSegment())
                .contains("user_id", "position_id", "score", "create_time", "LIMIT 10");
        assertThat(historyQuery.getValue().getParamNameValuePairs().values()).contains(7L, 20L);
    }

    @Test
    @DisplayName("同一用户的不同岗位使用独立 Mentor 缓存")
    void shouldCacheMentorInsightPerPosition() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 7L, "ACTIVE"));
        when(positionMapper.selectById(21L)).thenReturn(position(21L, "PRIVATE", 7L, "ACTIVE"));
        when(atomMapper.selectMaps(any())).thenReturn(
                List.of(totalRow("Java", 5)),
                List.of(totalRow("AI大模型", 8)));
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectList(any())).thenReturn(List.of());

        MentorInsightResponse javaInsight = service.getInsight(7L, 20L, false);
        MentorInsightResponse aiInsight = service.getInsight(7L, 21L, false);

        assertThat(javaInsight.getKnowledgeCoverage().getDetails())
                .extracting(MentorInsightResponse.KnowledgeCoverage.CategoryDetail::getCategory)
                .containsExactly("Java");
        assertThat(aiInsight.getKnowledgeCoverage().getDetails())
                .extracting(MentorInsightResponse.KnowledgeCoverage.CategoryDetail::getCategory)
                .containsExactly("AI大模型");
    }

    @Test
    @DisplayName("强制刷新只失效目标岗位缓存")
    void shouldForceRefreshOnlyRequestedPositionCache() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 7L, "ACTIVE"));
        when(positionMapper.selectById(21L)).thenReturn(position(21L, "PRIVATE", 7L, "ACTIVE"));
        when(atomMapper.selectMaps(any())).thenReturn(
                List.of(totalRow("Java-v1", 5)),
                List.of(totalRow("AI大模型", 8)),
                List.of(totalRow("Java-v2", 6)));
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectList(any())).thenReturn(List.of());

        MentorInsightResponse javaV1 = service.getInsight(7L, 20L, false);
        MentorInsightResponse aiV1 = service.getInsight(7L, 21L, false);
        assertThat(service.getInsight(7L, 20L, false)).isSameAs(javaV1);

        MentorInsightResponse javaV2 = service.getInsight(7L, 20L, true);
        MentorInsightResponse aiV2 = service.getInsight(7L, 21L, false);

        assertThat(javaV2).isNotSameAs(javaV1);
        assertThat(javaV2.getKnowledgeCoverage().getDetails().get(0).getCategory()).isEqualTo("Java-v2");
        assertThat(aiV2).isSameAs(aiV1);
        verify(atomMapper, times(3)).selectMaps(any());
    }

    @Test
    @DisplayName("Mentor 提示词使用服务端校验后的岗位名称")
    void shouldIncludeValidatedPositionNameInMentorPrompt() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        ReflectionTestUtils.setField(service, "userLlmModelFactory", userLlmModelFactory);
        InterviewPosition position = position(20L, "PRIVATE", 7L, "ACTIVE");
        position.setName("Java 后端开发");
        when(positionMapper.selectById(20L)).thenReturn(position);
        when(atomMapper.selectMaps(any())).thenReturn(List.of());
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of());
        InterviewRecord record = new InterviewRecord();
        record.setPosition("过期岗位文本");
        record.setScore(80);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        UserLlmRuntimeConfig runtime = new UserLlmRuntimeConfig(
                1L, 7L, "test", "Test", "https://example.com/v1", "test-model", "secret", 0.7);
        when(userLlmConfigService.requireActiveRuntimeConfig(7L)).thenReturn(runtime);
        when(userLlmModelFactory.createChatModel(runtime)).thenReturn(chatLanguageModel);
        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(new AiMessage("""
                {"diagnosis":{"overview":"稳定","strengths":[],"weaknesses":[]},"riskAlerts":[],"actions":[]}
                """)));

        service.getInsight(7L, 20L, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(chatLanguageModel).generate(messages.capture());
        assertThat(((UserMessage) messages.getValue().get(1)).singleText())
                .contains("目标岗位: Java 后端开发")
                .doesNotContain("目标岗位: 过期岗位文本");
    }

    @Test
    @DisplayName("Provider 返回无效内容时应报告失败且不得缓存为空历史")
    void shouldRejectMalformedProviderResponseInsteadOfCachingEmptyHistory() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        ReflectionTestUtils.setField(service, "userLlmModelFactory", userLlmModelFactory);
        InterviewPosition position = position(20L, "PRIVATE", 7L, "ACTIVE");
        position.setName("AI 大模型应用开发");
        when(positionMapper.selectById(20L)).thenReturn(position);
        when(atomMapper.selectMaps(any())).thenReturn(List.of());
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of());
        InterviewRecord record = new InterviewRecord();
        record.setScore(80);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        UserLlmRuntimeConfig runtime = new UserLlmRuntimeConfig(
                1L, 7L, "test", "Test", "https://example.com/v1", "test-model", "secret", 0.7);
        when(userLlmConfigService.requireActiveRuntimeConfig(7L)).thenReturn(runtime);
        when(userLlmModelFactory.createChatModel(runtime)).thenReturn(chatLanguageModel);
        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(new AiMessage("null")));

        assertThatThrownBy(() -> service.getInsight(7L, 20L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("格式无效");
        assertThatThrownBy(() -> service.getInsight(7L, 20L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("格式无效");

        verify(chatLanguageModel, times(2)).generate(anyList());
    }

    @Test
    @DisplayName("同岗位并发强制刷新只生成一次并共享最新结果")
    void shouldSingleFlightConcurrentRefreshesForSamePosition() throws Exception {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        ReflectionTestUtils.setField(service, "userLlmModelFactory", userLlmModelFactory);
        InterviewPosition position = position(20L, "PUBLIC", null, "ACTIVE");
        position.setName("Java 后端开发");
        CountDownLatch bothValidated = new CountDownLatch(2);
        when(positionMapper.selectById(20L)).thenAnswer(invocation -> {
            bothValidated.countDown();
            return position;
        });
        when(atomMapper.selectMaps(any())).thenReturn(List.of());
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of());
        InterviewRecord record = new InterviewRecord();
        record.setScore(80);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        UserLlmRuntimeConfig runtime = new UserLlmRuntimeConfig(
                1L, 7L, "test", "Test", "https://example.com/v1", "test-model", "secret", 0.7);
        when(userLlmConfigService.requireActiveRuntimeConfig(7L)).thenReturn(runtime);
        when(userLlmModelFactory.createChatModel(runtime)).thenReturn(chatLanguageModel);
        when(chatLanguageModel.generate(anyList())).thenAnswer(invocation -> {
            assertThat(bothValidated.await(5, TimeUnit.SECONDS)).isTrue();
            return Response.from(new AiMessage("""
                    {"diagnosis":{"overview":"稳定","strengths":[],"weaknesses":[]},"riskAlerts":[],"actions":[]}
                    """));
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MentorInsightResponse> first = executor.submit(() -> service.getInsight(7L, 20L, true));
            Future<MentorInsightResponse> second = executor.submit(() -> service.getInsight(7L, 20L, true));

            assertThat(first.get(10, TimeUnit.SECONDS)).isSameAs(second.get(10, TimeUnit.SECONDS));
            verify(chatLanguageModel).generate(anyList());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("知识覆盖率使用已发布 Atom 总量作为分母")
    void shouldMeasureCoverageAgainstPublishedAtoms() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        when(atomMapper.selectMaps(any())).thenReturn(List.of(
                totalRow("AI大模型", 10),
                totalRow("Java", 5)));
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of(
                deliveredTurn("ai-1", "ai-2", "ai-3", "ai-4")));
        when(atomMapper.selectList(any())).thenReturn(List.of(
                atom("ai-1", "AI大模型"),
                atom("ai-2", "AI大模型"),
                atom("ai-3", "AI大模型"),
                atom("ai-4", "AI大模型")));

        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PUBLIC", null, "ACTIVE"));

        MentorInsightResponse response = service.getKnowledgeCoverageOnly(1L, 20L);

        assertThat(response.getKnowledgeCoverage().getTotalCategories()).isEqualTo(2);
        assertThat(response.getKnowledgeCoverage().getCoveredCategories()).isEqualTo(1);
        assertThat(response.getKnowledgeCoverage().getCoveragePercent()).isEqualTo(26.7);
        assertThat(response.getKnowledgeCoverage().getDetails())
                .extracting(MentorInsightResponse.KnowledgeCoverage.CategoryDetail::getPercent)
                .containsExactly(40.0, 0.0);
    }

    @Test
    @DisplayName("仅完成检索时不提前计入学习覆盖")
    void shouldIgnorePlanningOnlyRetrievalWhenMeasuringCoverage() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        when(atomMapper.selectMaps(any())).thenReturn(List.of(totalRow("Java", 5)));
        lenient().when(ragLogMapper.selectMaps(any())).thenReturn(List.of(coveredRow("Java", 1)));
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of());

        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PUBLIC", null, "ACTIVE"));

        MentorInsightResponse response = service.getKnowledgeCoverageOnly(1L, 20L);

        assertThat(response.getKnowledgeCoverage().getCoveredCategories()).isZero();
        assertThat(response.getKnowledgeCoverage().getCoveragePercent()).isEqualTo(0.0);
        verify(ragLogMapper, never()).selectMaps(any());
    }

    @Test
    @DisplayName("知识覆盖查询按可见岗位过滤分母和分子")
    void shouldFilterCoverageByVisiblePositionId() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 7L, "ACTIVE"));
        when(atomMapper.selectMaps(any())).thenReturn(List.of(totalRow("Java", 5)));
        when(interviewTurnMapper.selectList(any())).thenReturn(List.of(deliveredTurn("java-1", "java-2")));
        when(atomMapper.selectList(any())).thenReturn(List.of(
                atom("java-1", "Java"),
                atom("java-2", "Java")));

        MentorInsightResponse response = service.getKnowledgeCoverageOnly(7L, 20L);

        assertThat(response.getKnowledgeCoverage().getCoveragePercent()).isEqualTo(40.0);
        ArgumentCaptor<QueryWrapper> totalAtomQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper> deliveredAtomQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper> deliveredTurnQuery = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(atomMapper).selectMaps(totalAtomQuery.capture());
        verify(atomMapper).selectList(deliveredAtomQuery.capture());
        verify(interviewTurnMapper).selectList(deliveredTurnQuery.capture());
        assertThat(totalAtomQuery.getValue().getSqlSegment()).contains("position_id");
        assertThat(deliveredAtomQuery.getValue().getSqlSegment()).contains("position_id", "atom_id");
        assertThat(deliveredTurnQuery.getValue().getSqlSegment()).contains("user_id", "position_id");
        assertThat(totalAtomQuery.getValue().getParamNameValuePairs().values()).contains(20L);
        assertThat(deliveredAtomQuery.getValue().getParamNameValuePairs().values()).contains(20L);
        assertThat(deliveredTurnQuery.getValue().getParamNameValuePairs().values()).contains(7L, 20L);
    }

    @Test
    @DisplayName("知识覆盖查询拒绝访问他人的私有岗位")
    void shouldRejectCoverageForOtherUsersPrivatePosition() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "atomMapper", atomMapper);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "interviewTurnMapper", interviewTurnMapper);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 8L, "ACTIVE"));

        assertThatThrownBy(() -> service.getKnowledgeCoverageOnly(7L, 20L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");

        verify(atomMapper, never()).selectMaps(any());
        verify(interviewTurnMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("Mentor 洞察在调用 Provider 前拒绝他人的私有岗位")
    void shouldRejectInsightForOtherUsersPrivatePositionBeforeProviderCall() {
        MentorService service = new MentorService();
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 8L, "ACTIVE"));

        assertThatThrownBy(() -> service.getInsight(7L, 20L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");

        verifyNoInteractions(userLlmConfigService, atomMapper, interviewTurnMapper, recordMapper);
    }

    @Test
    @DisplayName("关闭用户岗位维护时拒绝访问自有私有岗位")
    void shouldRejectOwnPrivatePositionWhenUserMaintenanceIsDisabled() {
        MentorService service = new MentorService();
        QuestionBankAccessProperties properties = new QuestionBankAccessProperties();
        properties.setUserMaintenanceEnabled(false);
        ReflectionTestUtils.setField(service, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "questionBankAccessProperties", properties);
        ReflectionTestUtils.setField(service, "userLlmConfigService", userLlmConfigService);
        when(positionMapper.selectById(20L)).thenReturn(position(20L, "PRIVATE", 7L, "ACTIVE"));

        assertThatThrownBy(() -> service.getInsight(7L, 20L, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");

        verifyNoInteractions(userLlmConfigService);
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

    private InterviewTurn deliveredTurn(String... atomIds) {
        InterviewTurn turn = new InterviewTurn();
        turn.setRetrievedAtomIds(com.alibaba.fastjson2.JSON.toJSONString(atomIds));
        return turn;
    }

    private KnowledgeAtom atom(String atomId, String category) {
        KnowledgeAtom atom = new KnowledgeAtom();
        atom.setAtomId(atomId);
        atom.setCategory(category);
        return atom;
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
