package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试用例表（核心表）：LLM 生成的“可执行用例”即写入此表
 */
@Data
@TableName("test_case")
public class TestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 关联接口（手动用例可空） */
    private Long apiId;

    private String name;

    /** normal / boundary / exception / manual */
    private String scenarioType;

    private String method;

    /** 含 {{变量}} 占位符 */
    private String urlTemplate;

    /** 请求头（JSON） */
    private String headers;

    /** 查询参数（JSON） */
    private String queryParams;

    /** 请求体（JSON） */
    private String body;

    /** 断言数组（JSON） */
    private String asserts;

    /** 前置依赖用例 */
    private Long preCaseId;

    /** 响应提取变量（JSON） */
    private String extractVars;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    /** 1 手动 / 2 AI 生成（P2 埋点） */
    private Integer source;

    /** AI 生成任务ID */
    private String genTaskId;

    /** 生成模型 */
    private String genModel;

    /** 生成温度 */
    private BigDecimal genTemperature;

    /** 生成 Prompt 版本 */
    private String genPromptVersion;

    /** 生成实际重试次数 */
    private Integer genRetryCount;

    private Long creatorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}