package com.apigentest.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchStatusDTO {

    @NotEmpty(message = "id 列表不能为空")
    private List<Long> ids;

    @NotNull(message = "status 不能为空")
    private Integer status;
}