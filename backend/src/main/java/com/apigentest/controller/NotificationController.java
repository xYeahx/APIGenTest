package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.service.NotificationService;
import com.apigentest.vo.NotificationVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的通知（分页，倒序） */
    @GetMapping("/notifications")
    public Result<Page<NotificationVO>> list(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(notificationService.listMine(page, size));
    }

    /** 未读数 */
    @GetMapping("/notifications/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount()));
    }

    /** 标记单条已读 */
    @PutMapping("/notifications/read/{id}")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/notifications/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead();
        return Result.ok();
    }
}