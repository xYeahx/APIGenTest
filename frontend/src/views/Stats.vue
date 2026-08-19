<template>
  <div class="stats-page" v-loading="loading">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="projectId"
          placeholder="全部项目（全局统计）"
          clearable
          style="width: 280px"
          @change="loadAll"
        >
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" plain @click="loadAll">
          <el-icon style="margin-right: 4px"><Refresh /></el-icon>刷新
        </el-button>
      </div>
      <el-alert
        class="hint"
        type="info"
        :closable="false"
        show-icon
        title="P2 论文实验统计：数据来自 AI 生成记录与用例执行/归因确认记录；选择具体项目可查看单项目数据"
      />
    </div>

    <!-- 生成质量 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="section-title">生成质量评估</div>
      </template>
      <el-alert
        v-if="!hasQualityData"
        class="mb-12"
        type="warning"
        :closable="false"
        show-icon
        title="暂无 AI 生成记录：请先在用例管理中通过「AI 生成」生成用例并确认入库，再执行用例，此处才会展示生成质量数据"
      />
      <el-row :gutter="16">
        <el-col :span="6" v-for="k in qualityKpis" :key="k.label">
          <div class="kpi">
            <div class="kpi-value" :style="{ color: k.color }">{{ k.value }}</div>
            <div class="kpi-label">{{ k.label }}</div>
          </div>
        </el-col>
      </el-row>
      <div class="chart-box" ref="chartRef"></div>
      <el-table :data="quality?.byScenario || []" size="small" border class="mt-12">
        <el-table-column label="场景类型" min-width="110">
          <template #default="{ row }">{{ scenarioLabel(row.group) }}</template>
        </el-table-column>
        <el-table-column prop="generated" label="生成数" width="80" align="center" />
        <el-table-column prop="confirmed" label="确认数" width="80" align="center" />
        <el-table-column label="有效率" width="90" align="center">
          <template #default="{ row }">{{ fmtPct(row.validRate) }}</template>
        </el-table-column>
        <el-table-column prop="executed" label="执行数" width="80" align="center" />
        <el-table-column prop="passed" label="通过数" width="80" align="center" />
        <el-table-column label="可执行率" width="90" align="center">
          <template #default="{ row }">{{ fmtPct(row.executableRate) }}</template>
        </el-table-column>
        <el-table-column label="断言通过率" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="rateTag(row.passRate)" size="small">{{ fmtPct(row.passRate) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-row :gutter="16" class="mt-12">
        <el-col :span="12">
          <div class="sub-title">按生成模型</div>
          <el-table :data="quality?.byModel || []" size="small" border>
            <el-table-column prop="group" label="模型" min-width="120" show-overflow-tooltip />
            <el-table-column prop="generated" label="生成" width="70" align="center" />
            <el-table-column prop="confirmed" label="确认" width="70" align="center" />
            <el-table-column label="有效率" width="80" align="center">
              <template #default="{ row }">{{ fmtPct(row.validRate) }}</template>
            </el-table-column>
            <el-table-column label="通过率" width="80" align="center">
              <template #default="{ row }">{{ fmtPct(row.passRate) }}</template>
            </el-table-column>
          </el-table>
        </el-col>
        <el-col :span="12">
          <div class="sub-title">按 Prompt 版本</div>
          <el-table :data="quality?.byPrompt || []" size="small" border>
            <el-table-column prop="group" label="Prompt 版本" min-width="120" show-overflow-tooltip />
            <el-table-column prop="generated" label="生成" width="70" align="center" />
            <el-table-column prop="confirmed" label="确认" width="70" align="center" />
            <el-table-column label="有效率" width="80" align="center">
              <template #default="{ row }">{{ fmtPct(row.validRate) }}</template>
            </el-table-column>
            <el-table-column label="通过率" width="80" align="center">
              <template #default="{ row }">{{ fmtPct(row.passRate) }}</template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 生成参数埋点记录 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="section-title">生成参数埋点记录</div>
      </template>
      <el-table :data="records" size="small" border>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="projectName" label="项目" min-width="110" show-overflow-tooltip />
        <el-table-column prop="apiName" label="接口" min-width="130" show-overflow-tooltip />
        <el-table-column prop="model" label="模型" min-width="120" show-overflow-tooltip />
        <el-table-column label="温度" width="70" align="center">
          <template #default="{ row }">{{ row.temperature ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="promptVersion" label="Prompt" width="80" align="center" />
        <el-table-column label="重试" width="80" align="center">
          <template #default="{ row }">{{ row.retryUsed ?? 0 }} / {{ row.maxRetry ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="生成/确认" width="100" align="center">
          <template #default="{ row }">{{ row.generatedCount ?? 0 }} / {{ row.confirmedCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-12"
        layout="total, prev, pager, next"
        :total="recordTotal"
        :page-size="recordPageSize"
        :current-page="recordPage"
        @current-change="onRecordPage"
      />
    </el-card>

    <!-- 归因准确率 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="section-title">归因准确率评估</div>
      </template>
      <el-row :gutter="16">
        <el-col :span="4" v-for="k in attrKpis" :key="k.label">
          <div class="kpi">
            <div class="kpi-value" :style="{ color: k.color }">{{ k.value }}</div>
            <div class="kpi-label">{{ k.label }}</div>
          </div>
        </el-col>
      </el-row>
      <el-table :data="attr?.byCategory || []" size="small" border class="mt-12">
        <el-table-column label="LLM 分类" min-width="120">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="analyzed" label="分析数" width="90" align="center" />
        <el-table-column prop="confirmed" label="已确认" width="90" align="center" />
        <el-table-column prop="correct" label="确认正确" width="90" align="center" />
        <el-table-column prop="corrected" label="人工修正" width="90" align="center" />
        <el-table-column label="准确率" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="rateTag(row.accuracy)" size="small">{{ fmtPct(row.accuracy) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="sub-title mt-12">最近人工确认样本（含修正）</div>
      <el-table :data="attr?.recentSamples || []" size="small" border>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="LLM 分类" width="130">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="确认分类" width="130">
          <template #default="{ row }">{{ categoryLabel(row.confirmedCategory) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.corrected ? 'warning' : 'success'" size="small">
              {{ row.corrected ? '已修正' : '确认正确' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.confirmedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { listProjects } from '../api/project'
import { getGenerationQuality, getAttributionAccuracy, getGenerationRecords } from '../api/stats'

const projectId = ref(null)
const projects = ref([])
const loading = ref(false)
const quality = ref(null)
const attr = ref(null)
const records = ref([])
const recordPage = ref(1)
const recordPageSize = ref(10)
const recordTotal = ref(0)
const chartRef = ref(null)
let chart = null

const qualityKpis = computed(() => {
  const o = quality.value?.overall || {}
  return [
    { label: '生成 / 确认用例', value: `${o.generated ?? 0} / ${o.confirmed ?? 0}`, color: '#409eff' },
    { label: '用例有效率', value: fmtPct(o.validRate), color: '#67c23a' },
    { label: '断言通过率', value: fmtPct(o.passRate), color: '#e6a23c' },
    { label: '可执行率', value: fmtPct(o.executableRate), color: '#909399' },
  ]
})

const hasQualityData = computed(() => (quality.value?.overall?.generated ?? 0) > 0)

const attrKpis = computed(() => {
  const a = attr.value || {}
  return [
    { label: '归因分析总数', value: a.totalAnalyzed ?? 0, color: '#409eff' },
    { label: '已人工确认', value: a.totalConfirmed ?? 0, color: '#67c23a' },
    { label: '确认正确', value: a.correct ?? 0, color: '#67c23a' },
    { label: '人工修正', value: a.corrected ?? 0, color: '#e6a23c' },
    { label: '归因准确率', value: fmtPct(a.accuracy), color: '#f56c6c' },
  ]
})

function fmtPct(v) {
  if (v == null || isNaN(Number(v))) return '—'
  return `${Number(v).toFixed(1)}%`
}
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 19) : '—'
}
function scenarioLabel(s) {
  return ({ normal: '正常', boundary: '边界', exception: '异常' })[s] || s || '—'
}
function categoryLabel(c) {
  return (
    { assert_error: '断言问题', data_error: '数据异常', env_error: '环境问题', real_defect: '真实缺陷' }[c] || c || '—'
  )
}
function rateTag(v) {
  const n = Number(v)
  if (n >= 90) return 'success'
  if (n >= 60) return 'warning'
  return 'danger'
}

async function loadAll() {
  loading.value = true
  try {
    const pid = projectId.value || undefined
    const [q, a] = await Promise.all([getGenerationQuality(pid), getAttributionAccuracy(pid)])
    quality.value = q
    attr.value = a
    await loadRecords()
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  const page = await getGenerationRecords(projectId.value || undefined, recordPage.value, recordPageSize.value)
  records.value = page.records || []
  recordTotal.value = page.total || 0
}

function onRecordPage(p) {
  recordPage.value = p
  loadRecords()
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const byScenario = quality.value?.byScenario || []
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['有效率', '可执行率', '断言通过率'], top: 0 },
    grid: { left: 40, right: 20, top: 46, bottom: 30 },
    xAxis: { type: 'category', data: byScenario.map((x) => scenarioLabel(x.group)) },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      { name: '有效率', type: 'bar', data: byScenario.map((x) => x.validRate), itemStyle: { color: '#67c23a' } },
      { name: '可执行率', type: 'bar', data: byScenario.map((x) => x.executableRate), itemStyle: { color: '#409eff' } },
      { name: '断言通过率', type: 'bar', data: byScenario.map((x) => x.passRate), itemStyle: { color: '#e6a23c' } },
    ],
  })
}

function onResize() {
  if (chart) chart.resize()
}

onMounted(async () => {
  try {
    projects.value = (await listProjects()) || []
  } catch (e) {
    projects.value = []
  }
  await loadAll()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.stats-page {
  padding: 4px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hint {
  flex: 1;
  min-width: 320px;
}
.section {
  margin-bottom: 16px;
}
.section-title {
  font-weight: 600;
}
.sub-title {
  font-size: 13px;
  color: #606266;
  font-weight: 600;
  margin-bottom: 8px;
}
.kpi {
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  text-align: center;
  margin-bottom: 16px;
}
.kpi-value {
  font-size: 24px;
  font-weight: 700;
}
.kpi-label {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}
.chart-box {
  height: 260px;
  margin-top: 8px;
}
.mt-12 {
  margin-top: 12px;
}
</style>
