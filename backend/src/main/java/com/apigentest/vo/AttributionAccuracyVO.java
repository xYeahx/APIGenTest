package com.apigentest.vo;

import lombok.Data;

import java.util.List;

/**
 * P2-4 归因准确率统计结果
 */
@Data
public class AttributionAccuracyVO {

    /** 分析总数 */
    private int totalAnalyzed;

    /** 已人工确认数 */
    private int totalConfirmed;

    /** 确认正确数 */
    private int correct;

    /** 人工修正数 */
    private int corrected;

    /** 正确率 = correct / (correct + corrected)（%） */
    private double accuracy;

    private List<CategoryAccuracy> byCategory;

    private List<AttributionSampleVO> recentSamples;
}
