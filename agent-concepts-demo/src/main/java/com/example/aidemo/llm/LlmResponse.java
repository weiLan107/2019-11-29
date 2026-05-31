package com.example.aidemo.llm;

import java.util.Map;

/**
 * LLM 一次推理的输出。在带"工具调用 / function calling"能力的模型里，
 * 模型每一步要么决定<strong>调用某个工具</strong>，要么给出<strong>最终回答</strong>。
 *
 * @param thought     模型的思考过程（ReAct 中的 Reasoning 部分，便于观察）
 * @param toolName    要调用的工具名；为 null 表示这步不调用工具
 * @param arguments   工具参数
 * @param finalAnswer 最终回答；非 null 表示推理结束
 */
public record LlmResponse(String thought,
                          String toolName,
                          Map<String, Object> arguments,
                          String finalAnswer) {

    public boolean isToolCall() {
        return toolName != null;
    }

    public boolean isFinal() {
        return finalAnswer != null;
    }

    public static LlmResponse toolCall(String thought, String toolName, Map<String, Object> arguments) {
        return new LlmResponse(thought, toolName, arguments, null);
    }

    public static LlmResponse finalAnswer(String thought, String answer) {
        return new LlmResponse(thought, null, null, answer);
    }
}
