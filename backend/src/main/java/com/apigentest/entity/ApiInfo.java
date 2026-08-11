package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_info")
public class ApiInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String method;

    private String path;

    private String summary;

    private String description;

    private String tags;

    /** 原始 OpenAPI 定义（JSON），供 LLM 生成使用 */
    private String spec;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}