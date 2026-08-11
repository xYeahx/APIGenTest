package com.apigentest.service;

import com.apigentest.dto.ScheduledTaskDTO;
import com.apigentest.vo.ScheduledTaskVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 定时任务：CRUD + 手动触发 + cron 调度扫描
 */
public interface ScheduledTaskService {

    Page<ScheduledTaskVO> list(Long projectId, long page, long size);

    ScheduledTaskVO create(Long projectId, ScheduledTaskDTO dto);

    ScheduledTaskVO update(Long id, ScheduledTaskDTO dto);

    void delete(Long id);

    void updateStatus(Long id, Integer enabled);

    /** 手动立即执行一次（便于测试），返回 executionId */
    Long runNow(Long id);

    /** cron 校验与预览：返回标准化 cron 与下次触发时间 */
    Map<String, Object> cronPreview(String cron);

    /** 定时扫描：由 @Scheduled 调用 */
    void scanDueTasks();
}