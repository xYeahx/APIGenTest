package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.OpenApiParser;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.ApiService;
import com.apigentest.service.ProjectService;
import com.apigentest.vo.ApiCoverageVO;
import com.apigentest.vo.ApiInfoVO;
import com.apigentest.vo.ImportResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
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

    /** SSRF 防护开关：为 true 时禁止导入内网/保留地址的文档（生产环境保持 true） */
    @Value("${app.import.block-private:true}")
    private boolean blockPrivateImport;

    public ApiServiceImpl(ApiInfoMapper apiInfoMapper, TestCaseMapper testCaseMapper,
                          ProjectService projectService, OpenApiParser openApiParser) {
        this.apiInfoMapper = apiInfoMapper;
        this.testCaseMapper = testCaseMapper;
        this.projectService = projectService;
        this.openApiParser = openApiParser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importFromFile(Long projectId, MultipartFile file) {
        projectService.requireWrite(projectId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return doImport(projectId, content);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IO_ERROR, "文件读取失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importFromUrl(Long projectId, String url) {
        projectService.requireWrite(projectId);
        validateRemoteUrl(url);
        try {
            String content = fetchRemote(url);
            return doImport(projectId, content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取远程文档失败：" + e.getMessage());
        }
    }

    /**
     * SSRF 防护：仅允许 http/https，且解析后的 IP 不能是内网/保留地址（环回、链路本地、站点本地等）。
     * 说明：校验在连接前完成；重定向在 fetchRemote 中统一拒绝。
     */
    private void validateRemoteUrl(String url) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文档地址必须是 http/https 链接");
        }
        if (!blockPrivateImport) {
            return;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "文档地址缺少有效主机名");
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateAddress(addr)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "出于安全考虑，禁止导入内网/保留地址的文档：" + host);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文档地址无法解析：" + e.getMessage());
        }
    }

    /** 内网/保留地址判断：任意地址、环回、链路本地、站点本地（10/8、172.16/12、192.168/16）、组播、IPv6 ULA */
    private boolean isPrivateAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        // IPv6 唯一本地地址 fc00::/7（isSiteLocalAddress 仅覆盖已弃用的 fec0::/10）
        if (addr instanceof Inet6Address) {
            byte[] b = addr.getAddress();
            return (b[0] & 0xFE) == 0xFC;
        }
        return false;
    }

    /** 拉取远程文档：关闭自动重定向，3xx 一律拒绝，避免重定向到内网地址 */
    private String fetchRemote(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(FETCH_TIMEOUT_MS);
            conn.setReadTimeout(FETCH_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept",
                    "application/json, application/yaml, text/plain;q=0.8, */*;q=0.5");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "文档地址返回重定向（HTTP " + code + "），已阻止自动跳转");
            }
            try (InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                if (in == null) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "远程文档无响应内容（HTTP " + code + "）");
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
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
            throw new BusinessException(ErrorCode.PARAM_INVALID, e.getMessage());
        }
        if (parsed.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文档中未解析到任何接口（paths 为空）");
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
        projectService.requireRead(projectId);
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
        List<ApiInfo> records = apiPage.getRecords();
        Map<Long, Long> caseCounts = loadCaseCounts(records.stream().map(ApiInfo::getId).toList());
        Page<ApiInfoVO> voPage = new Page<>(apiPage.getCurrent(), apiPage.getSize(), apiPage.getTotal());
        voPage.setRecords(records.stream().map(a -> toVO(a, caseCounts)).toList());
        return voPage;
    }

    @Override
    public ApiInfoVO getApiDetail(Long apiId) {
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "接口不存在");
        }
        projectService.requireRead(api.getProjectId());
        ApiInfoVO vo = toVO(api, Map.of());
        vo.setCaseCount(testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getApiId, apiId)));
        return vo;
    }

    @Override
    public ApiCoverageVO coverage(Long projectId) {
        projectService.requireRead(projectId);
        List<ApiInfo> apis = apiInfoMapper.selectList(
                new LambdaQueryWrapper<ApiInfo>().eq(ApiInfo::getProjectId, projectId));
        Map<Long, Long> caseCounts = loadCaseCounts(apis.stream().map(ApiInfo::getId).toList());
        ApiCoverageVO vo = new ApiCoverageVO();
        vo.setTotalApis(apis.size());

        Map<String, List<ApiInfo>> byTag = apis.stream().collect(Collectors.groupingBy(
                a -> (a.getTags() == null || a.getTags().isBlank()) ? "未分组" : a.getTags()));
        List<ApiCoverageVO.TagCoverage> tags = new ArrayList<>();
        for (Map.Entry<String, List<ApiInfo>> e : byTag.entrySet()) {
            ApiCoverageVO.TagCoverage tc = new ApiCoverageVO.TagCoverage();
            tc.setTag(e.getKey());
            tc.setTotal(e.getValue().size());
            tc.setCovered(e.getValue().stream()
                    .filter(a -> caseCounts.getOrDefault(a.getId(), 0L) > 0).count());
            tc.setRate(calcRate(tc.getTotal(), tc.getCovered()));
            tags.add(tc);
        }
        tags.sort(Comparator.comparing(ApiCoverageVO.TagCoverage::getTotal).reversed());
        vo.setByTag(tags);

        long covered = tags.stream().mapToLong(ApiCoverageVO.TagCoverage::getCovered).sum();
        vo.setCoveredApis(covered);
        vo.setRate(calcRate(apis.size(), covered));
        return vo;
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
        byProject.keySet().forEach(projectService::requireWrite);
        // 先解除用例对接口的引用（外键 fk_case_api），再删除接口
        testCaseMapper.update(null, new LambdaUpdateWrapper<TestCase>()
                .in(TestCase::getApiId, ids)
                .set(TestCase::getApiId, null));
        apiInfoMapper.deleteBatchIds(ids);
    }

    /** 统计指定接口集合中每个接口已关联的用例数 */
    private Map<Long, Long> loadCaseCounts(List<Long> apiIds) {
        if (apiIds.isEmpty()) {
            return Map.of();
        }
        return testCaseMapper.selectList(new LambdaQueryWrapper<TestCase>()
                        .in(TestCase::getApiId, apiIds)
                        .select(TestCase::getApiId))
                .stream()
                .collect(Collectors.groupingBy(TestCase::getApiId, Collectors.counting()));
    }

    private double calcRate(long total, long covered) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(covered * 1000.0 / total) / 10.0;
    }

    private ApiInfoVO toVO(ApiInfo api, Map<Long, Long> caseCounts) {
        ApiInfoVO vo = new ApiInfoVO();
        vo.setId(api.getId());
        vo.setProjectId(api.getProjectId());
        vo.setMethod(api.getMethod());
        vo.setPath(api.getPath());
        vo.setSummary(api.getSummary());
        vo.setTags(api.getTags());
        vo.setCaseCount(caseCounts.getOrDefault(api.getId(), 0L));
        vo.setSpec(api.getSpec());
        return vo;
    }
}
