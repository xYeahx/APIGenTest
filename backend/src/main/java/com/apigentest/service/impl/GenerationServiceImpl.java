package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.LlmCallException;
import com.apigentest.common.UserContext;
import com.apigentest.dto.GenerateRequestDTO;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.GenerationRecord;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.GenerationRecordMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.GenerationService;
import com.apigentest.service.ProjectService;
import com.apigentest.service.WebhookService;
import com.apigentest.service.llm.LlmClient;
import com.apigentest.service.llm.LlmConfigService;
import com.apigentest.service.llm.LlmPromptBuilder;
import com.apigentest.service.generation.ApiGenerationFailure;
import com.apigentest.service.generation.ApiGenerationResult;
import com.apigentest.service.generation.GeneratedCase;
import com.apigentest.service.generation.GenerationTask;
import com.apigentest.service.generation.GenerationValidationException;
import com.apigentest.service.generation.GenerationValidator;
import com.apigentest.vo.ConfirmResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.apigentest.vo.GenerationTaskVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * AI 用例生成：异步任务 + 轮询 + 校验重试 + 确认入库
 */
@Service
public class GenerationServiceImpl implements GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationServiceImpl.class);
    private static final Set<String> TERMINAL_OK = Set.of("SUCCESS", "PARTIAL_FAILED");

    private final Map<String, GenerationTask> taskStore = new ConcurrentHashMap<>();
    private final ApiInfoMapper apiInfoMapper;
    private final TestCaseMapper testCaseMapper;
    private final ProjectService projectService;
    private final LlmClient llmClient;
    private final LlmConfigService llmConfigService;
    private final LlmPromptBuilder promptBuilder;
    private final GenerationValidator validator;
    private final WebhookService webhookService;
    private final Executor generationExecutor;
    private final GenerationRecordMapper generationRecordMapper;
    private final ObjectMapper objectMapper;

    public GenerationServiceImpl(ApiInfoMapper apiInfoMapper, TestCaseMapper testCaseMapper,
                                 ProjectService projectService, LlmClient llmClient,
                                 LlmConfigService llmConfigService, LlmPromptBuilder promptBuilder,
                                 GenerationValidator validator, WebhookService webhookService,
                                 @Qualifier("generationExecutor") Executor generationExecutor,
                                 GenerationRecordMapper generationRecordMapper, ObjectMapper objectMapper) {
        this.apiInfoMapper = apiInfoMapper;
        this.testCaseMapper = testCaseMapper;
        this.projectService = projectService;
        this.llmClient = llmClient;
        this.llmConfigService = llmConfigService;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
        this.webhookService = webhookService;
        this.generationExecutor = generationExecutor;
        this.generationRecordMapper = generationRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String submit(List<Long> apiIds, String businessDesc) {
        if (apiIds == null || apiIds.isEmpty()) {
            throw new BusinessException(400, "请至少选择一个接口");
        }
        List<ApiInfo> apis = apiInfoMapper.selectBatchIds(apiIds);
        if (apis.size() != apiIds.size()) {
            throw new BusinessException(400, "部分接口不存在");
        }
        Set<Long> projects = apis.stream().map(ApiInfo::getProjectId).collect(Collectors.toSet());
        if (projects.size() != 1) {
            throw new BusinessException(400, "请选择同一项目下的接口");
        }
        Long projectId = projects.iterator().next();
        projectService.requireWrite(projectId);

        GenerationTask task = new GenerationTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStatus("PENDING");
        task.setProjectId(projectId);
        task.setBusinessDesc(businessDesc);
        task.setTotal(apis.size());
        task.setCreatedAt(LocalDateTime.now());
        taskStore.put(task.getTaskId(), task);
        generationExecutor.execute(() -> run(task, apis));
        return task.getTaskId();
    }

    private void run(GenerationTask task, List<ApiInfo> apis) {
        task.setStatus("RUNNING");
        try {
            String apiKey = llmConfigService.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new BusinessException(500, "LLM API Key 未配置，请管理员在系统配置中设置");
            }
            String model = llmConfigService.getModel();
            String baseUrl = llmConfigService.getBaseUrl();
            int maxRetry = llmConfigService.getMaxRetry();
            double temperature = llmConfigService.getTemperature();
            String promptVersion = LlmPromptBuilder.PROMPT_VERSION;
            task.setModel(model);
            task.setTemperature(temperature);
            task.setPromptVersion(promptVersion);
            task.setMaxRetry(maxRetry);
            for (ApiInfo api : apis) {
                try {
                    ApiGenerationResult result = generateForApi(api, task.getBusinessDesc(), apiKey, model, baseUrl, maxRetry, temperature);
                    result.setPromptVersion(promptVersion);
                    task.getResults().add(result);
                    task.setSuccess(task.getSuccess() + 1);
                    saveRecord(task, result, null, model, temperature, promptVersion, maxRetry);
                } catch (Exception e) {
                    log.warn("接口 {} 生成失败: {}", api.getId(), e.getMessage());
                    ApiGenerationFailure f = new ApiGenerationFailure();
                    f.setApiId(api.getId());
                    f.setError(e.getMessage());
                    f.setAttempts(maxRetry + 1);
                    task.getFailures().add(f);
                    task.setFailed(task.getFailed() + 1);
                    saveRecord(task, null, f, model, temperature, promptVersion, maxRetry);
                } finally {
                    task.setDone(task.getDone() + 1);
                }
            }
            if (task.getFailed() == task.getTotal()) {
                task.setStatus("FAILED");
            } else if (task.getFailed() > 0) {
                task.setStatus("PARTIAL_FAILED");
            } else {
                task.setStatus("SUCCESS");
            }
            try {
                webhookService.sendGenerationFinished(task.getProjectId(), task.getStatus(),
                        task.getSuccess(), task.getFailed(), task.getTotal());
            } catch (Exception ex) {
                log.warn("生成完成 Webhook 通知失败 taskId={}", task.getTaskId(), ex);
            }
        } catch (Exception e) {
            log.error("生成任务异常", e);
            task.setError(e.getMessage());
            task.setStatus("FAILED");
        }
    }

    private ApiGenerationResult generateForApi(ApiInfo api, String businessDesc,
                                               String apiKey, String model, String baseUrl, int maxRetry,
                                               double temperature) {
        if (api.getSpec() == null || api.getSpec().isBlank()) {
            throw new BusinessException(400, "接口缺少 OpenAPI 定义，请重新导入文档");
        }
        String userContent = promptBuilder.buildUserContent(api.getId(), api.getSummary(), businessDesc, api.getSpec());
        Exception last = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                String content = llmClient.chat(LlmPromptBuilder.SYSTEM_PROMPT, userContent, apiKey, baseUrl, model, temperature);
                List<GeneratedCase> cases = validator.parseAndValidate(content, api.getId());
                ApiGenerationResult result = new ApiGenerationResult();
                result.setApiId(api.getId());
                result.setCases(cases);
                result.setModel(model);
                result.setTemperature(temperature);
                result.setAttempts(attempt + 1);
                return result;
            } catch (GenerationValidationException e) {
                last = e;
                userContent += "\n\n上次输出校验未通过：" + e.getMessage() + "。请修正后重新只输出 JSON。";
            } catch (LlmCallException e) {
                last = e;
            }
        }
        throw new BusinessException(500, "接口生成失败（已重试 " + maxRetry + " 次）："
                + (last == null ? "未知错误" : last.getMessage()));
    }

    @Override
    public GenerationTaskVO get(String taskId) {
        GenerationTask task = getTask(taskId);
        projectService.requireRead(task.getProjectId());
        GenerationTaskVO vo = new GenerationTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setStatus(task.getStatus());
        vo.setProjectId(task.getProjectId());
        vo.setBusinessDesc(task.getBusinessDesc());
        vo.setTotal(task.getTotal());
        vo.setDone(task.getDone());
        vo.setSuccess(task.getSuccess());
        vo.setFailed(task.getFailed());
        vo.setError(task.getError());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setResults(task.getResults());
        vo.setFailures(task.getFailures());
        return vo;
    }

    @Override
    public ConfirmResultVO confirm(String taskId) {
        GenerationTask task = getTask(taskId);
        projectService.requireWrite(task.getProjectId());
        if ("CONFIRMED".equals(task.getStatus())) {
            throw new BusinessException(400, "该任务已确认入库，请勿重复操作");
        }
        if (!TERMINAL_OK.contains(task.getStatus())) {
            throw new BusinessException(400, "任务尚未生成完成，无法确认");
        }
        int saved = 0;
        for (ApiGenerationResult result : task.getResults()) {
            for (GeneratedCase gc : result.getCases()) {
                TestCase tc = new TestCase();
                tc.setProjectId(task.getProjectId());
                tc.setApiId(result.getApiId());
                tc.setName(gc.getName());
                tc.setScenarioType(gc.getScenarioType());
                tc.setMethod(gc.getMethod());
                tc.setUrlTemplate(gc.getUrlTemplate());
                tc.setHeaders(gc.getHeaders());
                tc.setQueryParams(gc.getQueryParams());
                tc.setBody(gc.getBody());
                tc.setAsserts(gc.getAsserts());
                tc.setExtractVars(gc.getExtractVars());
                tc.setStatus(1);
                tc.setSource(2);
                tc.setGenTaskId(task.getTaskId());
                tc.setGenModel(result.getModel());
                tc.setGenTemperature(BigDecimal.valueOf(result.getTemperature()));
                tc.setGenPromptVersion(result.getPromptVersion());
                tc.setGenRetryCount(Math.max(0, result.getAttempts() - 1));
                tc.setCreatorId(UserContext.getUserId());
                testCaseMapper.insert(tc);
                saved++;
            }
        }
        task.setStatus("CONFIRMED");
        updateRecordsOnConfirm(task);
        ConfirmResultVO vo = new ConfirmResultVO();
        vo.setProjectId(task.getProjectId());
        vo.setSaved(saved);
        return vo;
    }

    private void saveRecord(GenerationTask task, ApiGenerationResult result, ApiGenerationFailure failure,
                           String model, double temperature, String promptVersion, int maxRetry) {
        GenerationRecord record = new GenerationRecord();
        record.setTaskId(task.getTaskId());
        record.setProjectId(task.getProjectId());
        record.setApiId(result != null ? result.getApiId() : failure.getApiId());
        record.setModel(model);
        record.setTemperature(BigDecimal.valueOf(temperature));
        record.setPromptVersion(promptVersion);
        record.setMaxRetry(maxRetry);
        record.setRetryUsed((result != null ? result.getAttempts() : failure.getAttempts()) - 1);
        record.setBusinessDesc(truncate(task.getBusinessDesc(), 500));
        record.setCreatedBy(UserContext.getUserId());
        record.setConfirmedCount(0);
        if (result != null) {
            record.setStatus("SUCCESS");
            record.setGeneratedCount(result.getCases().size());
            record.setScenarioGenerated(scenarioJson(result.getCases()));
        } else {
            record.setStatus("FAILED");
            record.setGeneratedCount(0);
            record.setScenarioGenerated("{}");
            record.setError(truncate(failure.getError(), 500));
        }
        generationRecordMapper.insert(record);
    }

    private void updateRecordsOnConfirm(GenerationTask task) {
        for (ApiGenerationResult result : task.getResults()) {
            GenerationRecord record = generationRecordMapper.selectOne(new LambdaQueryWrapper<GenerationRecord>()
                    .eq(GenerationRecord::getTaskId, task.getTaskId())
                    .eq(GenerationRecord::getApiId, result.getApiId()));
            if (record != null) {
                record.setConfirmedCount(result.getCases().size());
                record.setScenarioConfirmed(scenarioJson(result.getCases()));
                record.setConfirmedAt(LocalDateTime.now());
                generationRecordMapper.updateById(record);
            }
        }
    }

    private String scenarioJson(List<GeneratedCase> cases) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (GeneratedCase c : cases) {
            String sc = c.getScenarioType() == null ? "unknown" : c.getScenarioType();
            counts.merge(sc, 1, Integer::sum);
        }
        try {
            return objectMapper.writeValueAsString(counts);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private GenerationTask getTask(String taskId) {
        GenerationTask task = taskStore.get(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在或已过期");
        }
        return task;
    }
}
