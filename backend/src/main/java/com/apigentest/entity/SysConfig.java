package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String configValue;

    /** 1 加密存储（API Key） */
    private Integer isSecret;

    private LocalDateTime updatedAt;
}