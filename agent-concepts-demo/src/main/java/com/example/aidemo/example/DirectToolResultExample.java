package com.example.aidemo.example;

import com.example.aidemo.agent.Agent;
import com.example.aidemo.harness.AgentHarness;
import com.example.aidemo.json.Json;
import com.example.aidemo.llm.LlmClient;
import com.example.aidemo.llm.LlmResponse;
import com.example.aidemo.llm.Message;
import com.example.aidemo.mcp.InMemoryTransport;
import com.example.aidemo.mcp.McpClient;
import com.example.aidemo.mcp.McpServer;
import com.example.aidemo.mcp.McpToolAdapter;
import com.example.aidemo.skill.CalculatorSkill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「直接返回大模型调用工具的原始结果」示例。
 *
 * <p>与 {@code Main} 里完整的 ReAct 循环（{@link AgentHarness#run}）不同，本示例演示
 * <strong>非 agentic</strong> 的单步用法：模型决策后<strong>不</strong>把工具结果回灌给模型做二次加工。</p>
 *
 * <ul>
 *   <li>模式 A —— {@link AgentHarness#decideOnce}：只拿模型的工具调用意图（名字 + 参数），不执行。
 *       适合"人工确认后再执行"、权限校验、把执行权交给下游服务等场景。</li>
 *   <li>模式 B —— {@link AgentHarness#runSingleTurn}：执行一次工具，把"调用信息 + 工具原始结果"直接返回。
 *       适合"问答即查询"的单工具管道，要的就是结构化原始数据。</li>
 * </ul>
 *
 * <p>运行：
 * {@code mvn -q compile exec:java -Dexec.mainClass=com.example.aidemo.example.DirectToolResultExample}
 * 或 {@code java -cp target/classes com.example.aidemo.example.DirectToolResultExample}</p>
 */
public class DirectToolResultExample {

    /**
     * 构建一个带 get_weather（MCP 远程工具）与 calculator（本地 Skill）的智能体，
     * 供示例与测试复用。
     */
    public static Agent buildAgent() {
        McpServer weatherServer = new McpServer("weather-mcp-server");
        weatherServer.registerTool(
                "get_weather",
                "查询指定城市的当前天气。",
                weatherSchema(),
                arguments -> {
                    Object city = arguments.get("city");
                    return (city == null ? "未知城市" : city) + "当前天气：晴，12℃，西北风 3 级";
                });
        McpClient client = new McpClient(new InMemoryTransport(weatherServer, false));

        Agent agent = new Agent("查询助手", "只做单步工具查询，不对结果做二次加工。");
        agent.addSkill(new CalculatorSkill());
        for (McpClient.RemoteTool rt : client.listTools()) {
            agent.addTool(new McpToolAdapter(client, rt));
        }
        return agent;
    }

    public static void main(String[] args) {
        Agent agent = buildAgent();

        // 模拟一个"决定调用 get_weather"的模型；真实业务里换成 OpenAiLlmClient 即可，
        // 因为下面两个方法都只依赖已解析好的 LlmResponse，与底层是原生 tool_calls 还是文本协议无关。
        LlmClient model = (List<Message> messages) -> LlmResponse.toolCall(
                "用户在问天气，应当调用 get_weather。",
                "get_weather",
                Map.of("city", "北京"));

        AgentHarness harness = new AgentHarness(agent, model, 8);
        String userGoal = "北京天气怎么样？";

        System.out.println("用户目标: " + userGoal);
        System.out.println();

        // ---- 模式 A：只返回模型的调用意图，不执行 ----
        System.out.println("== 模式 A：decideOnce（只拿意图，不执行）==");
        LlmResponse decision = harness.decideOnce(userGoal);
        System.out.println("  isToolCall = " + decision.isToolCall());
        System.out.println("  toolName   = " + decision.toolName());
        System.out.println("  arguments  = " + Json.write(decision.arguments()));
        System.out.println();

        // ---- 模式 B：执行一次工具，直接返回原始结果（不回灌模型）----
        System.out.println("== 模式 B：runSingleTurn（执行一次，返回原始结果）==");
        AgentHarness.SingleTurnResult result = harness.runSingleTurn(userGoal);
        System.out.println("  toolCall   = " + result.toolCall());
        System.out.println("  toolName   = " + result.toolName());
        System.out.println("  arguments  = " + Json.write(result.arguments()));
        System.out.println("  toolResult = " + result.toolResult());
        System.out.println();
        System.out.println("全程只调用了 1 次模型；工具原始结果直接返回，没有再喂回模型做二次加工。");
    }

    private static Map<String, Object> weatherSchema() {
        Map<String, Object> cityProp = new LinkedHashMap<>();
        cityProp.put("type", "string");
        cityProp.put("description", "城市名，例如 北京");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("city", cityProp);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("city"));
        return schema;
    }
}
