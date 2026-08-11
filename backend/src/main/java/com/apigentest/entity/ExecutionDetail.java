package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用例执行明细表
 */
@Data
@TableName("execution_detail")
public class ExecutionDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    private Long caseId;

    /** 1 通过 / 2 失败 / 3 异常 */
    private Integer status;

    private String requestText;

    private String responseText;

    private String errorMessage;

    private Long durationMs;

    private Integer retryCount;

    private LocalDateTime createdAt;
}