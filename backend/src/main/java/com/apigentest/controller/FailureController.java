package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.ConfirmAnalysisDTO;
import com.apigentest.service.FailureAnalysisService;
import com.apigentest.vo.FailureAnalysisVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/failures")
public class FailureController {

    private final FailureAnalysisService failureAnalysisService;

    public FailureController(FailureAnalysisService failureAnalysisService) {
        this.failureAnalysisService = failureAnalysisService;
    }

    /** 归因分析：调用 LLM 分析失败明细 */
    @PostMapping("/{detailId}/analyze")
    public Result<FailureAnalysisVO> analyze(@PathVariable Long detailId) {
        return Result.ok(failureAnalysisService.analyze(detailId));
    }

    /** 查询某条明细的归因结果 */
    @GetMapping("/{detailId}")
    public Result<FailureAnalysisVO> get(@PathVariable Long detailId) {
        return Result.ok(failureAnalysisService.getByDetailId(detailId));
    }

    /** 确认归因结果（可携带人工修正后的分类） */
    @PutMapping("/{id}/confirm")
    public Result<FailureAnalysisVO> confirm(@PathVariable Long id,
                                            @RequestBody(required = false) ConfirmAnalysisDTO dto) {
        return Result.ok(failureAnalysisService.confirm(id, dto == null ? null : dto.getCategory()));
    }
}