package com.apigentest.service.impl;

import com.apigentest.entity.Execution;
import com.apigentest.entity.ExecutionDetail;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ExecutionDetailMapper;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.ExecutionService;
import com.apigentest.service.ProjectService;
import com.apigentest.service.ReportService;
import com.apigentest.vo.ExecutionSummaryVO;
import com.apigentest.vo.ReportVO;
import com.apigentest.vo.TrendVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报告统计：单次执行报告（汇总 / 失败 TOP / 错误聚合）与项目趋势
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_TREND_LIMIT = 20;
    private static final int MAX_TREND_LIMIT = 50;
    private static final int TOP_SIZE = 10;

    private final ExecutionMapper executionMapper;
    private final ExecutionDetailMapper detailMapper;
    private final TestCaseMapper testCaseMapper;
    private final ProjectService projectService;
    private final ExecutionService executionService;

    public ReportServiceImpl(ExecutionMapper executionMapper, ExecutionDetailMapper detailMapper,
                             TestCaseMapper testCaseMapper, ProjectService projectService,
                             ExecutionService executionService) {
        this.executionMapper = executionMapper;
        this.detailMapper = detailMapper;
        this.testCaseMapper = testCaseMapper;
        this.projectService = projectService;
        this.executionService = executionService;
    }

    @Override
    public ReportVO getReport(Long executionId) {
        ExecutionSummaryVO execution = executionService.get(executionId);
        List<ExecutionDetail> failed = detailMapper.selectList(
                new LambdaQueryWrapper<ExecutionDetail>()
                        .eq(ExecutionDetail::getExecutionId, executionId)
                        .ne(ExecutionDetail::getStatus, 1));
        Map<Long, TestCase> caseMap = loadCaseMap(failed);
        return buildReport(execution, failed, caseMap);
    }

    @Override
    public TrendVO getTrend(Long projectId, Integer limit) {
        projectService.getOwnedProject(projectId);
        int n = (limit == null || limit <= 0) ? DEFAULT_TREND_LIMIT : Math.min(limit, MAX_TREND_LIMIT);
        List<Execution> executions = executionMapper.selectList(
                new LambdaQueryWrapper<Execution>()
                        .eq(Execution::getProjectId, projectId)
                        .eq(Execution::getStatus, 1)
                        .orderByAsc(Execution::getId));

        List<TrendVO.TrendPoint> points = new ArrayList<>();
        int executionCount = 0;
        int totalCases = 0;
        double passRateSum = 0;
        int passRateCount = 0;
        for (Execution ex : executions) {
            executionCount++;
            totalCases += ex.getTotalCases() == null ? 0 : ex.getTotalCases();
            double rate = calcPassRate(ex);
            if (ex.getTotalCases() != null && ex.getTotalCases() > 0) {
                passRateSum += rate;
                passRateCount++;
            }
            TrendVO.TrendPoint p = new TrendVO.TrendPoint();
            p.setExecutionId(ex.getId());
            p.setStartedAt(ex.getStartedAt());
            p.setPassRate(rate);
            p.setDurationMs(ex.getDurationMs());
            p.setTotalCases(ex.getTotalCases());
            points.add(p);
        }
        if (points.size() > n) {
            points = points.subList(points.size() - n, points.size());
        }
        TrendVO vo = new TrendVO();
        vo.setPoints(points);
        vo.setExecutionCount(executionCount);
        vo.setTotalCases(totalCases);
        vo.setAvgPassRate(passRateCount == 0 ? 0.0 : Math.round(passRateSum / passRateCount * 10.0) / 10.0);
        return vo;
    }

    // ---------- 私有方法 ----------

    private ReportVO buildReport(ExecutionSummaryVO execution, List<ExecutionDetail> failed,
                                 Map<Long, TestCase> caseMap) {
        ReportVO vo = new ReportVO();
        vo.setExecution(execution);

        // 失败 TOP：按用例分组统计失败次数
        Map<Long, List<ExecutionDetail>> byCase = failed.stream()
                .collect(Collectors.groupingBy(ExecutionDetail::getCaseId));
        List<ReportVO.FailedTopItem> top = byCase.entrySet().stream().map(e -> {
            ReportVO.FailedTopItem item = new ReportVO.FailedTopItem();
            item.setCaseId(e.getKey());
            TestCase tc = caseMap.get(e.getKey());
            item.setCaseName(tc == null ? "用例#" + e.getKey() : tc.getName());
            item.setFailedCount(e.getValue().size());
            item.setLastDetailId(e.getValue().get(e.getValue().size() - 1).getId());
            item.setLastError(e.getValue().stream()
                    .map(ExecutionDetail::getErrorMessage)
                    .filter(s -> s != null && !s.isBlank())
                    .findFirst().orElse(null));
            return item;
        }).sorted(Comparator.comparing(ReportVO.FailedTopItem::getFailedCount).reversed())
                .limit(TOP_SIZE).toList();
        vo.setFailedTop(top);

        // 错误聚合：按错误信息分组
        Map<String, List<ExecutionDetail>> byError = failed.stream()
                .collect(Collectors.groupingBy(d -> {
                    String error = d.getErrorMessage();
                    return (error == null || error.isBlank()) ? "无错误信息" : error;
                }));
        List<ReportVO.ErrorGroup> groups = byError.entrySet().stream().map(e -> {
            ReportVO.ErrorGroup g = new ReportVO.ErrorGroup();
            g.setError(e.getKey());
            g.setCount(e.getValue().size());
            g.setCases(e.getValue().stream()
                    .map(ExecutionDetail::getCaseId)
                    .distinct()
                    .map(id -> {
                        TestCase tc = caseMap.get(id);
                        return tc == null ? "用例#" + id : tc.getName();
                    })
                    .limit(5).toList());
            return g;
        }).sorted(Comparator.comparing(ReportVO.ErrorGroup::getCount).reversed())
                .limit(TOP_SIZE).toList();
        vo.setErrorGroups(groups);
        return vo;
    }

    private Map<Long, TestCase> loadCaseMap(List<ExecutionDetail> details) {
        List<Long> caseIds = details.stream()
                .map(ExecutionDetail::getCaseId).distinct().toList();
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        return testCaseMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));
    }

    private double calcPassRate(Execution ex) {
        if (ex.getTotalCases() == null || ex.getTotalCases() <= 0) {
            return 0.0;
        }
        return Math.round(ex.getPassed() * 1000.0 / ex.getTotalCases()) / 10.0;
    }
}