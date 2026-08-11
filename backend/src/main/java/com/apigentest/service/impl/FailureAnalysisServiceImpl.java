package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.LlmCallException;
import com.apigentest.entity.Execution;
import com.apigentest.entity.ExecutionDetail;
import com.apigentest.entity.FailureAnalysis;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ExecutionDetailMapper;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.FailureAnalysisMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.FailureAnalysisService;
import com.apigentest.service.ProjectService;
import com.apigentest.service.llm.LlmClient;
import com.apigentest.service.llm.LlmConfigService;
import com.apigentest.vo.FailureAnalysisVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 失败归因：基于失败用例的请求/响应/断言，调用 LLM 输出结构化归因
 * （复用生成模块的工程化模式：JSON 约束 + 校验重试 + 落库确认）
 */
@Service
public class FailureAnalysisServiceImpl implements FailureAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FailureAnalysisServiceImpl.class);
    private static final Set<String> CATEGORIES = Set.of("assert_error", "data_error", "env_error", "real_defect");
    private static final int RESPONSE_MAX_LENGTH = 3000;

    private static final String SYSTEM_PROMPT = """
            你是接口自动化测试平台的失败分析专家。根据一次失败的接口测试用例执行记录，判断失败类别并给出原因与定位建议。
            要求：
            1. category 只能是以下四种之一：
               - assert_error：断言本身设计不合理（期望值/期望状态码与被测接口真实行为不符，例如接口不校验 token 却断言 401）
               - data_error：接口返回数据异常（响应为空、字段缺失、数据格式错误或业务数据不满足条件）
               - env_error：环境问题（Base URL 错误、网络不通、请求超时、鉴权配置缺失、变量未替换）
               - real_defect：被测系统真实缺陷（接口行为与合理预期不符，且不属于上述情况）
            2. reason 用一句话概括失败原因，不超过 80 字；
            3. suggestion 给出可操作的定位建议，不超过 150 字；
            4. 必须只输出一个 JSON 对象，禁止输出 markdown 代码块、注释或任何解释文字。
            输出格式（严格）：
            {"category":"assert_error","reason":"...","suggestion":"..."}
            """;

    private final ExecutionDetailMapper detailMapper;
    private final ExecutionMapper executionMapper;
    private final TestCaseMapper testCaseMapper;
    private final FailureAnalysisMapper failureAnalysisMapper;
    private final ProjectService projectService;
    private final LlmClient llmClient;
    private final LlmConfigService llmConfigService;
    private final ObjectMapper objectMapper;

    public FailureAnalysisServiceImpl(ExecutionDetailMapper detailMapper, ExecutionMapper executionMapper,
                                      TestCaseMapper testCaseMapper, FailureAnalysisMapper failureAnalysisMapper,
                                      ProjectService projectService, LlmClient llmClient,
                                      LlmConfigService llmConfigService, ObjectMapper objectMapper) {
        this.detailMapper = detailMapper;
        this.executionMapper = executionMapper;
        this.testCaseMapper = testCaseMapper;
        this.failureAnalysisMapper = failureAnalysisMapper;
        this.projectService = projectService;
        this.llmClient = llmClient;
        this.llmConfigService = llmConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public FailureAnalysisVO analyze(Long detailId) {
        ExecutionDetail detail = requireDetail(detailId);
        if (detail.getStatus() == null || detail.getStatus() == 1) {
            throw new BusinessException(400, "仅失败或异常的用例支持归因分析");
        }
        TestCase tc = detail.getCaseId() == null ? null : testCaseMapper.selectById(detail.getCaseId());
        String apiKey = llmConfigService.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(400, "尚未配置 LLM API Key，请先在「系统设置」中完成配置");
        }
        String model = llmConfigService.getModel();
        String baseUrl = llmConfigService.getBaseUrl();
        int maxRetry = llmConfigService.getMaxRetry();
        String userContent = buildUserContent(tc, detail);

        Exception last = null;
        AnalysisResult result = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                String content = llmClient.chat(SYSTEM_PROMPT, userContent, apiKey, baseUrl, model);
                result = parseAndValidate(content);
                result.setModel(model);
                break;
            } catch (AnalysisValidationException e) {
                last = e;
                userContent += "\n\n上次输出校验未通过：" + e.getMessage() + "。请修正后重新只输出 JSON。";
            } catch (LlmCallException e) {
                last = e;
            }
        }
        if (result == null) {
            throw new BusinessException(500, "归因分析失败（已重试 " + maxRetry + " 次）："
                    + (last == null ? "未知错误" : last.getMessage()));
        }

        FailureAnalysis fa = failureAnalysisMapper.selectOne(new LambdaQueryWrapper<FailureAnalysis>()
                .eq(FailureAnalysis::getExecutionDetailId, detailId));
        boolean isNew = fa == null;
        if (isNew) {
            fa = new FailureAnalysis();
            fa.setExecutionDetailId(detailId);
        }
        fa.setCategory(result.getCategory());
        fa.setReason(result.getReason());
        fa.setSuggestion(result.getSuggestion());
        fa.setConfirmed(isNew ? 0 : fa.getConfirmed());
        fa.setLlmModel(result.getModel());
        if (isNew) {
            failureAnalysisMapper.insert(fa);
        } else {
            failureAnalysisMapper.updateById(fa);
        }
        log.info("失败归因完成 detailId={} category={} confirmed={}", detailId, result.getCategory(), fa.getConfirmed());
        return toVO(fa, tc, detail);
    }

    @Override
    public FailureAnalysisVO getByDetailId(Long detailId) {
        ExecutionDetail detail = requireDetail(detailId);
        FailureAnalysis fa = failureAnalysisMapper.selectOne(new LambdaQueryWrapper<FailureAnalysis>()
                .eq(FailureAnalysis::getExecutionDetailId, detailId));
        if (fa == null) {
            return null;
        }
        TestCase tc = detail.getCaseId() == null ? null : testCaseMapper.selectById(detail.getCaseId());
        return toVO(fa, tc, detail);
    }

    @Override
    public FailureAnalysisVO confirm(Long id) {
        FailureAnalysis fa = failureAnalysisMapper.selectById(id);
        if (fa == null) {
            throw new BusinessException(404, "归因记录不存在");
        }
        requireDetail(fa.getExecutionDetailId());
        fa.setConfirmed(1);
        failureAnalysisMapper.updateById(fa);
        ExecutionDetail detail = detailMapper.selectById(fa.getExecutionDetailId());
        TestCase tc = detail == null || detail.getCaseId() == null ? null : testCaseMapper.selectById(detail.getCaseId());
        return toVO(fa, tc, detail);
    }

    // ---------- 私有方法 ----------

    private ExecutionDetail requireDetail(Long detailId) {
        ExecutionDetail detail = detailMapper.selectById(detailId);
        if (detail == null) {
            throw new BusinessException(404, "执行明细不存在");
        }
        Execution execution = executionMapper.selectById(detail.getExecutionId());
        if (execution == null) {
            throw new BusinessException(404, "执行记录不存在");
        }
        projectService.getOwnedProject(execution.getProjectId());
        return detail;
    }

    private String buildUserContent(TestCase tc, ExecutionDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("用例名称：").append(tc == null ? "（已删除）" : tc.getName()).append("\n");
        if (tc != null) {
            sb.append("请求方法：").append(tc.getMethod()).append("\n");
            sb.append("请求地址：").append(tc.getUrlTemplate()).append("\n");
            if (tc.getAsserts() != null && !tc.getAsserts().isBlank()) {
                sb.append("断言配置：").append(tc.getAsserts()).append("\n");
            }
        }
        sb.append("\n实际发送请求：\n").append(truncate(detail.getRequestText())).append("\n");
        sb.append("\n实际响应：\n").append(truncate(detail.getResponseText())).append("\n");
        sb.append("\n断言/执行错误：\n").append(detail.getErrorMessage() == null ? "（无）" : detail.getErrorMessage()).append("\n");
        return sb.toString();
    }

    private String truncate(String text) {
        if (text == null) {
            return "（无）";
        }
        return text.length() <= RESPONSE_MAX_LENGTH ? text
                : text.substring(0, RESPONSE_MAX_LENGTH) + "\n...（内容过长已截断）";
    }

    private AnalysisResult parseAndValidate(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripFences(content));
        } catch (Exception e) {
            throw new AnalysisValidationException("LLM 输出不是合法 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new AnalysisValidationException("LLM 输出不是 JSON 对象");
        }
        String category = root.path("category").asText(null);
        String reason = root.path("reason").asText(null);
        String suggestion = root.path("suggestion").asText(null);
        if (category == null || !CATEGORIES.contains(category)) {
            throw new AnalysisValidationException("category 非法（仅支持 assert_error/data_error/env_error/real_defect）：" + category);
        }
        if (reason == null || reason.isBlank()) {
            throw new AnalysisValidationException("reason 不能为空");
        }
        if (suggestion == null || suggestion.isBlank()) {
            throw new AnalysisValidationException("suggestion 不能为空");
        }
        AnalysisResult r = new AnalysisResult();
        r.setCategory(category);
        r.setReason(reason.trim());
        r.setSuggestion(suggestion.trim());
        return r;
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

    private FailureAnalysisVO toVO(FailureAnalysis fa, TestCase tc, ExecutionDetail detail) {
        String caseName = tc == null ? null : tc.getName();
        Integer detailStatus = detail == null ? null : detail.getStatus();
        String errorMessage = detail == null ? null : detail.getErrorMessage();
        return FailureAnalysisVO.from(fa, caseName, detailStatus, errorMessage);
    }

    private static class AnalysisValidationException extends RuntimeException {
        AnalysisValidationException(String message) {
            super(message);
        }
    }

    private static class AnalysisResult {
        private String category;
        private String reason;
        private String suggestion;
        private String model;

        String getCategory() { return category; }
        void setCategory(String category) { this.category = category; }
        String getReason() { return reason; }
        void setReason(String reason) { this.reason = reason; }
        String getSuggestion() { return suggestion; }
        void setSuggestion(String suggestion) { this.suggestion = suggestion; }
        String getModel() { return model; }
        void setModel(String model) { this.model = model; }
    }
}