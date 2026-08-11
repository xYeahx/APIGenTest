package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExecutionSummaryVO {

    private Long id;

    private Long projectId;

    private Integer triggerType;

    /** 0 执行中 / 1 完成 */
    private Integer status;

    private Integer totalCases;

    private Integer passed;

    private Integer failed;

    private Long durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long operatorId;

    /** 通过率（0-100，完成时有效） */
    private Double passRate;

    /** 已完成明细数（轮询进度用） */
    private Long detailCount;
}