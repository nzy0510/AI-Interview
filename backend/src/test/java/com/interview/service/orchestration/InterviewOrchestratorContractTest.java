package com.interview.service.orchestration;

import com.interview.entity.InterviewPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("面试编排接口契约")
class InterviewOrchestratorContractTest {

    @Test
    @DisplayName("请求快照不受调用方后续修改影响")
    void requestShouldDefensivelyCopyMutableLists() {
        List<String> focusAreas = new ArrayList<>(List.of("architecture"));
        List<InterviewMessageSnapshot> history = new ArrayList<>(List.of(
                new InterviewMessageSnapshot("USER", "我使用过 Redis")
        ));

        InterviewTurnRequest request = new InterviewTurnRequest(
                10L, 20L, 30L, "Java 后端开发", InterviewPhase.TECHNICAL, 3,
                "mid", focusAreas, history, "我使用过 Redis", List.of("atom-1"), List.of());

        focusAreas.add("algorithm");
        history.add(new InterviewMessageSnapshot("AI", "请继续"));

        assertThat(request.focusAreas()).containsExactly("architecture");
        assertThat(request.recentHistory()).hasSize(1);
    }

    @Test
    @DisplayName("编排计划暴露稳定的动作模式和证据快照")
    void planShouldExposeStableDecisionSnapshot() {
        List<String> atomIds = new ArrayList<>(List.of("atom-1"));
        List<String> tools = new ArrayList<>(List.of("searchPositionKnowledge"));

        InterviewTurnPlan plan = new InterviewTurnPlan(
                InterviewPhase.TECHNICAL,
                InterviewAction.DEEPEN,
                OrchestrationMode.AGENT,
                "system prompt",
                "evidence",
                atomIds,
                atomIds,
                tools,
                "继续深挖 Redis 异常场景",
                null);

        atomIds.add("atom-2");
        tools.add("getCurrentResumeEvidence");

        assertThat(plan.evidenceAtomIds()).containsExactly("atom-1");
        assertThat(plan.consumedAtomIds()).containsExactly("atom-1");
        assertThat(plan.toolsUsed()).containsExactly("searchPositionKnowledge");
    }
}
