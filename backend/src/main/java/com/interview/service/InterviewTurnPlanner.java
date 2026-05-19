package com.interview.service;

import com.alibaba.fastjson2.JSON;
import com.interview.config.InterviewPrompts;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InterviewTurnPlanner {

    private final InterviewPrompts interviewPrompts;

    public InterviewTurnPlanner(InterviewPrompts interviewPrompts) {
        this.interviewPrompts = interviewPrompts;
    }

    public InterviewTurnPlan plan(InterviewRecord record,
                                  List<ChatMessage> chatHistory,
                                  String ragContext,
                                  List<String> tailoredQuestions) {
        int turn = chatHistory.size() / 2;
        InterviewTurnMarkers markers = detectMarkers(chatHistory);
        InterviewPhase phase = determineNextPhase(
                currentPhase(record.getPhase()), turn, markers.switchToHr(), markers.autoFinish());
        return new InterviewTurnPlan(phase, buildSystemPrompt(record, phase, ragContext, tailoredQuestions));
    }

    public InterviewPhase determineNextPhase(InterviewPhase current, int turn,
                                             boolean switchToHrMarker, boolean autoFinishMarker) {
        if (current == InterviewPhase.FINISHED) return InterviewPhase.FINISHED;
        if (autoFinishMarker) return InterviewPhase.FINISHED;
        if (current == InterviewPhase.OPENING && turn >= 1) return InterviewPhase.TECHNICAL;
        if (current == InterviewPhase.TECHNICAL) {
            if (switchToHrMarker || turn > 8) return InterviewPhase.HR;
            return InterviewPhase.TECHNICAL;
        }
        if (current == InterviewPhase.HR && turn > 11) return InterviewPhase.CLOSING;
        return current;
    }

    private InterviewTurnMarkers detectMarkers(List<ChatMessage> chatHistory) {
        boolean switchToHr = false;
        boolean autoFinish = false;
        for (ChatMessage message : chatHistory) {
            if (message instanceof AiMessage aiMessage) {
                String text = aiMessage.text();
                if (text == null) continue;
                if (text.contains("[SWITCH_TO_HR]")) switchToHr = true;
                if (text.contains("[AUTO_FINISH]")) autoFinish = true;
            }
        }
        return new InterviewTurnMarkers(switchToHr, autoFinish);
    }

    private InterviewPhase currentPhase(String value) {
        if (value == null || value.isEmpty()) {
            return InterviewPhase.OPENING;
        }
        return InterviewPhase.valueOf(value);
    }

    private String buildSystemPrompt(InterviewRecord record,
                                     InterviewPhase phase,
                                     String ragContext,
                                     List<String> tailoredQuestions) {
        String setupInstructions = buildSetupInstructions(record);
        if (phase == InterviewPhase.OPENING) {
            return interviewPrompts.getCoordinator() + "\n" + interviewPrompts.getAttitudeRule()
                    + setupInstructions
                    + "\n请先让候选人做个简短的自我介绍。";
        }
        if (phase == InterviewPhase.TECHNICAL) {
            String currentSystemPrompt = interviewPrompts.getTechnical() + "\n" + interviewPrompts.getAttitudeRule()
                    + setupInstructions
                    + "\n候选知识点（选择最相关的1个追问，不要直接说出标准答案，优先追问候选人回答中缺失的部分）：\n"
                    + ragContext;

            if (tailoredQuestions != null && !tailoredQuestions.isEmpty()) {
                StringBuilder qb = new StringBuilder("\n【重要指令：你的问题池已结合候选人简历更新，请**优先**依照以下量身定做题库向候选人发问】：\n");
                for (String question : tailoredQuestions) qb.append("- ").append(question).append("\n");
                qb.append("\n要求：如果在充分考察完上述定制题后，或者候选人在某点回答极其完善完美，**请务必主动发散到其他核心甚至进阶领域**，确保深挖候选人的知识广度。\n");
                qb.append("如果在极其满意的状况下认为无需进行任何技术面试了，你可以提前结束技术部分，并把话题抛给HR同事，此时在这个回答的最末尾追加标记：[SWITCH_TO_HR]\n");
                return currentSystemPrompt + qb;
            }
            return currentSystemPrompt
                    + "\n如果在极其满意的状况下认为无可挑剔且无需再问，你可以提前结束技术部分，并把话题抛给HR同事，此时在这个回答的最末尾追加标记：[SWITCH_TO_HR]\n";
        }
        if (phase == InterviewPhase.HR) {
            return interviewPrompts.getHr() + "\n" + interviewPrompts.getAttitudeRule()
                    + setupInstructions;
        }
        return interviewPrompts.getClosing() + setupInstructions;
    }

    private String buildSetupInstructions(InterviewRecord record) {
        StringBuilder sb = new StringBuilder("\n【候选人本场面试配置】\n");
        sb.append("- 难度倾向：").append(humanDifficulty(record.getDifficultyLevel())).append("\n");
        List<String> focusAreas = parseFocusAreas(record.getFocusAreas());
        if (!focusAreas.isEmpty()) {
            sb.append("- 重点能力：").append(String.join("、", focusAreas)).append("\n");
            sb.append("- 提问策略：优先围绕重点能力设计追问，但不要牺牲岗位核心知识覆盖。\n");
        }
        sb.append("- 难度策略：根据难度倾向调整追问深度、案例复杂度和容错标准。\n");
        return sb.toString();
    }

    private String humanDifficulty(String difficultyLevel) {
        return switch (normalizeDifficulty(difficultyLevel)) {
            case "junior" -> "应届/0-1年，重视基础概念、表达清晰度和项目参与度";
            case "senior" -> "3-5年，重视系统设计、复杂问题定位和技术取舍";
            case "principal" -> "5年+，重视架构判断、跨团队影响力和深层原理";
            default -> "1-3年，重视核心原理、业务落地和独立解决问题能力";
        };
    }

    private String normalizeDifficulty(String difficultyLevel) {
        if ("junior".equals(difficultyLevel)
                || "mid".equals(difficultyLevel)
                || "senior".equals(difficultyLevel)
                || "principal".equals(difficultyLevel)) {
            return difficultyLevel;
        }
        return "mid";
    }

    private List<String> parseFocusAreas(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        try {
            List<String> values = JSON.parseArray(raw, String.class);
            List<String> labels = new ArrayList<>();
            for (String value : values) {
                labels.add(humanFocusArea(value));
            }
            return labels;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String humanFocusArea(String focusArea) {
        return switch (focusArea) {
            case "projects" -> "项目经历深挖";
            case "depth" -> "技术原理深度";
            case "architecture" -> "系统设计与架构";
            case "algorithm" -> "算法基础与思维";
            case "communication" -> "表达沟通与协作";
            case "pressure" -> "压力应对与稳定性";
            default -> focusArea;
        };
    }

    private record InterviewTurnMarkers(boolean switchToHr, boolean autoFinish) {
    }

    public record InterviewTurnPlan(InterviewPhase phase, String systemPrompt) {
    }
}
