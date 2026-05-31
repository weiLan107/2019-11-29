# agent-concepts-demo

用一个**端到端、可运行**的 Java 示例，讲清楚大模型领域常被混用的四个名词：
**Agent（智能体）、Harness（运行骨架）、Skill（技能）、MCP（模型上下文协议）**。

工程**主代码零第三方依赖**（仅用 JDK 标准库），测试使用 JUnit 5。

## 四个名词一句话区分

| 名词 | 是什么 | 类比 | 对应代码 |
|---|---|---|---|
| **Agent（智能体）** | 能自主"感知→决策→行动"、循环调用工具完成目标的 LLM 系统；= 角色设定 + 能力集合 | 一个会用工具的"员工" | `agent/Agent.java` |
| **Harness（运行骨架）** | 驱动 Agent 跑起来的引擎/运行时：组织上下文、调模型、执行工具、循环、兜底 | 员工的"工位 + 工作流程" | `harness/AgentHarness.java` |
| **Skill（技能）** | 打包好的、可插拔的能力单元（说明书 + 可执行逻辑） | 员工掌握的一项"技能" | `skill/*.java` |
| **MCP（模型上下文协议）** | 一套标准协议（JSON-RPC 2.0），把"外部能力/数据源"标准化接入 Agent | 设备间的"USB 接口" | `mcp/*.java` |

关系：**Harness 这台引擎，驱动一个由 Skill 和 MCP 工具武装起来的 Agent。**

```
+----------------- Harness（引擎/循环）-------------------+
|  组织上下文 -> 调 LLM 决策 -> 执行工具 -> 回填 -> 直到最终回答 |
|                                                          |
|   Agent（身份 + 能力集合）                                 |
|     |-- Skill：calculator、text_stats（本地能力）          |
|     +-- Tool <- McpToolAdapter <- McpClient --JSON-RPC--> McpServer (get_weather) |
|                                    （MCP：标准化接入协议）   |
+----------------------------------------------------------+
```

## 目录结构

```
src/main/java/com/example/aidemo/
├── Main.java                 # 入口：把四个概念串成端到端 demo
├── agent/Agent.java          # Agent：身份 + 能力集合 + 系统提示
├── harness/AgentHarness.java # Harness：ReAct 循环引擎
├── skill/                    # Skill：Skill 基类 + Calculator / TextStats
├── mcp/                      # MCP：Server / Client / 传输层 / 工具适配器
├── llm/                      # LLM 抽象 + MockLlmClient + OpenAiLlmClient
├── tool/                     # Tool 统一抽象 + 注册表
└── json/Json.java            # 零依赖 JSON 工具（支撑 MCP 报文）

src/test/java/com/example/aidemo/   # JUnit 5 测试
```

## 运行

### 方式一：纯 JDK（零依赖，无需联网）

```bash
javac -encoding UTF-8 -d target/classes $(find src/main/java -name '*.java')
java -cp target/classes com.example.aidemo.Main
```

### 方式二：Maven

```bash
mvn -q compile exec:java
```

默认使用确定性的 `MockLlmClient`，输出会依次展示：
① MCP 握手与工具发现 → ② Agent 装载技能 → ③ Harness 的多步 ReAct 循环
（算 `12+8=20` → `20*3=60` → 查北京天气 → 总结）→ ④ 最终回答。

## 接入真实大模型

设置环境变量后再运行，`Main` 会自动切换到 `OpenAiLlmClient`（兼容 OpenAI Chat Completions 接口）：

```bash
export LLM_API_KEY=sk-xxxx
export LLM_BASE_URL=https://api.openai.com/v1   # 可选，默认值
export LLM_MODEL=gpt-4o-mini                     # 可选，默认值
mvn -q compile exec:java
```

同样适用于任何 OpenAI 兼容服务，只需改 `LLM_BASE_URL` / `LLM_MODEL`：

| 服务 | LLM_BASE_URL | 示例模型 |
|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| Moonshot/Kimi | `https://api.moonshot.cn/v1` | `moonshot-v1-8k` |
| 本地 Ollama | `http://localhost:11434/v1` | `qwen2.5` |

实现原理：采用"ReAct 文本协议"——系统提示要求模型输出固定格式的 JSON
（`{"thought":...,"action":{"tool":...,"args":{...}}}` 或 `{"thought":...,"final":...}`），
再由 `OpenAiLlmClient.parseAction` 解析回内部决策对象。这样无需依赖各家原生
function-calling 字段，几乎可兼容所有聊天模型。

## 运行测试

```bash
mvn test
```

测试覆盖：JSON 序列化/反序列化、计算器与文本统计技能、MCP 端到端往返
（Server→Client→Adapter）、Harness 完整循环与异常/最大步数兜底、
以及真实模型客户端中与网络无关的纯逻辑（请求体构造、响应与协议解析）。
