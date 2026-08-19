package com.apigentest.vo;

import lombok.Data;

import java.util.List;

/**
 * P2-1 生成质量统计结果
 */
@Data
public class GenerationQualityVO {

    private QualityMetric overall;

    private List<QualityMetric> byScenario;

    private List<QualityMetric> byModel;

    private List<QualityMetric> byPrompt;
}
