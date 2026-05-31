package com.example.aidemo.skill;

import java.util.Map;

/**
 * 计算器技能：对一个算术表达式求值。
 *
 * <p>支持 + - * / 与括号，演示"本地代码型技能"。
 * 参数：{@code {"expression": "12 + 8"}}。</p>
 */
public class CalculatorSkill extends Skill {

    public CalculatorSkill() {
        super("calculator", "对算术表达式求值，支持 + - * / 和括号。");
    }

    @Override
    public String instructions() {
        return """
               【calculator 使用说明】
               - 用途：计算数学算术表达式。
               - 参数：expression（字符串），例如 "12 + 8" 或 "(20 * 3) / 2"。
               - 返回：计算结果（数字）。
               - 注意：一次只算一个表达式；若是多步计算，请拆成多次调用。
               """;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Object expr = arguments.get("expression");
        if (expr == null) {
            return "错误：缺少参数 expression";
        }
        try {
            double result = new ExpressionEvaluator(expr.toString()).parse();
            // 整数结果就不显示小数
            if (result == Math.rint(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (RuntimeException e) {
            return "计算失败：" + e.getMessage();
        }
    }

    /** 极简递归下降表达式求值器：expr -> term (('+'|'-') term)* 等。 */
    private static final class ExpressionEvaluator {
        private final String s;
        private int pos;

        ExpressionEvaluator(String s) {
            this.s = s;
        }

        double parse() {
            double v = parseExpression();
            skipSpaces();
            if (pos < s.length()) {
                throw new IllegalArgumentException("表达式有多余字符: " + s.substring(pos));
            }
            return v;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (consume('+')) {
                    value += parseTerm();
                } else if (consume('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (consume('*')) {
                    value *= parseFactor();
                } else if (consume('/')) {
                    value /= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipSpaces();
            if (consume('(')) {
                double value = parseExpression();
                skipSpaces();
                if (!consume(')')) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return value;
            }
            if (consume('-')) {
                return -parseFactor();
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipSpaces();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("期望数字，位置 " + pos);
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private boolean consume(char c) {
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }
    }
}
