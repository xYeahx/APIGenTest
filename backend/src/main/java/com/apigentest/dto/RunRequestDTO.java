package com.apigentest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RunRequestDTO {

    @NotNull(message = "projectId 不能为空")
    private Long projectId;

    /** 环境不校验非空：先走权限校验（只读成员应返回 403），环境缺失由服务层校验 */
    private Long environmentId;

    @NotNull(message = "scope 不能为空")
    private ScopeDTO scope;
}