package com.apigentest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CaseDTO {

    /** 新建必填 */
    private Long projectId;

    /** 关联接口（手动用例可空） */
    private Long apiId;

    @NotBlank(message = "用例名不能为空")
    @Size(max = 200, message = "用例名最长 200 个字符")
    private String name;

    /** normal / boundary / exception / manual */
    @NotBlank(message = "场景类型不能为空")
    private String scenarioType;

    @NotBlank(message = "请求方法不能为空")
    private String method;

    @NotBlank(message = "请求地址不能为空")
    @Size(max = 500, message = "请求地址最长 500 个字符")
    private String urlTemplate;

    /** 请求头（JSON 字符串） */
    private String headers;

    /** 查询参数（JSON 字符串） */
    private String queryParams;

    /** 请求体（JSON 字符串） */
    private String body;

    /** 断言数组（JSON 字符串） */
    private String asserts;

    private Long preCaseId;

    /** 响应提取变量（JSON 字符串） */
    private String extractVars;

    /** 1 启用 / 0 禁用 */
    private Integer status;
}