package com.apigentest.dto;

import lombok.Data;

import java.util.List;

/**
 * 执行范围：type = caseIds / apiIds / all
 */
@Data
public class ScopeDTO {

    private String type;

    private List<Long> caseIds;

    private List<Long> apiIds;
}