package com.example.aidemo.mcp;

import com.example.aidemo.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 一个极简 MCP 服务器：用 JSON-RPC 2.0 对外暴露「工具」。
 *
 * <p>MCP（Model Context Protocol，模型上下文协议）是一套<strong>标准协议</strong>，
 * 让"提供能力的一方（Server）"与"使用能力的一方（Client/宿主应用）"解耦。
 * 任何遵守该协议的服务器都能被任何遵守该协议的客户端接入——
 * 就像 USB 之于外设。</p>
 *
 * <p>本服务器实现 MCP 最核心的三个方法：</p>
 * <ul>
 *   <li>{@code initialize}：握手，交换协议版本与能力。</li>
 *   <li>{@code tools/list}：列出本服务器提供的工具及其参数 schema。</li>
 *   <li>{@code tools/call}：按名字调用某个工具并返回结果。</li>
 * </ul>
 */
public class McpServer {

    /** 一个工具的定义：名字、描述、入参 schema、以及执行逻辑。 */
    private record ToolDef(String name,
                           String description,
                           Map<String, Object> inputSchema,
                           Function<Map<String, Object>, String> handler) {
    }

    private final String serverName;
    private final Map<String, ToolDef> tools = new LinkedHashMap<>();

    public McpServer(String serverName) {
        this.serverName = serverName;
    }

    /** 注册一个工具。inputSchema 用 JSON-Schema 风格的 Map 描述参数。 */
    public void registerTool(String name,
                             String description,
                             Map<String, Object> inputSchema,
                             Function<Map<String, Object>, String> handler) {
        tools.put(name, new ToolDef(name, description, inputSchema, handler));
    }

    /**
     * 处理一条 JSON-RPC 请求文本，返回响应文本。这是服务器的统一入口。
     */
    public String handle(String requestJson) {
        Map<String, Object> req;
        Object id = null;
        try {
            req = Json.parseObject(requestJson);
            id = req.get("id");
            String method = (String) req.get("method");
            @SuppressWarnings("unchecked")
            Map<String, Object> params =
                    (Map<String, Object>) req.getOrDefault("params", new LinkedHashMap<>());

            Object result = switch (method) {
                case "initialize" -> handleInitialize();
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(params);
                default -> throw new RpcException(-32601, "未知方法: " + method);
            };
            return success(id, result);
        } catch (RpcException e) {
            return error(id, e.code, e.getMessage());
        } catch (Exception e) {
            return error(id, -32603, "服务器内部错误: " + e.getMessage());
        }
    }

    private Map<String, Object> handleInitialize() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("protocolVersion", "2024-11-05");
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", new LinkedHashMap<>());
        info.put("capabilities", capabilities);
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", serverName);
        serverInfo.put("version", "1.0.0");
        info.put("serverInfo", serverInfo);
        return info;
    }

    private Map<String, Object> handleToolsList() {
        List<Object> list = new ArrayList<>();
        for (ToolDef def : tools.values()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", def.name());
            t.put("description", def.description());
            t.put("inputSchema", def.inputSchema());
            list.add(t);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", list);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        String name = (String) params.get("name");
        Map<String, Object> arguments =
                (Map<String, Object>) params.getOrDefault("arguments", new LinkedHashMap<>());
        ToolDef def = tools.get(name);
        if (def == null) {
            throw new RpcException(-32602, "未知工具: " + name);
        }
        String text = def.handler().apply(arguments);

        // MCP 约定：tools/call 的结果放在 content 数组里，每项有 type/text
        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(contentItem));
        result.put("isError", false);
        return result;
    }

    // ------- JSON-RPC 响应封装 -------

    private String success(Object id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return Json.write(resp);
    }

    private String error(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", err);
        return Json.write(resp);
    }

    private static final class RpcException extends RuntimeException {
        final int code;

        RpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
