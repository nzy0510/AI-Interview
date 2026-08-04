package com.interview.service.orchestration;

import com.interview.dto.MentorInsightResponse;
import com.interview.dto.ResumeProfileResponse;
import com.interview.entity.InterviewPhase;
import com.interview.service.InterviewRetrievalService;
import com.interview.service.InterviewTurnPlanner;
import com.interview.service.MentorService;
import com.interview.service.ResumeService;
import com.interview.service.UserLlmModelFactory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tool Calling 面试编排器")
class ToolCallingInterviewOrchestratorTest {

  @Mock
  private InterviewRetrievalService retrievalService;

  @Mock
  private ResumeService resumeService;

  @Mock
  private MentorService mentorService;

  @Mock
  private InterviewTurnPlanner turnPlanner;

  @BeforeEach
  void setUp() {
    lenient().when(turnPlanner.planForPhase(any(), any(), eq(""), anyList()))
        .thenAnswer(invocation -> {
          InterviewPhase phase = invocation.getArgument(1);
          return new InterviewTurnPlanner.InterviewTurnPlan(
              phase, phase.name() + "_PROMPT");
        });
  }

  @Test
  @DisplayName("工具调用成功后把岗位证据和 DEEPEN 动作写入最终 prompt")
  void successfulSearchShouldChangePrompt() {
    when(retrievalService.retrieve(eq(7L), any(), anyList(), eq("Redis"),
        eq(InterviewPhase.TECHNICAL), eq(List.of("atom-used"))))
        .thenReturn(new InterviewRetrievalService.TurnRetrieval(
            "Redis 证据：连接池耗尽时应先检查借还是否闭合", List.of("atom-redis"),
            List.of("atom-redis")));

    InterviewTurnPlan plan = orchestrator(new ScriptedChatModel(List.of(
        toolCall("searchPositionKnowledge", "{\"query\":\"Redis\"}"),
        jsonDecision("DEEPEN", "需要继续深挖", "继续追问连接池边界")))).plan(request(3, InterviewPhase.TECHNICAL));

    assertThat(plan.action()).isEqualTo(InterviewAction.DEEPEN);
    assertThat(plan.phase()).isEqualTo(InterviewPhase.TECHNICAL);
    assertThat(plan.toolsUsed()).containsExactly("searchPositionKnowledge");
    assertThat(plan.evidenceContext()).contains("Redis 证据");
    assertThat(plan.systemPrompt()).contains("Redis 证据").contains("继续深挖当前知识点");
    verify(retrievalService).retrieve(eq(7L), any(), anyList(), eq("Redis"),
        eq(InterviewPhase.TECHNICAL), eq(List.of("atom-used")));
  }

  @Test
  @DisplayName("简历工具证据进入 PROBE_RESUME 最终 prompt")
  void resumeEvidenceShouldChangePrompt() {
    ResumeProfileResponse profile = new ResumeProfileResponse();
    profile.setAnalysis(Map.of("project", "支付系统", "evidence", "处理过 Redis 热 key"));
    when(resumeService.getProfileByUserIdAndPosition(7L, 8L)).thenReturn(profile);

    InterviewTurnPlan plan = orchestrator(new ScriptedChatModel(List.of(
        toolCall("getCurrentResumeEvidence", "{}"),
        jsonDecision("PROBE_RESUME", "简历有相关证据", "请说明项目中的具体处理")))).plan(
        request(4, InterviewPhase.TECHNICAL));

    assertThat(plan.action()).isEqualTo(InterviewAction.PROBE_RESUME);
    assertThat(plan.evidenceContext()).contains("支付系统").contains("Redis 热 key");
    assertThat(plan.systemPrompt()).contains("简历证据").contains("支付系统")
        .contains("简历工具返回的证据");
    verify(resumeService).getProfileByUserIdAndPosition(7L, 8L);
  }

  @Test
  @DisplayName("覆盖率工具证据进入 REMEDIATE 最终 prompt")
  void coverageEvidenceShouldChangePrompt() {
    MentorInsightResponse insight = new MentorInsightResponse();
    MentorInsightResponse.KnowledgeCoverage coverage = new MentorInsightResponse.KnowledgeCoverage();
    coverage.setCoveragePercent(42.5);
    insight.setKnowledgeCoverage(coverage);
    when(mentorService.getKnowledgeCoverageOnly(7L, 8L)).thenReturn(insight);

    InterviewTurnPlan plan = orchestrator(new ScriptedChatModel(List.of(
        toolCall("getPositionLearningCoverage", "{}"),
        jsonDecision("REMEDIATE", "覆盖率偏低", "先补齐薄弱领域")))).plan(
        request(5, InterviewPhase.TECHNICAL));

    assertThat(plan.action()).isEqualTo(InterviewAction.REMEDIATE);
    assertThat(plan.evidenceContext()).contains("42.5");
    assertThat(plan.systemPrompt()).contains("学习覆盖率").contains("补救追问");
    verify(mentorService).getKnowledgeCoverageOnly(7L, 8L);
  }

  @Test
  @DisplayName("没有简历工具证据时拒绝执行 PROBE_RESUME")
  void probeResumeWithoutEvidenceShouldContinuePhase() {
    InterviewTurnPlan plan = orchestrator(new ScriptedChatModel(List.of(
        jsonDecision("PROBE_RESUME", "猜测有项目", "追问项目")))).plan(
        request(4, InterviewPhase.TECHNICAL));

    assertThat(plan.action()).isEqualTo(InterviewAction.CONTINUE_PHASE);
    assertThat(plan.systemPrompt()).contains("保持当前技术阶段");
    assertThat(plan.publicSummary()).contains("保持当前阶段");
  }

  @Test
  @DisplayName("达到技术轮次门槛时 MOVE_TO_HR 改变输出阶段")
  void moveToHrShouldChangePhaseAfterThreshold() {
    InterviewTurnPlan plan = orchestrator(new ScriptedChatModel(List.of(
        jsonDecision("MOVE_TO_HR", "技术证据充分", "进入下一阶段")))).plan(
        request(6, InterviewPhase.TECHNICAL));

    assertThat(plan.action()).isEqualTo(InterviewAction.MOVE_TO_HR);
    assertThat(plan.phase()).isEqualTo(InterviewPhase.HR);
    assertThat(plan.systemPrompt())
        .contains("HR_PROMPT", "转入 HR 阶段")
        .doesNotContain("TECHNICAL_PROMPT");
  }

  @Test
  @DisplayName("过早 MOVE_TO_HR 会切换知识点并保持技术阶段")
  void earlyMoveToHrShouldSwitchTopic() {
    InterviewTurnPlan plan = orchestrator(new ScriptedChatModel(List.of(
        jsonDecision("MOVE_TO_HR", "过早", "继续技术面试")))).plan(
        request(5, InterviewPhase.TECHNICAL));

    assertThat(plan.action()).isEqualTo(InterviewAction.SWITCH_TOPIC);
    assertThat(plan.phase()).isEqualTo(InterviewPhase.TECHNICAL);
    assertThat(plan.systemPrompt()).contains("切换到当前岗位的另一个知识点");
  }

  @Test
  @DisplayName("非法 JSON 统一转换为稳定 reasonCode")
  void invalidJsonShouldFailClosed() {
    assertThatThrownBy(() -> orchestrator(new ScriptedChatModel(List.of(
        new AiMessage("不是 JSON")))).plan(request(2, InterviewPhase.TECHNICAL)))
        .isInstanceOf(AgentPlanningException.class)
        .satisfies(error -> assertThat(((AgentPlanningException) error).reasonCode())
            .isEqualTo(ToolCallingInterviewOrchestrator.AGENT_INVALID_JSON));
  }

  @Test
  @DisplayName("非技术阶段不启动 Agent")
  void nonTechnicalPhaseShouldBeUnavailable() {
    AtomicBoolean modelResolved = new AtomicBoolean();
    ToolCallingInterviewOrchestrator orchestrator = newOrchestrator(
        userId -> {
          modelResolved.set(true);
          return new ScriptedChatModel(List.of(jsonDecision("DEEPEN", "", "")));
        });

    assertThatThrownBy(() -> orchestrator.plan(request(2, InterviewPhase.HR)))
        .isInstanceOf(AgentPlanningException.class)
        .satisfies(error -> assertThat(((AgentPlanningException) error).reasonCode())
            .isEqualTo(ToolCallingInterviewOrchestrator.AGENT_UNAVAILABLE_PHASE));
    assertThat(modelResolved).isFalse();
  }

  @Test
  @DisplayName("工具调用最多三次且工具签名不暴露 ID 参数")
  void toolLimitAndScopedSignaturesShouldHold() {
    ScriptedChatModel model = new ScriptedChatModel(List.of(
        toolCall("getPositionLearningCoverage", "{}"),
        toolCall("getCurrentResumeEvidence", "{}"),
        toolCall("searchPositionKnowledge", "{\"query\":\"x\"}"),
        toolCall("searchPositionKnowledge", "{\"query\":\"y\"}"),
        jsonDecision("CONTINUE_PHASE", "", "继续")));

    assertThatThrownBy(() -> orchestrator(model).plan(request(2, InterviewPhase.TECHNICAL)))
        .isInstanceOf(AgentPlanningException.class)
        .satisfies(error -> assertThat(((AgentPlanningException) error).reasonCode())
            .isEqualTo(ToolCallingInterviewOrchestrator.AGENT_TOOL_LIMIT));
    assertThat(model.calls()).isGreaterThanOrEqualTo(4);

    Class<?> toolsClass = Arrays.stream(ToolCallingInterviewOrchestrator.class.getDeclaredClasses())
        .filter(type -> type.getSimpleName().equals("BoundTools"))
        .findFirst().orElseThrow();
    Method search = Arrays.stream(toolsClass.getDeclaredMethods())
        .filter(method -> method.getName().equals("searchPositionKnowledge"))
        .findFirst().orElseThrow();
    Method resume = Arrays.stream(toolsClass.getDeclaredMethods())
        .filter(method -> method.getName().equals("getCurrentResumeEvidence"))
        .findFirst().orElseThrow();
    Method coverage = Arrays.stream(toolsClass.getDeclaredMethods())
        .filter(method -> method.getName().equals("getPositionLearningCoverage"))
        .findFirst().orElseThrow();
    assertThat(search.getParameterCount()).isEqualTo(1);
    assertThat(resume.getParameterCount()).isZero();
    assertThat(coverage.getParameterCount()).isZero();
    assertThat(Arrays.stream(search.getParameterTypes())
        .noneMatch(type -> type.equals(Long.class) || type.equals(long.class))).isTrue();
  }

  @Test
  @DisplayName("规划提示要求每轮优先只调用一个最相关工具")
  void planningPromptShouldPreferOneRelevantToolPerTurn() {
    ScriptedChatModel model = new ScriptedChatModel(List.of(
        jsonDecision("CONTINUE_PHASE", "证据已足够", "继续当前阶段")));

    orchestrator(model).plan(request(2, InterviewPhase.TECHNICAL));

    assertThat(model.systemPrompts())
        .anySatisfy(prompt -> assertThat(prompt)
            .contains("每轮优先只调用一个最相关工具")
            .contains("第一个工具明确无可用证据")
            .contains("禁止为了收集完整信息依次调用全部工具"));
  }

  @Test
  @DisplayName("未知动作统一转换为稳定 reasonCode")
  void unknownActionShouldFailClosed() {
    assertThatThrownBy(() -> orchestrator(new ScriptedChatModel(List.of(
        jsonDecision("NOT_ALLOWED", "", "")))).plan(request(2, InterviewPhase.TECHNICAL)))
        .isInstanceOf(AgentPlanningException.class)
        .satisfies(error -> assertThat(((AgentPlanningException) error).reasonCode())
            .isEqualTo(ToolCallingInterviewOrchestrator.AGENT_UNKNOWN_ACTION));
  }

  @Test
  @DisplayName("公开摘要会移除 Provider URL 和密钥")
  void publicSummaryShouldBeSanitized() {
    InterviewTurnPlan plan = orchestrator(new ScriptedChatModel(List.of(
        jsonDecision("CONTINUE_PHASE", "ok",
            "继续面试 api_key=top-secret https://provider.example/v1")))).plan(
        request(2, InterviewPhase.TECHNICAL));

    assertThat(plan.publicSummary())
        .contains("api_key=[REDACTED]", "[URL]")
        .doesNotContain("top-secret", "provider.example");
  }

  private ToolCallingInterviewOrchestrator orchestrator(ScriptedChatModel model) {
    return newOrchestrator(userId -> model);
  }

  private ToolCallingInterviewOrchestrator newOrchestrator(
      java.util.function.Function<Long, ChatLanguageModel> modelResolver) {
    return new ToolCallingInterviewOrchestrator(
        retrievalService,
        resumeService,
        mentorService,
        turnPlanner,
        modelResolver);
  }

  private InterviewTurnRequest request(int turnIndex, InterviewPhase phase) {
    return new InterviewTurnRequest(
        9L, 7L, 8L, "Java 后端开发", phase, turnIndex, "mid",
        List.of("architecture"), List.of(), "我使用过 Redis", List.of("atom-used"), List.of());
  }

  private static AiMessage toolCall(String name, String arguments) {
    ToolExecutionRequest request = ToolExecutionRequest.builder()
        .id("call-" + name)
        .name(name)
        .arguments(arguments)
        .build();
    return AiMessage.from(request);
  }

  private static AiMessage jsonDecision(String action, String reason, String publicSummary) {
    return new AiMessage("{\"action\":\"" + action + "\",\"reason\":\""
        + reason + "\",\"publicSummary\":\"" + publicSummary + "\"}");
  }

  private static final class ScriptedChatModel implements ChatLanguageModel {
    private final List<AiMessage> responses;
    private final List<List<ToolSpecification>> toolSpecifications = new ArrayList<>();
    private final List<String> systemPrompts = new ArrayList<>();
    private int index;

    private ScriptedChatModel(List<AiMessage> responses) {
      this.responses = responses;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
      captureSystemPrompts(messages);
      return next();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages,
                                        List<ToolSpecification> tools) {
      captureSystemPrompts(messages);
      toolSpecifications.add(tools);
      return next();
    }

    private void captureSystemPrompts(List<ChatMessage> messages) {
      messages.stream()
          .filter(SystemMessage.class::isInstance)
          .map(SystemMessage.class::cast)
          .map(SystemMessage::text)
          .forEach(systemPrompts::add);
    }

    private Response<AiMessage> next() {
      int responseIndex = Math.min(index++, responses.size() - 1);
      return Response.from(responses.get(responseIndex), new TokenUsage(1, 1));
    }

    private int calls() {
      return index;
    }

    private List<String> systemPrompts() {
      return List.copyOf(systemPrompts);
    }
  }
}
