package com.apigentest.service;

import com.apigentest.vo.FailureAnalysisVO;

public interface FailureAnalysisService {

    /** 调用 LLM 对失败明细进行归因分析（已存在则覆盖更新） */
    FailureAnalysisVO analyze(Long detailId);

    /** 查询某条明细的归因结果（未分析过返回 null） */
    FailureAnalysisVO getByDetailId(Long detailId);

    /** 确认归因结果（标记已确认） */
    FailureAnalysisVO confirm(Long id);
}