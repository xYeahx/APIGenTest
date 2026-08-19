package com.apigentest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生成记录（P2-2 埋点数据展示）
 */
@Data
public class GenerationRecordVO {

    private Long id;

    private String taskId;

    private Long projectId;

    private String projectName;

    private Long apiId;

    private String apiName;

    private String model;

    private BigDecimal temperature;

    private String promptVersion;

    private Integer maxRetry;

    private Integer retryUsed;

    private String businessDesc;

    private Integer generatedCount;

    private Integer confirmedCount;

    private String scenarioGenerated;

    private String scenarioConfirmed;

    private String status;

    private String error;

    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;
}
