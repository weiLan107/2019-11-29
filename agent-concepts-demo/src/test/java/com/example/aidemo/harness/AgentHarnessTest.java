package com.example.aidemo.harness;

import com.example.aidemo.agent.Agent;
import com.example.aidemo.llm.LlmClient;
import com.example.aidemo.llm.LlmResponse;
import com.example.aidemo.llm.Message;
import com.example.aidemo.llm.MockLlmClient;
import com.example.aidemo.mcp.InMemoryTransport;
import com.example.aidemo.mcp.McpClient;
import com.example.aidemo.mcp.McpServer;
import com.example.aidemo.mcp.McpToolAdapter;
import com.example.aidemo.skill.CalculatorSkill;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHarnessTest {

    /** 用确定性 Mock 跑完整循环，验证多步推理（算术 + MCP 天气）得到正确最终答案。 */
    @Test
    void runsFullReActLoopToFinalAnswer() {
        Agent agent = buildAgentWithWeather();
        AgentHarness harness = new AgentHarness(agent, new MockLlmClient(), 8);

        String answer = harness.run("帮我计算 (12 + 8) * 3，并查一下北京的天气，最后做个总结。");

        assertTrue(answer.contains("60"), "最终答案应包含计算结果 60，实际: " + answer);
        assertTrue(answer.contains("北京"), "最终答案应包含天气信息，实际: " + answer);
    }

    /** 工具抛错时，Harness 不应崩溃，而是把错误回填，让模型继续决策。 */
    @Test
    void recoversFromUnknownToolAndContinues() {
        Agent agent = new Agent("t", "test");
        agent.addSkill(new CalculatorSkill());

        // 脚本化模型：第 1 步调用不存在的工具，第 2 步给出最终回答
        LlmClient scripted = new LlmClient() {
            @Override
            public LlmResponse complete(List<Message> messages) {
                boolean sawToolResult = messages.stream()
                        .anyMatch(m -> m.role() == Message.Role.TOOL);
                if (!sawToolResult) {
                    return LlmResponse.toolCall("试试不存在的工具", "no_such_tool", Map.of());
                }
                String lastTool = messages.stream()
                        .filter(m -> m.role() == Message.Role.TOOL)
                        .reduce((a, b) -> b).map(Message::content).orElse("");
                return LlmResponse.finalAnswer("已知道该工具不存在", "处理完毕: " + lastTool);
            }
        };

        String answer = new AgentHarness(agent, scripted, 5).run("做点什么");
        assertTrue(answer.startsWith("处理完毕"), answer);
        assertTrue(answer.contains("不存在") || answer.contains("错误"),
                "应把工具错误回填到上下文，实际: " + answer);
    }

    /** 模型若永远不给最终回答，Harness 应在到达最大步数后安全退出。 */
    @Test
    void stopsAtMaxIterations() {
        Agent agent = new Agent("t", "test");
        agent.addSkill(new CalculatorSkill());
        LlmClient neverFinishes = messages ->
                LlmResponse.toolCall("再算一次", "calculator", Map.of("expression", "1 + 1"));

        String answer = new AgentHarness(agent, neverFinishes, 3).run("永动");
        assertTrue(answer.contains("最大步数"), answer);
    }

    private static Agent buildAgentWithWeather() {
        McpServer server = new McpServer("weather");
        server.registerTool("get_weather", "查天气", weatherSchema(),
                args -> args.get("city") + "当前天气：晴，12℃");
        McpClient client = new McpClient(new InMemoryTransport(server, false));

        Agent agent = new Agent("小助手", "严谨助理");
        agent.addSkill(new CalculatorSkill());
        for (McpClient.RemoteTool rt : client.listTools()) {
            agent.addTool(new McpToolAdapter(client, rt));
        }
        return agent;
    }

    private static Map<String, Object> weatherSchema() {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("city", prop);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }
}
