package cn.edu.medplatform.service;

import cn.edu.medplatform.entity.RuleDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/** 确定性规则引擎 —— JSON DSL 求值器 */
@Component
@RequiredArgsConstructor
public class RuleEngine {
    private final ObjectMapper om = new ObjectMapper();

    /** 评估单条规则，返回命中信息（null=未命中） */
    public Map<String, Object> evaluate(RuleDefinition rule, Map<String, Object> context) {
        try {
            JsonNode expr = om.readTree(rule.getConditionExpr());
            boolean hit = evalNode(expr, context);
            if (!hit) return null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ruleCode", rule.getCode());
            result.put("ruleName", rule.getName());
            result.put("category", rule.getCategory());
            result.put("riskLevel", rule.getRiskLevel());
            result.put("message", rule.getMessage());
            result.put("ruleVersion", rule.getCurrentVersion());
            result.put("ruleId", rule.getId());
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean evalNode(JsonNode node, Map<String, Object> ctx) {
        if (node.has("all")) {
            for (JsonNode child : node.get("all")) {
                if (!evalNode(child, ctx)) return false;
            }
            return true;
        }
        if (node.has("any")) {
            for (JsonNode child : node.get("any")) {
                if (evalNode(child, ctx)) return true;
            }
            return false;
        }
        if (node.has("not")) {
            return !evalNode(node.get("not"), ctx);
        }
        // 叶子条件：{field, op, value}
        String field = node.path("field").asText();
        String op = node.path("op").asText();
        JsonNode valueNode = node.get("value");
        Object fieldValue = ctx.get(field);
        return evalCondition(field, op, valueNode, fieldValue, ctx);
    }

    @SuppressWarnings("unchecked")
    private boolean evalCondition(String field, String op, JsonNode valueNode, Object fieldValue, Map<String, Object> ctx) {
        // 合并文本字段：symptoms = chiefComplaint + accompanying
        String fieldText = toText(fieldValue);

        switch (op) {
            case "EQ":
                return valueNode.asText().equals(String.valueOf(fieldValue));
            case "NE":
                return !valueNode.asText().equals(String.valueOf(fieldValue));
            case "IN":
                String fv = String.valueOf(fieldValue);
                for (JsonNode v : valueNode) if (v.asText().equals(fv)) return true;
                return false;
            case "CONTAINS_ANY":
                for (JsonNode v : valueNode) {
                    if (fieldText.contains(v.asText())) return true;
                }
                return false;
            case "CONTAINS_ALL":
                for (JsonNode v : valueNode) {
                    if (!fieldText.contains(v.asText())) return false;
                }
                return true;
            case "GT":
                return toNumber(fieldValue) > valueNode.asDouble();
            case "LT":
                return toNumber(fieldValue) < valueNode.asDouble();
            case "IS_EMPTY":
                return fieldValue == null || String.valueOf(fieldValue).isBlank();
            default:
                return false;
        }
    }

    private String toText(Object value) {
        if (value == null) return "";
        if (value instanceof List) return String.join(" ", (List<String>) value);
        if (value instanceof String) return (String) value;
        return String.valueOf(value);
    }

    private double toNumber(Object value) {
        if (value == null) return 0;
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (Exception e) { return 0; }
    }

    /** 从表单数据构建规则上下文 */
    public static Map<String, Object> buildContext(Map<String, Object> formData) {
        Map<String, Object> ctx = new HashMap<>();
        Map<String, Object> basic = (Map<String, Object>) formData.getOrDefault("basic", Map.of());
        ctx.put("specialGroup", basic.getOrDefault("specialGroup", "NONE"));
        ctx.put("severity", formData.getOrDefault("severity", ""));
        ctx.put("onsetTime", formData.get("onsetTime"));

        // symptoms = chiefComplaint + accompanying 聚合文本
        StringBuilder symptoms = new StringBuilder();
        symptoms.append(formData.getOrDefault("chiefComplaint", ""));
        Object acc = formData.get("accompanying");
        if (acc instanceof List) symptoms.append(" ").append(String.join(" ", (List<String>) acc));
        ctx.put("symptoms", symptoms.toString());

        ctx.put("pastHistory", formData.getOrDefault("pastHistory", ""));
        ctx.put("allergyHistory", formData.getOrDefault("allergyHistory", ""));
        ctx.put("medication", formData.getOrDefault("medication", ""));
        ctx.put("chiefComplaint", formData.getOrDefault("chiefComplaint", ""));

        // durationDays 从 onsetTime 计算
        Object onset = formData.get("onsetTime");
        if (onset != null && !onset.toString().isBlank()) {
            try {
                java.time.LocalDate onsetDate = java.time.LocalDate.parse(onset.toString());
                long days = java.time.temporal.ChronoUnit.DAYS.between(onsetDate, java.time.LocalDate.now());
                ctx.put("durationDays", (int) Math.max(0, days));
            } catch (Exception e) {
                ctx.put("durationDays", 0);
            }
        } else {
            ctx.put("durationDays", 0);
        }
        return ctx;
    }
}
