package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.service.ReportService;
import com.apigentest.vo.ReportVO;
import com.apigentest.vo.TrendVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 单次执行报告：汇总 + 失败 TOP + 错误聚合 */
    @GetMapping("/reports/{executionId}")
    public Result<ReportVO> report(@PathVariable Long executionId) {
        return Result.ok(reportService.getReport(executionId));
    }

    /** 项目历史执行趋势（通过率 / 耗时折线） */
    @GetMapping("/projects/{projectId}/stats/trend")
    public Result<TrendVO> trend(@PathVariable Long projectId,
                                 @RequestParam(required = false) Integer limit) {
        return Result.ok(reportService.getTrend(projectId, limit));
    }
}