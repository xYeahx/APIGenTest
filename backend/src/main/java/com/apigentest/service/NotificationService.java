package com.apigentest.service;

import com.apigentest.vo.NotificationVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 站内信通知
 */
public interface NotificationService {

    /** 给指定用户发送一条通知 */
    void notify(Long userId, String type, String title, String content, Long executionId);

    Page<NotificationVO> listMine(long page, long size);

    long unreadCount();

    void markRead(Long id);

    void markAllRead();
}