package com.example.aidemo.mcp;

import com.example.aidemo.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 极简 MCP 客户端：通过 {@link Transport} 与 MCP 服务器通信。
 *
 * <p>宿主应用（这里就是 Agent 的 Harness）用它来发现并调用远程服务器提供的工具。
 * 客户端屏蔽了 JSON-RPC 细节，对外暴露 {@link #initialize()}、{@link #listTools()}、
 * {@link #callTool(String, Map)} 三个高层方法。</p>
 */
public class McpClient {

    /** 远程工具的元信息。 */
    public record RemoteTool(String name, String description, Map<String, Object> inputSchema) {
    }

    private final Transport transport;
    private final AtomicLong idSeq = new AtomicLong(1);

    public McpClient(Transport transport) {
        this.transport = transport;
    }

    /** 与服务器握手。 */
    public Map<String, Object> initialize() {
        return call("initialize", new LinkedHashMap<>());
    }

    /** 发现服务器提供的所有工具。 */
    @SuppressWarnings("unchecked")
    public List<RemoteTool> listTools() {
        Map<String, Object> result = call("tools/list", new LinkedHashMap<>());
        List<Object> rawTools = (List<Object>) result.getOrDefault("tools", List.of());
        List<RemoteTool> tools = new ArrayList<>();
        for (Object o : rawTools) {
            Map<String, Object> m = (Map<String, Object>) o;
            tools.add(new RemoteTool(
                    (String) m.get("name"),
                    (String) m.get("description"),
                    (Map<String, Object>) m.getOrDefault("inputSchema", new LinkedHashMap<>())));
        }
        return tools;
    }

    /** 调用某个远程工具，返回其文本结果。 */
    @SuppressWarnings("unchecked")
    public String callTool(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments);
        Map<String, Object> result = call("tools/call", params);

        List<Object> content = (List<Object>) result.getOrDefault("content", List.of());
        StringBuilder sb = new StringBuilder();
        for (Object item : content) {
            Map<String, Object> m = (Map<String, Object>) item;
            if ("text".equals(m.get("type"))) {
                sb.append(m.get("text"));
            }
        }
        return sb.toString();
    }

    /** 发送一次 JSON-RPC 请求并返回 result 部分；遇到 error 抛异常。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, Map<String, Object> params) {
        long id = idSeq.getAndIncrement();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        String responseJson = transport.send(Json.write(request));
        Map<String, Object> response = Json.parseObject(responseJson);

        if (response.containsKey("error")) {
            Map<String, Object> err = (Map<String, Object>) response.get("error");
            throw new RuntimeException("MCP 调用失败 [" + err.get("code") + "]: " + err.get("message"));
        }
        return (Map<String, Object>) response.getOrDefault("result", new LinkedHashMap<>());
    }
}
