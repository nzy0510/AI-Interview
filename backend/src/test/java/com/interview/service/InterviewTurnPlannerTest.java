package com.interview.service;

import com.interview.config.InterviewPrompts;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewTurnPlanner — 面试轮次规划")
class InterviewTurnPlannerTest {

    private final InterviewPrompts prompts = prompts();
    private final InterviewTurnPlanner planner = new InterviewTurnPlanner(prompts);

    @Test
    @DisplayName("状态转换遵循真实 Turn Planner 实现")
    void shouldDetermineNextPhase() {
        assertThat(planner.determineNextPhase(InterviewPhase.OPENING, 0, false, false))
                .isEqualTo(InterviewPhase.OPENING);
        assertThat(planner.determineNextPhase(InterviewPhase.OPENING, 1, false, false))
                .isEqualTo(InterviewPhase.TECHNICAL);
        assertThat(planner.determineNextPhase(InterviewPhase.TECHNICAL, 3, true, false))
                .isEqualTo(InterviewPhase.HR);
        assertThat(planner.determineNextPhase(InterviewPhase.TECHNICAL, 9, false, false))
                .isEqualTo(InterviewPhase.HR);
        assertThat(planner.determineNextPhase(InterviewPhase.HR, 12, false, false))
                .isEqualTo(InterviewPhase.CLOSING);
        assertThat(planner.determineNextPhase(InterviewPhase.CLOSING, 13, false, true))
                .isEqualTo(InterviewPhase.FINISHED);
        assertThat(planner.determineNextPhase(InterviewPhase.FINISHED, 1, false, false))
                .isEqualTo(InterviewPhase.FINISHED);
        assertThat(planner.determineNextPhase(InterviewPhase.TECHNICAL, 5, false, false))
                .isEqualTo(InterviewPhase.TECHNICAL);
    }

    @Test
    @DisplayName("自动结束标记只能在收尾阶段结束面试")
    void shouldOnlyAutoFinishFromClosingPhase() {
        assertThat(planner.determineNextPhase(InterviewPhase.TECHNICAL, 5, false, true))
                .isEqualTo(InterviewPhase.TECHNICAL);
        assertThat(planner.determineNextPhase(InterviewPhase.HR, 10, false, true))
                .isEqualTo(InterviewPhase.HR);
        assertThat(planner.determineNextPhase(InterviewPhase.CLOSING, 13, false, true))
                .isEqualTo(InterviewPhase.FINISHED);
    }

    @Test
    @DisplayName("技术阶段 prompt 包含 RAG 上下文和面试配置")
    void shouldBuildTechnicalPromptWithRagContextAndSetup() {
        InterviewRecord record = record(InterviewPhase.TECHNICAL);
        record.setDifficultyLevel("senior");
        record.setFocusAreas("[\"projects\",\"architecture\"]");

        InterviewTurnPlanner.InterviewTurnPlan plan = planner.plan(
                record,
                List.of(new UserMessage("你好"), new AiMessage("技术问题")),
                "1. [atom_id: a1]\nHashMap context",
                List.of());

        assertThat(plan.phase()).isEqualTo(InterviewPhase.TECHNICAL);
        assertThat(plan.systemPrompt())
                .contains("technical")
                .contains("HashMap context")
                .contains("3-5年")
                .contains("项目经历深挖、系统设计与架构")
                .contains("[SWITCH_TO_HR]");
    }

    @Test
    @DisplayName("HR 阶段 prompt 使用 HR 软技能题库上下文")
    void shouldBuildHrPromptWithRagContext() {
        InterviewRecord record = record(InterviewPhase.HR);

        InterviewTurnPlanner.InterviewTurnPlan plan = planner.plan(
                record,
                List.of(new UserMessage("我会先沟通再推动"), new AiMessage("HR 问题")),
                "1. [atom_id: hr-conflict]\n团队冲突处理参考要点",
                List.of());

        assertThat(plan.phase()).isEqualTo(InterviewPhase.HR);
        assertThat(plan.systemPrompt())
                .contains("hr")
                .contains("团队冲突处理参考要点");
    }

    @Test
    @DisplayName("定制题存在时优先写入量身题库指令")
    void shouldPreferTailoredQuestionsWhenPresent() {
        InterviewRecord record = record(InterviewPhase.TECHNICAL);

        InterviewTurnPlanner.InterviewTurnPlan plan = planner.plan(
                record,
                List.of(new UserMessage("你好"), new AiMessage("技术问题")),
                "",
                List.of("请介绍简历里的缓存方案"));

        assertThat(plan.systemPrompt())
                .contains("量身定做题库")
                .contains("请介绍简历里的缓存方案");
    }

    @Test
    @DisplayName("AI 标记会驱动阶段切换")
    void shouldDetectMarkersFromHistory() {
        InterviewRecord record = record(InterviewPhase.TECHNICAL);
        List<ChatMessage> history = List.of(
                new UserMessage("回答"),
                new AiMessage("好的，我们交给 HR [SWITCH_TO_HR]")
        );

        InterviewTurnPlanner.InterviewTurnPlan plan = planner.plan(record, history, "", List.of());

        assertThat(plan.phase()).isEqualTo(InterviewPhase.HR);
        assertThat(plan.systemPrompt()).contains("hr");
    }

    @Test
    @DisplayName("显式阶段规划不应被截断的聊天历史重新计算")
    void shouldBuildPromptForExplicitPhase() {
        InterviewRecord record = record(InterviewPhase.TECHNICAL);
        record.setDifficultyLevel("mid");

        InterviewTurnPlanner.InterviewTurnPlan plan = planner.planForPhase(
                record, InterviewPhase.HR, "沟通协作证据", List.of());

        assertThat(plan.phase()).isEqualTo(InterviewPhase.HR);
        assertThat(plan.systemPrompt()).contains("沟通协作证据");
    }

    private InterviewRecord record(InterviewPhase phase) {
        InterviewRecord record = new InterviewRecord();
        record.setPhase(phase.name());
        return record;
    }

    private InterviewPrompts prompts() {
        InterviewPrompts prompts = new InterviewPrompts();
        prompts.setCoordinator("coordinator");
        prompts.setTechnical("technical");
        prompts.setHr("hr");
        prompts.setClosing("closing");
        prompts.setAttitudeRule("attitude");
        return prompts;
    }
}
