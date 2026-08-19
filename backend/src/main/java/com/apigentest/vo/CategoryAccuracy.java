package com.apigentest.vo;

import lombok.Data;

/**
 * 归因准确率（按分类）
 */
@Data
public class CategoryAccuracy {

    private String category;

    /** 分析总数 */
    private int analyzed;

    /** 已人工确认数 */
    private int confirmed;

    /** 确认正确数（未修正） */
    private int correct;

    /** 人工修正数 */
    private int corrected;

    /** 正确率 = correct / (correct + corrected)（%） */
    private double accuracy;
}
