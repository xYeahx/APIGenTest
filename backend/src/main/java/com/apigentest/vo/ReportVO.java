package com.apigentest.vo;

import lombok.Data;

import java.util.List;

/**
 * 单次执行报告：汇总 + 失败 TOP + 错误聚合
 */
@Data
public class ReportVO {

    /** 执行汇总（复用执行模块的汇总结构） */
    private ExecutionSummaryVO execution;

    /** 失败用例 TOP（按失败次数降序） */
    private List<FailedTopItem> failedTop;

    /** 错误信息聚合（同类型错误计数 + 涉及用例） */
    private List<ErrorGroup> errorGroups;

    @Data
    public static class FailedTopItem {
        private Long caseId;
        private String caseName;
        private Integer failedCount;
        private String lastError;

        /** 最近一次失败明细 ID（归因分析入口用） */
        private Long lastDetailId;
    }

    @Data
    public static class ErrorGroup {
        private String error;
        private Integer count;
        private List<String> cases;
    }
}