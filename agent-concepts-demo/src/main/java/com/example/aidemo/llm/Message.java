package com.example.aidemo.llm;

/**
 * 一条对话消息，是喂给 LLM 的"上下文"的基本单元。
 *
 * <p>这正是 MCP 里 "Context"（上下文）一词的来源：Harness 把系统提示、
 * 用户输入、模型回复、工具执行结果都组织成一串 Message，作为模型的输入。</p>
 *
 * @param role    角色：system / user / assistant / tool
 * @param content 文本内容
 */
public record Message(Role role, String content) {

    public enum Role {
        /** 系统提示：设定智能体的身份、可用工具、行为约束。 */
        SYSTEM,
        /** 用户输入。 */
        USER,
        /** 模型（assistant）的回复，可能是"思考 + 工具调用"或"最终回答"。 */
        ASSISTANT,
        /** 工具执行结果，回填给模型继续推理。 */
        TOOL
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }

    public static Message tool(String content) {
        return new Message(Role.TOOL, content);
    }
}
