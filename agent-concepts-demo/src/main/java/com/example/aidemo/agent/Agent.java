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

        sb.append("""

                  输出约定（必须严格遵守）：
                  你每一步只能输出一个 JSON 对象，不要输出多余文字，也不要用 Markdown 代码块包裹。
                  - 当需要调用工具时，输出：
                    {"thought":"你的简短思考","action":{"tool":"工具名","args":{"参数名":"参数值"}}}
                  - 当信息足够、可以给出最终回答时，输出：
                    {"thought":"你的简短思考","final":"给用户的最终回答"}
                  注意：args 里的参数名必须与工具说明书一致；多步任务请拆成多次工具调用。
                  """);
        return sb.toString();
    }
}
