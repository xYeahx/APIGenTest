package com.apigentest.service.generation;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存中的生成任务（重启后丢失，论文中可说明；生产可替换为 Redis）
 */
@Data
public class GenerationTask {

    /** PENDING / RUNNING / SUCCESS / PARTIAL_FAILED / FAILED / CONFIRMED */
    private String status;

    private String taskId;

    private Long projectId;

    private String businessDesc;

    private int total;

    private volatile int done;

    private volatile int success;

    private volatile int failed;

    private String error;

    private LocalDateTime createdAt;

    private final List<ApiGenerationResult> results = new CopyOnWriteArrayList<>();

    private final List<ApiGenerationFailure> failures = new CopyOnWriteArrayList<>();
}