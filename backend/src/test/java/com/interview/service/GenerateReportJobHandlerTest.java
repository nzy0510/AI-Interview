package com.interview.service;

import com.interview.entity.AppJob;
import com.interview.entity.InterviewRecord;
import com.interview.entity.InterviewReport;
import com.interview.entity.InterviewReportItem;
import com.interview.entity.InterviewTurn;
import com.interview.exception.LlmProviderRequiredException;
import com.interview.mapper.InterviewRecordMapper;
import com.interview.mapper.InterviewReportItemMapper;
import com.interview.mapper.InterviewReportMapper;
import com.interview.mapper.InterviewTurnMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("GenerateReportJobHandler — async interview report")
@ExtendWith(MockitoExtension.class)
class GenerateReportJobHandlerTest {

    @Mock
    private AppJobService appJobService;

    @Mock
    private InterviewRecordMapper recordMapper;

    @Mock
    private InterviewReportMapper reportMapper;

    @Mock
    private InterviewReportItemMapper reportItemMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private UserLlmConfigService userLlmConfigService;

    @Mock
    private UserLlmModelFactory userLlmModelFactory;

    @Mock
    private OpenAiChatModel chatModel;

    @Test
    @DisplayName("调用用户 LLM 生成综合参考答案、逐题评分并汇总详细总分")
    void shouldGenerateReportItemsWithLlmReferenceAnswerAndScoreRubric() {
        InterviewRecord record = record();
        record.setScore(86);
        record.setFeedback("整体反馈");
        record.setAbilityJson("{\"techDepth\":\"A\"}");
        record.setRecommendations("[{\"action\":\"复盘\"}]");
        when(recordMapper.selectById(10L)).thenReturn(record);
        when(reportMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            InterviewReport report = invocation.getArgument(0);
            report.setId(55L);
            return 1;
        }).when(reportMapper).insert(any());
        when(turnMapper.selectList(any())).thenReturn(List.of(turn()));
        UserLlmRuntimeConfig runtimeConfig = new UserLlmRuntimeConfig(
                1L, 1L, "deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "sk-test", 0.7);
        when(userLlmConfigService.requireActiveRuntimeConfig(1L)).thenReturn(runtimeConfig);
        when(userLlmModelFactory.createChatModel(runtimeConfig)).thenReturn(chatModel);
        when(chatModel.generate(anyList())).thenReturn(Response.from(new AiMessage("""
                {
                  "summary": "详细报告总结",
                  "items": [
                    {
                      "turnId": 20,
                      "score": 7.5,
                      "referenceAnswer": "RAG 的参考答案应综合说明：先检索相关知识，再把证据注入提示词，最后由模型基于证据生成回答，并说明召回质量会影响生成质量。",
                      "scoreBreakdown": {
                        "relevance": 2,
                        "correctness": 2,
                        "depth": 1.5,
                        "practicality": 1,
                        "communication": 1
                      },
                      "improvementSuggestion": "回答方向正确，但缺少检索、重排、上下文注入和质量边界。"
                    }
                  ]
                }
                """)));

        AppJob job = job();
        handler().handle(job);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        verify(recordMapper, never()).updateById(record);
        verify(reportMapper, org.mockito.Mockito.atLeastOnce()).updateById(reportCaptor.getValue());
        ArgumentCaptor<InterviewReportItem> itemCaptor = ArgumentCaptor.forClass(InterviewReportItem.class);
        verify(reportItemMapper).insert(itemCaptor.capture());
        assertThat(reportCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(reportCaptor.getValue().getOverallScore()).isEqualTo(75);
        assertThat(reportCaptor.getValue().getSummary()).isEqualTo("详细报告总结");
        assertThat(reportCaptor.getValue().getModelProvider()).isEqualTo("deepseek");
        assertThat(reportCaptor.getValue().getModelName()).isEqualTo("deepseek-chat");
        assertThat(itemCaptor.getValue().getScore()).isEqualByComparingTo("7.50");
        assertThat(itemCaptor.getValue().getReferenceAnswer()).startsWith("RAG 的参考答案应综合说明");
        assertThat(itemCaptor.getValue().getReferenceAnswer()).doesNotContain("[atom_id:");
        assertThat(itemCaptor.getValue().getImprovementSuggestion())
                .contains("相关性 2/2")
                .contains("正确性 2/3")
                .contains("深度 1.5/2")
                .contains("实践性 1/2")
                .contains("表达 1/1")
                .contains("缺少检索、重排");
        assertThat(itemCaptor.getValue().getModelProvider()).isEqualTo("deepseek");
        assertThat(itemCaptor.getValue().getModelName()).isEqualTo("deepseek-chat");
        assertThat(itemCaptor.getValue().getMatchedAtomSnapshotJson()).contains("promptContext");
        assertThat(itemCaptor.getValue().getAnswerSource()).isEqualTo("KNOWLEDGE_BASE");
        assertThat(job.getResultJson()).contains("\"reportId\":55");
        verify(chatModel).generate(anyList());
    }

    @Test
    @DisplayName("评分细则分项会按 rubic 上限截断后展示")
    void shouldClampScoreBreakdownToRubricMaxima() {
        InterviewRecord record = record();
        record.setScore(86);
        record.setFeedback("整体反馈");
        when(recordMapper.selectById(10L)).thenReturn(record);
        when(reportMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            InterviewReport report = invocation.getArgument(0);
            report.setId(55L);
            return 1;
        }).when(reportMapper).insert(any());
        when(turnMapper.selectList(any())).thenReturn(List.of(turn()));
        UserLlmRuntimeConfig runtimeConfig = new UserLlmRuntimeConfig(
                1L, 1L, "deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "sk-test", 0.7);
        when(userLlmConfigService.requireActiveRuntimeConfig(1L)).thenReturn(runtimeConfig);
        when(userLlmModelFactory.createChatModel(runtimeConfig)).thenReturn(chatModel);
        when(chatModel.generate(anyList())).thenReturn(Response.from(new AiMessage("""
                {
                  "summary": "详细报告总结",
                  "items": [
                    {
                      "turnId": 20,
                      "score": 8.8,
                      "referenceAnswer": "综合参考答案",
                      "scoreBreakdown": {
                        "relevance": 9,
                        "correctness": 5,
                        "depth": 3,
                        "practicality": 4,
                        "communication": 2
                      },
                      "improvementSuggestion": "继续补充案例。"
                    }
                  ]
                }
                """)));

        handler().handle(job());

        ArgumentCaptor<InterviewReportItem> itemCaptor = ArgumentCaptor.forClass(InterviewReportItem.class);
        verify(reportItemMapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getImprovementSuggestion())
                .contains("相关性 2/2")
                .contains("正确性 3/3")
                .contains("深度 2/2")
                .contains("实践性 2/2")
                .contains("表达 1/1");
    }

    @Test
    @DisplayName("LLM 返回坏 JSON 时详细报告失败且不写入逐题 item")
    void shouldFailDetailedReportWhenLlmReturnsInvalidJson() {
        InterviewRecord record = record();
        record.setScore(70);
        record.setFeedback("初步反馈");
        when(recordMapper.selectById(10L)).thenReturn(record);
        InterviewReport existing = new InterviewReport();
        existing.setId(55L);
        existing.setRecordId(10L);
        when(reportMapper.selectOne(any())).thenReturn(existing);
        when(turnMapper.selectList(any())).thenReturn(List.of(turn()));
        UserLlmRuntimeConfig runtimeConfig = new UserLlmRuntimeConfig(
                1L, 1L, "deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "sk-test", 0.7);
        when(userLlmConfigService.requireActiveRuntimeConfig(1L)).thenReturn(runtimeConfig);
        when(userLlmModelFactory.createChatModel(runtimeConfig)).thenReturn(chatModel);
        when(chatModel.generate(anyList())).thenReturn(Response.from(new AiMessage("不是 JSON")));

        assertThatThrownBy(() -> handler().handle(job()))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper, org.mockito.Mockito.atLeastOnce()).updateById(reportCaptor.capture());
        InterviewReport finalReport = reportCaptor.getAllValues().get(reportCaptor.getAllValues().size() - 1);
        assertThat(finalReport.getStatus()).isEqualTo("FAILED");
        verify(reportItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("缺少初步报告时同步标记 report 为 FAILED")
    void shouldMarkReportFailedWhenGenerationFails() {
        InterviewRecord record = record();
        when(recordMapper.selectById(10L)).thenReturn(record);
        InterviewReport existing = new InterviewReport();
        existing.setId(55L);
        existing.setRecordId(10L);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> handler().handle(job()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("初步报告");

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper, org.mockito.Mockito.atLeastOnce()).updateById(reportCaptor.capture());
        assertThat(reportCaptor.getAllValues().get(reportCaptor.getAllValues().size() - 1).getStatus())
                .isEqualTo("FAILED");
    }

    @Test
    @DisplayName("未配置用户 LLM 时详细报告失败而不是降级为原子堆砌")
    void shouldFailDetailedReportWhenUserLlmIsMissing() {
        InterviewRecord record = record();
        record.setScore(70);
        record.setFeedback("初步反馈");
        when(recordMapper.selectById(10L)).thenReturn(record);
        InterviewReport existing = new InterviewReport();
        existing.setId(55L);
        existing.setRecordId(10L);
        when(reportMapper.selectOne(any())).thenReturn(existing);
        when(turnMapper.selectList(any())).thenReturn(List.of(turn()));
        when(userLlmConfigService.requireActiveRuntimeConfig(1L)).thenThrow(new LlmProviderRequiredException());

        assertThatThrownBy(() -> handler().handle(job()))
                .isInstanceOf(LlmProviderRequiredException.class);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper, org.mockito.Mockito.atLeastOnce()).updateById(reportCaptor.capture());
        InterviewReport finalReport = reportCaptor.getAllValues().get(reportCaptor.getAllValues().size() - 1);
        assertThat(finalReport.getStatus()).isEqualTo("FAILED");
        verify(reportItemMapper, never()).insert(any());
        verify(userLlmModelFactory, never()).createChatModel(any());
    }

    private GenerateReportJobHandler handler() {
        return new GenerateReportJobHandler(
                appJobService, recordMapper, reportMapper, reportItemMapper, turnMapper,
                userLlmConfigService, userLlmModelFactory);
    }

    private AppJob job() {
        AppJob job = new AppJob();
        job.setId(900L);
        job.setRecordId(10L);
        job.setClaimedBy("worker");
        return job;
    }

    private InterviewRecord record() {
        InterviewRecord record = new InterviewRecord();
        record.setId(10L);
        record.setUserId(1L);
        record.setPositionId(101L);
        record.setVoiceWpm(120);
        record.setChatHistory("[{\"type\":\"AI\",\"text\":\"解释 RAG\"},{\"type\":\"USER\",\"text\":\"检索增强生成\"}]");
        return record;
    }

    private InterviewTurn turn() {
        InterviewTurn turn = new InterviewTurn();
        turn.setId(20L);
        turn.setRecordId(10L);
        turn.setTurnIndex(1);
        turn.setPhase("TECHNICAL");
        turn.setAiQuestion("解释 RAG");
        turn.setUserAnswer("检索增强生成");
        turn.setRetrievedAtomIds("[\"rag-flow\"]");
        turn.setContextSnapshotJson("""
                {"promptContext":"1. [atom_id: rag-flow]\\n考核点: RAG 流程\\n核心原理与标准答案: RAG 先召回知识，再注入上下文生成。\\n面试常见陷阱与候选人易错点: 不要把 RAG 说成模型训练。"}
                """);
        return turn;
    }
}
