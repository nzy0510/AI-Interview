package com.interview.service.orchestration;

import com.interview.config.InterviewAgentProperties;
import com.interview.entity.InterviewPhase;
import com.interview.service.SessionStore;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    long planningStartedNanos = System.nanoTime();
    String disabledReason = sessionStore.loadAgentDisabledReason(request.recordId());
    if (disabledReason != null && !disabledReason.isBlank()) {
      return fallbackOrThrow(request, safeReasonCode(disabledReason), null,
          elapsedMillis(planningStartedNanos));
    }

    Future<InterviewTurnPlan> planningTask = null;
    try {
      planningTask = taskExecutor.submit(() -> agentOrchestrator.plan(request));
      InterviewTurnPlan agentPlan = planningTask.get(planningTimeoutSeconds(), TimeUnit.SECONDS);
      sessionStore.clearAgentTimeoutCount(request.recordId());
      return agentPlan;
    } catch (TimeoutException e) {
      if (planningTask != null) {
        planningTask.cancel(true);
      }
      return timeoutFallbackOrThrow(request, e, elapsedMillis(planningStartedNanos));
    } catch (InterruptedException e) {
      if (planningTask != null) {
        planningTask.cancel(true);
      }
      Thread.currentThread().interrupt();
      return fallbackOrThrow(request, AGENT_INTERRUPTED, e,
          elapsedMillis(planningStartedNanos));
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      String reasonCode = cause instanceof AgentPlanningException planningException
          ? safeReasonCode(planningException.reasonCode())
          : AGENT_FAILURE;
      return fallbackOrThrow(request, reasonCode, cause,
          elapsedMillis(planningStartedNanos));
    } catch (RuntimeException e) {
      return fallbackOrThrow(request, AGENT_SCHEDULING_FAILURE, e,
          elapsedMillis(planningStartedNanos));
    }
  }

  private InterviewTurnPlan fallbackOrThrow(InterviewTurnRequest request,
                                             String reasonCode,
                                             Throwable cause,
                                             long elapsedMs) {
    if (!properties.isFallbackEnabled()) {
      logFallbackDecision(request.recordId(), reasonCode, elapsedMs, false);
      if (cause instanceof AgentPlanningException planningException) {
        throw planningException;
      }
      throw new AgentPlanningException(reasonCode, "面试 Agent 规划失败", cause);
    }

    sessionStore.disableAgent(request.recordId(), reasonCode);
    logFallbackDecision(request.recordId(), reasonCode, elapsedMs, true);
    return ruleFallback(request, reasonCode,
        "Agent 暂不可用，本场已切换稳定策略");
  }

  private InterviewTurnPlan timeoutFallbackOrThrow(InterviewTurnRequest request,
                                                    TimeoutException cause,
                                                    long elapsedMs) {
    if (!properties.isFallbackEnabled()) {
      logFallbackDecision(request.recordId(), AGENT_TIMEOUT, elapsedMs, false);
      throw new AgentPlanningException(AGENT_TIMEOUT, "面试 Agent 规划失败", cause);
    }

    int timeoutCount = sessionStore.incrementAgentTimeoutCount(request.recordId());
    if (timeoutCount >= 2) {
      return fallbackOrThrow(request, AGENT_TIMEOUT, cause, elapsedMs);
    }
    logFallbackDecision(request.recordId(), AGENT_TIMEOUT, elapsedMs, false);
    return ruleFallback(request, AGENT_TIMEOUT,
        "Agent 本轮规划超时，已使用稳定规则，下一轮自动重试");
  }

  private InterviewTurnPlan ruleFallback(InterviewTurnRequest request,
                                         String reasonCode,
                                         String publicSummary) {
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
        publicSummary,
        reasonCode);
  }

  private void logFallbackDecision(Long recordId,
                                   String reasonCode,
                                   long elapsedMs,
                                   boolean sticky) {
    log.warn("面试 Agent 回退: recordId={}, reason={}, elapsedMs={}, sticky={}",
        recordId, reasonCode, elapsedMs, sticky);
  }

  private long elapsedMillis(long planningStartedNanos) {
    return Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - planningStartedNanos));
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
