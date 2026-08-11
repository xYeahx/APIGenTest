package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectVO {

    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    private LocalDateTime createdAt;

    /** 接口数（详情接口返回） */
    private Long apiCount;

    /** 用例数（详情接口返回） */
    private Long caseCount;

    /** 环境数（详情接口返回） */
    private Long envCount;
}