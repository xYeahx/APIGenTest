package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目历史执行趋势：通过率 / 耗时折线数据 + 汇总
 */
@Data
public class TrendVO {

    /** 折线点（按执行先后升序，默认最近 20 次） */
    private List<TrendPoint> points;

    private Integer executionCount;

    private Integer totalCases;

    /** 平均通过率（0-100） */
    private Double avgPassRate;

    @Data
    public static class TrendPoint {
        private Long executionId;
        private LocalDateTime startedAt;
        private Double passRate;
        private Long durationMs;
        private Integer totalCases;
    }
}