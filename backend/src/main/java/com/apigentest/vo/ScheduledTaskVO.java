package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务视图对象
 */
@Data
public class ScheduledTaskVO {

    private Long id;

    private Long projectId;

    private String name;

    private String cron;

    private Long environmentId;

    private String envName;

    /** 执行范围 JSON（同实体 caseFilter） */
    private String scope;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    private String creatorName;

    private LocalDateTime lastRunAt;

    /** 下次触发时间（实时计算） */
    private LocalDateTime nextRunAt;

    private LocalDateTime createdAt;
}