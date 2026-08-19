-- =====================================================================
-- APIGenTest 旧版 AI 生成数据回填脚本
-- 背景：2026-08-09 之前 AI 生成用例在「确认入库」时未写入 source=2、
--       gen_* 元数据与 generation_record（该逻辑随 P2 统计功能落地补齐），
--       导致「生成质量评估」统计不到这批真实 AI 生成用例。
-- 本脚本仅处理项目 19 中 2026-08-09 22:46:35 批量入库的 22 条旧版 AI 用例。
-- 依赖：先执行 05_p2_stats.sql（generation_record 表）
-- 幂等：可重复执行，不会产生重复记录
-- =====================================================================

USE apigentest;

-- 1) 为旧版 AI 生成用例补 generation_record（每接口一条，含场景分布与确认数）
INSERT INTO generation_record
    (task_id, project_id, api_id, model, temperature, prompt_version,
     max_retry, retry_used, business_desc, generated_count, confirmed_count,
     scenario_generated, scenario_confirmed, status, created_by, created_at, confirmed_at)
SELECT
    CONCAT('LEGACY-API-', api_id)            AS task_id,
    19                                       AS project_id,
    api_id,
    'deepseek-v4-flash'                      AS model,
    0.30                                     AS temperature,
    'v1'                                     AS prompt_version,
    2                                        AS max_retry,
    0                                        AS retry_used,
    NULL                                     AS business_desc,
    COUNT(*)                                 AS generated_count,
    COUNT(*)                                 AS confirmed_count,
    CONCAT('{"normal":', SUM(scenario_type = 'normal'),
           ',"boundary":', SUM(scenario_type = 'boundary'),
           ',"exception":', SUM(scenario_type = 'exception'), '}') AS scenario_generated,
    CONCAT('{"normal":', SUM(scenario_type = 'normal'),
           ',"boundary":', SUM(scenario_type = 'boundary'),
           ',"exception":', SUM(scenario_type = 'exception'), '}') AS scenario_confirmed,
    'SUCCESS'                                AS status,
    1                                        AS created_by,
    MIN(created_at)                          AS created_at,
    MIN(created_at)                          AS confirmed_at
FROM test_case
WHERE project_id = 19
  AND created_at = '2026-08-09 22:46:35'
  AND NOT EXISTS (
      SELECT 1 FROM generation_record gr
      WHERE gr.project_id = test_case.project_id
        AND gr.api_id = test_case.api_id
        AND gr.task_id = CONCAT('LEGACY-API-', test_case.api_id)
  )
GROUP BY api_id;

-- 2) 回填用例自身的 AI 生成元数据
UPDATE test_case
SET source = 2,
    gen_task_id = CONCAT('LEGACY-API-', api_id),
    gen_model = 'deepseek-v4-flash',
    gen_temperature = 0.30,
    gen_prompt_version = 'v1',
    gen_retry_count = 0
WHERE project_id = 19
  AND source = 1
  AND gen_task_id IS NULL
  AND created_at = '2026-08-09 22:46:35';