package com.apigentest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    /** 超级管理员注册码（选填，匹配 sys_config 中的 super_admin_invite_code 则注册为超级管理员） */
    @Size(max = 64, message = "注册码最长 64 个字符")
    private String inviteCode;
}