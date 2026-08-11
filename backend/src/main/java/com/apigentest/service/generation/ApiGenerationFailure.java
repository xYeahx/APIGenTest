package com.apigentest.service.generation;

import lombok.Data;

@Data
public class ApiGenerationFailure {

    private Long apiId;

    private String error;
}