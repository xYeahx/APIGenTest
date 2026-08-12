package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.service.DashboardService;
import com.apigentest.vo.DashboardOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 概览页聚合接口
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public Result<DashboardOverviewVO> overview() {
        return Result.ok(dashboardService.overview());
    }
}