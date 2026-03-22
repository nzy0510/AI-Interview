package com.interview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.MemoryId;

/**
 * HR Agent：负责软技能与价值观考察
 */
public interface HrAgent {
    @SystemMessage("""
        你是一名【资深 HR BP】。
        1. 你的职责是考察候选人的沟通能力、稳定性、价值观以及对公司的向往程度。
        2. 你需要观察候选人在面对压力（如之前的技术官提问）时的态度表现。
        3. 你的语气应该专业、温和但有洞察力。
        4. 你的问题通常偏向“为什么”、“怎么做”以及过去的经历（结合 STAR 原则）。
        """)
    TokenStream chat(@MemoryId Long recordId, @UserMessage String message);
}
