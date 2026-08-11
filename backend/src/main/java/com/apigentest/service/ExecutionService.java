package com.apigentest.service;

import com.apigentest.dto.RunRequestDTO;
import com.apigentest.vo.ExecutionDetailVO;
import com.apigentest.vo.ExecutionSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.function.Consumer;

public interface ExecutionService {

    /** 提交执行任务，返回 executionId（异步执行，手动触发） */
    Long run(RunRequestDTO dto);

    /** 系统级触发（定时任务/CI）：跳过用户上下文校验，指定 triggerType 与操作人，完成后回调 */
    Long runBySystem(RunRequestDTO dto, int triggerType, Long operatorId, Consumer<Long> onFinished);

    /** 执行历史（按项目分页） */
    Page<ExecutionSummaryVO> list(Long projectId, long page, long size);

    /** 执行汇总（含进度：passRate / detailCount） */
    ExecutionSummaryVO get(Long id);

    /** 用例明细分页（可按状态筛选） */
    Page<ExecutionDetailVO> details(Long id, long page, long size, Integer status);

    /** 单条明细详情 */
    ExecutionDetailVO detail(Long executionId, Long detailId);
}