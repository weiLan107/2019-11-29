package com.example.aidemo.mcp;

/**
 * MCP 传输层抽象。
 *
 * <p>MCP 客户端与服务器之间通过「发送一条 JSON-RPC 请求、收到一条 JSON-RPC 响应」
 * 来通信。真实的 MCP 通常走 stdio（标准输入输出）或 HTTP/SSE；
 * 本演示用进程内传输 {@link InMemoryTransport}，但传递的依旧是 JSON 文本，
 * 以便看到真实的报文格式。</p>
 */
public interface Transport {

    /**
     * 发送一条 JSON-RPC 请求文本，返回服务器的 JSON-RPC 响应文本。
     */
    String send(String jsonRpcRequest);
}
