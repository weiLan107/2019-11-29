package com.example.aidemo.mcp;

/**
 * 进程内传输：直接把请求文本交给同进程的 {@link McpServer} 处理。
 *
 * <p>真实环境里这里会是 stdio 管道或 HTTP 连接；为了让示例零依赖、可独立运行，
 * 这里用方法调用代替网络/管道，但收发的仍是 JSON-RPC 文本。</p>
 */
public class InMemoryTransport implements Transport {

    private final McpServer server;
    private final boolean trace;

    public InMemoryTransport(McpServer server, boolean trace) {
        this.server = server;
        this.trace = trace;
    }

    @Override
    public String send(String jsonRpcRequest) {
        if (trace) {
            System.out.println("    [MCP wire] --> " + jsonRpcRequest);
        }
        String response = server.handle(jsonRpcRequest);
        if (trace) {
            System.out.println("    [MCP wire] <-- " + response);
        }
        return response;
    }
}
