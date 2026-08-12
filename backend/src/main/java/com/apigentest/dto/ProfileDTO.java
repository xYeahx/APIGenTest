package com.apigentest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料更新：昵称 / 邮箱 / 联系方式
 */
@Data
public class ProfileDTO {

    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长 100 个字符")
    private String email;

    @Size(max = 50, message = "联系方式最长 50 个字符")
    private String phone;
}