package com.example.aidemo.harness;

import com.example.aidemo.agent.Agent;
import com.example.aidemo.example.DirectToolResultExample;
import com.example.aidemo.llm.LlmClient;
import com.example.aidemo.llm.LlmResponse;
import com.example.aidemo.llm.Message;
import com.example.aidemo.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证「直接返回」模式：decideOnce（只决策不执行）与 runSingleTurn（执行一次返回原始结果，不回灌模型）。
 */
class SingleTurnHarnessTest {

    /** 一个会记录执行次数的工具，用来断言"是否真的被执行"。 */
    private static final class CountingTool implements Tool {
        int executions = 0;

        @Override
        public String name() {
            return "counter";
        }

        @Override
        public String description() {
            return "测试用计数工具";
        }

        @Override
        public String execute(Map<String, Object> arguments) {
            executions++;
            return "count=" + executions;
        }
    }

    /** 固定返回某个决策的假模型。 */
    private static LlmClient modelReturning(LlmResponse fixed) {
        return (List<Message> messages) -> fixed;
    }

    @Test
    void decideOnceReturnsToolIntentWithoutExecuting() {
        CountingTool tool = new CountingTool();
        Agent agent = new Agent("t", "test").addTool(tool);
        LlmClient model = modelReturning(
                LlmResponse.toolCall("调用计数器", "counter", Map.of("k", "v")));
        AgentHarness harness = new AgentHarness(agent, model, 8);

        LlmResponse decision = harness.decideOnce("做点什么");

        assertTrue(decision.isToolCall());
        assertEquals("counter", decision.toolName());
        assertEquals("v", decision.arguments().get("k"));
        // 关键：decideOnce 不应执行工具
        assertEquals(0, tool.executions, "decideOnce 不应执行工具");
    }

    @Test
    void runSingleTurnExecutesOnceAndReturnsRawResult() {
        CountingTool tool = new CountingTool();
        Agent agent = new Agent("t", "test").addTool(tool);
        LlmClient model = modelReturning(
                LlmResponse.toolCall("调用计数器", "counter", Map.of()));
        AgentHarness harness = new AgentHarness(agent, model, 8);

        AgentHarness.SingleTurnResult r = harness.runSingleTurn("做点什么");

        assertTrue(r.toolCall());
        assertEquals("counter", r.toolName());
        assertEquals("count=1", r.toolResult());
        assertNull(r.finalAnswer());
        // 关键：只执行一次，没有第二次（不回灌模型再触发）
        assertEquals(1, tool.executions, "工具应恰好执行一次");
    }

    @Test
    void runSingleTurnReturnsFinalAnswerWhenModelAnswersDirectly() {
        Agent agent = new Agent("t", "test");
        LlmClient model = modelReturning(LlmResponse.finalAnswer("直接答", "你好"));
        AgentHarness harness = new AgentHarness(agent, model, 8);

        AgentHarness.SingleTurnResult r = harness.runSingleTurn("打个招呼");

        assertFalse(r.toolCall());
        assertEquals("你好", r.finalAnswer());
        assertNull(r.toolName());
    }

    @Test
    void runSingleTurnReportsErrorForUnknownTool() {
        Agent agent = new Agent("t", "test");
        LlmClient model = modelReturning(
                LlmResponse.toolCall("调用不存在的工具", "ghost", Map.of()));
        AgentHarness harness = new AgentHarness(agent, model, 8);

        AgentHarness.SingleTurnResult r = harness.runSingleTurn("做点什么");

        assertTrue(r.toolCall());
        assertEquals("ghost", r.toolName());
        assertTrue(r.toolResult().contains("不存在"), r.toolResult());
    }

    @Test
    void exampleBuildsAgentWithWeatherTool() {
        Agent agent = DirectToolResultExample.buildAgent();
        assertTrue(agent.tools().has("get_weather"));
        assertTrue(agent.tools().has("calculator"));

        // 用示例的 agent 直接跑一次 runSingleTurn，拿到天气工具的原始结果
        LlmClient model = modelReturning(
                LlmResponse.toolCall("查天气", "get_weather", Map.of("city", "北京")));
        AgentHarness.SingleTurnResult r =
                new AgentHarness(agent, model, 8).runSingleTurn("北京天气怎么样？");
        assertTrue(r.toolResult().contains("北京"), r.toolResult());
    }
}
