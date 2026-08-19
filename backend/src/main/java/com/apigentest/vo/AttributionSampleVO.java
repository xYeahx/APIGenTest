package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 归因确认样本（P2-4）
 */
@Data
public class AttributionSampleVO {

    private Long id;

    private Long executionDetailId;

    /** LLM 分类 */
    private String category;

    /** 人工确认/修正后的分类 */
    private String confirmedCategory;

    /** 是否被人工修正 */
    private boolean corrected;

    private LocalDateTime confirmedAt;
}
