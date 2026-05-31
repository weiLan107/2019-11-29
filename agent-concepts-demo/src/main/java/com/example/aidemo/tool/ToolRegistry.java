package com.example.aidemo.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具注册表：Harness 用它来登记、查找和调用所有可用工具。
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalStateException("工具名重复: " + tool.name());
        }
        tools.put(tool.name(), tool);
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public Tool get(String name) {
        Tool t = tools.get(name);
        if (t == null) {
            throw new IllegalArgumentException("未知工具: " + name);
        }
        return t;
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    /** 生成可读的工具清单文本，供写入系统提示。 */
    public String describeForPrompt() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : tools.values()) {
            sb.append("  - ").append(t.name())
              .append(": ").append(t.description())
              .append('\n');
        }
        return sb.toString();
    }
}
