package com.apigentest.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class GenerateRequestDTO {

    @NotEmpty(message = "请至少选择一个接口")
    private List<Long> apiIds;

    /** 可选业务描述，辅助 LLM 理解业务 */
    private String businessDesc;
}