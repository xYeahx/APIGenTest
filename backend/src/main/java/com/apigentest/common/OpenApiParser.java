package com.apigentest.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI 解析器（轻量实现）：
 * 支持 OpenAPI 3.x 与 Swagger 2.0 的 JSON / YAML 格式，
 * 提取 method / path / summary / description / tags，并保留原始定义（统一转为 JSON）供 LLM 使用。
 */
@Component
public class OpenApiParser {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch", "head", "options");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Yaml yaml = new Yaml();

    @Data
    public static class ParsedApi {
        private String method;
        private String path;
        private String summary;
        private String description;
        private String tags;
    }

    /**
     * 解析文档内容，返回接口清单
     */
    public List<ParsedApi> parseApis(String content) {
        JsonNode root = toJsonNode(content);
        JsonNode paths = root.get("paths");
        if (paths == null || !paths.isObject()) {
            throw new IllegalArgumentException("不是有效的 OpenAPI/Swagger 文档：缺少 paths 节点");
        }
        List<ParsedApi> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();
        while (pathIter.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIter.next();
            String path = pathEntry.getKey();
            JsonNode operations = pathEntry.getValue();
            if (operations == null || !operations.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> opIter = operations.fields();
            while (opIter.hasNext()) {
                Map.Entry<String, JsonNode> opEntry = opIter.next();
                String method = opEntry.getKey().toLowerCase();
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                JsonNode op = opEntry.getValue();
                if (op == null || !op.isObject()) {
                    continue;
                }
                ParsedApi api = new ParsedApi();
                api.setMethod(method.toUpperCase());
                api.setPath(path);
                api.setSummary(text(op.get("summary")));
                api.setDescription(text(op.get("description")));
                api.setTags(joinTags(op.get("tags")));
                result.add(api);
            }
        }
        return result;
    }

    /**
     * 将原始文档统一转为 JSON 字符串（YAML 输入转 JSON），用于存储 spec 字段
     */
    public String normalizeToJson(String content) {
        JsonNode node = toJsonNode(content);
        return node.toString();
    }

    private JsonNode toJsonNode(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文档内容为空");
        }
        try {
            return objectMapper.readTree(content);
        } catch (Exception jsonEx) {
            // JSON 解析失败，尝试 YAML
            try {
                Object obj = yaml.load(content);
                if (obj == null) {
                    throw new IllegalArgumentException("文档内容为空");
                }
                return objectMapper.valueToTree(obj);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception yamlEx) {
                throw new IllegalArgumentException("无法解析文档：既不是合法的 JSON 也不是合法的 YAML");
            }
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private String joinTags(JsonNode tags) {
        if (tags == null || !tags.isArray()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        tags.forEach(t -> {
            if (!t.isNull()) {
                list.add(t.asText());
            }
        });
        return list.isEmpty() ? null : String.join(",", list);
    }
}