package com.apigentest.service;

import com.apigentest.vo.AttributionAccuracyVO;
import com.apigentest.vo.GenerationQualityVO;
import com.apigentest.vo.GenerationRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * P2 实验统计：生成质量（P2-1）/ 生成参数埋点查询（P2-2）/ 归因准确率（P2-4）
 */
public interface StatsService {

    /** P2-1 生成质量统计（projectId 为空 = 全局统计，需管理员） */
    GenerationQualityVO generationQuality(Long projectId);

    /** P2-4 归因准确率统计 */
    AttributionAccuracyVO attributionAccuracy(Long projectId);

    /** P2-2 生成记录分页查询（埋点数据） */
    Page<GenerationRecordVO> generationRecords(Long projectId, long page, long size);
}
