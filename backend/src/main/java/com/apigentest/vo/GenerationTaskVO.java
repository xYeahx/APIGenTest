package com.apigentest.vo;

import com.apigentest.service.generation.ApiGenerationFailure;
import com.apigentest.service.generation.ApiGenerationResult;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GenerationTaskVO {

    private String taskId;

    /** PENDING / RUNNING / SUCCESS / PARTIAL_FAILED / FAILED / CONFIRMED */
    private String status;

    private Long projectId;

    private String businessDesc;

    private int total;

    private int done;

    private int success;

    private int failed;

    private String error;

    private LocalDateTime createdAt;

    private List<ApiGenerationResult> results;

    private List<ApiGenerationFailure> failures;
}