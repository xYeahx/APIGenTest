package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 加密存储，不对外序列化 */
    @JsonIgnore
    private String password;

    private String nickname;

    /** 1 普通用户 / 2 管理员 */
    private Integer role;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}