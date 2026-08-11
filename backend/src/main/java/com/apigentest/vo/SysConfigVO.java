package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysConfigVO {

    private String configKey;

    /** 敏感项（API Key）脱敏展示 */
    private String configValue;

    private Integer isSecret;

    private LocalDateTime updatedAt;
}