package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.ScheduledTaskDTO;
import com.apigentest.service.ScheduledTaskService;
import com.apigentest.vo.ScheduledTaskVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /** 定时任务列表（分页） */
    @GetMapping("/projects/{projectId}/tasks")
    public Result<Page<ScheduledTaskVO>> list(@PathVariable Long projectId,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(scheduledTaskService.list(projectId, page, size));
    }

    /** 创建定时任务 */
    @PostMapping("/projects/{projectId}/tasks")
    public Result<ScheduledTaskVO> create(@PathVariable Long projectId,
                                          @Valid @RequestBody ScheduledTaskDTO dto) {
        return Result.ok(scheduledTaskService.create(projectId, dto));
    }

    /** 编辑定时任务 */
    @PutMapping("/tasks/{id}")
    public Result<ScheduledTaskVO> update(@PathVariable Long id,
                                          @Valid @RequestBody ScheduledTaskDTO dto) {
        return Result.ok(scheduledTaskService.update(id, dto));
    }

    /** 启用/停用：body {"enabled": 0/1} */
    @PutMapping("/tasks/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        scheduledTaskService.updateStatus(id, body == null ? null : body.get("enabled"));
        return Result.ok();
    }

    /** 删除任务 */
    @DeleteMapping("/tasks/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduledTaskService.delete(id);
        return Result.ok();
    }

    /** 手动立即执行一次（便于测试） */
    @PostMapping("/tasks/{id}/run")
    public Result<Map<String, Long>> runNow(@PathVariable Long id) {
        return Result.ok(Map.of("executionId", scheduledTaskService.runNow(id)));
    }

    /** cron 校验与预览：body {"cron": "..."} */
    @PostMapping("/tasks/cron-preview")
    public Result<Map<String, Object>> cronPreview(@RequestBody Map<String, String> body) {
        return Result.ok(scheduledTaskService.cronPreview(body == null ? null : body.get("cron")));
    }
}