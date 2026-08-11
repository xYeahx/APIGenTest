package com.apigentest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfigValueDTO {

    @NotNull(message = "value 不能为空")
    private String value;
}