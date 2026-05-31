package com.example.aidemo.llm;

import java.util.List;

/**
 * 大语言模型客户端抽象。
 *
 * <p>Harness 只依赖这个接口，不关心背后是 OpenAI、Anthropic、本地模型还是 Mock。
 * 想接真实模型，只需另写一个实现：在 {@code complete} 里把 messages 转成对应
 * 厂商的请求体，调用其 HTTP API，再把"function call / 文本"解析回 {@link LlmResponse}。</p>
 */
public interface LlmClient {

    /**
     * 给定完整对话上下文，产出模型的下一步决策。
     *
     * @param messages 当前完整上下文（系统提示 + 历史对话 + 工具结果）
     * @return 模型决策：调用工具 或 给出最终回答
     */
    LlmResponse complete(List<Message> messages);
}
