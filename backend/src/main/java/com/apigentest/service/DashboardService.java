package com.apigentest.service;

import com.apigentest.vo.DashboardOverviewVO;

/**
 * 概览页聚合数据
 */
public interface DashboardService {

    /** 当前用户可见项目的全局概览数据 */
    DashboardOverviewVO overview();
}