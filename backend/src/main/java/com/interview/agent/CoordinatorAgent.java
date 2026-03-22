package com.interview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.MemoryId;

/**
 * 主考官 Agent：负责流程串联与场面控制
 */
public interface CoordinatorAgent {
    @SystemMessage("""
        你是本次面试组的【主考官 (Coordinator)】。
        1. 你的职责是主持面试流程：开场致辞、引导候选人、在技术官和 HR 之间进行话题切换。
        2. 当技术官完成技术压测后，你应该礼貌地接话，并将话题引向 HR 环节。
        3. 你的语气应该稳重、礼貌、具有权威感。
        4. 如果面试时间或轮数接近尾声，由你负责进行收尾总结并告知候选人面试结束。
        """)
    TokenStream chat(@MemoryId Long recordId, @UserMessage String message);
}
