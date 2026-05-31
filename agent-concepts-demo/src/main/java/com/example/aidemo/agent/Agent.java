package com.example.aidemo.agent;

import com.example.aidemo.skill.Skill;
import com.example.aidemo.tool.Tool;
import com.example.aidemo.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能体（Agent）：一个能<strong>感知-决策-行动</strong>、自主完成目标的 LLM 系统的"配置与身份"。
 *
 * <p>一个 Agent = 角色设定(persona/目标) + 一组能力(Skills / MCP 工具)。
 * 它本身不包含"如何循环跑起来"的逻辑——那是 Harness 的职责。
 * 这种拆分让同一个 Harness 可以驱动不同的 Agent，同一个 Agent 也能换不同 Harness/模型。</p>
 */
public class Agent {

    private final String name;
    private final String persona;
    private final ToolRegistry toolRegistry = new ToolRegistry();
    private final List<Skill> skills = new ArrayList<>();

    public Agent(String name, String persona) {
        this.name = name;
        this.persona = persona;
    }

    /** 装载一个技能：技能既是工具，又会贡献一段说明书进系统提示。 */
    public Agent addSkill(Skill skill) {
        skills.add(skill);
        toolRegistry.register(skill);
        return this;
    }

    /** 装载一个普通工具（例如来自 MCP 的远程工具适配器）。 */
    public Agent addTool(Tool tool) {
        toolRegistry.register(tool);
        return this;
    }

    public String name() {
        return name;
    }

    public ToolRegistry tools() {
        return toolRegistry;
    }

    /**
     * 构建系统提示：身份 + 可用工具清单 + 各技能说明书 + 输出格式约定。
     * 这就是 Harness 每轮喂给模型的"第一条消息"。
     */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「").append(name).append("」。\n");
        sb.append(persona).append("\n\n");

        sb.append("你可以调用以下工具来完成任务：\n");
        sb.append(toolRegistry.describeForPrompt());

        if (!skills.isEmpty()) {
            sb.append("\n以下是部分能力的详细说明书（按需参考）：\n");
            for (Skill skill : skills) {
                sb.append(skill.instructions()).append('\n');
            }
        }

        sb.append("\n输出约定：每一步要么调用一个工具，要么在信息足够时给出最终回答。\n");
        return sb.toString();
    }
}
