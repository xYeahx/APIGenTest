package com.apigentest.service.impl;

import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.Execution;
import com.apigentest.entity.ExecutionDetail;
import com.apigentest.entity.ScheduledTask;
import com.apigentest.entity.TestCase;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.ExecutionDetailMapper;
import com.apigentest.mapper.ExecutionMapper;
import com.apigentest.mapper.ScheduledTaskMapper;
import com.apigentest.mapper.TestCaseMapper;
import com.apigentest.service.DashboardService;
import com.apigentest.service.NotificationService;
import com.apigentest.service.ProjectService;
import com.apigentest.vo.DashboardOverviewVO;
import com.apigentest.vo.NotificationVO;
import com.apigentest.vo.ProjectVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 概览页聚合：跨项目统计、趋势、失败 TOP、项目卡片（覆盖率）、最近动态
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final int TREND_LIMIT = 20;
    private static final int RECENT_EXEC_LIMIT = 10;
    private static final int FAIL_TOP_SIZE = 8;
    private static final int ACTIVITY_LIMIT = 12;

    private final ProjectService projectService;
    private final ApiInfoMapper apiInfoMapper;
    private final TestCaseMapper testCaseMapper;
    private final ExecutionMapper executionMapper;
    private final ExecutionDetailMapper executionDetailMapper;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final NotificationService notificationService;

    public DashboardServiceImpl(ProjectService projectService,
                                ApiInfoMapper apiInfoMapper,
                                TestCaseMapper testCaseMapper,
                                ExecutionMapper executionMapper,
                                ExecutionDetailMapper executionDetailMapper,
                                ScheduledTaskMapper scheduledTaskMapper,
                                NotificationService notificationService) {
        this.projectService = projectService;
        this.apiInfoMapper = apiInfoMapper;
        this.testCaseMapper = testCaseMapper;
        this.executionMapper = executionMapper;
        this.executionDetailMapper = executionDetailMapper;
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.notificationService = notificationService;
    }

    @Override
    public DashboardOverviewVO overview() {
        DashboardOverviewVO vo = new DashboardOverviewVO();
        List<ProjectVO> projects = projectService.listMyProjects();
        List<Long> projectIds = projects.stream().map(ProjectVO::getId).toList();
        Map<Long, String> projectNames = projects.stream()
                .collect(Collectors.toMap(ProjectVO::getId, p -> p.getName() == null ? "项目" + p.getId() : p.getName()));

        // ---------- 执行数据 ----------
        List<Execution> executions = new ArrayList<>();
        if (!projectIds.isEmpty()) {
            executions = executionMapper.selectList(new LambdaQueryWrapper<Execution>()
                    .in(Execution::getProjectId, projectIds)
                    .orderByDesc(Execution::getId));
        }
        Map<Long, Execution> lastExecByProject = new HashMap<>();
        for (Execution ex : executions) {
            lastExecByProject.putIfAbsent(ex.getProjectId(), ex);
        }

        // ---------- 统计 ----------
        DashboardOverviewVO.Stats stats = new DashboardOverviewVO.Stats();
        stats.setProjectCount(projects.size());
        stats.setUnreadCount(notificationService.unreadCount());
        stats.setTaskCount(projectIds.isEmpty() ? 0L : scheduledTaskMapper.selectCount(
                new LambdaQueryWrapper<ScheduledTask>()
                        .in(ScheduledTask::getProjectId, projectIds)
                        .eq(ScheduledTask::getEnabled, 1)));
        if (!projectIds.isEmpty()) {
            stats.setApiCount(countByGroup(apiInfoMapper, "api_info", projectIds));
            stats.setCaseCount(countByGroup(testCaseMapper, "test_case", projectIds));
            stats.setExecutionCount((long) executions.size());
            long passedSum = 0;
            long totalSum = 0;
            long failedSum = 0;
            for (Execution ex : executions) {
                if (ex.getStatus() != null && ex.getStatus() == 1) {
                    if (ex.getPassed() != null) {
                        passedSum += ex.getPassed();
                    }
                    if (ex.getTotalCases() != null) {
                        totalSum += ex.getTotalCases();
                    }
                    if (ex.getFailed() != null) {
                        failedSum += ex.getFailed();
                    }
                }
            }
            stats.setFailedCases(failedSum);
            stats.setPassRate(totalSum == 0 ? 0.0 : round1(passedSum * 100.0 / totalSum));
        } else {
            stats.setApiCount(0L);
            stats.setCaseCount(0L);
            stats.setExecutionCount(0L);
            stats.setFailedCases(0L);
            stats.setPassRate(0.0);
        }
        vo.setStats(stats);

        // ---------- 趋势（最近 20 次已完成执行，按时间升序） ----------
        List<Execution> completedAsc = executions.stream()
                .filter(e -> e.getStatus() != null && e.getStatus() == 1)
                .sorted(Comparator.comparing(Execution::getId))
                .toList();
        List<DashboardOverviewVO.TrendPoint> trend = new ArrayList<>();
        int from = Math.max(0, completedAsc.size() - TREND_LIMIT);
        for (int i = from; i < completedAsc.size(); i++) {
            Execution ex = completedAsc.get(i);
            DashboardOverviewVO.TrendPoint p = new DashboardOverviewVO.TrendPoint();
            p.setExecutionId(ex.getId());
            p.setProjectId(ex.getProjectId());
            p.setProjectName(projectNames.get(ex.getProjectId()));
            p.setStartedAt(ex.getStartedAt());
            p.setPassRate(calcPassRate(ex));
            p.setDurationMs(ex.getDurationMs());
            p.setTotalCases(ex.getTotalCases());
            trend.add(p);
        }
        vo.setTrend(trend);

        // ---------- 最近执行 ----------
        List<DashboardOverviewVO.RecentExecution> recent = new ArrayList<>();
        for (int i = 0; i < Math.min(RECENT_EXEC_LIMIT, executions.size()); i++) {
            Execution ex = executions.get(i);
            DashboardOverviewVO.RecentExecution r = new DashboardOverviewVO.RecentExecution();
            r.setExecutionId(ex.getId());
            r.setProjectId(ex.getProjectId());
            r.setProjectName(projectNames.get(ex.getProjectId()));
            r.setTriggerType(ex.getTriggerType());
            r.setStatus(ex.getStatus());
            r.setTotalCases(ex.getTotalCases());
            r.setPassed(ex.getPassed());
            r.setFailed(ex.getFailed());
            r.setDurationMs(ex.getDurationMs());
            r.setStartedAt(ex.getStartedAt());
            r.setPassRate(calcPassRate(ex));
            recent.add(r);
        }
        vo.setRecentExecutions(recent);

        // ---------- 失败 TOP ----------
        vo.setFailTop(buildFailTop(executions, projectNames));

        // ---------- 项目卡片（覆盖率） ----------
        vo.setProjects(buildProjectCards(projects, projectIds, lastExecByProject));

        // ---------- 最近动态 ----------
        vo.setActivities(buildActivities(executions, projectNames));

        return vo;
    }

    // ---------- 私有方法 ----------

    private List<DashboardOverviewVO.FailedTopItem> buildFailTop(List<Execution> executions, Map<Long, String> projectNames) {
        List<DashboardOverviewVO.FailedTopItem> top = new ArrayList<>();
        if (executions.isEmpty()) {
            return top;
        }
        List<Long> execIds = executions.stream().map(Execution::getId).toList();
        List<ExecutionDetail> failed = executionDetailMapper.selectList(new LambdaQueryWrapper<ExecutionDetail>()
                .in(ExecutionDetail::getExecutionId, execIds)
                .ne(ExecutionDetail::getStatus, 1)
                .orderByDesc(ExecutionDetail::getId));
        if (failed.isEmpty()) {
            return top;
        }
        List<Long> caseIds = failed.stream().map(ExecutionDetail::getCaseId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, TestCase> caseMap = caseIds.isEmpty() ? Map.of()
                : testCaseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(TestCase::getId, t -> t));
        Map<Long, Execution> execMap = executions.stream()
                .collect(Collectors.toMap(Execution::getId, e -> e));

        Map<Long, List<ExecutionDetail>> byCase = failed.stream()
                .filter(d -> d.getCaseId() != null)
                .collect(Collectors.groupingBy(ExecutionDetail::getCaseId));
        List<Map.Entry<Long, List<ExecutionDetail>>> entries = new ArrayList<>(byCase.entrySet());
        entries.sort((a, b) -> b.getValue().size() - a.getValue().size());
        for (int i = 0; i < Math.min(FAIL_TOP_SIZE, entries.size()); i++) {
            Map.Entry<Long, List<ExecutionDetail>> e = entries.get(i);
            DashboardOverviewVO.FailedTopItem item = new DashboardOverviewVO.FailedTopItem();
            item.setCaseId(e.getKey());
            TestCase tc = caseMap.get(e.getKey());
            item.setCaseName(tc == null ? "用例#" + e.getKey() : tc.getName());
            item.setFailCount((long) e.getValue().size());
            item.setLastError(e.getValue().get(0).getErrorMessage());
            Execution ex = execMap.get(e.getValue().get(0).getExecutionId());
            if (ex != null) {
                item.setProjectId(ex.getProjectId());
                item.setProjectName(projectNames.get(ex.getProjectId()));
            }
            top.add(item);
        }
        return top;
    }

    private List<DashboardOverviewVO.ProjectCard> buildProjectCards(List<ProjectVO> projects, List<Long> projectIds,
                                                                   Map<Long, Execution> lastExecByProject) {
        Map<Long, Long> apiCounts = countMap(apiInfoMapper, projectIds);
        Map<Long, Long> caseCounts = countMap(testCaseMapper, projectIds);
        Map<Long, Set<Long>> coveredApis = coveredApiMap(projectIds);

        List<DashboardOverviewVO.ProjectCard> cards = new ArrayList<>();
        for (ProjectVO p : projects) {
            DashboardOverviewVO.ProjectCard card = new DashboardOverviewVO.ProjectCard();
            card.setProjectId(p.getId());
            card.setName(p.getName());
            card.setMyRole(p.getMyRole());
            long apiCount = apiCounts.getOrDefault(p.getId(), 0L);
            long covered = coveredApis.getOrDefault(p.getId(), Set.of()).size();
            card.setApiCount(apiCount);
            card.setCaseCount(caseCounts.getOrDefault(p.getId(), 0L));
            card.setCoveredApis(covered);
            card.setCoverageRate(apiCount == 0 ? 0.0 : round1(covered * 100.0 / apiCount));
            Execution last = lastExecByProject.get(p.getId());
            if (last != null) {
                card.setLastExecutionId(last.getId());
                card.setLastStatus(last.getStatus());
                card.setLastPassRate(calcPassRate(last));
                card.setLastExecutedAt(last.getStartedAt());
            }
            cards.add(card);
        }
        return cards;
    }

    private List<DashboardOverviewVO.Activity> buildActivities(List<Execution> executions, Map<Long, String> projectNames) {
        List<DashboardOverviewVO.Activity> activities = new ArrayList<>();
        for (int i = 0; i < Math.min(5, executions.size()); i++) {
            Execution ex = executions.get(i);
            DashboardOverviewVO.Activity a = new DashboardOverviewVO.Activity();
            a.setType("execution");
            a.setTime(ex.getStartedAt());
            a.setProjectId(ex.getProjectId());
            a.setExecutionId(ex.getId());
            a.setTitle("执行完成：" + projectNames.getOrDefault(ex.getProjectId(), "项目#" + ex.getProjectId()));
            a.setContent(buildExecSummary(ex));
            activities.add(a);
        }
        List<NotificationVO> notifications = notificationService.listMine(1, 10).getRecords();
        for (NotificationVO n : notifications) {
            DashboardOverviewVO.Activity a = new DashboardOverviewVO.Activity();
            a.setType("notification");
            a.setTime(n.getCreatedAt());
            a.setProjectId(n.getProjectId());
            a.setExecutionId(n.getExecutionId());
            a.setTitle(n.getTitle());
            a.setContent(n.getContent());
            activities.add(a);
        }
        activities.sort(Comparator.comparing(DashboardOverviewVO.Activity::getTime, Comparator.nullsLast(Comparator.reverseOrder())));
        if (activities.size() > ACTIVITY_LIMIT) {
            return activities.subList(0, ACTIVITY_LIMIT);
        }
        return activities;
    }

    private String buildExecSummary(Execution ex) {
        String trigger = switch (ex.getTriggerType() == null ? 0 : ex.getTriggerType()) {
            case 2 -> "定时";
            case 3 -> "CI";
            default -> "手动";
        };
        String status = ex.getStatus() != null && ex.getStatus() == 1 ? "完成" : "执行中";
        return trigger + "触发 · " + status + " · 通过 " + nullSafe(ex.getPassed()) + " / 失败 "
                + nullSafe(ex.getFailed()) + " / 共 " + nullSafe(ex.getTotalCases());
    }

    /** 按项目分组统计表行数：SELECT project_id, COUNT(*) */
    private <T> Map<Long, Long> countMap(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, List<Long> projectIds) {
        Map<Long, Long> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        QueryWrapper<T> qw = new QueryWrapper<>();
        qw.select("project_id AS pid", "COUNT(*) AS cnt").in("project_id", projectIds).groupBy("project_id");
        List<Map<String, Object>> rows = mapper.selectMaps(qw);
        for (Map<String, Object> row : rows) {
            Long pid = longVal(row, "pid");
            Long cnt = longVal(row, "cnt");
            if (pid != null) {
                result.put(pid, cnt == null ? 0L : cnt);
            }
        }
        return result;
    }

    private long countByGroup(com.baomidou.mybatisplus.core.mapper.BaseMapper<?> mapper, String table, List<Long> projectIds) {
        return countMap(mapper, projectIds).values().stream().mapToLong(Long::longValue).sum();
    }

    /** 每个项目已生成用例的接口集合（去重 api_id） */
    private Map<Long, Set<Long>> coveredApiMap(List<Long> projectIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        QueryWrapper<TestCase> qw = new QueryWrapper<>();
        qw.select("project_id AS pid", "api_id").in("project_id", projectIds).isNotNull("api_id");
        for (Map<String, Object> row : testCaseMapper.selectMaps(qw)) {
            Long pid = longVal(row, "pid");
            Long apiId = longVal(row, "api_id");
            if (pid != null && apiId != null) {
                result.computeIfAbsent(pid, k -> new HashSet<>()).add(apiId);
            }
        }
        return result;
    }

    private Long longVal(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key) && e.getValue() != null) {
                if (e.getValue() instanceof Number num) {
                    return num.longValue();
                }
                try {
                    return Long.parseLong(e.getValue().toString());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private double calcPassRate(Execution ex) {
        if (ex.getTotalCases() == null || ex.getTotalCases() <= 0 || ex.getStatus() == null || ex.getStatus() != 1) {
            return 0.0;
        }
        int passed = ex.getPassed() == null ? 0 : ex.getPassed();
        return round1(passed * 100.0 / ex.getTotalCases());
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }
}