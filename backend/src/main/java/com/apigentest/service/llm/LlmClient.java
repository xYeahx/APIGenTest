package com.apigentest.service.llm;

import com.apigentest.common.ErrorCode;
import com.apigentest.common.LlmCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的 Chat Completions 客户端
 * （通义千问 / DeepSeek 等国内大模型均提供该兼容接口）
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LlmClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(90_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 发起对话补全请求，返回模型输出文本
     */
    public String chat(String systemPrompt, String userContent, String apiKey, String baseUrl, String model,
                        double temperature) {
        String url = baseUrl.endsWith("/chat/completions")
                ? baseUrl
                : baseUrl + "/chat/completions";
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)));
        try {
            String json = objectMapper.writeValueAsString(body);
            ResponseEntity<String> response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toEntity(String.class);
            return extractContent(response.getBody());
        } catch (LlmCallException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage());
            if (isTimeout(e)) {
                throw new LlmCallException(ErrorCode.LLM_TIMEOUT, "LLM 调用超时：" + e.getMessage(), e);
            }
            throw new LlmCallException(ErrorCode.LLM_CALL_FAILED, "LLM 调用失败：" + e.getMessage(), e);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText(null);
            if (content == null) {
                throw new LlmCallException(ErrorCode.LLM_VALIDATE_FAILED, "LLM 响应缺少 content 字段");
            }
            return content;
        } catch (LlmCallException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmCallException(ErrorCode.LLM_VALIDATE_FAILED, "LLM 响应解析失败", e);
        }
    }

    private boolean isTimeout(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}