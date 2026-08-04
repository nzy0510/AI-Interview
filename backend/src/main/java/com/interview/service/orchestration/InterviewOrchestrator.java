package com.interview.service.orchestration;

/**
 * 面试单轮规划 seam。实现负责隐藏检索、工具调用、策略校验和回退细节。
 */
public interface InterviewOrchestrator {

    InterviewTurnPlan plan(InterviewTurnRequest request);
}
