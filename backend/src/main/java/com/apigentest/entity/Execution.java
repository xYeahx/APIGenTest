package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行记录表（一次执行）
 */
@Data
@TableName("execution")
public class Execution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 1 手动 / 2 定时 / 3 CI */
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
}