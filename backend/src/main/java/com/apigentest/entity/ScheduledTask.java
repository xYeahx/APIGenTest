package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务表
 */
@Data
@TableName("scheduled_task")
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    /** 标准 cron（6 段，含秒） */
    private String cron;

    private Long environmentId;

    /** 执行范围 JSON：{"type":"all"} / {"type":"caseIds","caseIds":[...]} */
    private String caseFilter;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    private Long creatorId;

    private LocalDateTime lastRunAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}