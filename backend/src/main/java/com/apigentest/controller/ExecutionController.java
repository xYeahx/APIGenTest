package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.RunRequestDTO;
import com.apigentest.service.ExecutionService;
import com.apigentest.vo.ExecutionDetailVO;
import com.apigentest.vo.ExecutionSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /** 提交执行任务，返回 executionId */
    @PostMapping("/executions/run")
    public Result<Map<String, Long>> run(@Valid @RequestBody RunRequestDTO dto) {
        Long executionId = executionService.run(dto);
        return Result.ok(Map.of("executionId", executionId));
    }

    /** 执行历史（分页，按项目筛选） */
    @GetMapping("/executions")
    public Result<Page<ExecutionSummaryVO>> list(@RequestParam Long projectId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.ok(executionService.list(projectId, page, size));
    }

    /** 执行汇总（总数/通过/失败/通过率/耗时/状态/进度） */
    @GetMapping("/executions/{id}")
    public Result<ExecutionSummaryVO> get(@PathVariable Long id) {
        return Result.ok(executionService.get(id));
    }

    /** 用例明细分页（可按状态筛选） */
    @GetMapping("/executions/{id}/details")
    public Result<Page<ExecutionDetailVO>> details(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) Integer status) {
        return Result.ok(executionService.details(id, page, size, status));
    }

    /** 单条明细详情（完整请求/响应/错误信息） */
    @GetMapping("/executions/{id}/details/{detailId}")
    public Result<ExecutionDetailVO> detail(@PathVariable Long id, @PathVariable Long detailId) {
        return Result.ok(executionService.detail(id, detailId));
    }
}