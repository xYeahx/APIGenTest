package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAdminVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatarUrl;

    private String email;

    private String phone;

    /** 1 普通用户 / 2 管理员 */
    private Integer role;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    private LocalDateTime createdAt;
}