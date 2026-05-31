package com.example.aidemo.skill;

import com.example.aidemo.tool.Tool;

/**
 * 技能（Skill）：一个"打包好的、可复用的能力单元"。
 *
 * <p>它不仅是一个能被调用的 {@link Tool}，还自带一段「使用说明」
 * （{@link #instructions()}）。这正对应业界 "Agent Skills" 的做法：
 * 一个技能 = 元数据(名字/描述) + 说明书(SKILL.md) + 可执行逻辑(脚本/代码)。</p>
 *
 * <p>说明书会在技能被装载时注入系统提示，从而"按需"教会模型如何正确使用该能力，
 * 而无需把所有细节永远塞在上下文里。这就是技能的"渐进式装载 / 可插拔"特性。</p>
 */
public abstract class Skill implements Tool {

    private final String name;
    private final String description;

    protected Skill(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String description() {
        return description;
    }

    /**
     * 技能说明书：相当于 SKILL.md 的正文。
     * 描述何时使用、参数格式、注意事项等，会被注入系统提示。
     */
    public abstract String instructions();
}
