package com.apigentest.vo;

import lombok.Data;

/**
 * 生成质量指标（P2-1）：可同时表达整体 / 场景类型 / 模型 / Prompt 版本分组
 */
@Data
public class QualityMetric {

    /** 分组标识：ALL / normal / boundary / exception / 模型名 / Prompt 版本 */
    private String group;

    /** 生成（校验通过）用例数 */
    private int generated;

    /** 确认入库用例数 */
    private int confirmed;

    /** 有效率 = confirmed / generated（%） */
    private double validRate;

    /** 已执行明细数 */
    private int executed;

    /** 通过数 */
    private int passed;

    /** 可执行数（无请求异常） */
    private int executable;

    /** 可执行率 = executable / executed（%） */
    private double executableRate;

    /** 断言通过率 = passed / executed（%） */
    private double passRate;
}
