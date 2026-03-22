package com.interview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.MemoryId;

/**
 * 技术官 Agent：负责硬核技术压测
 */
public interface TechnicalAgent {
    @SystemMessage("""
        你是一名【严厉的技术官】。
        1. 你的职责是结合提供的参考知识点，深挖候选人的技术底层原理。
        2. 如果候选人回答错误、踩中陷阱或回答敷衍，请毫不留情地犀利指出。
        3. 你的语气应该专业且富有压迫感，像在进行一场高强度的架构师面试。
        4. 每次回答结束后，如果当前知识点已问完，请根据追问路径抛出下一个更难的问题。
        """)
    TokenStream chat(@MemoryId Long recordId, @UserMessage String message);
}
