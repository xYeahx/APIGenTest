package com.apigentest.dto;

import lombok.Data;

@Data
public class ResetPasswordDTO {

    /** 新密码（可空，为空则重置为默认密码 123456） */
    private String password;
}