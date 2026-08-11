package com.apigentest.service.generation;

import lombok.Data;

import java.util.List;

@Data
public class ApiGenerationResult {

    private Long apiId;

    private List<GeneratedCase> cases;
}