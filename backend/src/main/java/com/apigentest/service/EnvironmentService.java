package com.apigentest.service;

import com.apigentest.dto.EnvironmentDTO;
import com.apigentest.entity.Environment;

import java.util.List;

public interface EnvironmentService {

    List<Environment> listByProject(Long projectId);

    Environment create(Long projectId, EnvironmentDTO dto);

    Environment update(Long id, EnvironmentDTO dto);

    void delete(Long id);
}