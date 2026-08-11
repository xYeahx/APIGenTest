package com.apigentest.service.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * LLM 输出结构化校验与规范化：
 * 约束输出 JSON 结构（results[].cases[]），字段类型 / 枚举校验，规范化后转统一用例形态
 */
@Component
public class GenerationValidator {

    private static final Set<String> SCENARIO_TYPES = Set.of("normal", "boundary", "exception");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    private final ObjectMapper objectMapper;

    public GenerationValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<GeneratedCase> parseAndValidate(String content, Long expectedApiId) {
        JsonNode root = parse(stripFences(content));
        JsonNode results = root.get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            throw new GenerationValidationException("输出缺少 results 数组");
        }
        JsonNode target = null;
        for (JsonNode r : results) {
            if (r.isObject() && r.path("apiId").asLong(-1) == expectedApiId) {
                target = r;
                break;
            }
        }
        if (target == null) {
            throw new GenerationValidationException("输出中缺少 apiId=" + expectedApiId + " 的结果");
        }
        JsonNode cases = target.get("cases");
        if (cases == null || !cases.isArray() || cases.isEmpty()) {
            throw new GenerationValidationException("apiId=" + expectedApiId + " 的 cases 为空");
        }
        List<GeneratedCase> list = new ArrayList<>();
        for (JsonNode c : cases) {
            list.add(parseCase(c));
        }
        return list;
    }

    private JsonNode parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new GenerationValidationException("LLM 输出不是 JSON 对象");
            }
            return node;
        } catch (GenerationValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new GenerationValidationException("LLM 输出不是合法 JSON");
        }
    }

    private GeneratedCase parseCase(JsonNode c) {
        if (c == null || !c.isObject()) {
            throw new GenerationValidationException("cases 中存在非对象元素");
        }
        String name = text(c.get("name"));
        if (name == null || name.isBlank()) {
            throw new GenerationValidationException("存在缺少 name 的用例");
        }
        String scenarioType = text(c.get("scenarioType"));
        if (scenarioType == null || !SCENARIO_TYPES.contains(scenarioType)) {
            throw new GenerationValidationException("场景类型非法（仅支持 normal/boundary/exception）：" + scenarioType);
        }
        String method = text(c.get("method"));
        if (method == null || !METHODS.contains(method.toUpperCase())) {
            throw new GenerationValidationException("请求方法非法：" + method);
        }
        String urlTemplate = text(c.get("urlTemplate"));
        if (urlTemplate == null || urlTemplate.isBlank()) {
            throw new GenerationValidationException("用例「" + name + "」缺少 urlTemplate");
        }
        GeneratedCase gc = new GeneratedCase();
        gc.setName(name);
        gc.setScenarioType(scenarioType);
        gc.setMethod(method.toUpperCase());
        gc.setUrlTemplate(urlTemplate);
        gc.setHeaders(jsonString(c.get("headers")));
        gc.setQueryParams(jsonString(c.get("queryParams")));
        gc.setBody(jsonString(c.get("body")));
        gc.setAsserts(jsonString(c.get("asserts")));
        gc.setExtractVars(jsonString(c.get("extractVars")));
        return gc;
    }

    private String jsonString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject() && !node.isArray()) {
            throw new GenerationValidationException("字段类型必须是对象或数组");
        }
        return node.toString();
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String stripFences(String content) {
        if (content == null) {
            return "";
        }
        String t = content.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
            t = t.trim();
        }
        return t;
    }
}