package com.apigentest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信通知表
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 通知类型：execution（执行完成）等 */
    private String type;

    private String title;

    private String content;

    /** 关联执行记录（可选） */
    private Long executionId;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}