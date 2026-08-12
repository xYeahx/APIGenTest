package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目成员 VO（owner 以 role=0 形式一并返回）
 */
@Data
public class MemberVO {

    private Long id;

    private Long projectId;

    private Long userId;

    private String username;

    private String nickname;

    private String avatarUrl;

    private String email;

    private String phone;

    /** 0 所有者 / 1 成员 / 2 只读成员 */
    private Integer role;

    private LocalDateTime createdAt;
}