package com.apigentest.dto;

import lombok.Data;

/**
 * 角色变更：仅超级管理员可用，目标角色 1 普通用户 / 2 管理员
 */
@Data
public class RoleDTO {

    private Integer role;
}