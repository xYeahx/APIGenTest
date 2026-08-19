package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 生成记录表（P2 实验数据埋点）：任务 x 接口粒度
 */
@Data
@TableName("generation_record")
public class GenerationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private Long projectId;

    private Long apiId;

    /** 生成模型 */
    private String model;

    /** 生成温度 */
    private BigDecimal temperature;

    /** Prompt 版本 */
    private String promptVersion;

    /** 配置最大重试次数 */
    private Integer maxRetry;

    /** 本次实际重试次数 */
    private Integer retryUsed;

    private String businessDesc;

    /** 校验通过可入库用例数 */
    private Integer generatedCount;

    /** 确认入库用例数 */
    private Integer confirmedCount;

    /** 按场景类型生成数（JSON） */
    private String scenarioGenerated;

    /** 按场景类型确认数（JSON） */
    private String scenarioConfirmed;

    private String status;

    private String error;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;
}
