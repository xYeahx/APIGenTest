package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("environment")
public class Environment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    private String baseUrl;

    /** 环境变量（JSON） */
    private String variables;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}