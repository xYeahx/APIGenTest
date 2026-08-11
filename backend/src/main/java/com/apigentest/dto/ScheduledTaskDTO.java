package com.apigentest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 定时任务创建/编辑入参
 */
@Data
public class ScheduledTaskDTO {

    @NotBlank(message = "任务名不能为空")
    private String name;

    /** 支持 5 段或 6 段 cron */
    @NotBlank(message = "cron 表达式不能为空")
    private String cron;

    @NotNull(message = "执行环境不能为空")
    private Long environmentId;

    @NotNull(message = "执行范围不能为空")
    private ScopeDTO scope;

    /** 1 启用 / 0 停用，默认启用 */
    private Integer enabled;
}