package com.example.aidemo;

import com.example.aidemo.agent.Agent;
import com.example.aidemo.harness.AgentHarness;
import com.example.aidemo.llm.LlmClient;
import com.example.aidemo.llm.MockLlmClient;
import com.example.aidemo.mcp.InMemoryTransport;
import com.example.aidemo.mcp.McpClient;
import com.example.aidemo.mcp.McpServer;
import com.example.aidemo.mcp.McpToolAdapter;
import com.example.aidemo.mcp.Transport;
import com.example.aidemo.skill.CalculatorSkill;
import com.example.aidemo.skill.TextStatsSkill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入口：把 Agent / MCP / Skill / Harness 四个概念串成一个可运行的端到端示例。
 *
 * <pre>
 *   ┌─────────────────────────── Harness（运行骨架/引擎）────────────────────────────┐
 *   │  循环：组织上下文 → 调 LLM 决策 → 执行工具 → 回填结果 → 直到最终回答              │
 *   │                                                                                │
 *   │   Agent（智能体：身份 + 能力集合）                                               │
 *   │     ├── Skill（本地技能）：calculator、text_stats                                │
 *   │     └── Tool ← McpToolAdapter ← McpClient ──JSON-RPC──► McpServer（get_weather）│
 *   │                                          （MCP：标准化的能力接入协议）          │
 *   └────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        banner("大模型四大名词端到端演示：Agent / MCP / Skill / Harness");

        // ============================================================
        // 1. MCP：启动一个 MCP 服务器，对外用 JSON-RPC 暴露 get_weather 工具
        // ============================================================
        banner("① MCP —— 用标准协议把「远程能力」接入进来");

        McpServer weatherServer = new McpServer("weather-mcp-server");
        weatherServer.registerTool(
                "get_weather",
                "查询指定城市的当前天气。",
                weatherInputSchema(),
                arguments -> {
                    Object city = arguments.get("city");
                    String c = city == null ? "未知城市" : city.toString();
                    // 演示用的固定数据
                    Map<String, String> fake = Map.of(
                            "北京", "晴，12℃，西北风 3 级",
                            "上海", "多云，18℃，东南风 2 级");
                    String weather = fake.getOrDefault(c, "暂无该城市数据");
                    return c + "当前天气：" + weather;
                });

        // 宿主端：通过 Transport + Client 与服务器握手、发现工具
        Transport transport = new InMemoryTransport(weatherServer, /*trace=*/ true);
        McpClient mcpClient = new McpClient(transport);

        System.out.println("  -> initialize 握手:");
        Map<String, Object> initResult = mcpClient.initialize();
        System.out.println("     服务器信息: " + initResult.get("serverInfo"));

        System.out.println("  -> tools/list 发现远程工具:");
        List<McpClient.RemoteTool> remoteTools = mcpClient.listTools();
        for (McpClient.RemoteTool rt : remoteTools) {
            System.out.println("     发现工具: " + rt.name() + " - " + rt.description());
        }

        // ============================================================
        // 2. Skill + Agent：组装一个智能体，装载本地技能 + MCP 远程工具
        // ============================================================
        banner("② Skill + Agent —— 给智能体装上「能力」");

        Agent agent = new Agent(
                "小助手",
                "你是一个严谨的助理，善于把复杂任务拆成多步、逐步调用工具来完成。");

        // 2a. 装载本地技能（Skill）
        agent.addSkill(new CalculatorSkill());
        agent.addSkill(new TextStatsSkill());
        System.out.println("  -> 已装载本地技能: calculator, text_stats");

        // 2b. 把每个 MCP 远程工具适配成本地 Tool 后装载
        for (McpClient.RemoteTool rt : remoteTools) {
            agent.addTool(new McpToolAdapter(mcpClient, rt));
        }
        System.out.println("  -> 已把 MCP 远程工具适配并装载: get_weather");
        System.out.println();
        System.out.println("  Agent 的系统提示（Harness 每轮发给模型的第一条消息）:");
        System.out.println(indent(agent.buildSystemPrompt()));

        // ============================================================
        // 3. Harness：用一个 LLM 客户端驱动 agent 循环跑起来
        // ============================================================
        banner("③ Harness —— 驱动智能体循环：感知→决策→行动");

        LlmClient llm = new MockLlmClient(); // 想接真实模型就换这一行的实现
        AgentHarness harness = new AgentHarness(agent, llm, /*maxIterations=*/ 8);

        String userGoal = "帮我计算 (12 + 8) * 3，并查一下北京的天气，最后做个总结。";
        String answer = harness.run(userGoal);

        // ============================================================
        // 4. 结果
        // ============================================================
        banner("④ 最终回答");
        System.out.println(answer);
        System.out.println();
        System.out.println("说明：以上 calculator/text_stats 是 Skill；get_weather 经 MCP 协议接入；");
        System.out.println("整个「思考-调用工具-回填-再思考」的循环由 Harness 驱动；它们合起来构成一个 Agent。");
    }

    private static Map<String, Object> weatherInputSchema() {
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

    private static void banner(String title) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(" " + title);
        System.out.println("============================================================");
    }

    private static String indent(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            sb.append("    | ").append(line).append('\n');
        }
        return sb.toString();
    }
}
