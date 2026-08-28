package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.UserContext;
import com.apigentest.dto.CaseDTO;
import com.apigentest.dto.CaseQuery;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.Environment;
import com.apigentest.entity.Project;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.EnvironmentMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.ProjectService;
import com.apigentest.service.TestCaseService;
import com.apigentest.vo.CaseVO;
import com.apigentest.vo.ImportResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TestCaseServiceImpl implements TestCaseService {

    private static final Set<String> SCENARIO_TYPES = Set.of("normal", "boundary", "exception", "manual");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    private final TestCaseMapper testCaseMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final EnvironmentMapper environmentMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public TestCaseServiceImpl(TestCaseMapper testCaseMapper, ApiInfoMapper apiInfoMapper,
                               EnvironmentMapper environmentMapper, ProjectService projectService,
                               ObjectMapper objectMapper) {
        this.testCaseMapper = testCaseMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.environmentMapper = environmentMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<CaseVO> list(Long projectId, CaseQuery query, long page, long size) {
        projectService.requireRead(projectId);
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProjectId, projectId)
                .orderByDesc(TestCase::getId);
        if (query != null) {
            if (query.getApiId() != null) {
                wrapper.eq(TestCase::getApiId, query.getApiId());
            }
            if (query.getScenarioType() != null && !query.getScenarioType().isBlank()) {
                wrapper.eq(TestCase::getScenarioType, query.getScenarioType());
            }
            if (query.getStatus() != null) {
                wrapper.eq(TestCase::getStatus, query.getStatus());
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                wrapper.and(w -> w.like(TestCase::getName, query.getKeyword())
                        .or().like(TestCase::getUrlTemplate, query.getKeyword()));
            }
        }
        Page<TestCase> casePage = testCaseMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, ApiInfo> apiMap = loadApiMap(projectId);
        Page<CaseVO> voPage = new Page<>(casePage.getCurrent(), casePage.getSize(), casePage.getTotal());
        voPage.setRecords(casePage.getRecords().stream()
                .map(c -> toVO(c, apiMap))
                .toList());
        return voPage;
    }

    @Override
    public CaseVO getDetail(Long id) {
        TestCase tc = getOwnedCase(id, ProjectService.LEVEL_READ);
        return toVO(tc, loadApiMap(tc.getProjectId()));
    }

    @Override
    public CaseVO create(CaseDTO dto) {
        projectService.requireWrite(dto.getProjectId());
        validate(dto, dto.getProjectId());
        TestCase tc = new TestCase();
        tc.setProjectId(dto.getProjectId());
        applyDto(tc, dto);
        tc.setCreatorId(UserContext.getUserId());
        tc.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        testCaseMapper.insert(tc);
        return toVO(tc, loadApiMap(tc.getProjectId()));
    }

    @Override
    public CaseVO update(Long id, CaseDTO dto) {
        TestCase tc = getOwnedCase(id, ProjectService.LEVEL_WRITE);
        validate(dto, tc.getProjectId());
        applyDto(tc, dto);
        if (dto.getStatus() != null) {
            tc.setStatus(dto.getStatus());
        }
        testCaseMapper.updateById(tc);
        return toVO(tc, loadApiMap(tc.getProjectId()));
    }

    @Override
    public void delete(Long id) {
        getOwnedCase(id, ProjectService.LEVEL_WRITE);
        // 解除其它用例对它的前置引用（外键 fk_case_pre）
        testCaseMapper.update(null, new LambdaUpdateWrapper<TestCase>()
                .eq(TestCase::getPreCaseId, id)
                .set(TestCase::getPreCaseId, null));
        testCaseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchStatus(List<Long> ids, Integer status) {
        checkBatchPermission(ids);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        testCaseMapper.update(null, new LambdaUpdateWrapper<TestCase>()
                .in(TestCase::getId, ids)
                .set(TestCase::getStatus, status));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        List<TestCase> cases = checkBatchPermission(ids);
        if (cases.isEmpty()) {
            return;
        }
        // 先解除用例间 pre_case_id 双向引用（外键 fk_case_pre）
        testCaseMapper.update(null, new LambdaUpdateWrapper<TestCase>()
                .and(w -> w.in(TestCase::getId, ids).or().in(TestCase::getPreCaseId, ids))
                .set(TestCase::getPreCaseId, null));
        testCaseMapper.deleteBatchIds(ids);
    }
    // ---------- 导入导出 ----------

    @Override
    public ExportFile exportCases(Long projectId, String format, List<Long> caseIds) {
        projectService.requireRead(projectId);
        List<TestCase> cases = loadExportCases(projectId, caseIds);
        String fmt = format == null || format.isBlank() ? "json" : format.trim().toLowerCase();
        try {
            switch (fmt) {
                case "postman":
                    return postmanExport(projectId, cases);
                case "openapi":
                    return openApiExport(projectId, cases);
                case "json":
                default:
                    return jsonExport(projectId, cases);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.IO_ERROR, "导出失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importCases(Long projectId, MultipartFile file) {
        projectService.requireWrite(projectId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IO_ERROR, "文件读取失败");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不是合法的 JSON 文件");
        }
        List<TestCase> toInsert = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode n : root) {
                toInsert.add(fromPlatformJson(projectId, n));
            }
        } else if (root.has("cases") && root.get("cases").isArray()) {
            for (JsonNode n : root.get("cases")) {
                toInsert.add(fromPlatformJson(projectId, n));
            }
        } else if (root.has("item") && root.get("item").isArray()) {
            collectPostmanItems(projectId, root.get("item"), "", toInsert);
        } else {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无法识别文件格式（支持 Postman Collection 或平台导出的 JSON）");
        }
        if (toInsert.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件中未解析到任何用例");
        }
        Map<String, ApiInfo> pathIndex = buildPathIndex(projectId);
        int saved = 0;
        for (TestCase tc : toInsert) {
            String path = extractPath(tc.getUrlTemplate());
            if (path != null) {
                ApiInfo api = pathIndex.get(path);
                if (api != null) {
                    tc.setApiId(api.getId());
                }
            }
            tc.setCreatorId(UserContext.getUserId());
            testCaseMapper.insert(tc);
            saved++;
        }
        ImportResultVO vo = new ImportResultVO();
        vo.setProjectId(projectId);
        vo.setTotal(saved);
        return vo;
    }

    @Override
    public String exportPytest(Long projectId, Long environmentId, List<Long> caseIds) {
        projectService.requireRead(projectId);
        List<TestCase> cases = topoSortCases(loadExportCases(projectId, caseIds));
        String baseUrl = "";
        Map<String, String> envVars = new LinkedHashMap<>();
        if (environmentId != null) {
            Environment env = environmentMapper.selectById(environmentId);
            if (env != null && env.getProjectId().equals(projectId)) {
                if (env.getBaseUrl() != null) {
                    baseUrl = env.getBaseUrl();
                }
                JsonNode vars = parseJson(env.getVariables());
                if (vars != null && vars.isObject()) {
                    vars.fields().forEachRemaining(e -> {
                        if (e.getValue().isValueNode()) {
                            envVars.put(e.getKey(), e.getValue().asText());
                        }
                    });
                }
            }
        }
        StringBuilder sb = new StringBuilder(PYTEST_HEADER);
        sb.append("BASE_URL = ").append(pyStr(baseUrl)).append("\n");
        sb.append("ENV_VARS = ").append(envVars.isEmpty() ? "{}" : pyLiteral(objectMapper.valueToTree(envVars))).append("\n");
        sb.append("\n# ---------- 用例 ----------\n");
        Set<String> used = new HashSet<>();
        for (TestCase tc : cases) {
            sb.append(buildPytestFunction(tc, used)).append("\n");
        }
        return sb.toString();
    }

    // ---------- 导出实现 ----------

    private List<TestCase> loadExportCases(Long projectId, List<Long> caseIds) {
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProjectId, projectId);
        if (caseIds != null && !caseIds.isEmpty()) {
            wrapper.in(TestCase::getId, caseIds);
        } else {
            wrapper.eq(TestCase::getStatus, 1);
        }
        wrapper.orderByAsc(TestCase::getId);
        return testCaseMapper.selectList(wrapper);
    }

    private ExportFile jsonExport(Long projectId, List<TestCase> cases) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("projectId", projectId);
        root.put("format", "apigentest-cases");
        root.put("exportedAt", LocalDateTime.now().toString());
        ArrayNode arr = root.putArray("cases");
        for (TestCase tc : cases) {
            arr.add(caseToJson(tc));
        }
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        return new ExportFile("cases-" + projectId + ".json", bytes);
    }

    private ObjectNode caseToJson(TestCase tc) {
        ObjectNode o = objectMapper.createObjectNode();
        o.put("name", tc.getName());
        o.put("scenarioType", tc.getScenarioType());
        o.put("method", tc.getMethod());
        o.put("urlTemplate", tc.getUrlTemplate());
        o.put("headers", tc.getHeaders());
        o.put("queryParams", tc.getQueryParams());
        o.put("body", tc.getBody());
        o.put("asserts", tc.getAsserts());
        o.put("extractVars", tc.getExtractVars());
        o.put("status", tc.getStatus() == null ? 1 : tc.getStatus());
        return o;
    }

    private ExportFile postmanExport(Long projectId, List<TestCase> cases) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode info = root.putObject("info");
        Project p = projectService.requireRead(projectId);
        info.put("name", "APIGenTest-" + (p.getName() == null ? "项目" + projectId : p.getName()));
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        ArrayNode items = root.putArray("item");
        for (TestCase tc : cases) {
            items.add(caseToPostmanItem(tc));
        }
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        return new ExportFile("cases-" + projectId + ".postman_collection.json", bytes);
    }

    private ObjectNode caseToPostmanItem(TestCase tc) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", tc.getName());
        ObjectNode request = item.putObject("request");
        request.put("method", tc.getMethod());
        ObjectNode url = request.putObject("url");
        url.put("raw", tc.getUrlTemplate());
        ArrayNode headers = request.putArray("header");
        JsonNode hdrs = parseJson(tc.getHeaders());
        if (hdrs != null && hdrs.isObject()) {
            hdrs.fields().forEachRemaining(e -> {
                ObjectNode h = headers.addObject();
                h.put("key", e.getKey());
                h.put("value", e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString());
            });
        }
        ArrayNode query = url.putArray("query");
        JsonNode qp = parseJson(tc.getQueryParams());
        if (qp != null && qp.isObject()) {
            qp.fields().forEachRemaining(e -> {
                ObjectNode q = query.addObject();
                q.put("key", e.getKey());
                q.put("value", e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString());
            });
        }
        if (tc.getBody() != null && !tc.getBody().isBlank()) {
            ObjectNode body = request.putObject("body");
            body.put("mode", "raw");
            body.put("raw", tc.getBody());
        }
        return item;
    }

    private ExportFile openApiExport(Long projectId, List<TestCase> cases) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.0.1");
        ObjectNode info = root.putObject("info");
        Project p = projectService.requireRead(projectId);
        info.put("title", "APIGenTest-" + (p.getName() == null ? "项目" + projectId : p.getName()));
        info.put("version", "1.0.0");
        ObjectNode paths = root.putObject("paths");
        for (TestCase tc : cases) {
            String path = extractPath(tc.getUrlTemplate());
            if (path == null || path.isBlank()) {
                continue;
            }
            String method = tc.getMethod() == null ? "get" : tc.getMethod().toLowerCase();
            ObjectNode pathNode = (ObjectNode) paths.get(path);
            if (pathNode == null) {
                pathNode = paths.putObject(path);
            }
            ObjectNode op = pathNode.putObject(method);
            op.put("summary", tc.getName());
            op.put("operationId", "case_" + tc.getId());
            ObjectNode responses = op.putObject("responses");
            ObjectNode resp = responses.putObject("200");
            resp.put("description", "ok");
        }
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        return new ExportFile("cases-" + projectId + ".openapi.json", bytes);
    }

    /** 从 urlTemplate 提取纯路径（去掉协议/域名/查询串），用于 OpenAPI 导出与导入匹配 */
    private String extractPath(String urlTemplate) {
        if (urlTemplate == null || urlTemplate.isBlank()) {
            return null;
        }
        String t = urlTemplate.trim();
        int q = t.indexOf('?');
        if (q >= 0) {
            t = t.substring(0, q);
        }
        if (t.startsWith("http://") || t.startsWith("https://")) {
            try {
                return new URI(t).getPath();
            } catch (Exception e) {
                return null;
            }
        }
        int idx = t.indexOf("}}");
        if (idx >= 0) {
            t = t.substring(idx + 2);
        }
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        return t;
    }

    /** 平台 JSON 导出格式 -> 用例 */
    private TestCase fromPlatformJson(Long projectId, JsonNode n) {
        TestCase tc = new TestCase();
        tc.setProjectId(projectId);
        tc.setName(text(n.get("name"), "未命名用例"));
        tc.setScenarioType(text(n.get("scenarioType"), "manual"));
        String method = text(n.get("method"), "GET").toUpperCase();
        if (!METHODS.contains(method)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的请求方法：" + method);
        }
        tc.setMethod(method);
        tc.setUrlTemplate(text(n.get("urlTemplate"), ""));
        tc.setHeaders(stringOrNull(n.get("headers")));
        tc.setQueryParams(stringOrNull(n.get("queryParams")));
        tc.setBody(stringOrNull(n.get("body")));
        tc.setAsserts(stringOrNull(n.get("asserts")));
        tc.setExtractVars(stringOrNull(n.get("extractVars")));
        tc.setStatus(n.hasNonNull("status") ? n.get("status").asInt() : 1);
        return tc;
    }

    /** 递归收集 Postman Collection 的请求为用例 */
    private void collectPostmanItems(Long projectId, JsonNode items, String folderPrefix, List<TestCase> out) {
        for (JsonNode item : items) {
            if (item.has("item") && item.get("item").isArray()) {
                String name = item.path("name").asText("");
                String prefix = folderPrefix.isBlank() ? name : folderPrefix + "/" + name;
                collectPostmanItems(projectId, item.get("item"), prefix, out);
                continue;
            }
            JsonNode request = item.get("request");
            if (request == null || !request.isObject()) {
                continue;
            }
            TestCase tc = new TestCase();
            tc.setProjectId(projectId);
            String itemName = item.path("name").asText("未命名用例");
            tc.setName(folderPrefix.isBlank() ? itemName : folderPrefix + " / " + itemName);
            tc.setScenarioType("manual");
            String method = request.path("method").asText("GET").toUpperCase();
            tc.setMethod(METHODS.contains(method) ? method : "GET");
            JsonNode url = request.get("url");
            String raw = url != null ? url.path("raw").asText("") : "";
            if (raw.isBlank() && url != null) {
                StringBuilder sb = new StringBuilder();
                JsonNode host = url.get("host");
                if (host != null && host.isArray()) {
                    for (JsonNode h : host) {
                        sb.append(h.asText()).append(".");
                    }
                    if (sb.length() > 0) {
                        sb.setLength(sb.length() - 1);
                    }
                }
                JsonNode pathArr = url.get("path");
                if (pathArr != null && pathArr.isArray()) {
                    for (JsonNode p : pathArr) {
                        sb.append("/").append(p.asText());
                    }
                }
                raw = sb.toString();
            }
            tc.setUrlTemplate(raw);
            ObjectNode headers = objectMapper.createObjectNode();
            JsonNode hdrArr = request.get("header");
            if (hdrArr != null && hdrArr.isArray()) {
                for (JsonNode h : hdrArr) {
                    String key = h.path("key").asText("");
                    boolean disabled = h.path("disabled").asBoolean(false);
                    if (!key.isBlank() && !disabled) {
                        headers.put(key, h.path("value").asText(""));
                    }
                }
            }
            tc.setHeaders(headers.isEmpty() ? null : headers.toString());
            ObjectNode qp = objectMapper.createObjectNode();
            JsonNode qArr = url != null ? url.get("query") : null;
            if (qArr != null && qArr.isArray()) {
                for (JsonNode q : qArr) {
                    String key = q.path("key").asText("");
                    boolean disabled = q.path("disabled").asBoolean(false);
                    if (!key.isBlank() && !disabled) {
                        qp.put(key, q.path("value").asText(""));
                    }
                }
            }
            tc.setQueryParams(qp.isEmpty() ? null : qp.toString());
            JsonNode body = request.get("body");
            if (body != null && body.isObject()) {
                String rawBody = body.path("raw").asText("");
                if (!rawBody.isBlank()) {
                    tc.setBody(rawBody);
                }
            }
            tc.setAsserts("[]");
            tc.setExtractVars("[]");
            tc.setStatus(1);
            out.add(tc);
        }
    }

    private Map<String, ApiInfo> buildPathIndex(Long projectId) {
        return apiInfoMapper.selectList(new LambdaQueryWrapper<ApiInfo>()
                        .eq(ApiInfo::getProjectId, projectId))
                .stream()
                .collect(Collectors.toMap(ApiInfo::getPath, Function.identity(), (a, b) -> a));
    }
    // ---------- pytest 生成 ----------

    private static final String PYTEST_HEADER = """
# -*- coding: utf-8 -*-
\"\"\"APIGenTest 导出的 pytest + requests 测试脚本

运行方式:
    pip install pytest requests
    pytest test_cases.py -v
\"\"\"
import json
import re
import requests

BASE_URL = ''
ENV_VARS = {}
VARS = {}


def _sub(text):
    if not isinstance(text, str):
        return text

    def repl(m):
        expr = m.group(1).strip()
        if expr.startswith('env:'):
            return str(ENV_VARS.get(expr[4:], m.group(0)))
        return str(VARS.get(expr, m.group(0)))

    return re.sub(r'\\{\\{([^{}]+)\\}\\}', repl, text)


def _read_path(data, path):
    if not path:
        return None
    cur = data
    p = path.strip()
    if p.startswith('$'):
        p = p[1:]
    parts = re.findall(r'([^.\\[\\]]+)|\\[(\\d+)\\]', p)
    for name, idx in parts:
        key = name if name else int(idx)
        try:
            cur = cur[key]
        except Exception:
            return None
    return cur


def _check_asserts(asserts, status_code, body):
    errors = []
    for a in asserts or []:
        t = a.get('type')
        if t == 'statusCode':
            expect = a.get('expect')
            if status_code != int(expect):
                errors.append('状态码断言失败：期望 %s，实际 %s' % (expect, status_code))
        elif t == 'field':
            try:
                data = json.loads(body)
            except Exception:
                data = body
            actual = _read_path(data, a.get('path'))
            cond = a.get('condition', 'notEmpty')
            if cond == 'notEmpty' and (actual is None or actual == ''):
                errors.append('字段断言失败：%s 不存在或为空' % a.get('path'))
            elif cond == 'equal' and str(actual) != str(a.get('expect')):
                errors.append('字段断言失败：%s 期望 %s，实际 %s' % (a.get('path'), a.get('expect'), actual))
            elif cond == 'contains' and (actual is None or str(a.get('expect')) not in str(actual)):
                errors.append('字段断言失败：%s 不包含 %s' % (a.get('path'), a.get('expect')))
        else:
            errors.append('不支持的断言类型：%s' % t)
    return errors


def _extract_vars(extract, headers, body):
    for e in extract or []:
        var_name = e.get('varName')
        expr = e.get('expr')
        if not var_name or not expr:
            continue
        try:
            if e.get('from') == 'header':
                value = headers.get(expr)
            else:
                data = json.loads(body)
                value = _read_path(data, expr)
            if value is not None:
                VARS[var_name] = value
        except Exception:
            pass


""";

    private String buildPytestFunction(TestCase tc, Set<String> used) {
        String slug = slugify(tc.getName());
        if (slug.isBlank()) {
            slug = "case_" + tc.getId();
        }
        String base = slug;
        int i = 2;
        while (!used.add(slug)) {
            slug = base + "_" + i;
            i++;
        }
        String method = tc.getMethod() == null ? "GET" : tc.getMethod().toUpperCase();
        StringBuilder sb = new StringBuilder();
        sb.append("def test_").append(slug).append("():\n");
        sb.append("    url = _sub(").append(pyStr(tc.getUrlTemplate())).append(")\n");
        sb.append("    if not url.startswith('http'):\n");
        sb.append("        url = BASE_URL.rstrip('/') + ('/' + url.lstrip('/') if url else '')\n");
        sb.append("    headers = ").append(pyDictExpr(tc.getHeaders())).append("\n");
        sb.append("    params = ").append(pyDictExpr(tc.getQueryParams())).append("\n");
        String bodyExpr = (tc.getBody() == null || tc.getBody().isBlank())
                ? "None" : "_sub('''" + escapeTriple(tc.getBody()) + "''')";
        sb.append("    body = ").append(bodyExpr).append("\n");
        if ("GET".equals(method) || "HEAD".equals(method)) {
            sb.append("    resp = requests.request('").append(method)
                    .append("', url, headers=headers, params=params, timeout=30)\n");
        } else {
            sb.append("    data = json.loads(body) if body else None\n");
            sb.append("    resp = requests.request('").append(method)
                    .append("', url, headers=headers, params=params, json=data, timeout=30)\n");
        }
        sb.append("    asserts = ").append(pyListExpr(tc.getAsserts())).append("\n");
        sb.append("    errors = _check_asserts(asserts, resp.status_code, resp.text)\n");
        sb.append("    _extract_vars(").append(pyListExpr(tc.getExtractVars())).append(", resp.headers, resp.text)\n");
        sb.append("    assert not errors, '; '.join(errors)\n");
        return sb.toString();
    }

    /** 按 preCaseId 拓扑排序，保证提取变量在前置用例后可用 */
    private List<TestCase> topoSortCases(List<TestCase> cases) {
        Map<Long, TestCase> byId = cases.stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));
        List<TestCase> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> visiting = new HashSet<>();
        for (TestCase tc : cases) {
            sortDfs(tc, byId, visited, visiting, result);
        }
        return result;
    }

    private void sortDfs(TestCase tc, Map<Long, TestCase> byId, Set<Long> visited,
                         Set<Long> visiting, List<TestCase> result) {
        if (visited.contains(tc.getId())) {
            return;
        }
        if (visiting.contains(tc.getId())) {
            return; // 有环时跳过，避免死循环
        }
        visiting.add(tc.getId());
        if (tc.getPreCaseId() != null) {
            TestCase pre = byId.get(tc.getPreCaseId());
            if (pre != null) {
                sortDfs(pre, byId, visited, visiting, result);
            }
        }
        visiting.remove(tc.getId());
        visited.add(tc.getId());
        result.add(tc);
    }
    private String slugify(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Python 字符串字面量 */
    private String pyStr(String s) {
        if (s == null) {
            return "''";
        }
        String escaped = s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "'" + escaped + "'";
    }

    /** JSON 值转 Python 字面量 */
    private String pyLiteral(JsonNode node) {
        if (node == null || node.isNull()) {
            return "None";
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "True" : "False";
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isTextual()) {
            return pyStr(node.asText());
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (JsonNode n : node) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(pyLiteral(n));
            }
            return sb.append("]").toString();
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(pyStr(e.getKey())).append(": ").append(pyLiteral(e.getValue()));
            }
            return sb.append("}").toString();
        }
        return "None";
    }

    /** 请求头/查询参数 JSON -> Python dict 字面量（值包 _sub 支持变量替换） */
    private String pyDictExpr(String json) {
        JsonNode node = parseJson(json);
        if (node == null || !node.isObject()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(pyStr(e.getKey())).append(": _sub(").append(pyLiteral(e.getValue())).append(")");
        }
        return sb.append("}").toString();
    }

    /** 断言/提取变量 JSON 数组 -> Python list 字面量 */
    private String pyListExpr(String json) {
        JsonNode node = parseJson(json);
        if (node == null || !node.isArray()) {
            return "[]";
        }
        return pyLiteral(node);
    }

    /** 嵌入 Python '''...''' 内的字符串：转义反斜杠与三引号，压缩换行 */
    private String escapeTriple(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("'''", "\\'\\'\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    // ---------- 私有方法 ----------

    private List<TestCase> checkBatchPermission(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<TestCase> cases = testCaseMapper.selectBatchIds(ids);
        Map<Long, List<TestCase>> byProject = cases.stream()
                .collect(Collectors.groupingBy(TestCase::getProjectId));
        byProject.keySet().forEach(projectService::requireWrite);
        return cases;
    }

    private TestCase getOwnedCase(Long id, int level) {
        TestCase tc = testCaseMapper.selectById(id);
        if (tc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用例不存在");
        }
        projectService.requireAccess(tc.getProjectId(), level);
        return tc;
    }

    private void validate(CaseDTO dto, Long projectId) {
        if (!SCENARIO_TYPES.contains(dto.getScenarioType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "场景类型仅支持 normal / boundary / exception / manual");
        }
        if (!METHODS.contains(dto.getMethod().toUpperCase())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的请求方法：" + dto.getMethod());
        }
        validateJson("请求头 headers", dto.getHeaders());
        validateJson("查询参数 queryParams", dto.getQueryParams());
        validateJson("请求体 body", dto.getBody());
        validateJson("断言 asserts", dto.getAsserts());
        validateJson("提取变量 extractVars", dto.getExtractVars());
        if (dto.getApiId() != null) {
            ApiInfo api = apiInfoMapper.selectById(dto.getApiId());
            if (api == null || !api.getProjectId().equals(projectId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "关联接口不存在或不属于该项目");
            }
        }
        if (dto.getPreCaseId() != null) {
            TestCase pre = testCaseMapper.selectById(dto.getPreCaseId());
            if (pre == null || !pre.getProjectId().equals(projectId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "前置用例不存在或不属于该项目");
            }
        }
    }

    private void validateJson(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, fieldName + " 必须是合法的 JSON");
        }
    }

    private void applyDto(TestCase tc, CaseDTO dto) {
        tc.setApiId(dto.getApiId());
        tc.setName(dto.getName());
        tc.setScenarioType(dto.getScenarioType());
        tc.setMethod(dto.getMethod().toUpperCase());
        tc.setUrlTemplate(dto.getUrlTemplate());
        tc.setHeaders(dto.getHeaders());
        tc.setQueryParams(dto.getQueryParams());
        tc.setBody(dto.getBody());
        tc.setAsserts(dto.getAsserts());
        tc.setPreCaseId(dto.getPreCaseId());
        tc.setExtractVars(dto.getExtractVars());
    }

    private Map<Long, ApiInfo> loadApiMap(Long projectId) {
        List<ApiInfo> apis = apiInfoMapper.selectList(
                new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, projectId));
        return apis.stream().collect(Collectors.toMap(ApiInfo::getId, Function.identity(), (a, b) -> a));
    }

    private CaseVO toVO(TestCase tc, Map<Long, ApiInfo> apiMap) {
        CaseVO vo = new CaseVO();
        vo.setId(tc.getId());
        vo.setProjectId(tc.getProjectId());
        vo.setApiId(tc.getApiId());
        ApiInfo api = tc.getApiId() == null ? null : apiMap.get(tc.getApiId());
        if (api != null) {
            vo.setApiPath(api.getPath());
            vo.setApiSummary(api.getSummary());
        }
        vo.setName(tc.getName());
        vo.setScenarioType(tc.getScenarioType());
        vo.setMethod(tc.getMethod());
        vo.setUrlTemplate(tc.getUrlTemplate());
        vo.setHeaders(tc.getHeaders());
        vo.setQueryParams(tc.getQueryParams());
        vo.setBody(tc.getBody());
        vo.setAsserts(tc.getAsserts());
        vo.setPreCaseId(tc.getPreCaseId());
        vo.setExtractVars(tc.getExtractVars());
        vo.setStatus(tc.getStatus());
        vo.setCreatorId(tc.getCreatorId());
        vo.setCreatedAt(tc.getCreatedAt());
        vo.setUpdatedAt(tc.getUpdatedAt());
        return vo;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode node, String def) {
        return node == null || node.isNull() ? def : node.asText();
    }

    private String stringOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}