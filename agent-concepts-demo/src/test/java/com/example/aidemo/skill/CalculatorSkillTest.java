package com.example.aidemo.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorSkillTest {

    private final CalculatorSkill skill = new CalculatorSkill();

    @Test
    void addsTwoNumbers() {
        assertEquals("20", skill.execute(Map.of("expression", "12 + 8")));
    }

    @Test
    void respectsOperatorPrecedenceAndParentheses() {
        assertEquals("60", skill.execute(Map.of("expression", "(12 + 8) * 3")));
        assertEquals("14", skill.execute(Map.of("expression", "2 + 3 * 4")));
        assertEquals("30", skill.execute(Map.of("expression", "(20 * 3) / 2")));
    }

    @Test
    void supportsNegativeNumbers() {
        assertEquals("-5", skill.execute(Map.of("expression", "5 - 10")));
        assertEquals("6", skill.execute(Map.of("expression", "-2 * -3")));
    }

    @Test
    void returnsErrorWhenExpressionMissing() {
        String result = skill.execute(Map.of());
        assertTrue(result.contains("缺少"), "应提示缺少参数，实际: " + result);
    }

    @Test
    void exposesNameAndInstructions() {
        assertEquals("calculator", skill.name());
        assertTrue(skill.instructions().contains("expression"));
    }
}
