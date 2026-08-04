package com.interview.service.orchestration;

import com.interview.config.InterviewAgentProperties;
import com.interview.entity.InterviewPhase;
import com.interview.service.SessionStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Primary
@Service
public class FallbackInterviewOrchestrator implements InterviewOrchestrator {

  static final String AGENT_TIMEOUT = "AGENT_TIMEOUT";
  static final String AGENT_INTERRUPTED = "AGENT_INTERRUPTED";
  static final String AGENT_SCHEDULING_FAILURE = "AGENT_SCHEDULING_FAILURE";
  static final String AGENT_FAILURE = "AGENT_FAILURE";

  private static final Pattern SAFE_REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
  private static final int MAX_TIMEOUT_SECONDS = 60;

  private final InterviewOrchestrator agentOrchestrator;
  private final InterviewOrchestrator ruleOrchestrator;
  private final SessionStore sessionStore;
  private final AsyncTaskExecutor taskExecutor;
  private final InterviewAgentProperties properties;

  public FallbackInterviewOrchestrator(
      @Qualifier("toolCallingInterviewOrchestrator") InterviewOrchestrator agentOrchestrator,
      @Qualifier("ruleBasedInterviewOrchestrator") InterviewOrchestrator ruleOrchestrator,
      SessionStore sessionStore,
      @Qualifier("interviewAgentTaskExecutor") AsyncTaskExecutor taskExecutor,
      InterviewAgentProperties properties) {
    this.agentOrchestrator = agentOrchestrator;
    this.ruleOrchestrator = ruleOrchestrator;
    this.sessionStore = sessionStore;
    this.taskExecutor = taskExecutor;
    this.properties = properties;
  }

  @Override
  public InterviewTurnPlan plan(InterviewTurnRequest request) {
    if (!properties.isEnabled() || request.currentPhase() != InterviewPhase.TECHNICAL) {
      return ruleOrchestrator.plan(request);
    }

    String disabledReason = sessionStore.loadAgentDisabledReason(request.recordId());
    if (disabledReason != null && !disabledReason.isBlank()) {
      return fallbackOrThrow(request, safeReasonCode(disabledReason), null);
    }

    Future<InterviewTurnPlan> planningTask = null;
    try {
      planningTask = taskExecutor.submit(() -> agentOrchestrator.plan(request));
      return planningTask.get(planningTimeoutSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      if (planningTask != null) {
        planningTask.cancel(true);
      }
      return fallbackOrThrow(request, AGENT_TIMEOUT, e);
    } catch (InterruptedException e) {
      if (planningTask != null) {
        planningTask.cancel(true);
      }
      Thread.currentThread().interrupt();
      return fallbackOrThrow(request, AGENT_INTERRUPTED, e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      String reasonCode = cause instanceof AgentPlanningException planningException
          ? safeReasonCode(planningException.reasonCode())
          : AGENT_FAILURE;
      return fallbackOrThrow(request, reasonCode, cause);
    } catch (RuntimeException e) {
      return fallbackOrThrow(request, AGENT_SCHEDULING_FAILURE, e);
    }
  }

  private InterviewTurnPlan fallbackOrThrow(InterviewTurnRequest request,
                                             String reasonCode,
                                             Throwable cause) {
    if (!properties.isFallbackEnabled()) {
      if (cause instanceof AgentPlanningException planningException) {
        throw planningException;
      }
      throw new AgentPlanningException(reasonCode, "面试 Agent 规划失败", cause);
    }

    sessionStore.disableAgent(request.recordId(), reasonCode);
    InterviewTurnPlan rulePlan = ruleOrchestrator.plan(request);
    return new InterviewTurnPlan(
        rulePlan.phase(),
        rulePlan.action(),
        OrchestrationMode.RULE_FALLBACK,
        rulePlan.systemPrompt(),
        rulePlan.evidenceContext(),
        rulePlan.evidenceAtomIds(),
        rulePlan.consumedAtomIds(),
        rulePlan.toolsUsed(),
        "Agent 暂不可用，已自动切换到稳定面试策略",
        reasonCode);
  }

  private int planningTimeoutSeconds() {
    return Math.max(1, Math.min(properties.getPlanningTimeoutSeconds(), MAX_TIMEOUT_SECONDS));
  }

  private String safeReasonCode(String reasonCode) {
    if (reasonCode != null && SAFE_REASON_CODE.matcher(reasonCode).matches()) {
      return reasonCode;
    }
    return AGENT_FAILURE;
  }
}
