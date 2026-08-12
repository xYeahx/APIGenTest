package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.BatchDeleteDTO;
import com.apigentest.dto.BatchStatusDTO;
import com.apigentest.dto.CaseDTO;
import com.apigentest.dto.CaseQuery;
import com.apigentest.service.TestCaseService;
import com.apigentest.vo.ImportResultVO;
import com.apigentest.vo.CaseVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @GetMapping("/projects/{projectId}/cases")
    public Result<Page<CaseVO>> list(@PathVariable Long projectId,
                                     @RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) Long apiId,
                                     @RequestParam(required = false) String scenarioType,
                                     @RequestParam(required = false) Integer status,
                                     @RequestParam(required = false) String keyword) {
        CaseQuery query = new CaseQuery();
        query.setApiId(apiId);
        query.setScenarioType(scenarioType);
        query.setStatus(status);
        query.setKeyword(keyword);
        return Result.ok(testCaseService.list(projectId, query, page, size));
    }

    /** 导出用例：format=json / postman / openapi，可选 caseIds 过滤 */
    @GetMapping("/projects/{projectId}/cases/export")
    public ResponseEntity<byte[]> export(@PathVariable Long projectId,
                                         @RequestParam(required = false) String format,
                                         @RequestParam(required = false) List<Long> caseIds) {
        TestCaseService.ExportFile file = testCaseService.exportCases(projectId, format, caseIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(file.content);
    }

    /** 导出 pytest + requests 脚本（text/plain 下载） */
    @GetMapping("/projects/{projectId}/cases/export-pytest")
    public ResponseEntity<String> exportPytest(@PathVariable Long projectId,
                                               @RequestParam(required = false) Long environmentId,
                                               @RequestParam(required = false) List<Long> caseIds) {
        String script = testCaseService.exportPytest(projectId, environmentId, caseIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test_cases.py\"")
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(script);
    }

    /** 导入用例（multipart，支持 Postman Collection / 平台导出 JSON） */
    @PostMapping(value = "/projects/{projectId}/cases/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResultVO> importCases(@PathVariable Long projectId,
                                              @RequestParam("file") MultipartFile file) {
        return Result.ok(testCaseService.importCases(projectId, file));
    }
    @GetMapping("/cases/{id}")
    public Result<CaseVO> detail(@PathVariable Long id) {
        return Result.ok(testCaseService.getDetail(id));
    }

    @PostMapping("/cases")
    public Result<CaseVO> create(@Valid @RequestBody CaseDTO dto) {
        return Result.ok(testCaseService.create(dto));
    }

    @PutMapping("/cases/{id}")
    public Result<CaseVO> update(@PathVariable Long id, @Valid @RequestBody CaseDTO dto) {
        return Result.ok(testCaseService.update(id, dto));
    }

    @DeleteMapping("/cases/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        testCaseService.delete(id);
        return Result.ok();
    }

    @PutMapping("/cases/batch-status")
    public Result<Void> batchStatus(@Valid @RequestBody BatchStatusDTO dto) {
        testCaseService.batchStatus(dto.getIds(), dto.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/cases/batch")
    public Result<Void> batchDelete(@RequestBody BatchDeleteDTO dto) {
        testCaseService.batchDelete(dto.getIds());
        return Result.ok();
    }
}