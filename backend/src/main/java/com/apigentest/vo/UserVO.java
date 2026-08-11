package com.apigentest.vo;

import lombok.Data;

@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    /** 1 普通用户 / 2 管理员 */
    private Integer role;
}