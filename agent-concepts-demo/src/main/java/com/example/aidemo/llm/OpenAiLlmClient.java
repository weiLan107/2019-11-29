package com.example.aidemo.llm;

import com.example.aidemo.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个对接真实大模型的 {@link LlmClient} 实现，兼容 OpenAI 的 Chat Completions 接口。
 *
 * <p>它同样适用于任何「OpenAI 兼容」的服务，只需改 baseUrl / model，例如：</p>
 * <ul>
 *   <li>OpenAI：{@code https://api.openai.com/v1}，模型如 {@code gpt-4o-mini}</li>
 *   <li>DeepSeek：{@code https://api.deepseek.com/v1}，模型如 {@code deepseek-chat}</li>
 *   <li>Moonshot/Kimi：{@code https://api.moonshot.cn/v1}，模型如 {@code moonshot-v1-8k}</li>
 *   <li>本地 Ollama：{@code http://localhost:11434/v1}，模型如 {@code qwen2.5}</li>
 * </ul>
 *
 * <p>本类不使用厂商原生的 function-calling 字段，而是采用「ReAct 文本协议」：
 * 在系统提示里要求模型输出一个固定格式的 JSON（见 {@code Agent.buildSystemPrompt}），
 * 然后由 {@link #parseAction(String)} 把模型输出解析回 {@link LlmResponse}。
 * 这样可以兼容几乎所有聊天模型，而且解析逻辑可被单元测试覆盖。</p>
 *
 * <p>零第三方依赖：仅用 JDK 自带的 {@link HttpClient} 发请求，用本工程的
 * {@link Json} 做序列化/反序列化。</p>
 */
public class OpenAiLlmClient implements LlmClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    /**
     * @param baseUrl 形如 {@code https://api.openai.com/v1}（不带末尾斜杠）
     * @param apiKey  鉴权用的 API Key
     * @param model   模型名，例如 {@code gpt-4o-mini}
     */
    public OpenAiLlmClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public LlmResponse complete(List<Message> messages) {
        String requestBody = buildRequestBody(messages);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM 接口返回 " + resp.statusCode() + ": " + resp.body());
            }
            String content = extractContent(resp.body());
            return parseAction(content);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用 LLM 失败: " + e.getMessage(), e);
        }
    }

    /** 把内部 Message 列表转换成 OpenAI Chat Completions 的请求体 JSON。 */
    String buildRequestBody(List<Message> messages) {
        List<Object> apiMessages = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            switch (m.role()) {
                case SYSTEM -> {
                    msg.put("role", "system");
                    msg.put("content", m.content());
                }
                case USER -> {
                    msg.put("role", "user");
                    msg.put("content", m.content());
                }
                case ASSISTANT -> {
                    msg.put("role", "assistant");
                    msg.put("content", m.content());
                }
                case TOOL -> {
                    // 不使用原生 tool 角色（需 tool_call_id），统一作为观察结果回填给模型
                    msg.put("role", "user");
                    msg.put("content", "（工具返回结果）\n" + m.content());
                }
            }
            apiMessages.add(msg);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", apiMessages);
        body.put("temperature", 0);
        return Json.write(body);
    }

    /** 从响应 JSON 中取出 choices[0].message.content。 */
    @SuppressWarnings("unchecked")
    static String extractContent(String responseJson) {
        Map<String, Object> root = Json.parseObject(responseJson);
        List<Object> choices = (List<Object>) root.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("响应中没有 choices: " + responseJson);
        }
        Map<String, Object> first = (Map<String, Object>) choices.get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        Object content = message == null ? null : message.get("content");
        if (content == null) {
            throw new RuntimeException("响应中没有 message.content: " + responseJson);
        }
        return content.toString();
    }

    /**
     * 把模型输出的文本解析成 {@link LlmResponse}。
     *
     * <p>模型应输出形如
     * {@code {"thought":"...","action":{"tool":"calculator","args":{"expression":"12 + 8"}}}}
     * 或 {@code {"thought":"...","final":"..."}} 的 JSON。
     * 本方法会容忍模型在 JSON 外包裹的多余文字或 ```json 代码块。</p>
     */
    @SuppressWarnings("unchecked")
    public static LlmResponse parseAction(String content) {
        String json = extractJsonObject(content);
        Map<String, Object> obj = Json.parseObject(json);

        String thought = obj.get("thought") == null ? "" : obj.get("thought").toString();

        if (obj.containsKey("final") && obj.get("final") != null) {
            return LlmResponse.finalAnswer(thought, obj.get("final").toString());
        }

        Object actionObj = obj.get("action");
        if (actionObj instanceof Map<?, ?> action) {
            Map<String, Object> a = (Map<String, Object>) action;
            String tool = a.get("tool") == null ? null : a.get("tool").toString();
            Object argsObj = a.getOrDefault("args", new LinkedHashMap<>());
            Map<String, Object> args = argsObj instanceof Map
                    ? (Map<String, Object>) argsObj
                    : new LinkedHashMap<>();
            if (tool != null) {
                return LlmResponse.toolCall(thought, tool, args);
            }
        }

        // 兜底：模型没按协议给出 action/final，则把整段文本当作最终回答
        return LlmResponse.finalAnswer(thought, content.trim());
    }

    /**
     * 从可能包含多余内容的文本里截取第一个「平衡的」JSON 对象（从首个 '{' 到匹配的 '}'）。
     * 会正确跳过字符串内的花括号与转义字符。
     */
    static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            throw new RuntimeException("模型输出中找不到 JSON 对象: " + text);
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
                default -> { /* skip */ }
            }
        }
        throw new RuntimeException("模型输出中的 JSON 对象不完整: " + text);
    }
}
