package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失败归因表：LLM 对失败用例的分析结果
 */
@Data
@TableName("failure_analysis")
public class FailureAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应失败执行明细 */
    private Long executionDetailId;

    /** assert_error / data_error / env_error / real_defect */
    private String category;

    private String reason;

    private String suggestion;

    /** 0 待确认 / 1 已确认 */
    private Integer confirmed;

    /** 人工确认/修正后的分类（NULL=未确认；与 category 相同=确认正确，不同=人工修正） */
    private String confirmedCategory;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    private String llmModel;

    private LocalDateTime createdAt;
}