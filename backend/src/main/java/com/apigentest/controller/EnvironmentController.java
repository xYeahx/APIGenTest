package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.EnvironmentDTO;
import com.apigentest.entity.Environment;
import com.apigentest.service.EnvironmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping("/projects/{projectId}/environments")
    public Result<List<Environment>> list(@PathVariable Long projectId) {
        return Result.ok(environmentService.listByProject(projectId));
    }

    @PostMapping("/projects/{projectId}/environments")
    public Result<Environment> create(@PathVariable Long projectId, @Valid @RequestBody EnvironmentDTO dto) {
        return Result.ok(environmentService.create(projectId, dto));
    }

    @PutMapping("/environments/{id}")
    public Result<Environment> update(@PathVariable Long id, @Valid @RequestBody EnvironmentDTO dto) {
        return Result.ok(environmentService.update(id, dto));
    }

    @DeleteMapping("/environments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        environmentService.delete(id);
        return Result.ok();
    }
}