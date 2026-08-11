package com.apigentest.vo;

import com.apigentest.entity.ExecutionDetail;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExecutionDetailVO {

    private Long id;

    private Long executionId;

    private Long caseId;

    /** 用例名（列表展示用） */
    private String caseName;

    private String method;

    private String urlTemplate;

    private Integer status;

    private String requestText;

    private String responseText;

    private String errorMessage;

    private Long durationMs;

    private Integer retryCount;

    private LocalDateTime createdAt;

    public static ExecutionDetailVO from(ExecutionDetail d, String caseName, String method, String urlTemplate) {
        ExecutionDetailVO vo = new ExecutionDetailVO();
        vo.setId(d.getId());
        vo.setExecutionId(d.getExecutionId());
        vo.setCaseId(d.getCaseId());
        vo.setCaseName(caseName);
        vo.setMethod(method);
        vo.setUrlTemplate(urlTemplate);
        vo.setStatus(d.getStatus());
        vo.setRequestText(d.getRequestText());
        vo.setResponseText(d.getResponseText());
        vo.setErrorMessage(d.getErrorMessage());
        vo.setDurationMs(d.getDurationMs());
        vo.setRetryCount(d.getRetryCount());
        vo.setCreatedAt(d.getCreatedAt());
        return vo;
    }
}