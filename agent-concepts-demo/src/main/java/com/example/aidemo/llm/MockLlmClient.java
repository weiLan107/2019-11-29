package com.example.aidemo.llm;

import java.util.List;
import java.util.Map;

/**
 * 一个"确定性"的假 LLM，用来在没有 API key 的情况下也能完整跑通 Agent 循环。
 *
 * <p>真实 LLM 是根据上下文"理解"后自由决策；这里为了可复现，
 * 我们用一个写死的"剧本"来模拟模型在每一步的决策：它会观察对话里已经有多少条
 * 工具结果（TOOL 消息），据此决定下一步做什么。这恰好对应一个真实的多步推理过程：</p>
 *
 * <pre>
 *   用户："帮我计算 (12 + 8) * 3，并查一下北京的天气，最后做个总结。"
 *   第1步：调用 calculator 计算 12 + 8
 *   第2步：拿到 20，调用 calculator 计算 20 * 3
 *   第3步：拿到 60，调用 MCP 远程工具 get_weather 查北京天气
 *   第4步：综合结果，给出最终回答
 * </pre>
 *
 * <p>想换成真实模型，只需用真实实现替换本类（见 {@link LlmClient} 注释）。</p>
 */
public class MockLlmClient implements LlmClient {

    @Override
    public LlmResponse complete(List<Message> messages) {
        // 统计已有多少条工具结果，作为"当前进行到第几步"的依据
        long toolResults = messages.stream()
                .filter(m -> m.role() == Message.Role.TOOL)
                .count();

        return switch ((int) toolResults) {
            case 0 -> LlmResponse.toolCall(
                    "用户要算 (12 + 8) * 3。先算括号里的 12 + 8。",
                    "calculator",
                    Map.of("expression", "12 + 8"));

            case 1 -> {
                // 读取上一步的计算结果，拼出下一步表达式（模拟模型"看到"了中间结果）
                String prev = lastToolResult(messages);
                yield LlmResponse.toolCall(
                        "12 + 8 = " + prev + "，接着把它乘以 3。",
                        "calculator",
                        Map.of("expression", prev + " * 3"));
            }

            case 2 -> LlmResponse.toolCall(
                    "算术部分完成，结果是 " + lastToolResult(messages)
                            + "。现在去查北京的天气（用 MCP 远程工具）。",
                    "get_weather",
                    Map.of("city", "北京"));

            default -> {
                // 汇总：第二条工具结果是最终算术值，第三条是天气
                List<String> results = messages.stream()
                        .filter(m -> m.role() == Message.Role.TOOL)
                        .map(Message::content)
                        .toList();
                String calcResult = results.get(1);
                String weather = results.get(2);
                yield LlmResponse.finalAnswer(
                        "我已拿到计算结果和天气信息，可以给出最终回答了。",
                        "（12 + 8）* 3 = " + calcResult + "。" + weather);
            }
        };
    }

    private static String lastToolResult(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == Message.Role.TOOL) {
                return messages.get(i).content();
            }
        }
        return "";
    }
}
