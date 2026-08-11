package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.GenerateRequestDTO;
import com.apigentest.service.GenerationService;
import com.apigentest.vo.ConfirmResultVO;
import com.apigentest.vo.GenerationTaskVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GenerationController {

    private final GenerationService generationService;

    public GenerationController(GenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/apis/generate")
    public Result<Map<String, String>> generate(@Valid @RequestBody GenerateRequestDTO dto) {
        String taskId = generationService.submit(dto.getApiIds(), dto.getBusinessDesc());
        return Result.ok(Map.of("taskId", taskId));
    }

    @GetMapping("/generations/{taskId}")
    public Result<GenerationTaskVO> get(@PathVariable String taskId) {
        return Result.ok(generationService.get(taskId));
    }

    @PostMapping("/generations/{taskId}/confirm")
    public Result<ConfirmResultVO> confirm(@PathVariable String taskId) {
        return Result.ok(generationService.confirm(taskId));
    }
}