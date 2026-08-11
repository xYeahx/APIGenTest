package com.apigentest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusDTO {

    /** 1 启用 / 0 禁用 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}