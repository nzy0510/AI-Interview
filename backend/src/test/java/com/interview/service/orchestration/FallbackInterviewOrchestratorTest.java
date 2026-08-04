package com.interview.service.orchestration;

import com.interview.config.InterviewAgentProperties;
import com.interview.entity.InterviewPhase;
import com.interview.service.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("面试 Agent 稳定回退")
class FallbackInterviewOrchestratorTest {

  @Mock
  private InterviewOrchestrator agentOrchestrator;

  @Mock
  private InterviewOrchestrator ruleOrchestrator;

  @Mock
  private SessionStore sessionStore;

  @Mock
  private AsyncTaskExecutor taskExecutor;

  private InterviewAgentProperties properties;
  private FallbackInterviewOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    properties = new InterviewAgentProperties();
    orchestrator = new FallbackInterviewOrchestrator(
        agentOrchestrator, ruleOrchestrator, sessionStore, taskExecutor, properties);
  }

  @Test
  @DisplayName("Agent 关闭时直接使用稳定规则")
  void disabledAgentShouldUseStableRule() {
    properties.setEnabled(false);
    InterviewTurnPlan rulePlan = rulePlan(InterviewPhase.TECHNICAL);
    when(ruleOrchestrator.plan(any())).thenReturn(rulePlan);

    InterviewTurnPlan result = orchestrator.plan(request(InterviewPhase.TECHNICAL));

    assertThat(result).isSameAs(rulePlan);
    verifyNoInteractions(agentOrchestrator, taskExecutor);
  }

  @Test
  @DisplayName("非技术阶段不启动 Agent")
  void nonTechnicalPhaseShouldUseStableRule() {
    InterviewTurnPlan rulePlan = rulePlan(InterviewPhase.HR);
    when(ruleOrchestrator.plan(any())).thenReturn(rulePlan);

    InterviewTurnPlan result = orchestrator.plan(request(InterviewPhase.HR));

    assertThat(result).isSameAs(rulePlan);
    verifyNoInteractions(agentOrchestrator, taskExecutor);
  }

  @Test
  @DisplayName("技术阶段正常返回 Agent 计划")
  void technicalPhaseShouldReturnAgentPlan() throws Exception {
    InterviewTurnPlan agentPlan = agentPlan();
    when(agentOrchestrator.plan(any())).thenReturn(agentPlan);
    executeSubmittedCallableImmediately();

    InterviewTurnPlan result = orchestrator.plan(request(InterviewPhase.TECHNICAL));

    assertThat(result).isSameAs(agentPlan);
    verify(sessionStore).clearAgentTimeoutCount(9L);
    verify(sessionStore, never()).disableAgent(anyLong(), any());
    verifyNoInteractions(ruleOrchestrator);
  }

  @Test
  @DisplayName("Agent 契约失败后整场会话固定回退稳定规则")
  void agentFailureShouldDisableSessionAndReturnRuleFallback() throws Exception {
    when(agentOrchestrator.plan(any()))
        .thenThrow(new AgentPlanningException("AGENT_INVALID_JSON", "invalid"));
    when(ruleOrchestrator.plan(any())).thenReturn(rulePlan(InterviewPhase.TECHNICAL));
    executeSubmittedCallableImmediately();

    InterviewTurnPlan result = orchestrator.plan(request(InterviewPhase.TECHNICAL));

    assertThat(result.orchestrationMode()).isEqualTo(OrchestrationMode.RULE_FALLBACK);
    assertThat(result.fallbackReasonCode()).isEqualTo("AGENT_INVALID_JSON");
    assertThat(result.publicSummary()).contains("本场已切换稳定策略");
    verify(sessionStore).disableAgent(9L, "AGENT_INVALID_JSON");
  }

  @Test
  @DisplayName("已回退会话不重复发起 Agent 调用")
  void disabledSessionShouldNotRetryAgent() {
    when(sessionStore.loadAgentDisabledReason(9L)).thenReturn("AGENT_TOOL_FAILURE");
    when(ruleOrchestrator.plan(any())).thenReturn(rulePlan(InterviewPhase.TECHNICAL));

    InterviewTurnPlan result = orchestrator.plan(request(InterviewPhase.TECHNICAL));

    assertThat(result.orchestrationMode()).isEqualTo(OrchestrationMode.RULE_FALLBACK);
    assertThat(result.fallbackReasonCode()).isEqualTo("AGENT_TOOL_FAILURE");
    verifyNoInteractions(agentOrchestrator, taskExecutor);
  }

  @Test
  @DisplayName("首次规划超时只回退当前轮并允许下一轮自动重试")
  void firstTimeoutShouldFallbackOnlyForCurrentTurn(CapturedOutput output) throws Exception {
    @SuppressWarnings("unchecked")
    Future<InterviewTurnPlan> future = org.mockito.Mockito.mock(Future.class);
    when(taskExecutor.submit(any(Callable.class))).thenReturn(future);
    when(future.get(anyLong(), eq(TimeUnit.SECONDS)))
        .thenThrow(new TimeoutException("provider-secret-should-not-leak"));
    when(sessionStore.incrementAgentTimeoutCount(9L)).thenReturn(1);
    when(ruleOrchestrator.plan(any())).thenReturn(rulePlan(InterviewPhase.TECHNICAL));

    InterviewTurnPlan result = orchestrator.plan(request(InterviewPhase.TECHNICAL));

    assertThat(result.fallbackReasonCode()).isEqualTo("AGENT_TIMEOUT");
    assertThat(result.publicSummary()).contains("下一轮自动重试");
    assertThat(output.getAll())
        .contains("recordId=9", "reason=AGENT_TIMEOUT", "elapsedMs=", "sticky=false")
        .doesNotContain("provider-secret-should-not-leak");

    InterviewTurnPlan agentPlan = agentPlan();
    when(agentOrchestrator.plan(any())).thenReturn(agentPlan);
    executeSubmittedCallableImmediately();

    InterviewTurnPlan retryResult = orchestrator.plan(request(InterviewPhase.TECHNICAL));

    assertThat(retryResult).isSameAs(agentPlan);
    verify(future).cancel(true);
    verify(sessionStore).clearAgentTimeoutCount(9L);
    verify(sessionStore, never()).disableAgent(anyLong(), any());
  }

  @Test
  @DisplayName("连续第二次规划超时后整场会话固定回退")
  void secondConsecutiveTimeoutShouldDisableAgentForSession(CapturedOutput output) throws Exception {
    @SuppressWarnings("unchecked")
    Future<InterviewTurnPlan> future = org.mockito.Mockito.mock(Future.class);
    when(taskExecutor.submit(any(Callable.class))).thenReturn(future);
    when(future.get(anyLong(), eq(TimeUnit.SECONDS)))
        .thenThrow(new TimeoutException("provider-secret-should-not-leak"));
    when(sessionStore.incrementAgentTimeoutCount(9L)).thenReturn(2);
    when(ruleOrchestrator.plan(any())).thenReturn(rulePlan(InterviewPhase.TECHNICAL));

    InterviewTurnPlan result = orchestrator.plan(request(InterviewPhase.TECHNICAL));

    assertThat(result.fallbackReasonCode()).isEqualTo("AGENT_TIMEOUT");
    assertThat(result.publicSummary()).contains("本场已切换稳定策略");
    assertThat(output.getAll())
        .contains("recordId=9", "reason=AGENT_TIMEOUT", "elapsedMs=", "sticky=true")
        .doesNotContain("provider-secret-should-not-leak");
    verify(future).cancel(true);
    verify(sessionStore).disableAgent(9L, "AGENT_TIMEOUT");
  }

  @Test
  @DisplayName("关闭回退时保留稳定失败码并向上抛出")
  void disabledFallbackShouldPropagateStableFailure() throws Exception {
    properties.setFallbackEnabled(false);
    when(agentOrchestrator.plan(any()))
        .thenThrow(new AgentPlanningException("AGENT_MODEL_FAILURE", "failed"));
    executeSubmittedCallableImmediately();

    assertThatThrownBy(() -> orchestrator.plan(request(InterviewPhase.TECHNICAL)))
        .isInstanceOf(AgentPlanningException.class)
        .satisfies(error -> assertThat(((AgentPlanningException) error).reasonCode())
            .isEqualTo("AGENT_MODEL_FAILURE"));
    verify(sessionStore, never()).disableAgent(anyLong(), any());
    verifyNoInteractions(ruleOrchestrator);
  }

  @Test
  @DisplayName("关闭回退时规划超时仍按原语义向上抛出")
  void disabledFallbackShouldPropagateTimeout() throws Exception {
    properties.setFallbackEnabled(false);
    @SuppressWarnings("unchecked")
    Future<InterviewTurnPlan> future = org.mockito.Mockito.mock(Future.class);
    when(taskExecutor.submit(any(Callable.class))).thenReturn(future);
    when(future.get(anyLong(), eq(TimeUnit.SECONDS))).thenThrow(new TimeoutException());

    assertThatThrownBy(() -> orchestrator.plan(request(InterviewPhase.TECHNICAL)))
        .isInstanceOf(AgentPlanningException.class)
        .satisfies(error -> assertThat(((AgentPlanningException) error).reasonCode())
            .isEqualTo("AGENT_TIMEOUT"));
    verify(future).cancel(true);
    verify(sessionStore, never()).incrementAgentTimeoutCount(anyLong());
    verify(sessionStore, never()).disableAgent(anyLong(), any());
    verifyNoInteractions(ruleOrchestrator);
  }

  private void executeSubmittedCallableImmediately() throws Exception {
    when(taskExecutor.submit(any(Callable.class))).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Callable<InterviewTurnPlan> callable = invocation.getArgument(0);
      FutureTask<InterviewTurnPlan> future = new FutureTask<>(callable);
      future.run();
      return future;
    });
  }

  private InterviewTurnRequest request(InterviewPhase phase) {
    return new InterviewTurnRequest(
        9L, 7L, 8L, "Java 后端开发", phase, 4, "mid",
        List.of("architecture"), List.of(), "我使用过 Redis", List.of(), List.of());
  }

  private InterviewTurnPlan agentPlan() {
    return new InterviewTurnPlan(
        InterviewPhase.TECHNICAL, InterviewAction.DEEPEN, OrchestrationMode.AGENT,
        "agent prompt", "evidence", List.of("atom-1"), List.of("atom-1"),
        List.of("searchPositionKnowledge"), "继续深挖", null);
  }

  private InterviewTurnPlan rulePlan(InterviewPhase phase) {
    return new InterviewTurnPlan(
        phase, InterviewAction.CONTINUE_PHASE, OrchestrationMode.RULE,
        "rule prompt", "", List.of(), List.of(), List.of(), "继续当前阶段", null);
  }
}
