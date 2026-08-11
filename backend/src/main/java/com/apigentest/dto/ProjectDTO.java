package com.apigentest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectDTO {

    @NotBlank(message = "项目名不能为空")
    @Size(max = 100, message = "项目名最长 100 个字符")
    private String name;

    @Size(max = 500, message = "项目描述最长 500 个字符")
    private String description;
}