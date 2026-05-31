package com.example.aidemo.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextStatsSkillTest {

    private final TextStatsSkill skill = new TextStatsSkill();

    @Test
    void countsCharsWordsLines() {
        String result = skill.execute(Map.of("text", "hello world\nsecond line"));
        assertTrue(result.contains("单词数=4"), result);
        assertTrue(result.contains("行数=2"), result);
        assertTrue(result.contains("字符数=23"), result);
    }

    @Test
    void returnsErrorWhenTextMissing() {
        assertTrue(skill.execute(Map.of()).contains("缺少"));
    }

    @Test
    void hasStableName() {
        assertEquals("text_stats", skill.name());
    }
}
