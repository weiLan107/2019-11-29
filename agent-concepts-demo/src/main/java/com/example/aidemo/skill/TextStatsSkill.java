package com.example.aidemo.skill;

import java.util.Map;

/**
 * 文本统计技能：统计一段文本的字符数 / 词数 / 行数。
 *
 * <p>演示第二个"本地技能"，说明技能是可以并列、可插拔地注册到智能体上的。
 * 参数：{@code {"text": "hello world"}}。</p>
 */
public class TextStatsSkill extends Skill {

    public TextStatsSkill() {
        super("text_stats", "统计文本的字符数、单词数、行数。");
    }

    @Override
    public String instructions() {
        return """
               【text_stats 使用说明】
               - 用途：统计文本规模。
               - 参数：text（字符串）。
               - 返回：字符数、单词数、行数。
               """;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Object text = arguments.get("text");
        if (text == null) {
            return "错误：缺少参数 text";
        }
        String t = text.toString();
        int chars = t.length();
        int words = t.trim().isEmpty() ? 0 : t.trim().split("\\s+").length;
        int lines = t.isEmpty() ? 0 : t.split("\n", -1).length;
        return "字符数=" + chars + ", 单词数=" + words + ", 行数=" + lines;
    }
}
