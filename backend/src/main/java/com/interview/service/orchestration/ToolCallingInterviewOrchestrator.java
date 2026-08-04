package com.interview.service.orchestration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.interview.config.InterviewAgentProperties;
import com.interview.dto.MentorInsightResponse;
import com.interview.dto.ResumeProfileResponse;
import com.interview.entity.InterviewPhase;
import com.interview.entity.InterviewRecord;
import com.interview.service.InterviewRetrievalService;
import com.interview.service.InterviewTurnPlanner;
import com.interview.service.MentorService;
import com.interview.service.ResumeService;
import com.interview.service.UserLlmConfigService;
import com.interview.service.UserLlmModelFactory;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 受约束的 LangChain4j Tool Calling 面试编排器。
 *
 * <p>该适配器只负责技术阶段的单轮动作规划，不接管 InterviewServiceImpl 的会话状态或持久化。</p>
 */
@Service
public class ToolCallingInterviewOrchestrator implements InterviewOrchestrator {

  static final int MAX_TOOL_CALLS = 3;

  static final String AGENT_UNAVAILABLE_PHASE = "AGENT_UNAVAILABLE_PHASE";
  static final String AGENT_PROVIDER_UNAVAILABLE = "AGENT_PROVIDER_UNAVAILABLE";
  static final String AGENT_TOOL_LIMIT = "AGENT_TOOL_LIMIT";
  static final String AGENT_TOOL_FAILURE = "AGENT_TOOL_FAILURE";
  static final String AGENT_MODEL_FAILURE = "AGENT_MODEL_FAILURE";
  static final String AGENT_INVALID_JSON = "AGENT_INVALID_JSON";
  static final String AGENT_UNKNOWN_ACTION = "AGENT_UNKNOWN_ACTION";

  private static final Pattern SECRET_PATTERN = Pattern.compile(
      "(?i)(api[_-]?key|token|password|secret|authorization)\\s*([=:])\\s*([^\\s,;]+)");
  private static final Pattern BEARER_PATTERN = Pattern.compile(
      "(?i)Bearer\\s+[A-Za-z0-9._-]+");
  private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
  private static final Set<String> DECISION_FIELDS = Set.of("action", "reason", "publicSummary");

  private final InterviewRetrievalService retrievalService;
  private final ResumeService resumeService;
  private final MentorService mentorService;
  private final InterviewTurnPlanner turnPlanner;
  private final int maxToolCalls;
  private final Function<Long, ChatLanguageModel> modelResolver;

  @Autowired
  public ToolCallingInterviewOrchestrator(InterviewRetrievalService retrievalService,
                                          ResumeService resumeService,
                                          MentorService mentorService,
                                          UserLlmConfigService userLlmConfigService,
                                          UserLlmModelFactory modelFactory,
                                          InterviewTurnPlanner turnPlanner,
                                          InterviewAgentProperties properties) {
    this(retrievalService, resumeService, mentorService, turnPlanner,
        clampMaxToolCalls(properties.getMaxToolCalls()),
        userId -> modelFactory.createChatModel(
            userLlmConfigService.requireActiveRuntimeConfig(userId)));
  }

  ToolCallingInterviewOrchestrator(InterviewRetrievalService retrievalService,
                                   ResumeService resumeService,
                                   MentorService mentorService,
                                   InterviewTurnPlanner turnPlanner,
                                   Function<Long, ChatLanguageModel> modelResolver) {
    this(retrievalService, resumeService, mentorService, turnPlanner,
        MAX_TOOL_CALLS, modelResolver);
  }

  ToolCallingInterviewOrchestrator(InterviewRetrievalService retrievalService,
                                   ResumeService resumeService,
                                   MentorService mentorService,
                                   InterviewTurnPlanner turnPlanner,
                                   int maxToolCalls,
                                   Function<Long, ChatLanguageModel> modelResolver) {
    this.retrievalService = retrievalService;
    this.resumeService = resumeService;
    this.mentorService = mentorService;
    this.turnPlanner = turnPlanner;
    this.maxToolCalls = clampMaxToolCalls(maxToolCalls);
    this.modelResolver = modelResolver;
  }

  @Override
  public InterviewTurnPlan plan(InterviewTurnRequest request) {
    if (request.currentPhase() != InterviewPhase.TECHNICAL) {
      throw new AgentPlanningException(
          AGENT_UNAVAILABLE_PHASE,
          "Tool Calling Agent 仅用于 TECHNICAL 阶段");
    }

    InterviewRecord record = toRecord(request);
    List<ChatMessage> history = toChatHistory(request.recentHistory());
    InterviewTurnPlanner.InterviewTurnPlan rulePlan = planForPhase(
        record, InterviewPhase.TECHNICAL, request.tailoredQuestions());
    String baseSystemPrompt = rulePlan.systemPrompt();
    String userPrompt = buildUserPrompt(request);

    BoundTools boundTools = new BoundTools(request, history);
    ChatLanguageModel chatModel;
    try {
      chatModel = modelResolver.apply(request.userId());
    } catch (Exception e) {
      throw new AgentPlanningException(
          AGENT_PROVIDER_UNAVAILABLE,
          "用户尚未配置可用的面试模型");
    }

    String rawDecision;
    try {
      InterviewPlanningAgent agent = AiServices.builder(InterviewPlanningAgent.class)
          .chatLanguageModel(chatModel)
          .tools(boundTools)
          .build();
      rawDecision = agent.plan(appendActionContract(baseSystemPrompt), userPrompt);
    } catch (AgentPlanningException e) {
      throw e;
    } catch (Exception e) {
      if (boundTools.failureCode() != null) {
        throw new AgentPlanningException(boundTools.failureCode(),
            safeFailureMessage(boundTools.failureCode()), e);
      }
      throw new AgentPlanningException(AGENT_MODEL_FAILURE,
          "面试 Agent 模型调用失败", e);
    }

    if (boundTools.failureCode() != null) {
      throw new AgentPlanningException(boundTools.failureCode(),
          safeFailureMessage(boundTools.failureCode()));
    }

    AgentDecision decision = parseDecision(rawDecision);
    InterviewAction requestedAction = normalizeAction(decision.action(), request.turnIndex());
    InterviewAction action = enforceEvidenceRequirements(requestedAction, boundTools);
    InterviewPhase outputPhase = action == InterviewAction.MOVE_TO_HR
        ? InterviewPhase.HR : request.currentPhase();
    String outputPhasePrompt = planForPhase(record, outputPhase, request.tailoredQuestions())
        .systemPrompt();
    String systemPrompt = buildFinalSystemPrompt(outputPhasePrompt, action, boundTools);
    String publicSummary = safePublicSummary(
        action == requestedAction ? decision.publicSummary() : "", action, boundTools);

    return new InterviewTurnPlan(
        outputPhase,
        action,
        OrchestrationMode.AGENT,
        systemPrompt,
        boundTools.evidenceContext(),
        boundTools.evidenceAtomIds(),
        boundTools.consumedAtomIds(),
        boundTools.toolsUsed(),
        publicSummary,
        null);
  }

  private InterviewRecord toRecord(InterviewTurnRequest request) {
    InterviewRecord record = new InterviewRecord();
    record.setId(request.recordId());
    record.setUserId(request.userId());
    record.setPosition(request.positionName());
    record.setPositionId(request.positionId());
    record.setPhase(request.currentPhase().name());
    record.setDifficultyLevel(request.difficultyLevel());
    record.setFocusAreas(JSON.toJSONString(request.focusAreas()));
    return record;
  }

  private List<ChatMessage> toChatHistory(List<InterviewMessageSnapshot> snapshots) {
    if (snapshots.isEmpty()) {
      return List.of();
    }
    List<ChatMessage> history = new ArrayList<>(snapshots.size());
    for (InterviewMessageSnapshot snapshot : snapshots) {
      String role = snapshot.role().toUpperCase(Locale.ROOT);
      if (role.equals("AI") || role.equals("ASSISTANT")) {
        history.add(AiMessage.from(snapshot.content()));
      } else if (role.equals("SYSTEM")) {
        history.add(dev.langchain4j.data.message.SystemMessage.from(snapshot.content()));
      } else {
        history.add(dev.langchain4j.data.message.UserMessage.from(snapshot.content()));
      }
    }
    return List.copyOf(history);
  }

  private String appendActionContract(String plannerPrompt) {
    return plannerPrompt + """


        【Tool Calling Agent 动作契约】
        你只能通过只读工具补充当前岗位证据，然后返回一个严格 JSON 对象。
        JSON 只能包含 action、reason、publicSummary 三个字符串字段；不要输出 Markdown、代码围栏、思维链或其他字段。
        action 只能是 DEEPEN、REMEDIATE、SWITCH_TOPIC、PROBE_RESUME、MOVE_TO_HR、CONTINUE_PHASE。
        reason 仅供编排层记录，publicSummary 只能是面向用户的简短安全说明。
        工具调用总数最多 3 次；没有证据时不要臆造事实。
        """;
  }

  private String buildUserPrompt(InterviewTurnRequest request) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("岗位：").append(safeText(request.positionName(), 120)).append("\n");
    prompt.append("难度：").append(safeText(request.difficultyLevel(), 40)).append("\n");
    prompt.append("当前轮次：").append(request.turnIndex()).append("\n");
    prompt.append("重点能力：").append(String.join("、", safeList(request.focusAreas(), 80))).append("\n");
    prompt.append("候选人最新回答：").append(safeText(request.latestAnswer(), 1800)).append("\n");
    prompt.append("已使用知识原子：").append(String.join(",", safeList(request.usedAtomIds(), 80))).append("\n");
    if (!request.recentHistory().isEmpty()) {
      prompt.append("最近对话：\n");
      for (InterviewMessageSnapshot message : request.recentHistory()) {
        prompt.append("- ").append(safeText(message.role(), 20)).append(": ")
            .append(safeText(message.content(), 500)).append("\n");
      }
    }
    prompt.append("请先判断是否需要调用工具，再只返回契约规定的 JSON。");
    return prompt.toString();
  }

  private String buildFinalSystemPrompt(String basePrompt, InterviewAction action, BoundTools tools) {
    StringBuilder prompt = new StringBuilder(basePrompt);
    if (!tools.evidenceContext().isBlank()) {
      prompt.append("\n\n【本轮受控证据】\n").append(tools.evidenceContext());
    }
    prompt.append("\n\n【本轮动作】\n").append(actionInstruction(action));
    prompt.append("\n只依据上面的受控证据继续提问，不要展示内部规划或工具调用过程。");
    return prompt.toString();
  }

  private InterviewTurnPlanner.InterviewTurnPlan planForPhase(InterviewRecord record,
                                                               InterviewPhase phase,
                                                               List<String> tailoredQuestions) {
    return turnPlanner.planForPhase(record, phase, "", tailoredQuestions);
  }

  private static int clampMaxToolCalls(int configuredValue) {
    return Math.max(1, Math.min(configuredValue, MAX_TOOL_CALLS));
  }

  private String actionInstruction(InterviewAction action) {
    return switch (action) {
      case DEEPEN -> "继续深挖当前知识点，追问回答中缺失的技术证据。";
      case REMEDIATE -> "围绕最相关的薄弱点做一次低难度补救追问，不直接给答案。";
      case SWITCH_TOPIC -> "切换到当前岗位的另一个知识点，不重复上一轮。";
      case PROBE_RESUME -> "只使用简历工具返回的证据追问候选人的真实经历，不得臆造经历。";
      case MOVE_TO_HR -> "技术阶段已达到切换门槛，转入 HR 阶段。";
      case CONTINUE_PHASE -> "保持当前技术阶段并自然推进下一题。";
    };
  }

  private List<String> safeList(List<String> values, int itemLength) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream().map(value -> safeText(value, itemLength)).toList();
  }

  private AgentDecision parseDecision(String rawDecision) {
    if (rawDecision == null || rawDecision.isBlank()) {
      throw new AgentPlanningException(AGENT_INVALID_JSON, "Agent 返回为空");
    }
    String json = rawDecision.trim();
    if (json.contains("```") || !json.startsWith("{") || !json.endsWith("}")) {
      throw new AgentPlanningException(AGENT_INVALID_JSON, "Agent 返回不是严格 JSON");
    }
    try {
      JSONObject object = JSON.parseObject(json);
      if (object == null || object.isEmpty() || !object.keySet().stream().allMatch(DECISION_FIELDS::contains)) {
        throw new AgentPlanningException(AGENT_INVALID_JSON, "Agent JSON 字段不符合契约");
      }
      String action = object.getString("action");
      if (action == null || action.isBlank()) {
        throw new AgentPlanningException(AGENT_INVALID_JSON, "Agent JSON 缺少 action");
      }
      String reason = object.getString("reason");
      String publicSummary = object.getString("publicSummary");
      if (reason == null || publicSummary == null) {
        throw new AgentPlanningException(AGENT_INVALID_JSON, "Agent JSON 缺少安全说明字段");
      }
      return new AgentDecision(action, reason, publicSummary);
    } catch (AgentPlanningException e) {
      throw e;
    } catch (Exception e) {
      throw new AgentPlanningException(AGENT_INVALID_JSON, "Agent 返回无法解析为 JSON", e);
    }
  }

  private InterviewAction normalizeAction(String action, int turnIndex) {
    InterviewAction normalized;
    try {
      normalized = InterviewAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      throw new AgentPlanningException(AGENT_UNKNOWN_ACTION, "Agent 返回未知动作");
    }
    if (normalized == InterviewAction.MOVE_TO_HR && turnIndex < 6) {
      return InterviewAction.SWITCH_TOPIC;
    }
    return normalized;
  }

  private InterviewAction enforceEvidenceRequirements(InterviewAction action, BoundTools tools) {
    if (action == InterviewAction.PROBE_RESUME && !tools.hasResumeEvidence()) {
      return InterviewAction.CONTINUE_PHASE;
    }
    return action;
  }

  private String safePublicSummary(String summary, InterviewAction action, BoundTools tools) {
    String safe = safeText(summary, 280);
    if (!safe.isBlank()) {
      return safe;
    }
    if (!tools.evidenceAtomIds().isEmpty()) {
      return "已结合岗位知识证据，下一轮将" + actionDescription(action) + "。";
    }
    return "下一轮将" + actionDescription(action) + "。";
  }

  private String actionDescription(InterviewAction action) {
    return switch (action) {
      case DEEPEN -> "继续深挖当前知识点";
      case REMEDIATE -> "围绕薄弱点进行补救追问";
      case SWITCH_TOPIC -> "切换到另一个岗位知识点";
      case PROBE_RESUME -> "结合简历经历进行追问";
      case MOVE_TO_HR -> "进入 HR 阶段";
      case CONTINUE_PHASE -> "保持当前阶段";
    };
  }

  private String safeFailureMessage(String reasonCode) {
    return switch (reasonCode) {
      case AGENT_TOOL_LIMIT -> "Agent 工具调用次数超过上限";
      case AGENT_TOOL_FAILURE -> "Agent 只读工具调用失败";
      default -> "Agent 规划失败";
    };
  }

  private String safeText(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String sanitized = URL_PATTERN.matcher(value).replaceAll("[URL]");
    sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
    sanitized = SECRET_PATTERN.matcher(sanitized).replaceAll("$1$2[REDACTED]");
    return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
  }

  public interface InterviewPlanningAgent {
    @SystemMessage(fromResource = "/prompts/interview-agent-planning.txt")
    @UserMessage("{{userPrompt}}")
    String plan(@V("systemPrompt") String systemPrompt, @V("userPrompt") String userPrompt);
  }

  private final class BoundTools {
    private final InterviewTurnRequest request;
    private final List<ChatMessage> history;
    private final List<String> toolsUsed = new ArrayList<>();
    private final LinkedHashSet<String> evidenceAtomIds = new LinkedHashSet<>();
    private final LinkedHashSet<String> consumedAtomIds = new LinkedHashSet<>();
    private int callCount;
    private String evidenceContext = "";
    private String failureCode;
    private boolean resumeEvidenceAvailable;

    private BoundTools(InterviewTurnRequest request, List<ChatMessage> history) {
      this.request = request;
      this.history = history;
    }

    @Tool(name = "searchPositionKnowledge", value = "搜索当前用户当前岗位可见的知识原子。只传自然语言查询，不传任何 ID。")
    public String searchPositionKnowledge(String query) {
      beforeTool("searchPositionKnowledge");
      try {
        InterviewRecord record = toRecord(request);
        InterviewRetrievalService.TurnRetrieval retrieval = retrievalService.retrieve(
            request.userId(), record, history, safeText(query, 500), InterviewPhase.TECHNICAL,
            request.usedAtomIds());
        if (retrieval == null) {
          return "没有检索到可用的岗位知识证据";
        }
        appendEvidence("岗位知识", retrieval.promptContext(), 6000);
        evidenceAtomIds.addAll(retrieval.promptAtomIds());
        consumedAtomIds.addAll(retrieval.contextAtomIds());
        return safeText(retrieval.promptContext(), 3500);
      } catch (ToolInvocationFailure e) {
        throw e;
      } catch (Exception e) {
        failureCode = AGENT_TOOL_FAILURE;
        throw new ToolInvocationFailure(AGENT_TOOL_FAILURE);
      }
    }

    @Tool(name = "getCurrentResumeEvidence", value = "读取当前用户绑定到当前岗位的简历证据。无参数。")
    public String getCurrentResumeEvidence() {
      beforeTool("getCurrentResumeEvidence");
      try {
        ResumeProfileResponse profile = resumeService.getProfileByUserIdAndPosition(
            request.userId(), request.positionId());
        if (profile == null || profile.getAnalysis() == null) {
          return "当前岗位没有可用的简历证据";
        }
        String evidence = safeText(JSON.toJSONString(profile.getAnalysis()), 3500);
        appendEvidence("简历证据", evidence, 3500);
        resumeEvidenceAvailable = !evidence.isBlank();
        return evidence;
      } catch (ToolInvocationFailure e) {
        throw e;
      } catch (Exception e) {
        failureCode = AGENT_TOOL_FAILURE;
        throw new ToolInvocationFailure(AGENT_TOOL_FAILURE);
      }
    }

    @Tool(name = "getPositionLearningCoverage", value = "读取当前用户当前岗位的知识覆盖率。无参数。")
    public String getPositionLearningCoverage() {
      beforeTool("getPositionLearningCoverage");
      try {
        MentorInsightResponse insight = mentorService.getKnowledgeCoverageOnly(
            request.userId(), request.positionId());
        if (insight == null || insight.getKnowledgeCoverage() == null) {
          return "当前岗位没有可用的学习覆盖率数据";
        }
        String evidence = safeText(JSON.toJSONString(insight.getKnowledgeCoverage()), 2500);
        appendEvidence("学习覆盖率", evidence, 2500);
        return evidence;
      } catch (ToolInvocationFailure e) {
        throw e;
      } catch (Exception e) {
        failureCode = AGENT_TOOL_FAILURE;
        throw new ToolInvocationFailure(AGENT_TOOL_FAILURE);
      }
    }

    private void beforeTool(String toolName) {
      if (callCount >= maxToolCalls) {
        failureCode = AGENT_TOOL_LIMIT;
        throw new ToolInvocationFailure(AGENT_TOOL_LIMIT);
      }
      callCount++;
      toolsUsed.add(toolName);
    }

    private void appendEvidence(String label, String evidence, int maxLength) {
      String safeEvidence = safeText(evidence, maxLength);
      if (safeEvidence.isBlank()) {
        return;
      }
      if (!evidenceContext.isBlank()) {
        evidenceContext += "\n\n";
      }
      evidenceContext += "【" + label + "】\n" + safeEvidence;
    }

    private String failureCode() {
      return failureCode;
    }

    private String evidenceContext() {
      return evidenceContext;
    }

    private List<String> evidenceAtomIds() {
      return List.copyOf(evidenceAtomIds);
    }

    private List<String> consumedAtomIds() {
      return List.copyOf(consumedAtomIds);
    }

    private List<String> toolsUsed() {
      return List.copyOf(toolsUsed);
    }

    private boolean hasResumeEvidence() {
      return resumeEvidenceAvailable;
    }
  }

  private record AgentDecision(String action, String reason, String publicSummary) {
  }

  private static final class ToolInvocationFailure extends RuntimeException {
    private ToolInvocationFailure(String reasonCode) {
      super(reasonCode);
    }
  }
}
