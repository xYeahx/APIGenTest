package com.apigentest.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteDTO {

    @NotEmpty(message = "id 列表不能为空")
    private List<Long> ids;
}