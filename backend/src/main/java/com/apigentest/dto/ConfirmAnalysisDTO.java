package com.apigentest.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 归因确认：可选提交人工修正后的分类（空表示确认 LLM 原分类）
 */
@Data
public class ConfirmAnalysisDTO {

    /** assert_error / data_error / env_error / real_defect */
    @Size(max = 20, message = "分类最长 20 个字符")
    private String category;
}
