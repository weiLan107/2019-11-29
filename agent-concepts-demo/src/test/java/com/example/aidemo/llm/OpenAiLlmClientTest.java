package com.example.aidemo.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证真实模型客户端里「与网络无关」的纯逻辑：请求体构造、响应解析、ReAct 协议解析。
 */
class OpenAiLlmClientTest {

    private final OpenAiLlmClient client =
            new OpenAiLlmClient("https://api.example.com/v1/", "sk-test", "gpt-4o-mini");

    @Test
    void parsesToolCallAction() {
        String content = "{\"thought\":\"先算加法\",\"action\":{\"tool\":\"calculator\","
                + "\"args\":{\"expression\":\"12 + 8\"}}}";
        LlmResponse r = OpenAiLlmClient.parseAction(content);
        assertTrue(r.isToolCall());
        assertFalse(r.isFinal());
        assertEquals("calculator", r.toolName());
        assertEquals("12 + 8", r.arguments().get("expression"));
    }

    @Test
    void parsesFinalAnswer() {
        String content = "{\"thought\":\"够了\",\"final\":\"答案是 60\"}";
        LlmResponse r = OpenAiLlmClient.parseAction(content);
        assertTrue(r.isFinal());
        assertEquals("答案是 60", r.finalAnswer());
    }

    @Test
    void toleratesMarkdownFencesAndSurroundingProse() {
        String content = "好的，这是我的决定：\n```json\n"
                + "{\"thought\":\"查天气\",\"action\":{\"tool\":\"get_weather\","
                + "\"args\":{\"city\":\"北京\"}}}\n```\n（完）";
        LlmResponse r = OpenAiLlmClient.parseAction(content);
        assertTrue(r.isToolCall());
        assertEquals("get_weather", r.toolName());
        assertEquals("北京", r.arguments().get("city"));
    }

    @Test
    void extractsBalancedJsonIgnoringBracesInStrings() {
        String text = "prefix {\"final\":\"含有 } 花括号的字符串\"} suffix";
        String json = OpenAiLlmClient.extractJsonObject(text);
        assertEquals("{\"final\":\"含有 } 花括号的字符串\"}", json);
    }

    @Test
    void extractsContentFromOpenAiResponseShape() {
        String resp = "{\"id\":\"x\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}";
        assertEquals("hello", OpenAiLlmClient.extractContent(resp));
    }

    @Test
    void buildsRequestBodyMappingRolesAndModel() {
        String body = client.buildRequestBody(List.of(
                Message.system("你是助手"),
                Message.user("算一下"),
                Message.assistant("调用工具 calculator"),
                Message.tool("20")));

        assertTrue(body.contains("\"model\":\"gpt-4o-mini\""));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        // TOOL 角色被映射为 user，并加上观察前缀
        assertTrue(body.contains("（工具返回结果）"), body);
    }
}
