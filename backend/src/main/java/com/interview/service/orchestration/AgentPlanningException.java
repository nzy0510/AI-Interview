package com.interview.service.orchestration;

/**
 * Agent 规划失败时交给组合层处理的稳定异常。
 *
 * <p>异常消息只包含稳定的 reasonCode，不携带 Provider、URL、密钥或原始模型响应。</p>
 */
public class AgentPlanningException extends RuntimeException {

  private final String reasonCode;

  public AgentPlanningException(String reasonCode, String message) {
    super(message);
    this.reasonCode = reasonCode;
  }

  public AgentPlanningException(String reasonCode, String message, Throwable cause) {
    super(message, cause);
    this.reasonCode = reasonCode;
  }

  public String reasonCode() {
    return reasonCode;
  }
}
