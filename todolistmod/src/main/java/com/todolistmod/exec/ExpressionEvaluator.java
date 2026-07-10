package com.todolistmod.exec;

import com.todolistmod.store.ChecklistStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 递归下降表达式求值器。支持变量引用、算术运算、比较运算和逻辑运算。
 *
 * <p>语法：
 * <pre>
 * 表达式 = 逻辑或
 * 逻辑或 = 逻辑与 ("||" 逻辑和)*
 * 逻辑与 = 逻辑非 ("&&" 逻辑非)*
 * 逻辑非 = "!" 逻辑非 | 比较
 * 比较   = 加减 (("==" | "!=" | "<" | ">" | "<=" | ">=") 加减)?
 * 加减   = 乘除 (("+" | "-") 乘除)*
 * 乘除   = 因子 (("*" | "/" | "%") 因子)*
 * 因子   = 数字 | 字符串 | 布尔 | 变量引用 | "(" 表达式 ")"
 * </pre>
 */
public class ExpressionEvaluator {
    private final Map<String, Object> variables;
    private List<Token> tokens;
    private int pos;

    public ExpressionEvaluator(Map<String, Object> customVars) {
        this.variables = customVars;
    }

    /** 求值表达式，返回 Object（Integer/Double/Boolean/String）或 null */
    public Object evaluate(String expr) {
        if (expr == null || expr.trim().isEmpty()) return null;
        try {
            tokens = tokenize(expr);
            pos = 0;
            Object result = parseOr();
            if (pos < tokens.size()) {
                ChecklistStore.LOGGER.warn("[ChatTodolist] 表达式未完全解析: {}", expr);
            }
            return result;
        } catch (Exception e) {
            ChecklistStore.LOGGER.warn("[ChatTodolist] 表达式求值失败 '{}': {}", expr, e.getMessage());
            return null;
        }
    }

    /** 简单变量替换：将 ${name} 替换为字符串值，用于 print/run */
    public static String substitute(String text, Map<String, Object> vars) {
        if (text == null) return "";
        if (!text.contains("${")) return text;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 1 < text.length() && text.charAt(i) == '$' && text.charAt(i + 1) == '{') {
                int end = text.indexOf('}', i + 2);
                if (end == -1) {
                    sb.append(text.substring(i));
                    break;
                }
                String name = text.substring(i + 2, end).trim();
                sb.append(varToString(name, vars));
                i = end + 1;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /** 将变量名转换为字符串值 */
    private static String varToString(String name, Map<String, Object> vars) {
        Object val = null;
        if (vars != null && vars.containsKey(name)) {
            val = vars.get(name);
        } else {
            val = GameVariables.get(name);
        }
        if (val == null) return "?";
        return String.valueOf(val);
    }

    // ===== Tokenizer =====

    private enum TokenType { NUMBER, STRING, IDENT, OP, LPAREN, RPAREN, EOF }

    private static class Token {
        final TokenType type;
        final String value;
        Token(TokenType type, String value) { this.type = type; this.value = value; }
    }

    private List<Token> tokenize(String expr) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            // Variable reference ${name}
            if (c == '$' && i + 1 < expr.length() && expr.charAt(i + 1) == '{') {
                int end = expr.indexOf('}', i + 2);
                if (end == -1) throw new RuntimeException("未闭合的 ${");
                String name = expr.substring(i + 2, end).trim();
                tokens.add(new Token(TokenType.IDENT, name));
                i = end + 1;
                continue;
            }
            // String literal
            if (c == '"') {
                int end = expr.indexOf('"', i + 1);
                if (end == -1) throw new RuntimeException("未闭合的字符串");
                tokens.add(new Token(TokenType.STRING, expr.substring(i + 1, end)));
                i = end + 1;
                continue;
            }
            // Number
            if (Character.isDigit(c) || (c == '-' && (tokens.isEmpty() || isOpToken(tokens.get(tokens.size()-1))))) {
                StringBuilder sb = new StringBuilder();
                if (c == '-') { sb.append('-'); i++; }
                boolean hasDot = false;
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    if (expr.charAt(i) == '.') hasDot = true;
                    sb.append(expr.charAt(i));
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, sb.toString()));
                continue;
            }
            // Identifier (true/false)
            if (Character.isLetter(c)) {
                StringBuilder sb = new StringBuilder();
                while (i < expr.length() && (Character.isLetterOrDigit(expr.charAt(i)) || expr.charAt(i) == '_')) {
                    sb.append(expr.charAt(i));
                    i++;
                }
                tokens.add(new Token(TokenType.IDENT, sb.toString()));
                continue;
            }
            // Two-char operators
            if (i + 1 < expr.length()) {
                String two = expr.substring(i, i + 2);
                if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=") || two.equals("&&") || two.equals("||")) {
                    tokens.add(new Token(TokenType.OP, two));
                    i += 2;
                    continue;
                }
            }
            // Single-char operators
            if (c == '(') { tokens.add(new Token(TokenType.LPAREN, "(")); i++; continue; }
            if (c == ')') { tokens.add(new Token(TokenType.RPAREN, ")")); i++; continue; }
            if ("+-*/%<>!".indexOf(c) >= 0) {
                tokens.add(new Token(TokenType.OP, String.valueOf(c)));
                i++;
                continue;
            }
            throw new RuntimeException("未知字符: " + c);
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private boolean isOpToken(Token t) {
        return t.type == TokenType.OP || t.type == TokenType.LPAREN;
    }

    private Token peek() { return tokens.get(pos); }
    private Token next() { return tokens.get(pos++); }
    private boolean match(String op) {
        Token t = peek();
        if (t.type == TokenType.OP && t.value.equals(op)) { pos++; return true; }
        return false;
    }

    // ===== Parser =====

    private Object parseOr() {
        Object left = parseAnd();
        while (match("||")) {
            Object right = parseAnd();
            left = toBool(left) || toBool(right);
        }
        return left;
    }

    private Object parseAnd() {
        Object left = parseNot();
        while (match("&&")) {
            Object right = parseNot();
            left = toBool(left) && toBool(right);
        }
        return left;
    }

    private Object parseNot() {
        if (match("!")) {
            Object val = parseNot();
            return !toBool(val);
        }
        return parseComparison();
    }

    private Object parseComparison() {
        Object left = parseAddSub();
        Token t = peek();
        if (t.type == TokenType.OP && (t.value.equals("==") || t.value.equals("!=") ||
            t.value.equals("<") || t.value.equals(">") || t.value.equals("<=") || t.value.equals(">="))) {
            next();
            Object right = parseAddSub();
            return compare(left, right, t.value);
        }
        return left;
    }

    private Object parseAddSub() {
        Object left = parseMulDiv();
        while (true) {
            Token t = peek();
            if (t.type == TokenType.OP && (t.value.equals("+") || t.value.equals("-"))) {
                next();
                Object right = parseMulDiv();
                if (t.value.equals("+")) {
                    // String concatenation if either is String
                    if (left instanceof String || right instanceof String) {
                        left = String.valueOf(left) + String.valueOf(right);
                    } else {
                        left = addNum(left, right);
                    }
                } else {
                    left = subNum(left, right);
                }
            } else break;
        }
        return left;
    }

    private Object parseMulDiv() {
        Object left = parseFactor();
        while (true) {
            Token t = peek();
            if (t.type == TokenType.OP && (t.value.equals("*") || t.value.equals("/") || t.value.equals("%"))) {
                next();
                Object right = parseFactor();
                double l = toNum(left);
                double r = toNum(right);
                switch (t.value) {
                    case "*": left = l * r; break;
                    case "/": left = r != 0 ? l / r : 0; break;
                    case "%": left = r != 0 ? l % r : 0; break;
                }
            } else break;
        }
        return left;
    }

    private Object parseFactor() {
        Token t = peek();
        switch (t.type) {
            case NUMBER: {
                next();
                String s = t.value;
                if (s.contains(".")) return Double.parseDouble(s);
                return Integer.parseInt(s);
            }
            case STRING: {
                next();
                return t.value;
            }
            case IDENT: {
                next();
                if (t.value.equals("true")) return Boolean.TRUE;
                if (t.value.equals("false")) return Boolean.FALSE;
                // Variable lookup
                Object val = null;
                if (variables != null && variables.containsKey(t.value)) {
                    val = variables.get(t.value);
                } else {
                    val = GameVariables.get(t.value);
                }
                return val;
            }
            case LPAREN: {
                next();
                Object val = parseOr();
                if (peek().type == TokenType.RPAREN) next();
                return val;
            }
            default:
                throw new RuntimeException("意外的 token: " + t.value);
        }
    }

    // ===== Type helpers =====

    private boolean toBool(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).doubleValue() != 0;
        if (o instanceof String) return !o.equals("") && !o.equals("false") && !o.equals("0");
        return false;
    }

    private double toNum(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof Boolean) return (Boolean) o ? 1 : 0;
        if (o instanceof String) {
            try { return Double.parseDouble((String) o); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    private Object addNum(Object a, Object b) {
        double r = toNum(a) + toNum(b);
        if (r == (int) r) return (int) r;
        return r;
    }

    private Object subNum(Object a, Object b) {
        double r = toNum(a) - toNum(b);
        if (r == (int) r) return (int) r;
        return r;
    }

    private boolean compare(Object a, Object b, String op) {
        // If both are numbers, numeric comparison
        if (a instanceof Number || b instanceof Number) {
            double av = toNum(a);
            double bv = toNum(b);
            switch (op) {
                case "==": return av == bv;
                case "!=": return av != bv;
                case "<": return av < bv;
                case ">": return av > bv;
                case "<=": return av <= bv;
                case ">=": return av >= bv;
            }
        }
        // String comparison
        String as = String.valueOf(a);
        String bs = String.valueOf(b);
        switch (op) {
            case "==": return as.equals(bs);
            case "!=": return !as.equals(bs);
            case "<": return as.compareTo(bs) < 0;
            case ">": return as.compareTo(bs) > 0;
            case "<=": return as.compareTo(bs) <= 0;
            case ">=": return as.compareTo(bs) >= 0;
        }
        return false;
    }
}
