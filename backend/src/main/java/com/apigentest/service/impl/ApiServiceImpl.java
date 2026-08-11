package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.OpenApiParser;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.ApiService;
import com.apigentest.service.ProjectService;
import com.apigentest.vo.ApiInfoVO;
import com.apigentest.vo.ImportResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApiServiceImpl implements ApiService {

    private static final int FETCH_TIMEOUT_MS = 15_000;

    private final ApiInfoMapper apiInfoMapper;
    private final TestCaseMapper testCaseMapper;
    private final ProjectService projectService;
    private final OpenApiParser openApiParser;
    private final RestClient restClient;

    public ApiServiceImpl(ApiInfoMapper apiInfoMapper, TestCaseMapper testCaseMapper, ProjectService projectService, OpenApiParser openApiParser) {
        this.apiInfoMapper = apiInfoMapper;
        this.testCaseMapper = testCaseMapper;
        this.projectService = projectService;
        this.openApiParser = openApiParser;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(FETCH_TIMEOUT_MS);
        factory.setReadTimeout(FETCH_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importFromFile(Long projectId, MultipartFile file) {
        projectService.getOwnedProject(projectId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return doImport(projectId, content);
        } catch (IOException e) {
            throw new BusinessException(500, "文件读取失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importFromUrl(Long projectId, String url) {
        projectService.getOwnedProject(projectId);
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new BusinessException(400, "文档地址必须是 http/https 链接");
        }
        try {
            String content = restClient.get().uri(url).retrieve().body(String.class);
            return doImport(projectId, content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "获取远程文档失败：" + e.getMessage());
        }
    }

    /**
     * 核心导入逻辑：解析并重建该项目的接口清单（重新导入即刷新）
     */
    private ImportResultVO doImport(Long projectId, String content) {
        List<OpenApiParser.ParsedApi> parsed;
        try {
            parsed = openApiParser.parseApis(content);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        }
        if (parsed.isEmpty()) {
            throw new BusinessException(400, "文档中未解析到任何接口（paths 为空）");
        }
        // 替换式导入：先清空该项目旧接口
        apiInfoMapper.delete(new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, projectId));
        String specJson = openApiParser.normalizeToJson(content);
        for (OpenApiParser.ParsedApi p : parsed) {
            ApiInfo api = new ApiInfo();
            api.setProjectId(projectId);
            api.setMethod(p.getMethod());
            api.setPath(p.getPath());
            api.setSummary(p.getSummary());
            api.setDescription(p.getDescription());
            api.setTags(p.getTags());
            api.setSpec(specJson);
            apiInfoMapper.insert(api);
        }
        ImportResultVO vo = new ImportResultVO();
        vo.setProjectId(projectId);
        vo.setTotal(parsed.size());
        return vo;
    }

    @Override
    public Page<ApiInfoVO> listApis(Long projectId, long page, long size, String keyword, String tag) {
        projectService.getOwnedProject(projectId);
        LambdaQueryWrapper<ApiInfo> wrapper = new LambdaQueryWrapper<ApiInfo>()
                .eq(ApiInfo::getProjectId, projectId)
                .orderByAsc(ApiInfo::getPath);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ApiInfo::getPath, keyword)
                    .or().like(ApiInfo::getSummary, keyword)
                    .or().like(ApiInfo::getDescription, keyword));
        }
        if (tag != null && !tag.isBlank()) {
            wrapper.like(ApiInfo::getTags, tag);
        }
        Page<ApiInfo> apiPage = apiInfoMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ApiInfoVO> voPage = new Page<>(apiPage.getCurrent(), apiPage.getSize(), apiPage.getTotal());
        voPage.setRecords(apiPage.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public ApiInfoVO getApiDetail(Long apiId) {
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null) {
            throw new BusinessException(404, "接口不存在");
        }
        projectService.getOwnedProject(api.getProjectId());
        return toVO(api);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<ApiInfo> apis = apiInfoMapper.selectBatchIds(ids);
        // 权限校验：按项目分组检查归属
        Map<Long, List<ApiInfo>> byProject = apis.stream()
                .collect(Collectors.groupingBy(ApiInfo::getProjectId));
        byProject.keySet().forEach(projectService::getOwnedProject);
        // 先解除用例对接口的引用（外键 fk_case_api），再删除接口
        testCaseMapper.update(null, new LambdaUpdateWrapper<TestCase>()
                .in(TestCase::getApiId, ids)
                .set(TestCase::getApiId, null));
        apiInfoMapper.deleteBatchIds(ids);
    }

    private ApiInfoVO toVO(ApiInfo api) {
        ApiInfoVO vo = new ApiInfoVO();
        vo.setId(api.getId());
        vo.setProjectId(api.getProjectId());
        vo.setMethod(api.getMethod());
        vo.setPath(api.getPath());
        vo.setSummary(api.getSummary());
        vo.setTags(api.getTags());
        vo.setSpec(api.getSpec());
        return vo;
    }
}