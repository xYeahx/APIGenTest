package com.apigentest.service.impl;
import com.apigentest.common.ErrorCode;

import com.apigentest.common.BusinessException;
import com.apigentest.dto.EnvironmentDTO;
import com.apigentest.entity.Environment;
import com.apigentest.mapper.EnvironmentMapper;
import com.apigentest.service.EnvironmentService;
import com.apigentest.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnvironmentServiceImpl implements EnvironmentService {

    private final EnvironmentMapper environmentMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public EnvironmentServiceImpl(EnvironmentMapper environmentMapper, ProjectService projectService, ObjectMapper objectMapper) {
        this.environmentMapper = environmentMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Environment> listByProject(Long projectId) {
        projectService.requireRead(projectId);
        return environmentMapper.selectList(
                new LambdaQueryWrapper<Environment>()
                        .eq(Environment::getProjectId, projectId)
                        .orderByDesc(Environment::getId));
    }

    @Override
    public Environment create(Long projectId, EnvironmentDTO dto) {
        projectService.requireWrite(projectId);
        validateVariables(dto.getVariables());
        Environment env = new Environment();
        env.setProjectId(projectId);
        env.setName(dto.getName());
        env.setBaseUrl(dto.getBaseUrl());
        env.setVariables(dto.getVariables());
        environmentMapper.insert(env);
        return env;
    }

    @Override
    public Environment update(Long id, EnvironmentDTO dto) {
        Environment env = getOwnedEnvironment(id);
        validateVariables(dto.getVariables());
        env.setName(dto.getName());
        env.setBaseUrl(dto.getBaseUrl());
        env.setVariables(dto.getVariables());
        environmentMapper.updateById(env);
        return env;
    }

    @Override
    public void delete(Long id) {
        getOwnedEnvironment(id);
        environmentMapper.deleteById(id);
    }

    private Environment getOwnedEnvironment(Long id) {
        Environment env = environmentMapper.selectById(id);
        if (env == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "环境不存在");
        }
        projectService.requireWrite(env.getProjectId());
        return env;
    }

    private void validateVariables(String variables) {
        if (variables == null || variables.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(variables);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "环境变量必须是合法的 JSON");
        }
    }
}
