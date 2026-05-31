package com.example.aidemo.harness;

import com.example.aidemo.agent.Agent;
import com.example.aidemo.json.Json;
import com.example.aidemo.llm.LlmClient;
import com.example.aidemo.llm.LlmResponse;
import com.example.aidemo.llm.Message;
import com.example.aidemo.tool.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行骨架（Harness）：驱动智能体跑起来的"引擎/运行时"。
 *
 * <p>Harness 是把模型、上下文、工具、循环、错误处理缝合在一起的脚手架代码。
 * 模型本身只会"给定上下文、输出下一步"，而真正让它<strong>循环地</strong>感知-决策-行动、
 * 直到完成目标的，是 Harness。它负责的事情包括：</p>
 *
 * <ol>
 *   <li>组织上下文（系统提示 + 历史消息 + 工具结果）；</li>
 *   <li>调用模型拿到决策（{@link LlmResponse}）；</li>
 *   <li>若是工具调用：找到对应工具、执行、把结果回填为新的上下文；</li>
 *   <li>若是最终回答：结束循环并返回；</li>
 *   <li>控制最大步数、异常兜底等。</li>
 * </ol>
 *
 * <p>这正是 ReAct（Reasoning + Acting）循环的工程化实现。</p>
 */
public class AgentHarness {

    private final Agent agent;
    private final LlmClient llm;
    private final int maxIterations;

    public AgentHarness(Agent agent, LlmClient llm, int maxIterations) {
        this.agent = agent;
        this.llm = llm;
        this.maxIterations = maxIterations;
    }

    /**
     * 针对一个用户目标，运行完整的 agent 循环，返回最终回答。
     */
    public String run(String userGoal) {
        // 1) 初始化上下文
        List<Message> context = new ArrayList<>();
        context.add(Message.system(agent.buildSystemPrompt()));
        context.add(Message.user(userGoal));

        System.out.println("用户目标: " + userGoal);
        System.out.println("------------------------------------------------------------");

        // 2) 进入感知-决策-行动循环
        for (int step = 1; step <= maxIterations; step++) {
            LlmResponse decision = llm.complete(context);

            System.out.println("[第 " + step + " 步] 模型思考: " + decision.thought());

            if (decision.isFinal()) {
                context.add(Message.assistant(decision.finalAnswer()));
                System.out.println("[第 " + step + " 步] 模型给出最终回答，循环结束。");
                System.out.println("------------------------------------------------------------");
                return decision.finalAnswer();
            }

            // 工具调用分支
            String toolName = decision.toolName();
            String argsJson = Json.write(decision.arguments());
            System.out.println("[第 " + step + " 步] 决定调用工具: " + toolName + " 参数: " + argsJson);

            // 把模型这步"动作"记入上下文
            context.add(Message.assistant("调用工具 " + toolName + " " + argsJson));

            String result;
            try {
                if (!agent.tools().has(toolName)) {
                    result = "错误：不存在名为 " + toolName + " 的工具";
                } else {
                    Tool tool = agent.tools().get(toolName);
                    result = tool.execute(decision.arguments());
                }
            } catch (Exception e) {
                // 工具异常不应让整个 agent 崩溃，而是回填错误让模型自行决定下一步
                result = "工具执行异常: " + e.getMessage();
            }

            System.out.println("[第 " + step + " 步] 工具返回: " + result);
            System.out.println();

            // 把工具结果回填进上下文，供下一轮推理
            context.add(Message.tool(result));
        }

        return "（已达到最大步数 " + maxIterations + "，未能得出最终回答）";
    }

    // =====================================================================
    // 「直接返回」模式：只让模型决策一步，不进入循环、不把工具结果回灌给模型。
    // 适用于：业务侧想自己掌控工具的执行与结果处理，而不是让模型综合后再回答。
    // =====================================================================

    /**
     * 单步决策 + 单次执行的结构化结果。
     *
     * @param toolCall    本步是否是工具调用（false 表示模型直接给了文字回答）
     * @param toolName    工具名（toolCall=true 时有效）
     * @param arguments   模型给出的工具参数（toolCall=true 时有效）
     * @param toolResult  工具的原始返回（仅 {@link #runSingleTurn} 执行后有值）
     * @param finalAnswer 模型直接给出的文字回答（toolCall=false 时有效）
     * @param thought     模型的思考过程
     */
    public record SingleTurnResult(boolean toolCall,
                                   String toolName,
                                   java.util.Map<String, Object> arguments,
                                   String toolResult,
                                   String finalAnswer,
                                   String thought) {
    }

    /**
     * 模式 A —— 只决策、不执行：
     * 让模型基于用户目标决策一步，<strong>原样返回它的决定</strong>（可能是工具调用，也可能是最终回答）。
     * 不执行任何工具、也不再次调用模型。业务侧拿到后可自行决定如何执行 / 是否执行。
     *
     * <p>这等价于直接拿原生 function-calling 接口返回的 {@code tool_calls}。</p>
     */
    public LlmResponse decideOnce(String userGoal) {
        List<Message> context = new ArrayList<>();
        context.add(Message.system(agent.buildSystemPrompt()));
        context.add(Message.user(userGoal));
        return llm.complete(context);
    }

    /**
     * 模式 B —— 执行一次就返回：
     * 让模型决策一步；若它要调用工具，则<strong>执行该工具并把"调用信息 + 工具原始结果"直接返回</strong>，
     * <strong>绝不</strong>把结果回灌给模型做二次加工。若模型直接给出文字回答，则原样返回。
     */
    public SingleTurnResult runSingleTurn(String userGoal) {
        LlmResponse decision = decideOnce(userGoal);

        if (!decision.isToolCall()) {
            return new SingleTurnResult(false, null, null, null,
                    decision.finalAnswer(), decision.thought());
        }

        String name = decision.toolName();
        String result;
        try {
            if (!agent.tools().has(name)) {
                result = "错误：不存在名为 " + name + " 的工具";
            } else {
                result = agent.tools().get(name).execute(decision.arguments());
            }
        } catch (Exception e) {
            result = "工具执行异常: " + e.getMessage();
        }

        return new SingleTurnResult(true, name, decision.arguments(), result,
                null, decision.thought());
    }
}
