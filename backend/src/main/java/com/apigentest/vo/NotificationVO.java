package com.apigentest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信通知视图对象
 */
@Data
public class NotificationVO {

    private Long id;

    private Long userId;

    private String type;

    private String title;

    private String content;

    private Long executionId;

    /** 关联执行所属项目，前端跳转用 */
    private Long projectId;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}