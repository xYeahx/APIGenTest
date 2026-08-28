package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.UserContext;
import com.apigentest.dto.DebugRunDTO;
import com.apigentest.dto.RunRequestDTO;
import com.apigentest.dto.ScopeDTO;
import com.apigentest.entity.Environment;
import com.apigentest.entity.Execution;
import com.apigentest.entity.ExecutionDetail;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.EnvironmentMapper;
import com.apigentest.mapper.ExecutionDetailMapper;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.ExecutionService;
import com.apigentest.service.NotificationService;
import com.apigentest.service.WebhookService;
import com.apigentest.service.ProjectService;
import com.apigentest.service.llm.LlmConfigService;
import com.apigentest.vo.ExecutionDetailVO;
import com.apigentest.vo.ExecutionSummaryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 执行引擎：范围解析 -> 依赖排序 -> 变量替换 -> HTTP 执行 -> 断言 -> 提取变量 -> 失败重试
 * 异步线程池执行，逐条落库，完成后更新 execution 汇总
 */
@Service
public class ExecutionServiceImpl implements ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionServiceImpl.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final int MAX_TEXT_LEN = 512 * 1024;

    private final ExecutionMapper executionMapper;
    private final ExecutionDetailMapper detailMapper;
    private final TestCaseMapper testCaseMapper;
    private final EnvironmentMapper environmentMapper;
    private final ProjectService projectService;
    private final LlmConfigService llmConfigService;
    private final NotificationService notificationService;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final Executor executionExecutor;

    public ExecutionServiceImpl(ExecutionMapper executionMapper, ExecutionDetailMapper detailMapper,
                                TestCaseMapper testCaseMapper, EnvironmentMapper environmentMapper,
                                ProjectService projectService, LlmConfigService llmConfigService,
                                NotificationService notificationService, WebhookService webhookService,
                                ObjectMapper objectMapper,
                                @Qualifier("executionExecutor") Executor executionExecutor) {
        this.executionMapper = executionMapper;
        this.detailMapper = detailMapper;
        this.testCaseMapper = testCaseMapper;
        this.environmentMapper = environmentMapper;
        this.projectService = projectService;
        this.llmConfigService = llmConfigService;
        this.notificationService = notificationService;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.executionExecutor = executionExecutor;
    }

    @Override
    public Long run(RunRequestDTO dto) {
        projectService.requireWrite(dto.getProjectId());
        Long operatorId = UserContext.getUserId();
        return doRun(dto, 1, operatorId, executionId -> notifyExecutionFinished(executionId, operatorId));
    }

    /** 系统级触发（定时任务/CI）：跳过用户权限校验，完成后回调通知 */
    @Override
    public Long runBySystem(RunRequestDTO dto, int triggerType, Long operatorId, Consumer<Long> onFinished) {
        projectService.requireProject(dto.getProjectId());
        return doRun(dto, triggerType, operatorId, onFinished);
    }

    private Long doRun(RunRequestDTO dto, int triggerType, Long operatorId, Consumer<Long> onFinished) {
        Environment env = environmentMapper.selectById(dto.getEnvironmentId());
        if (env == null || !env.getProjectId().equals(dto.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "执行环境不存在或不属于该项目");
        }
        List<TestCase> cases = resolveCases(dto.getProjectId(), dto.getScope());
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "没有可执行的启用用例，请先创建并启用用例");
        }
        List<TestCase> ordered = topoSort(cases);

        Execution execution = new Execution();
        execution.setProjectId(dto.getProjectId());
        execution.setTriggerType(triggerType);
        execution.setStatus(0);
        execution.setTotalCases(ordered.size());
        execution.setPassed(0);
        execution.setFailed(0);
        execution.setDurationMs(0L);
        execution.setStartedAt(LocalDateTime.now());
        execution.setOperatorId(operatorId);
        executionMapper.insert(execution);

        Long executionId = execution.getId();
        executionExecutor.execute(() -> execute(executionId, ordered, env, onFinished));
        return executionId;
    }

    private void execute(Long executionId, List<TestCase> cases, Environment env, Consumer<Long> onFinished) {
        Map<String, String> envVars = new HashMap<>();
        parseEnvVars(env.getVariables(), envVars);
        Map<String, String> vars = new HashMap<>();
        if (env.getBaseUrl() != null && !env.getBaseUrl().isBlank()) {
            vars.put("baseUrl", env.getBaseUrl());
        }
        RestClient restClient = buildRestClient(llmConfigService.getDefaultTimeoutMs());
        int passed = 0;
        int failed = 0;
        long start = System.currentTimeMillis();
        try {
            for (TestCase tc : cases) {
                ExecutionDetail detail = executeCase(executionId, tc, env, envVars, vars, restClient);
                detailMapper.insert(detail);
                if (detail.getStatus() == 1) {
                    passed++;
                } else {
                    failed++;
                }
                Execution partial = new Execution();
                partial.setId(executionId);
                partial.setPassed(passed);
                partial.setFailed(failed);
                executionMapper.updateById(partial);
            }
        } catch (Exception e) {
            log.error("执行任务异常 executionId={}", executionId, e);
        } finally {
            Execution finish = new Execution();
            finish.setId(executionId);
            finish.setStatus(1);
            finish.setFinishedAt(LocalDateTime.now());
            finish.setDurationMs(System.currentTimeMillis() - start);
            executionMapper.updateById(finish);
            if (onFinished != null) {
                try {
                    onFinished.accept(executionId);
                } catch (Exception ex) {
                    log.error("执行完成回调异常 executionId={}", executionId, ex);
                }
            }
            try {
                webhookService.sendExecutionResult(executionMapper.selectById(executionId));
            } catch (Exception ex) {
                log.error("Webhook 通知异常 executionId={}", executionId, ex);
            }
        }
    }

    /** 手动执行完成站内通知（与定时任务通知一致） */
    private void notifyExecutionFinished(Long executionId, Long operatorId) {
        try {
            Execution e = executionMapper.selectById(executionId);
            if (e == null || operatorId == null) {
                return;
            }
            int total = e.getTotalCases() == null ? 0 : e.getTotalCases();
            int passed = e.getPassed() == null ? 0 : e.getPassed();
            int failed = e.getFailed() == null ? 0 : e.getFailed();
            double rate = total == 0 ? 0.0 : Math.round(passed * 1000.0 / total) / 10.0;
            String title = "执行完成";
            String content = String.format("共 %d 条用例，通过 %d，失败 %d，通过率 %.1f%%", total, passed, failed, rate);
            notificationService.notify(operatorId, "execution", title, content, executionId);
        } catch (Exception ex) {
            log.error("执行完成通知发送失败 executionId={}", executionId, ex);
        }
    }

    // ---------- 执行范围解析与排序 ----------

    private List<TestCase> resolveCases(Long projectId, ScopeDTO scope) {
        String type = (scope == null || scope.getType() == null || scope.getType().isBlank())
                ? "all" : scope.getType().trim();
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProjectId, projectId)
                .eq(TestCase::getStatus, 1);
        switch (type) {
            case "caseIds": {
                List<Long> ids = scope.getCaseIds();
                if (ids == null || ids.isEmpty()) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择要执行的用例");
                }
                wrapper.in(TestCase::getId, ids);
                List<TestCase> list = testCaseMapper.selectList(wrapper);
                if (list.size() != ids.size()) {
                    throw new BusinessException(ErrorCode.ILLEGAL_STATE, "部分用例不存在或已禁用");
                }
                return list;
            }
            case "apiIds": {
                List<Long> apiIds = scope.getApiIds();
                if (apiIds == null || apiIds.isEmpty()) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择要执行的接口");
                }
                wrapper.in(TestCase::getApiId, apiIds);
                return testCaseMapper.selectList(wrapper);
            }
            case "all":
                return testCaseMapper.selectList(wrapper);
            default:
                throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的执行范围类型：" + type);
        }
    }

    /** 按 preCaseId 拓扑排序，保证前置用例先执行；存在环时报错 */
    private List<TestCase> topoSort(List<TestCase> cases) {
        Map<Long, TestCase> byId = cases.stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));
        List<TestCase> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> visiting = new HashSet<>();
        for (TestCase tc : cases) {
            dfs(tc, byId, visited, visiting, result);
        }
        return result;
    }

    private void dfs(TestCase tc, Map<Long, TestCase> byId, Set<Long> visited,
                     Set<Long> visiting, List<TestCase> result) {
        if (visited.contains(tc.getId())) {
            return;
        }
        if (visiting.contains(tc.getId())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "用例前置依赖存在循环，无法执行");
        }
        visiting.add(tc.getId());
        if (tc.getPreCaseId() != null) {
            TestCase pre = byId.get(tc.getPreCaseId());
            if (pre != null) {
                dfs(pre, byId, visited, visiting, result);
            }
        }
        visiting.remove(tc.getId());
        visited.add(tc.getId());
        result.add(tc);
    }

    // ---------- 单用例执行 ----------

    private ExecutionDetail executeCase(Long executionId, TestCase tc, Environment env,
                                        Map<String, String> envVars, Map<String, String> vars,
                                        RestClient restClient) {
        CaseRunResult result = runWithRetry(tc, env, envVars, vars, restClient);
        ExecutionDetail detail = new ExecutionDetail();
        detail.setExecutionId(executionId);
        detail.setCaseId(tc.getId());
        detail.setStatus(result.status);
        detail.setErrorMessage(result.errorMessage);
        detail.setRequestText(result.requestText);
        detail.setResponseText(result.responseText);
        detail.setRetryCount(result.retryCount);
        detail.setDurationMs(result.durationMs);
        return detail;
    }

    /**
     * 单条用例调试重放：同步执行、不落库，返回与执行明细一致的结果结构
     * 支持覆盖请求头/查询参数/请求体/断言后再跑
     */
    @Override
    public ExecutionDetailVO debugRun(Long caseId, DebugRunDTO dto) {
        TestCase tc = testCaseMapper.selectById(caseId);
        if (tc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用例不存在");
        }
        projectService.requireWrite(tc.getProjectId());
        Environment env = environmentMapper.selectById(dto.getEnvironmentId());
        if (env == null || !env.getProjectId().equals(tc.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "执行环境不存在或不属于该项目");
        }
        TestCase effective = cloneForDebug(tc, dto);

        Map<String, String> envVars = new HashMap<>();
        parseEnvVars(env.getVariables(), envVars);
        Map<String, String> vars = new HashMap<>();
        if (env.getBaseUrl() != null && !env.getBaseUrl().isBlank()) {
            vars.put("baseUrl", env.getBaseUrl());
        }
        RestClient restClient = buildRestClient(llmConfigService.getDefaultTimeoutMs());
        CaseRunResult result = runWithRetry(effective, env, envVars, vars, restClient);

        ExecutionDetailVO vo = new ExecutionDetailVO();
        vo.setCaseId(caseId);
        vo.setCaseName(tc.getName());
        vo.setMethod(effective.getMethod());
        vo.setUrlTemplate(effective.getUrlTemplate());
        vo.setStatus(result.status);
        vo.setRequestText(result.requestText);
        vo.setResponseText(result.responseText);
        vo.setErrorMessage(result.errorMessage);
        vo.setDurationMs(result.durationMs);
        vo.setRetryCount(result.retryCount);
        return vo;
    }

    /** 应用调试覆盖参数（仅覆盖非空字段） */
    private TestCase cloneForDebug(TestCase tc, DebugRunDTO dto) {
        TestCase c = new TestCase();
        c.setId(tc.getId());
        c.setProjectId(tc.getProjectId());
        c.setApiId(tc.getApiId());
        c.setName(tc.getName());
        c.setScenarioType(tc.getScenarioType());
        c.setMethod(tc.getMethod());
        c.setUrlTemplate(tc.getUrlTemplate());
        c.setHeaders(notBlank(dto.getHeaders()) ? dto.getHeaders() : tc.getHeaders());
        c.setQueryParams(notBlank(dto.getQueryParams()) ? dto.getQueryParams() : tc.getQueryParams());
        c.setBody(notBlank(dto.getBody()) ? dto.getBody() : tc.getBody());
        c.setAsserts(notBlank(dto.getAsserts()) ? dto.getAsserts() : tc.getAsserts());
        c.setPreCaseId(tc.getPreCaseId());
        c.setExtractVars(tc.getExtractVars());
        c.setStatus(tc.getStatus());
        return c;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 带重试的单用例执行（不落库），返回最终结果 */
    private CaseRunResult runWithRetry(TestCase tc, Environment env,
                                       Map<String, String> envVars, Map<String, String> vars,
                                       RestClient restClient) {
        int maxRetry = llmConfigService.getMaxRetry();
        long start = System.currentTimeMillis();
        int finalStatus = 3; // 默认异常
        String finalError = null;
        String requestText = null;
        String responseText = null;
        int retryCount = 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            AttemptResult r;
            try {
                r = attemptOnce(tc, env, envVars, vars, restClient);
            } catch (ExecutionRequestException e) {
                r = new AttemptResult(0, null, e.requestText, new HashMap<>(), e.errorMessage);
            } catch (BusinessException e) {
                // 用例本身配置问题（如环境缺 baseUrl），无需重试
                return new CaseRunResult(3, e.getMessage(), e.getMessage(), null, attempt,
                        System.currentTimeMillis() - start);
            } catch (Exception e) {
                r = new AttemptResult(0, null, null, new HashMap<>(), "请求异常：" + e.getMessage());
            }
            requestText = truncateText(r.requestText);
            responseText = truncateText(r.responseBody);
            retryCount = attempt;
            if (r.errorMessage != null) {
                finalError = r.errorMessage;
                continue;
            }
            List<String> assertErrors = checkAssertions(tc.getAsserts(), r.statusCode, r.responseBody);
            if (assertErrors.isEmpty()) {
                extractVars(tc.getExtractVars(), r.headers, r.responseBody, vars);
                finalStatus = 1;
                finalError = null;
                break;
            }
            finalError = String.join("；", assertErrors);
            finalStatus = 2;
        }
        return new CaseRunResult(finalStatus, finalError, requestText, responseText, retryCount,
                System.currentTimeMillis() - start);
    }

    /** 单次尝试：变量替换 -> 拼装请求 -> 发送 -> 返回状态码与响应体 */
    private AttemptResult attemptOnce(TestCase tc, Environment env, Map<String, String> envVars,
                                      Map<String, String> vars, RestClient restClient) {
        String urlTemplate = substitute(tc.getUrlTemplate(), envVars, vars);
        String fullUrl = urlTemplate;
        if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
            String base = env.getBaseUrl();
            if (base == null || base.isBlank()) {
                throw new BusinessException(ErrorCode.ENV_NOT_CONFIGURED, "环境未配置 baseUrl，无法执行相对地址用例");
            }
            fullUrl = base + (urlTemplate.startsWith("/") ? urlTemplate : "/" + urlTemplate);
        }
        Map<String, String> headers = parseStringMap(tc.getHeaders(), envVars, vars);
        String body = substituteJsonString(tc.getBody(), envVars, vars);

        UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(fullUrl);
        JsonNode qp = parseJson(tc.getQueryParams());
        if (qp != null && qp.isObject()) {
            qp.fields().forEachRemaining(e ->
                    ub.queryParam(e.getKey(), substitute(e.getValue().asText(), envVars, vars)));
        }
        URI uri = ub.build().encode().toUri();

        RestClient.RequestBodyUriSpec request = restClient.method(HttpMethod.valueOf(tc.getMethod().toUpperCase()));
        request.uri(uri);
        headers.forEach(request::header);
        boolean hasBody = body != null && !body.isBlank()
                && !"GET".equalsIgnoreCase(tc.getMethod()) && !"HEAD".equalsIgnoreCase(tc.getMethod());
        if (hasBody) {
            if (!headers.containsKey("Content-Type")) {
                request.header("Content-Type", "application/json;charset=UTF-8");
            }
            request.body(body);
        }

        String requestText = buildRequestText(tc.getMethod(), uri, headers, hasBody ? body : null);
        ResponseEntity<String> response;
        try {
            response = request.retrieve().toEntity(String.class);
        } catch (RestClientResponseException e) {
            response = ResponseEntity.status(e.getStatusCode().value()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new ExecutionRequestException(requestText, "请求异常：" + e.getMessage());
        }
        return new AttemptResult(response.getStatusCode().value(), response.getBody(), requestText, headers, null);
    }

    // ---------- 断言与变量 ----------

    private List<String> checkAssertions(String assertsJson, int statusCode, String responseBody) {
        List<String> errors = new ArrayList<>();
        JsonNode asserts = parseJson(assertsJson);
        if (asserts == null || !asserts.isArray()) {
            return errors;
        }
        for (JsonNode node : asserts) {
            String type = node.path("type").asText();
            if ("statusCode".equals(type)) {
                int expect = node.path("expect").asInt();
                if (statusCode != expect) {
                    errors.add("状态码断言失败：期望 " + expect + "，实际 " + statusCode);
                }
            } else if ("field".equals(type)) {
                String path = node.path("path").asText();
                String condition = node.path("condition").asText("notEmpty");
                String expectStr = node.hasNonNull("expect") ? node.get("expect").asText() : null;
                String actual = readJsonPath(responseBody, path);
                switch (condition) {
                    case "notEmpty":
                        if (actual == null || actual.isBlank()) {
                            errors.add("字段断言失败：" + path + " 不存在或为空");
                        }
                        break;
                    case "equal":
                        if (!Objects.equals(actual, expectStr)) {
                            errors.add("字段断言失败：" + path + " 期望 " + expectStr + "，实际 " + actual);
                        }
                        break;
                    case "contains":
                        if (actual == null || expectStr == null || !actual.contains(expectStr)) {
                            errors.add("字段断言失败：" + path + " 不包含 " + expectStr);
                        }
                        break;
                    default:
                        errors.add("不支持的断言条件：" + condition);
                }
            } else {
                errors.add("不支持的断言类型：" + type);
            }
        }
        return errors;
    }

    private void extractVars(String extractVarsJson, Map<String, String> headers,
                             String responseBody, Map<String, String> vars) {
        JsonNode arr = parseJson(extractVarsJson);
        if (arr == null || !arr.isArray()) {
            return;
        }
        for (JsonNode node : arr) {
            String from = node.path("from").asText("response");
            String expr = node.path("expr").asText();
            String varName = node.path("varName").asText();
            if (varName.isBlank() || expr.isBlank()) {
                continue;
            }
            try {
                String value;
                if ("header".equals(from)) {
                    value = headers.get(expr);
                } else {
                    value = readJsonPath(responseBody, expr);
                }
                if (value != null) {
                    vars.put(varName, value);
                }
            } catch (Exception e) {
                log.debug("提取变量失败 case 忽略 varName={}", varName);
            }
        }
    }

    /** 超长文本截断，防止超大响应超出数据库字段上限 */
    private String truncateText(String text) {
        if (text == null || text.length() <= MAX_TEXT_LEN) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LEN) + "\n...[响应/请求过长，已截断]";
    }

    private String readJsonPath(String body, String path) {
        if (body == null || body.isBlank() || path == null || path.isBlank()) {
            return null;
        }
        try {
            Object value = JsonPath.read(body, path);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** 替换 {{xxx}} 与 {{env:xxx}} 占位符；未解析到的占位符原样保留 */
    private String substitute(String text, Map<String, String> envVars, Map<String, String> vars) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = VAR_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            String value = expr.startsWith("env:") ? envVars.get(expr.substring(4)) : vars.get(expr);
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Map<String, String> parseStringMap(String json, Map<String, String> envVars, Map<String, String> vars) {
        Map<String, String> map = new HashMap<>();
        JsonNode node = parseJson(json);
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(e -> {
            String value = e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString();
            map.put(e.getKey(), substitute(value, envVars, vars));
        });
        return map;
    }

    private String substituteJsonString(String json, Map<String, String> envVars, Map<String, String> vars) {
        JsonNode node = parseJson(json);
        if (node == null) {
            return substitute(json, envVars, vars);
        }
        return substituteJson(node, envVars, vars).toString();
    }

    private JsonNode substituteJson(JsonNode node, Map<String, String> envVars, Map<String, String> vars) {
        if (node.isTextual()) {
            return TextNode.valueOf(substitute(node.asText(), envVars, vars));
        }
        if (node.isArray()) {
            ArrayNode arr = objectMapper.createArrayNode();
            node.forEach(item -> arr.add(substituteJson(item, envVars, vars)));
            return arr;
        }
        if (node.isObject()) {
            ObjectNode obj = objectMapper.createObjectNode();
            node.fields().forEachRemaining(e -> obj.set(e.getKey(), substituteJson(e.getValue(), envVars, vars)));
            return obj;
        }
        return node;
    }

    private void parseEnvVars(String variablesJson, Map<String, String> target) {
        JsonNode node = parseJson(variablesJson);
        if (node == null || !node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(e -> {
            if (e.getValue().isValueNode()) {
                target.put(e.getKey(), e.getValue().asText());
            }
        });
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

    private RestClient buildRestClient(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder().requestFactory(factory).build();
    }

    private String buildRequestText(String method, URI uri, Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder(method).append(' ').append(uri);
        if (headers != null && !headers.isEmpty()) {
            sb.append("\nHeaders: ").append(headers.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", ")));
        }
        if (body != null && !body.isBlank()) {
            sb.append("\nBody: ").append(body);
        }
        return sb.toString();
    }

    // ---------- 查询 ----------

    @Override
    public Page<ExecutionSummaryVO> list(Long projectId, long page, long size) {
        projectService.requireRead(projectId);
        Page<Execution> executionPage = executionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Execution>()
                        .eq(Execution::getProjectId, projectId)
                        .orderByDesc(Execution::getId));
        Page<ExecutionSummaryVO> voPage = new Page<>(executionPage.getCurrent(), executionPage.getSize(), executionPage.getTotal());
        voPage.setRecords(executionPage.getRecords().stream().map(this::toSummary).toList());
        return voPage;
    }

    @Override
    public ExecutionSummaryVO get(Long id) {
        return toSummary(getOwnedExecution(id));
    }

    @Override
    public Page<ExecutionDetailVO> details(Long id, long page, long size, Integer status) {
        getOwnedExecution(id);
        LambdaQueryWrapper<ExecutionDetail> wrapper = new LambdaQueryWrapper<ExecutionDetail>()
                .eq(ExecutionDetail::getExecutionId, id)
                .orderByAsc(ExecutionDetail::getId);
        if (status != null) {
            wrapper.eq(ExecutionDetail::getStatus, status);
        }
        Page<ExecutionDetail> detailPage = detailMapper.selectPage(new Page<>(page, size), wrapper);
        List<Long> caseIds = detailPage.getRecords().stream()
                .map(ExecutionDetail::getCaseId).distinct().toList();
        Map<Long, TestCase> caseMap = caseIds.isEmpty() ? Map.of()
                : testCaseMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));
        Page<ExecutionDetailVO> voPage = new Page<>(detailPage.getCurrent(), detailPage.getSize(), detailPage.getTotal());
        voPage.setRecords(detailPage.getRecords().stream()
                .map(d -> toDetailVO(d, caseMap)).toList());
        return voPage;
    }

    @Override
    public ExecutionDetailVO detail(Long executionId, Long detailId) {
        getOwnedExecution(executionId);
        ExecutionDetail d = detailMapper.selectById(detailId);
        if (d == null || !d.getExecutionId().equals(executionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "执行明细不存在");
        }
        TestCase tc = testCaseMapper.selectById(d.getCaseId());
        return toDetailVO(d, tc == null ? Map.of() : Map.of(tc.getId(), tc));
    }

    private Execution getOwnedExecution(Long id) {
        Execution execution = executionMapper.selectById(id);
        if (execution == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "执行记录不存在");
        }
        projectService.requireRead(execution.getProjectId());
        return execution;
    }

    private ExecutionSummaryVO toSummary(Execution e) {
        ExecutionSummaryVO vo = new ExecutionSummaryVO();
        vo.setId(e.getId());
        vo.setProjectId(e.getProjectId());
        vo.setTriggerType(e.getTriggerType());
        vo.setStatus(e.getStatus());
        vo.setTotalCases(e.getTotalCases());
        vo.setPassed(e.getPassed());
        vo.setFailed(e.getFailed());
        vo.setDurationMs(e.getDurationMs());
        vo.setStartedAt(e.getStartedAt());
        vo.setFinishedAt(e.getFinishedAt());
        vo.setOperatorId(e.getOperatorId());
        if (e.getTotalCases() != null && e.getTotalCases() > 0) {
            vo.setPassRate(Math.round(e.getPassed() * 1000.0 / e.getTotalCases()) / 10.0);
        } else {
            vo.setPassRate(0.0);
        }
        vo.setDetailCount(detailMapper.selectCount(
                new LambdaQueryWrapper<ExecutionDetail>().eq(ExecutionDetail::getExecutionId, e.getId())));
        return vo;
    }

    private ExecutionDetailVO toDetailVO(ExecutionDetail d, Map<Long, TestCase> caseMap) {
        TestCase tc = caseMap.get(d.getCaseId());
        String caseName = tc == null ? null : tc.getName();
        String method = tc == null ? null : tc.getMethod();
        String urlTemplate = tc == null ? null : tc.getUrlTemplate();
        return ExecutionDetailVO.from(d, caseName, method, urlTemplate);
    }

    // ---------- 内部结构 ----------

    private static class ExecutionRequestException extends RuntimeException {
        final String requestText;
        final String errorMessage;

        ExecutionRequestException(String requestText, String errorMessage) {
            super(errorMessage);
            this.requestText = requestText;
            this.errorMessage = errorMessage;
        }
    }

    private static class AttemptResult {
        final int statusCode;
        final String responseBody;
        final String requestText;
        final Map<String, String> headers;
        final String errorMessage;

        AttemptResult(int statusCode, String responseBody, String requestText,
                      Map<String, String> headers, String errorMessage) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.requestText = requestText;
            this.headers = headers;
            this.errorMessage = errorMessage;
        }
    }

    /** 单用例执行结果（带重试后的最终状态） */
    private static class CaseRunResult {
        final int status;
        final String errorMessage;
        final String requestText;
        final String responseText;
        final int retryCount;
        final long durationMs;

        CaseRunResult(int status, String errorMessage, String requestText, String responseText,
                      int retryCount, long durationMs) {
            this.status = status;
            this.errorMessage = errorMessage;
            this.requestText = requestText;
            this.responseText = responseText;
            this.retryCount = retryCount;
            this.durationMs = durationMs;
        }
    }
}