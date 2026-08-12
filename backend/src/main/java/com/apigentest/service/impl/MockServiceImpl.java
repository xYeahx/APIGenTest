package com.apigentest.service.impl;

import com.apigentest.entity.ApiInfo;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.service.MockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mock 服务实现：读项目 api_info.spec（整份 OpenAPI JSON），
 * 按 paths[path][method].responses.200 的 schema 生成智能 mock 数据。
 * 返回统一信封 {code, message, data}，与平台既有 mock-target 行为一致。
 */
@Service
public class MockServiceImpl implements MockService {

    private static final Logger log = LoggerFactory.getLogger(MockServiceImpl.class);
    private static final int MAX_DEPTH = 6;

    private final ApiInfoMapper apiInfoMapper;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public MockServiceImpl(ApiInfoMapper apiInfoMapper, ObjectMapper objectMapper) {
        this.apiInfoMapper = apiInfoMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<String> handle(Long projectId, String method, String path, Map<String, String[]> params) {
        if (params == null) {
            params = Map.of();
        }
        List<ApiInfo> apis = apiInfoMapper.selectList(
                new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, projectId));
        if (apis.isEmpty()) {
            return json(404, envelope(404, "项目未导入接口文档，无法提供 Mock", null));
        }
        ApiInfo api = matchApi(apis, method, path);
        if (api == null) {
            return json(404, envelope(404, "Mock 未找到匹配接口：" + method + " " + path, null));
        }
        // 按查询参数返回不同结果
        if (params.containsKey("mock_error")) {
            int code = parseIntFirst(params.get("mock_error"), 500);
            return json(code, envelope(code, "Mock 模拟错误（mock_error=" + code + "）", null));
        }
        if (params.containsKey("mock_delay")) {
            int ms = parseIntFirst(params.get("mock_delay"), 0);
            if (ms > 0) {
                try {
                    Thread.sleep(Math.min(ms, 10000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (params.containsKey("mock_data")) {
            String raw = params.get("mock_data")[0];
            try {
                return json(200, objectMapper.readTree(raw));
            } catch (Exception ignored) {
                // 非法 JSON 忽略，走 schema 生成
            }
        }
        if (params.containsKey("mock_empty")) {
            return json(200, envelope(0, "ok", objectMapper.createArrayNode()));
        }
        JsonNode data = generateForOperation(api, method, path);
        return json(200, envelope(0, "ok", data));
    }
    // ---------- 匹配 ----------

    private ApiInfo matchApi(List<ApiInfo> apis, String method, String path) {
        String m = method == null ? "" : method.toUpperCase();
        for (ApiInfo api : apis) {
            if (api.getMethod() == null || !api.getMethod().equalsIgnoreCase(m)) {
                continue;
            }
            if (pathMatches(api.getPath(), path)) {
                return api;
            }
        }
        return null;
    }

    /** OpenAPI 路径模板（含 {param}）与真实请求路径匹配 */
    private boolean pathMatches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        String[] p = pattern.split("/");
        String[] t = path.split("/");
        if (p.length != t.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            String seg = p[i];
            if (seg.startsWith("{") && seg.endsWith("}")) {
                continue;
            }
            if (!seg.equals(t[i])) {
                return false;
            }
        }
        return true;
    }

    // ---------- schema 生成 ----------

    private JsonNode generateForOperation(ApiInfo api, String method, String path) {
        try {
            JsonNode spec = objectMapper.readTree(api.getSpec());
            JsonNode op = spec.path("paths").path(api.getPath()).path(method.toLowerCase());
            JsonNode schema = findSuccessSchema(op.path("responses"), spec);
            if (schema == null) {
                // 无响应 schema：按路径猜测列表 / 对象
                String p = path == null ? "" : path;
                if (p.endsWith("/list") || p.contains("/list")) {
                    return objectMapper.createArrayNode();
                }
                ObjectNode n = objectMapper.createObjectNode();
                n.put("id", 1);
                n.put("message", "mock default");
                return n;
            }
            return generateMock(schema, spec, new HashSet<>(), 0);
        } catch (Exception e) {
            log.warn("Mock schema 生成失败 path={} err={}", path, e.getMessage());
            ObjectNode n = objectMapper.createObjectNode();
            n.put("id", 1);
            n.put("message", "mock default");
            return n;
        }
    }

    private JsonNode findSuccessSchema(JsonNode responses, JsonNode spec) {
        if (responses == null || !responses.isObject()) {
            return null;
        }
        JsonNode ok = responses.get("200");
        if (ok == null) {
            Iterator<Map.Entry<String, JsonNode>> it = responses.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().startsWith("2")) {
                    ok = e.getValue();
                    break;
                }
            }
        }
        if (ok == null) {
            return null;
        }
        JsonNode content = ok.path("content").path("application/json");
        if (content.isMissingNode() || content.isNull()) {
            // 兼容 Swagger 2.0：responses.200.schema
            JsonNode s2 = ok.get("schema");
            return s2 == null ? null : resolveRef(s2, spec);
        }
        return content.get("schema");
    }

    private JsonNode generateMock(JsonNode schema, JsonNode spec, Set<String> refStack, int depth) {
        if (schema == null || schema.isNull() || depth > MAX_DEPTH) {
            return objectMapper.nullNode();
        }
        if (schema.has("example")) {
            return schema.get("example").deepCopy();
        }
        if (schema.has("default")) {
            return schema.get("default").deepCopy();
        }
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            if (refStack.contains(ref)) {
                return objectMapper.createObjectNode();
            }
            JsonNode target = resolveRef(schema, spec);
            if (target == null || target.isMissingNode()) {
                return objectMapper.nullNode();
            }
            refStack.add(ref);
            JsonNode value = generateMock(target, spec, refStack, depth + 1);
            refStack.remove(ref);
            return value;
        }
        if (schema.has("allOf") && schema.get("allOf").isArray()) {
            ObjectNode merged = objectMapper.createObjectNode();
            for (JsonNode sub : schema.get("allOf")) {
                JsonNode g = generateMock(sub, spec, refStack, depth + 1);
                if (g.isObject()) {
                    merged.setAll((ObjectNode) g);
                }
            }
            return merged;
        }
        String oneKey = schema.has("oneOf") ? "oneOf" : (schema.has("anyOf") ? "anyOf" : null);
        if (oneKey != null && schema.get(oneKey).isArray() && schema.get(oneKey).size() > 0) {
            return generateMock(schema.get(oneKey).get(0), spec, refStack, depth + 1);
        }
        if (schema.has("enum") && schema.get("enum").isArray() && schema.get("enum").size() > 0) {
            return schema.get("enum").get(0).deepCopy();
        }
        String type = schema.path("type").asText("");
        switch (type) {
            case "string":
                return TextNode.valueOf(mockString(schema));
            case "integer":
                return IntNode.valueOf(mockInt(schema));
            case "number":
                return DoubleNode.valueOf(mockDouble(schema));
            case "boolean":
                return BooleanNode.valueOf(true);
            case "array": {
                ArrayNode arr = objectMapper.createArrayNode();
                JsonNode items = schema.get("items");
                int min = schema.path("minItems").asInt(1);
                int count = Math.min(Math.max(min, 1), 5);
                for (int i = 0; i < count; i++) {
                    arr.add(generateMock(items, spec, refStack, depth + 1));
                }
                return arr;
            }
            case "object":
                return mockObject(schema, spec, refStack, depth);
            default:
                // 缺省 type 但有 properties / additionalProperties 视为对象
                if (schema.has("properties")) {
                    return mockObject(schema, spec, refStack, depth);
                }
                return objectMapper.createObjectNode();
        }
    }

    private JsonNode mockObject(JsonNode schema, JsonNode spec, Set<String> refStack, int depth) {
        ObjectNode obj = objectMapper.createObjectNode();
        JsonNode props = schema.get("properties");
        if (props != null && props.isObject()) {
            props.fields().forEachRemaining(e -> {
                JsonNode value = generateMock(e.getValue(), spec, refStack, depth + 1);
                obj.set(e.getKey(), value);
            });
        }
        JsonNode addProps = schema.get("additionalProperties");
        if (addProps != null && addProps.isObject() && !obj.isEmpty()) {
            obj.set("extra", generateMock(addProps, spec, refStack, depth + 1));
        }
        return obj;
    }

    private String mockString(JsonNode schema) {
        String format = schema.path("format").asText("");
        switch (format) {
            case "email":
                return "user" + (random.nextInt(999) + 1) + "@example.com";
            case "uuid":
                return UUID.randomUUID().toString();
            case "date-time":
                return LocalDateTime.now().withNano(0).toString() + "Z";
            case "date":
                return LocalDate.now().toString();
            case "time":
                return LocalTime.now().withNano(0).toString();
            case "uri":
            case "url":
                return "https://example.com/resource/" + (random.nextInt(999) + 1);
            case "hostname":
                return "api.example.com";
            case "ipv4":
                return "192.168.1." + (random.nextInt(200) + 1);
            case "ipv6":
                return "::1";
            case "byte":
                return "aGVsbG8=";
            case "binary":
                return "binary-data";
            case "password":
                return "123456";
            case "phone":
                return "13800138000";
            case "int32":
            case "int64":
                return String.valueOf(mockInt(schema));
            case "double":
            case "float":
                return String.valueOf(mockDouble(schema));
            default:
                return "mock_" + (random.nextInt(100000));
        }
    }

    private int mockInt(JsonNode schema) {
        JsonNode example = schema.get("example");
        if (example != null && example.isNumber()) {
            return example.asInt();
        }
        return random.nextInt(999) + 1;
    }

    private double mockDouble(JsonNode schema) {
        JsonNode example = schema.get("example");
        if (example != null && example.isNumber()) {
            return example.asDouble();
        }
        return Math.round(random.nextDouble() * 99900) / 100.0;
    }

    /** 解析 $ref（#/components/schemas/X），失败返回原节点 */
    private JsonNode resolveRef(JsonNode node, JsonNode spec) {
        JsonNode ref = node.get("$ref");
        if (ref == null) {
            return node;
        }
        String r = ref.asText();
        if (r.startsWith("#/")) {
            try {
                JsonNode target = spec.at(r.substring(1));
                if (!target.isMissingNode()) {
                    return target;
                }
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        return node;
    }

    // ---------- 响应组装 ----------

    private ObjectNode envelope(int code, String message, JsonNode data) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("code", code);
        o.put("message", message);
        o.set("data", data == null ? objectMapper.nullNode() : data);
        return o;
    }

    private ResponseEntity<String> json(int status, JsonNode body) {
        String text;
        try {
            text = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            text = "{}";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return new ResponseEntity<>(text, headers, HttpStatus.valueOf(status));
    }

    private int parseIntFirst(String[] arr, int def) {
        if (arr == null || arr.length == 0) {
            return def;
        }
        try {
            return Integer.parseInt(arr[0]);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}