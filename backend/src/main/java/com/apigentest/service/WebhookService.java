package com.apigentest.service;

import com.apigentest.entity.Execution;

/**
 * 外部 Webhook 推送（企业微信 / 钉钉 / 通用 HTTP）：
 * 从 sys_config 读取 webhook_url / webhook_enabled，执行完成、定时任务失败、生成完成时推送结果摘要
 */
public interface WebhookService {

    /** 执行完成通知（手动 / 定时 / CI 触发均覆盖） */
    void sendExecutionResult(Execution execution);

    /** 定时任务触发失败通知 */
    void sendTaskFailed(Long taskId, String taskName, Long projectId, String error);

    /** AI 生成任务完成通知 */
    void sendGenerationFinished(Long projectId, String status, int success, int failed, int total);

    /** 发送测试消息（管理员在设置页验证），返回结果描述 */
    String sendTest();
}