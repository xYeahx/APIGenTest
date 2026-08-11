package com.apigentest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EnvironmentDTO {

    @NotBlank(message = "环境名不能为空")
    @Size(max = 50, message = "环境名最长 50 个字符")
    private String name;

    @Size(max = 255, message = "Base URL 最长 255 个字符")
    private String baseUrl;

    /** 环境变量（JSON 字符串） */
    private String variables;
}