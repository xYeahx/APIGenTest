package com.apigentest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单条用例调试重放：指定执行环境，并可选覆盖请求头/查询参数/请求体/断言
 */
@Data
public class DebugRunDTO {

    @NotNull(message = "environmentId 不能为空")
    private Long environmentId;

    /** 覆盖的请求头（JSON），为空则不覆盖 */
    private String headers;

    /** 覆盖的查询参数（JSON），为空则不覆盖 */
    private String queryParams;

    /** 覆盖的请求体（JSON 字符串），为空则不覆盖 */
    private String body;

    /** 覆盖的断言（JSON 数组），为空则不覆盖 */
    private String asserts;
}