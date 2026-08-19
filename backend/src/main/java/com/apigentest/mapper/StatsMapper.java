package com.apigentest.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * P2 实验统计聚合查询（生成质量 / 归因准确率）
 */
@Mapper
public interface StatsMapper {

    /** AI 生成用例执行情况（整体） */
    @Select("<script>"
            + "SELECT COUNT(*) AS executed, "
            + "COALESCE(SUM(CASE WHEN d.status = 1 THEN 1 ELSE 0 END), 0) AS passed, "
            + "COALESCE(SUM(CASE WHEN d.status != 3 THEN 1 ELSE 0 END), 0) AS executable "
            + "FROM execution_detail d JOIN test_case tc ON tc.id = d.case_id "
            + "WHERE tc.source = 2 "
            + "<if test='projectId != null'> AND tc.project_id = #{projectId} </if>"
            + "</script>")
    Map<String, Object> aiCaseExecOverall(@Param("projectId") Long projectId);

    /** AI 生成用例执行情况（按场景类型） */
    @Select("<script>"
            + "SELECT tc.scenario_type AS grp, COUNT(*) AS executed, "
            + "COALESCE(SUM(CASE WHEN d.status = 1 THEN 1 ELSE 0 END), 0) AS passed, "
            + "COALESCE(SUM(CASE WHEN d.status != 3 THEN 1 ELSE 0 END), 0) AS executable "
            + "FROM execution_detail d JOIN test_case tc ON tc.id = d.case_id "
            + "WHERE tc.source = 2 "
            + "<if test='projectId != null'> AND tc.project_id = #{projectId} </if>"
            + "GROUP BY tc.scenario_type"
            + "</script>")
    List<Map<String, Object>> aiCaseExecByScenario(@Param("projectId") Long projectId);

    /** AI 生成用例执行情况（按生成模型） */
    @Select("<script>"
            + "SELECT COALESCE(tc.gen_model, '(unknown)') AS grp, COUNT(*) AS executed, "
            + "COALESCE(SUM(CASE WHEN d.status = 1 THEN 1 ELSE 0 END), 0) AS passed, "
            + "COALESCE(SUM(CASE WHEN d.status != 3 THEN 1 ELSE 0 END), 0) AS executable "
            + "FROM execution_detail d JOIN test_case tc ON tc.id = d.case_id "
            + "WHERE tc.source = 2 AND tc.gen_model IS NOT NULL "
            + "<if test='projectId != null'> AND tc.project_id = #{projectId} </if>"
            + "GROUP BY tc.gen_model"
            + "</script>")
    List<Map<String, Object>> aiCaseExecByModel(@Param("projectId") Long projectId);

    /** AI 生成用例执行情况（按 Prompt 版本） */
    @Select("<script>"
            + "SELECT COALESCE(tc.gen_prompt_version, '(unknown)') AS grp, COUNT(*) AS executed, "
            + "COALESCE(SUM(CASE WHEN d.status = 1 THEN 1 ELSE 0 END), 0) AS passed, "
            + "COALESCE(SUM(CASE WHEN d.status != 3 THEN 1 ELSE 0 END), 0) AS executable "
            + "FROM execution_detail d JOIN test_case tc ON tc.id = d.case_id "
            + "WHERE tc.source = 2 AND tc.gen_prompt_version IS NOT NULL "
            + "<if test='projectId != null'> AND tc.project_id = #{projectId} </if>"
            + "GROUP BY tc.gen_prompt_version"
            + "</script>")
    List<Map<String, Object>> aiCaseExecByPrompt(@Param("projectId") Long projectId);

    /** 归因分析总数 */
    @Select("<script>"
            + "SELECT COUNT(*) AS total FROM failure_analysis fa "
            + "JOIN execution_detail d ON d.id = fa.execution_detail_id "
            + "JOIN execution e ON e.id = d.execution_id "
            + "<if test='projectId != null'> WHERE e.project_id = #{projectId} </if>"
            + "</script>")
    long countAnalyzed(@Param("projectId") Long projectId);

    /** 已人工确认数 */
    @Select("<script>"
            + "SELECT COUNT(*) AS total FROM failure_analysis fa "
            + "JOIN execution_detail d ON d.id = fa.execution_detail_id "
            + "JOIN execution e ON e.id = d.execution_id "
            + "WHERE fa.confirmed = 1 "
            + "<if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + "</script>")
    long countConfirmed(@Param("projectId") Long projectId);

    /** 确认正确数（人工确认分类与 LLM 分类一致） */
    @Select("<script>"
            + "SELECT COUNT(*) AS total FROM failure_analysis fa "
            + "JOIN execution_detail d ON d.id = fa.execution_detail_id "
            + "JOIN execution e ON e.id = d.execution_id "
            + "WHERE fa.confirmed = 1 AND fa.confirmed_category IS NOT NULL "
            + "AND fa.confirmed_category = fa.category "
            + "<if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + "</script>")
    long countCorrect(@Param("projectId") Long projectId);

    /** 按分类统计归因准确率 */
    @Select("<script>"
            + "SELECT fa.category AS grp, COUNT(*) AS analyzed, "
            + "COALESCE(SUM(CASE WHEN fa.confirmed = 1 THEN 1 ELSE 0 END), 0) AS confirmed, "
            + "COALESCE(SUM(CASE WHEN fa.confirmed = 1 AND fa.confirmed_category IS NOT NULL AND fa.confirmed_category = fa.category THEN 1 ELSE 0 END), 0) AS correct, "
            + "COALESCE(SUM(CASE WHEN fa.confirmed = 1 AND fa.confirmed_category IS NOT NULL AND fa.confirmed_category != fa.category THEN 1 ELSE 0 END), 0) AS corrected "
            + "FROM failure_analysis fa "
            + "JOIN execution_detail d ON d.id = fa.execution_detail_id "
            + "JOIN execution e ON e.id = d.execution_id "
            + "<if test='projectId != null'> WHERE e.project_id = #{projectId} </if>"
            + "GROUP BY fa.category"
            + "</script>")
    List<Map<String, Object>> attributionByCategory(@Param("projectId") Long projectId);

    /** 最近人工确认样本（含修正记录） */
    @Select("<script>"
            + "SELECT fa.id, fa.execution_detail_id AS detail_id, fa.category, fa.confirmed_category, fa.confirmed_at "
            + "FROM failure_analysis fa "
            + "JOIN execution_detail d ON d.id = fa.execution_detail_id "
            + "JOIN execution e ON e.id = d.execution_id "
            + "WHERE fa.confirmed = 1 AND fa.confirmed_category IS NOT NULL "
            + "<if test='projectId != null'> AND e.project_id = #{projectId} </if>"
            + "ORDER BY fa.confirmed_at DESC, fa.id DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> recentConfirmedSamples(@Param("projectId") Long projectId, @Param("limit") int limit);
}
