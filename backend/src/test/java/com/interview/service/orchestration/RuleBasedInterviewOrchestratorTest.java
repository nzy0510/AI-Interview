package com.interview.service.orchestration;

import com.interview.entity.InterviewPhase;
import com.interview.service.InterviewRetrievalService;
import com.interview.service.InterviewTurnPlanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("稳定规则面试编排")
class RuleBasedInterviewOrchestratorTest {

    @Mock
    private InterviewRetrievalService retrievalService;

    @Mock
    private InterviewTurnPlanner turnPlanner;

    private RuleBasedInterviewOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new RuleBasedInterviewOrchestrator(retrievalService, turnPlanner);
    }

    @Test
    @DisplayName("规则编排复用作用域检索并返回稳定计划")
    void shouldReuseScopedRetrievalAndReturnRulePlan() {
        when(turnPlanner.determineNextPhase(any(), anyList()))
                .thenReturn(InterviewPhase.TECHNICAL);
        when(retrievalService.retrieve(eq(20L), any(), anyList(), eq("我用过 Redis"),
                eq(InterviewPhase.TECHNICAL), eq(List.of("used-1"))))
                .thenReturn(new InterviewRetrievalService.TurnRetrieval(
                        "【本轮检索决策：补救追问】\nRedis 续期机制",
                        List.of("atom-1"),
                        List.of("atom-1")));
        when(turnPlanner.plan(any(), anyList(), any(), anyList()))
                .thenReturn(new InterviewTurnPlanner.InterviewTurnPlan(
                        InterviewPhase.TECHNICAL, "system prompt"));

        InterviewTurnPlan plan = orchestrator.plan(request());

        assertThat(plan.orchestrationMode()).isEqualTo(OrchestrationMode.RULE);
        assertThat(plan.action()).isEqualTo(InterviewAction.REMEDIATE);
        assertThat(plan.systemPrompt()).isEqualTo("system prompt");
        assertThat(plan.evidenceAtomIds()).containsExactly("atom-1");
        assertThat(plan.publicSummary()).contains("补救");
    }

    private InterviewTurnRequest request() {
        return new InterviewTurnRequest(
                10L,
                20L,
                30L,
                "Java 后端开发",
                InterviewPhase.TECHNICAL,
                3,
                "mid",
                List.of("architecture"),
                List.of(
                        new InterviewMessageSnapshot("AI", "请介绍 Redis"),
                        new InterviewMessageSnapshot("USER", "我用过 Redis")
                ),
                "我用过 Redis",
                List.of("used-1"),
                List.of());
    }
}
