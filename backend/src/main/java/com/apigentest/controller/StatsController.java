package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.service.StatsService;
import com.apigentest.vo.AttributionAccuracyVO;
import com.apigentest.vo.GenerationQualityVO;
import com.apigentest.vo.GenerationRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P2 实验统计：生成质量（P2-1）/ 生成记录（P2-2）/ 归因准确率（P2-4）
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/generation-quality")
    public Result<GenerationQualityVO> generationQuality(@RequestParam(required = false) Long projectId) {
        return Result.ok(statsService.generationQuality(projectId));
    }

    @GetMapping("/attribution-accuracy")
    public Result<AttributionAccuracyVO> attributionAccuracy(@RequestParam(required = false) Long projectId) {
        return Result.ok(statsService.attributionAccuracy(projectId));
    }

    @GetMapping("/generation-records")
    public Result<Page<GenerationRecordVO>> generationRecords(@RequestParam(required = false) Long projectId,
                                                              @RequestParam(defaultValue = "1") long page,
                                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(statsService.generationRecords(projectId, page, size));
    }
}
