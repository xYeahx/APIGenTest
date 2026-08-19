package com.apigentest.vo;

import com.apigentest.entity.FailureAnalysis;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失败归因结果（含关联用例/明细信息，便于前端展示）
 */
@Data
public class FailureAnalysisVO {

    private Long id;

    private Long executionDetailId;

    /** assert_error / data_error / env_error / real_defect */
    private String category;

    private String reason;

    private String suggestion;

    /** 0 待确认 / 1 已确认 */
    private Integer confirmed;

    private String llmModel;

    private LocalDateTime createdAt;

    /** 人工确认/修正后的分类 */
    private String confirmedCategory;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 关联用例名（可能已删除） */
    private String caseName;

    /** 明细状态：2 失败 / 3 异常 */
    private Integer detailStatus;

    /** 明细错误信息 */
    private String errorMessage;

    public static FailureAnalysisVO from(FailureAnalysis f, String caseName, Integer detailStatus, String errorMessage) {
        FailureAnalysisVO vo = new FailureAnalysisVO();
        vo.setId(f.getId());
        vo.setExecutionDetailId(f.getExecutionDetailId());
        vo.setCategory(f.getCategory());
        vo.setReason(f.getReason());
        vo.setSuggestion(f.getSuggestion());
        vo.setConfirmed(f.getConfirmed());
        vo.setLlmModel(f.getLlmModel());
        vo.setCreatedAt(f.getCreatedAt());
        vo.setConfirmedCategory(f.getConfirmedCategory());
        vo.setConfirmedAt(f.getConfirmedAt());
        vo.setCaseName(caseName);
        vo.setDetailStatus(detailStatus);
        vo.setErrorMessage(errorMessage);
        return vo;
    }
}