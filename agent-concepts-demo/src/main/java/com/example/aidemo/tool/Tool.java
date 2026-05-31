package com.example.aidemo.tool;

import java.util.Map;

/**
 * 工具（Tool）：智能体能够调用的一个"动作"。
 *
 * <p>它是把 Skill、MCP 远程能力等一切"可执行能力"统一接入 Harness 的抽象。
 * 无论能力来自本地 Skill 还是远程 MCP Server，最终都会被适配成一个 Tool，
 * 让 Harness 用同一套方式调度。</p>
 */
public interface Tool {

    /** 工具名，模型通过这个名字来选择调用哪个工具。 */
    String name();

    /** 工具用途描述，会写进系统提示，帮助模型判断何时调用。 */
    String description();

    /**
     * 执行工具。
     *
     * @param arguments 模型给出的参数（键值对）
     * @return 执行结果文本，会作为 TOOL 消息回填给模型
     */
    String execute(Map<String, Object> arguments);
}
