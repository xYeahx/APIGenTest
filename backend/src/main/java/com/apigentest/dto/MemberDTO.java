package com.apigentest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 项目成员操作 DTO（邀请 / 修改角色）
 */
@Data
public class MemberDTO {

    /** 被邀请用户登录名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 1 成员 / 2 只读成员 */
    @NotNull(message = "角色不能为空")
    private Integer role;
}