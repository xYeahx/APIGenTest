package com.apigentest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * URL 方式导入 OpenAPI：{ "type": "url", "url": "https://..." }
 */
@Data
public class ImportRequestDTO {

    @NotBlank(message = "文档地址不能为空")
    private String url;
}