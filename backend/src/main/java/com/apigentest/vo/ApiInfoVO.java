package com.apigentest.vo;

import lombok.Data;

@Data
public class ApiInfoVO {

    private Long id;

    private Long projectId;

    private String method;

    private String path;

    private String summary;

    private String tags;

    /** 该接口已关联的用例数（列表展示，判断是否已覆盖） */
    private Long caseCount;

    /** 接口详情才返回的原始 OpenAPI 定义 */
    private String spec;
}