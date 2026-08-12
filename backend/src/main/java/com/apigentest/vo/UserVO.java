package com.apigentest.vo;

import lombok.Data;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatarUrl;

    private String email;

    private String phone;

    /** 1 普通用户 / 2 管理员 / 3 超级管理员 */
    private Integer role;

    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime updatedAt;
}