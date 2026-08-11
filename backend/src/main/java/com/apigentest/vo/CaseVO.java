package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CaseVO {

    private Long id;

    private Long projectId;

    private Long apiId;

    /** 关联接口路径（列表展示用） */
    private String apiPath;

    /** 关联接口名称（列表展示用） */
    private String apiSummary;

    private String name;

    private String scenarioType;

    private String method;

    private String urlTemplate;

    private String headers;

    private String queryParams;

    private String body;

    private String asserts;

    private Long preCaseId;

    private String extractVars;

    private Integer status;

    private Long creatorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}