package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.entity.Execution;
import com.apigentest.entity.SysConfig;
import com.apigentest.mapper.SysConfigMapper;
import com.apigentest.service.WebhookService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook 推送实现：配置项 webhook_url / webhook_enabled 存于 sys_config（管理员维护）。
 * 推送失败只记日志，不影响主流程。
 */
@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);
    private static final int TIMEOUT_MS = 5000;

    private final SysConfigMapper sysConfigMapper;
    private final RestClient restClient;

    public WebhookServiceImpl(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    private String config(String key) {
        SysConfig c = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return c == null ? null : c.getConfigValue();
    }

    private boolean enabled() {
        return "1".equals(config("webhook_enabled"));
    }

    private void post(String event, Map<String, Object> payload) {
        String url = config("webhook_url");
        if (!enabled() || url == null || url.isBlank()) {
            return;
        }
        Map<String, Object> body = new HashMap<>(payload);
        body.put("event", event);
        body.put("timestamp", LocalDateTime.now().toString());
        try {
            restClient.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook 发送成功 event={} url={}", event, url);
        } catch (Exception e) {
            log.warn("Webhook 发送失败 event={} url={} err={}", event, url, e.getMessage());
        }
    }

    @Override
    public void sendExecutionResult(Execution e) {
        if (e == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", e.getId());
        payload.put("projectId", e.getProjectId());
        payload.put("triggerType", e.getTriggerType());
        payload.put("status", e.getStatus());
        payload.put("totalCases", e.getTotalCases());
        payload.put("passed", e.getPassed());
        payload.put("failed", e.getFailed());
        double rate = (e.getTotalCases() == null || e.getTotalCases() == 0) ? 0.0
                : Math.round(e.getPassed() * 1000.0 / e.getTotalCases()) / 10.0;
        payload.put("passRate", rate);
        payload.put("durationMs", e.getDurationMs());
        payload.put("startedAt", e.getStartedAt());
        payload.put("finishedAt", e.getFinishedAt());
        post("execution_finished", payload);
    }

    @Override
    public void sendTaskFailed(Long taskId, String taskName, Long projectId, String error) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("taskName", taskName);
        payload.put("projectId", projectId);
        payload.put("error", error);
        post("scheduled_task_failed", payload);
    }

    @Override
    public void sendGenerationFinished(Long projectId, String status, int success, int failed, int total) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("projectId", projectId);
        payload.put("status", status);
        payload.put("success", success);
        payload.put("failed", failed);
        payload.put("total", total);
        post("generation_finished", payload);
    }

    @Override
    public String sendTest() {
        String url = config("webhook_url");
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Webhook 地址未配置，请先保存 Webhook URL");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("event", "test");
        body.put("message", "APIGenTest Webhook 配置测试");
        body.put("timestamp", LocalDateTime.now().toString());
        try {
            restClient.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return "发送成功";
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "发送失败：" + e.getMessage());
        }
    }
}