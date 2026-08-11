package com.apigentest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RunRequestDTO {

    @NotNull(message = "projectId 不能为空")
    private Long projectId;

    @NotNull(message = "environmentId 不能为空")
    private Long environmentId;

    @NotNull(message = "scope 不能为空")
    private ScopeDTO scope;
}