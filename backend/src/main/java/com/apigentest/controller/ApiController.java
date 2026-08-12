package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.BatchDeleteDTO;
import com.apigentest.dto.ImportRequestDTO;
import com.apigentest.service.ApiService;
import com.apigentest.vo.ApiCoverageVO;
import com.apigentest.vo.ApiInfoVO;
import com.apigentest.vo.ImportResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ApiService apiService;

    public ApiController(ApiService apiService) {
        this.apiService = apiService;
    }

    /**
     * 文件方式导入 OpenAPI（multipart/form-data，参数名 file）
     */
    @PostMapping(value = "/projects/{projectId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResultVO> importFile(@PathVariable Long projectId,
                                             @RequestParam("file") MultipartFile file) {
        return Result.ok(apiService.importFromFile(projectId, file));
    }

    /**
     * URL 方式导入 OpenAPI（JSON body：{ "url": "https://..." }）
     */
    @PostMapping(value = "/projects/{projectId}/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ImportResultVO> importUrl(@PathVariable Long projectId,
                                            @Valid @RequestBody ImportRequestDTO dto) {
        return Result.ok(apiService.importFromUrl(projectId, dto.getUrl()));
    }

    @GetMapping("/projects/{projectId}/apis")
    public Result<Page<ApiInfoVO>> list(@PathVariable Long projectId,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String tag) {
        return Result.ok(apiService.listApis(projectId, page, size, keyword, tag));
    }

    @GetMapping("/projects/{projectId}/coverage")
    public Result<ApiCoverageVO> coverage(@PathVariable Long projectId) {
        return Result.ok(apiService.coverage(projectId));
    }
    @GetMapping("/apis/{apiId}")
    public Result<ApiInfoVO> detail(@PathVariable Long apiId) {
        return Result.ok(apiService.getApiDetail(apiId));
    }

    @DeleteMapping("/apis/batch")
    public Result<Void> batchDelete(@RequestBody BatchDeleteDTO dto) {
        apiService.batchDelete(dto.getIds());
        return Result.ok();
    }
}