package com.apigentest.dto;

import lombok.Data;

@Data
public class CaseQuery {

    private Long apiId;

    private String scenarioType;

    private Integer status;

    private String keyword;
}