package com.apigentest.service;

import com.apigentest.vo.ReportVO;
import com.apigentest.vo.TrendVO;

public interface ReportService {

    /** 单次执行报告（汇总 + 失败 TOP + 错误聚合） */
    ReportVO getReport(Long executionId);

    /** 项目历史执行趋势（通过率 / 耗时，最近 limit 次，默认 20） */
    TrendVO getTrend(Long projectId, Integer limit);
}