package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.UserContext;
import com.apigentest.dto.CaseDTO;
import com.apigentest.dto.CaseQuery;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.ProjectService;
import com.apigentest.service.TestCaseService;
import com.apigentest.vo.CaseVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
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
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public TestCaseServiceImpl(TestCaseMapper testCaseMapper, ApiInfoMapper apiInfoMapper,
                               ProjectService projectService, ObjectMapper objectMapper) {
        this.testCaseMapper = testCaseMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<CaseVO> list(Long projectId, CaseQuery query, long page, long size) {
        projectService.getOwnedProject(projectId);
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
        TestCase tc = getOwnedCase(id);
        return toVO(tc, loadApiMap(tc.getProjectId()));
    }

    @Override
    public CaseVO create(CaseDTO dto) {
        projectService.getOwnedProject(dto.getProjectId());
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
        TestCase tc = getOwnedCase(id);
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
        getOwnedCase(id);
        // 解除其它用例对它的前置引用（外键 fk_case_pre）
        testCaseMapper.update(null, new LambdaUpdateWrapper<TestCase>()
                .eq(TestCase::getPreCaseId, id)
                .set(TestCase::getPreCaseId, null));
        testCaseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchStatus(List<Long> ids, Integer status) {
        List<TestCase> cases = checkBatchPermission(ids);
        if (cases.isEmpty()) {
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

    // ---------- 私有方法 ----------

    private List<TestCase> checkBatchPermission(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<TestCase> cases = testCaseMapper.selectBatchIds(ids);
        Map<Long, List<TestCase>> byProject = cases.stream()
                .collect(Collectors.groupingBy(TestCase::getProjectId));
        byProject.keySet().forEach(projectService::getOwnedProject);
        return cases;
    }

    private TestCase getOwnedCase(Long id) {
        TestCase tc = testCaseMapper.selectById(id);
        if (tc == null) {
            throw new BusinessException(404, "用例不存在");
        }
        projectService.getOwnedProject(tc.getProjectId());
        return tc;
    }

    private void validate(CaseDTO dto, Long projectId) {
        if (!SCENARIO_TYPES.contains(dto.getScenarioType())) {
            throw new BusinessException(400, "场景类型仅支持 normal / boundary / exception / manual");
        }
        if (!METHODS.contains(dto.getMethod().toUpperCase())) {
            throw new BusinessException(400, "不支持的请求方法：" + dto.getMethod());
        }
        validateJson("请求头 headers", dto.getHeaders());
        validateJson("查询参数 queryParams", dto.getQueryParams());
        validateJson("请求体 body", dto.getBody());
        validateJson("断言 asserts", dto.getAsserts());
        validateJson("提取变量 extractVars", dto.getExtractVars());
        if (dto.getApiId() != null) {
            ApiInfo api = apiInfoMapper.selectById(dto.getApiId());
            if (api == null || !api.getProjectId().equals(projectId)) {
                throw new BusinessException(400, "关联接口不存在或不属于该项目");
            }
        }
        if (dto.getPreCaseId() != null) {
            TestCase pre = testCaseMapper.selectById(dto.getPreCaseId());
            if (pre == null || !pre.getProjectId().equals(projectId)) {
                throw new BusinessException(400, "前置用例不存在或不属于该项目");
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
            throw new BusinessException(400, fieldName + " 必须是合法的 JSON");
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
}