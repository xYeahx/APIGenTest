package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.Roles;
import com.apigentest.common.UserContext;
import com.apigentest.entity.ApiInfo;
import com.apigentest.entity.FailureAnalysis;
import com.apigentest.entity.GenerationRecord;
import com.apigentest.entity.Project;
import com.apigentest.mapper.ApiInfoMapper;
import com.apigentest.mapper.FailureAnalysisMapper;
import com.apigentest.mapper.GenerationRecordMapper;
import com.apigentest.mapper.ProjectMapper;
import com.apigentest.mapper.StatsMapper;
import com.apigentest.service.ProjectService;
import com.apigentest.service.StatsService;
import com.apigentest.vo.AttributionAccuracyVO;
import com.apigentest.vo.AttributionSampleVO;
import com.apigentest.vo.CategoryAccuracy;
import com.apigentest.vo.GenerationQualityVO;
import com.apigentest.vo.GenerationRecordVO;
import com.apigentest.vo.QualityMetric;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * P2 实验统计实现：基于 generation_record / test_case(source=2) / failure_analysis 聚合
 */
@Service
public class StatsServiceImpl implements StatsService {

    private static final int RECENT_SAMPLE_SIZE = 10;
    private static final List<String> SCENARIOS = List.of("normal", "boundary", "exception");

    private final StatsMapper statsMapper;
    private final GenerationRecordMapper generationRecordMapper;
    private final FailureAnalysisMapper failureAnalysisMapper;
    private final ProjectMapper projectMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public StatsServiceImpl(StatsMapper statsMapper, GenerationRecordMapper generationRecordMapper,
                            FailureAnalysisMapper failureAnalysisMapper, ProjectMapper projectMapper,
                            ApiInfoMapper apiInfoMapper, ProjectService projectService,
                            ObjectMapper objectMapper) {
        this.statsMapper = statsMapper;
        this.generationRecordMapper = generationRecordMapper;
        this.failureAnalysisMapper = failureAnalysisMapper;
        this.projectMapper = projectMapper;
        this.apiInfoMapper = apiInfoMapper;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
    }

    @Override
    public GenerationQualityVO generationQuality(Long projectId) {
        requireStatsAccess(projectId);
        List<GenerationRecord> records = selectRecords(projectId);
        Map<String, Map<String, Object>> execByScenario = toMap(statsMapper.aiCaseExecByScenario(projectId));
        Map<String, Map<String, Object>> execByModel = toMap(statsMapper.aiCaseExecByModel(projectId));
        Map<String, Map<String, Object>> execByPrompt = toMap(statsMapper.aiCaseExecByPrompt(projectId));
        Map<String, Object> overallExec = statsMapper.aiCaseExecOverall(projectId);

        GenerationQualityVO vo = new GenerationQualityVO();
        vo.setOverall(buildOverall(records, overallExec));
        vo.setByScenario(buildByScenario(records, execByScenario));
        vo.setByModel(buildByRecordGroup(records, execByModel, GenerationRecord::getModel, "(unknown)"));
        vo.setByPrompt(buildByRecordGroup(records, execByPrompt, GenerationRecord::getPromptVersion, "(unknown)"));
        return vo;
    }

    @Override
    public AttributionAccuracyVO attributionAccuracy(Long projectId) {
        requireStatsAccess(projectId);
        long totalAnalyzed = statsMapper.countAnalyzed(projectId);
        long totalConfirmed = statsMapper.countConfirmed(projectId);
        long correct = statsMapper.countCorrect(projectId);
        long corrected = totalConfirmed - correct;
        List<Map<String, Object>> byCat = statsMapper.attributionByCategory(projectId);
        List<Map<String, Object>> samples = statsMapper.recentConfirmedSamples(projectId, RECENT_SAMPLE_SIZE);

        AttributionAccuracyVO vo = new AttributionAccuracyVO();
        vo.setTotalAnalyzed((int) totalAnalyzed);
        vo.setTotalConfirmed((int) totalConfirmed);
        vo.setCorrect((int) correct);
        vo.setCorrected((int) corrected);
        vo.setAccuracy(pct((int) correct, (int) (correct + corrected)));
        vo.setByCategory(buildCategoryAccuracy(byCat));
        vo.setRecentSamples(buildSamples(samples));
        return vo;
    }

    @Override
    public Page<GenerationRecordVO> generationRecords(Long projectId, long page, long size) {
        requireStatsAccess(projectId);
        LambdaQueryWrapper<GenerationRecord> wrapper = new LambdaQueryWrapper<GenerationRecord>()
                .eq(projectId != null, GenerationRecord::getProjectId, projectId)
                .orderByDesc(GenerationRecord::getId);
        Page<GenerationRecord> p = generationRecordMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, String> projectNames = new HashMap<>();
        Map<Long, String> apiNames = new HashMap<>();
        Set<Long> pids = p.getRecords().stream().map(GenerationRecord::getProjectId).collect(Collectors.toSet());
        Set<Long> aids = p.getRecords().stream().map(GenerationRecord::getApiId).collect(Collectors.toSet());
        if (!pids.isEmpty()) {
            projectMapper.selectBatchIds(pids).forEach(x -> projectNames.put(x.getId(), x.getName()));
        }
        if (!aids.isEmpty()) {
            apiInfoMapper.selectBatchIds(aids).forEach(x -> apiNames.put(x.getId(), x.getSummary() == null
                    ? "接口" + x.getId() : x.getSummary()));
        }
        Page<GenerationRecordVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(r -> toVO(r, projectNames, apiNames)).toList());
        return voPage;
    }

    // ---------- 私有方法 ----------

    private List<GenerationRecord> selectRecords(Long projectId) {
        return generationRecordMapper.selectList(new LambdaQueryWrapper<GenerationRecord>()
                .eq(projectId != null, GenerationRecord::getProjectId, projectId));
    }

    private QualityMetric buildOverall(List<GenerationRecord> records, Map<String, Object> exec) {
        QualityMetric m = new QualityMetric();
        m.setGroup("ALL");
        m.setGenerated(sum(records, GenerationRecord::getGeneratedCount));
        m.setConfirmed(sum(records, GenerationRecord::getConfirmedCount));
        applyExec(m, exec);
        fillRates(m);
        return m;
    }

    private List<QualityMetric> buildByScenario(List<GenerationRecord> records,
                                                Map<String, Map<String, Object>> execByScenario) {
        List<QualityMetric> list = new ArrayList<>();
        for (String sc : SCENARIOS) {
            QualityMetric m = new QualityMetric();
            m.setGroup(sc);
            int gen = 0;
            int cfm = 0;
            for (GenerationRecord r : records) {
                gen += scenarioCount(r.getScenarioGenerated(), sc);
                cfm += scenarioCount(r.getScenarioConfirmed(), sc);
            }
            m.setGenerated(gen);
            m.setConfirmed(cfm);
            applyExec(m, execByScenario.get(sc));
            fillRates(m);
            list.add(m);
        }
        return list;
    }

    private List<QualityMetric> buildByRecordGroup(List<GenerationRecord> records,
                                                   Map<String, Map<String, Object>> execByGroup,
                                                   Function<GenerationRecord, String> field,
                                                   String unknown) {
        Map<String, List<GenerationRecord>> groups = records.stream().collect(Collectors.groupingBy(
                r -> field.apply(r) == null ? unknown : field.apply(r), LinkedHashMap::new, Collectors.toList()));
        List<QualityMetric> list = new ArrayList<>();
        for (Map.Entry<String, List<GenerationRecord>> e : groups.entrySet()) {
            QualityMetric m = new QualityMetric();
            m.setGroup(e.getKey());
            m.setGenerated(sum(e.getValue(), GenerationRecord::getGeneratedCount));
            m.setConfirmed(sum(e.getValue(), GenerationRecord::getConfirmedCount));
            applyExec(m, execByGroup.get(e.getKey()));
            fillRates(m);
            list.add(m);
        }
        return list;
    }

    private List<CategoryAccuracy> buildCategoryAccuracy(List<Map<String, Object>> rows) {
        List<CategoryAccuracy> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            CategoryAccuracy c = new CategoryAccuracy();
            c.setCategory(str(row.get("grp")));
            c.setAnalyzed(intOf(row, "analyzed"));
            c.setConfirmed(intOf(row, "confirmed"));
            c.setCorrect(intOf(row, "correct"));
            c.setCorrected(intOf(row, "corrected"));
            c.setAccuracy(pct(c.getCorrect(), c.getCorrect() + c.getCorrected()));
            list.add(c);
        }
        return list;
    }

    private List<AttributionSampleVO> buildSamples(List<Map<String, Object>> rows) {
        List<AttributionSampleVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            AttributionSampleVO s = new AttributionSampleVO();
            s.setId(longOf(row, "id"));
            s.setExecutionDetailId(longOf(row, "detail_id"));
            s.setCategory(str(row.get("category")));
            s.setConfirmedCategory(str(row.get("confirmed_category")));
            s.setCorrected(!java.util.Objects.equals(s.getCategory(), s.getConfirmedCategory()));
            Object at = row.get("confirmed_at");
            if (at != null) {
                try {
                    s.setConfirmedAt(java.time.LocalDateTime.parse(at.toString().replace(' ', 'T')));
                } catch (Exception ignore) {
                    // 忽略时间解析失败
                }
            }
            list.add(s);
        }
        return list;
    }

    private GenerationRecordVO toVO(GenerationRecord r, Map<Long, String> projectNames, Map<Long, String> apiNames) {
        GenerationRecordVO vo = new GenerationRecordVO();
        vo.setId(r.getId());
        vo.setTaskId(r.getTaskId());
        vo.setProjectId(r.getProjectId());
        vo.setProjectName(projectNames.get(r.getProjectId()));
        vo.setApiId(r.getApiId());
        vo.setApiName(apiNames.get(r.getApiId()));
        vo.setModel(r.getModel());
        vo.setTemperature(r.getTemperature());
        vo.setPromptVersion(r.getPromptVersion());
        vo.setMaxRetry(r.getMaxRetry());
        vo.setRetryUsed(r.getRetryUsed());
        vo.setBusinessDesc(r.getBusinessDesc());
        vo.setGeneratedCount(r.getGeneratedCount());
        vo.setConfirmedCount(r.getConfirmedCount());
        vo.setScenarioGenerated(r.getScenarioGenerated());
        vo.setScenarioConfirmed(r.getScenarioConfirmed());
        vo.setStatus(r.getStatus());
        vo.setError(r.getError());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setConfirmedAt(r.getConfirmedAt());
        return vo;
    }

    private void requireStatsAccess(Long projectId) {
        if (projectId == null) {
            if (!Roles.isAdmin(UserContext.getRole())) {
                throw new BusinessException(403, "仅管理员可查看全局实验统计");
            }
        } else {
            projectService.requireRead(projectId);
        }
    }

    private void applyExec(QualityMetric m, Map<String, Object> exec) {
        if (exec == null) {
            return;
        }
        m.setExecuted(intOf(exec, "executed"));
        m.setPassed(intOf(exec, "passed"));
        m.setExecutable(intOf(exec, "executable"));
    }

    private void fillRates(QualityMetric m) {
        m.setValidRate(pct(m.getConfirmed(), m.getGenerated()));
        m.setExecutableRate(pct(m.getExecutable(), m.getExecuted()));
        m.setPassRate(pct(m.getPassed(), m.getExecuted()));
    }

    private int scenarioCount(String json, String scenario) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            Map<String, Integer> map = objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
            return map.getOrDefault(scenario, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private int sum(List<GenerationRecord> records, Function<GenerationRecord, Integer> getter) {
        return records.stream().mapToInt(r -> getter.apply(r) == null ? 0 : getter.apply(r)).sum();
    }

    private Map<String, Map<String, Object>> toMap(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(str(row.get("grp")), row);
        }
        return map;
    }

    private double pct(int part, int total) {
        return total <= 0 ? 0 : Math.round(part * 10000.0 / total) / 100.0;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private int intOf(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long longOf(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
