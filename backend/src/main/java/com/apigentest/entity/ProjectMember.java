package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目成员表（团队协作）
 */
@Data
@TableName("project_member")
public class ProjectMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long userId;

    /** 1 成员 / 2 只读成员 */
    private Integer role;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}