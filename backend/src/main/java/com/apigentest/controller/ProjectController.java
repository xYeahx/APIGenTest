package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.ProjectDTO;
import com.apigentest.service.ProjectService;
import com.apigentest.vo.ProjectVO;
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
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<List<ProjectVO>> list() {
        return Result.ok(projectService.listMyProjects());
    }

    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectDTO dto) {
        return Result.ok(projectService.createProject(dto));
    }

    @GetMapping("/{id}")
    public Result<ProjectVO> detail(@PathVariable Long id) {
        return Result.ok(projectService.getProject(id));
    }

    @PutMapping("/{id}")
    public Result<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectDTO dto) {
        return Result.ok(projectService.updateProject(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.ok();
    }
}