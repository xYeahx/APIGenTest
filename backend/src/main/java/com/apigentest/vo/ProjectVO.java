package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectVO {

    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    /** 所有者登录名 */
    private String ownerName;

    /** 当前用户在该项目中的角色：0 所有者 / 1 成员 / 2 只读成员（管理员恒为 0） */
    private Integer myRole;

    private LocalDateTime createdAt;

    /** 接口数（详情接口返回） */
    private Long apiCount;

    /** 用例数（详情接口返回） */
    private Long caseCount;

    /** 环境数（详情接口返回） */
    private Long envCount;
}