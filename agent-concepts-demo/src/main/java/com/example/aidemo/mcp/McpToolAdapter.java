package com.example.aidemo.mcp;

import com.example.aidemo.tool.Tool;

import java.util.Map;

/**
 * 适配器：把一个「远程 MCP 工具」包装成本地 {@link Tool}。
 *
 * <p>这是 MCP 与 Agent 衔接的关键一环：Harness 不关心某个能力到底是本地 Skill
 * 还是远程 MCP 工具，它只面向 {@link Tool} 接口编程。适配器在 {@link #execute}
 * 时把调用透明地转发给 {@link McpClient}。</p>
 */
public class McpToolAdapter implements Tool {

    private final McpClient client;
    private final String name;
    private final String description;

    public McpToolAdapter(McpClient client, McpClient.RemoteTool remoteTool) {
        this.client = client;
        this.name = remoteTool.name();
        this.description = "[MCP] " + remoteTool.description();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        return client.callTool(name, arguments);
    }
}
