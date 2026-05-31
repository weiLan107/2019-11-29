package com.example.aidemo.mcp;

import com.example.aidemo.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证 MCP：Server 暴露工具 -> Client 经 JSON-RPC 发现并调用 -> Adapter 适配为本地 Tool。
 */
class McpRoundTripTest {

    private McpServer server;
    private McpClient client;

    @BeforeEach
    void setUp() {
        server = new McpServer("test-server");
        server.registerTool(
                "echo",
                "回显输入的 message",
                schema(),
                args -> "echo:" + args.get("message"));
        client = new McpClient(new InMemoryTransport(server, false));
    }

    @Test
    void initializeReturnsServerInfo() {
        Map<String, Object> result = client.initialize();
        @SuppressWarnings("unchecked")
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("test-server", serverInfo.get("name"));
    }

    @Test
    void listsAdvertisedTools() {
        List<McpClient.RemoteTool> tools = client.listTools();
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).name());
    }

    @Test
    void callsToolAndGetsTextContent() {
        String out = client.callTool("echo", Map.of("message", "hi"));
        assertEquals("echo:hi", out);
    }

    @Test
    void unknownToolRaisesRpcError() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.callTool("does_not_exist", Map.of()));
        assertTrue(ex.getMessage().contains("未知工具"), ex.getMessage());
    }

    @Test
    void adapterExposesRemoteToolAsLocalTool() {
        McpClient.RemoteTool remote = client.listTools().get(0);
        Tool tool = new McpToolAdapter(client, remote);
        assertEquals("echo", tool.name());
        assertTrue(tool.description().startsWith("[MCP]"));
        assertEquals("echo:world", tool.execute(Map.of("message", "world")));
    }

    private static Map<String, Object> schema() {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("message", prop);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }
}
