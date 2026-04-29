package com.interview.entity;

/**
 * 面试阶段枚举。控制多智能体轮转顺序。
 */
public enum InterviewPhase {
    OPENING,    // 开场致辞 + 自我介绍
    TECHNICAL,  // 技术压测（RAG + 简历定制题）
    HR,         // 软技能考察
    CLOSING,    // 总结收尾
    FINISHED    // 面试结束
}
