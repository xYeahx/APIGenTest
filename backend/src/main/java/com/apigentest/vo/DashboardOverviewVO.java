package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 概览页聚合数据：全局统计 + 趋势 + 失败 TOP + 最近执行 + 项目卡片 + 最近动态
 */
@Data
public class DashboardOverviewVO {

    /** 全局统计 */
    private Stats stats;

    /** 最近 20 次执行趋势（通过率/耗时，跨项目） */
    private List<TrendPoint> trend;

    /** 失败用例 TOP（跨项目） */
    private List<FailedTopItem> failTop;

    /** 最近 10 条执行记录 */
    private List<RecentExecution> recentExecutions;

    /** 项目卡片（含覆盖率） */
    private List<ProjectCard> projects;

    /** 最近动态（执行/通知合并时间线） */
    private List<Activity> activities;

    @Data
    public static class Stats {
        private Integer projectCount;
        private Long apiCount;
        private Long caseCount;
        private Long executionCount;
        /** 整体通过率（0-100，仅已完成执行） */
        private Double passRate;
        private Long failedCases;
        /** 启用中的定时任务数 */
        private Long taskCount;
        private Long unreadCount;
    }

    @Data
    public static class TrendPoint {
        private Long executionId;
        private Long projectId;
        private String projectName;
        private LocalDateTime startedAt;
        private Double passRate;
        private Long durationMs;
        private Integer totalCases;
    }

    @Data
    public static class FailedTopItem {
        private Long caseId;
        private String caseName;
        private Long projectId;
        private String projectName;
        private Long failCount;
        private String lastError;
    }

    @Data
    public static class RecentExecution {
        private Long executionId;
        private Long projectId;
        private String projectName;
        private Integer triggerType;
        private Integer status;
        private Integer totalCases;
        private Integer passed;
        private Integer failed;
        private Long durationMs;
        private LocalDateTime startedAt;
        private Double passRate;
    }

    @Data
    public static class ProjectCard {
        private Long projectId;
        private String name;
        private Integer myRole;
        private Long apiCount;
        private Long caseCount;
        private Long coveredApis;
        /** 覆盖率（0-100，1 位小数） */
        private Double coverageRate;
        private Long lastExecutionId;
        /** 最近一次执行状态：null 表示从未执行 */
        private Integer lastStatus;
        private Double lastPassRate;
        private LocalDateTime lastExecutedAt;
    }

    @Data
    public static class Activity {
        /** execution / notification */
        private String type;
        private String title;
        private String content;
        private Long projectId;
        private Long executionId;
        private LocalDateTime time;
    }
}