<script setup>
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { getReport, getTrend } from '../../api/report'
import { listExecutions } from '../../api/execution'
import FailureAnalysisDialog from '../../components/FailureAnalysisDialog.vue'

const props = defineProps({
  projectId: { type: Number, required: true },
  refreshKey: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false },
})

const trend = ref(null)
const execRows = ref([])
const execTotal = ref(0)
const report = ref(null)
const selectedId = ref(null)
const analysisVisible = ref(false)
const analysisDetailId = ref(null)
const loadingTrend = ref(false)
const loadingReport = ref(false)
let pollTimer = null
let loadingList = false

const chartEl = ref(null)
let chart = null
let chartObserver = null
let chartObservedEl = null
let chartPending = false

const statusText = { 1: '通过', 2: '失败', 3: '异常' }

function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

function fmtDuration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

async function loadTrend() {
  loadingTrend.value = true
  try {
    trend.value = await getTrend(props.projectId, 20)
  } finally {
    loadingTrend.value = false
  }
}

async function loadExecutions() {
  if (loadingList) return
  loadingList = true
  try {
    const page = await listExecutions(props.projectId, { page: 1, size: 10 })
    execRows.value = page.records
    execTotal.value = page.total
    if (page.records.length) {
      selectedId.value = page.records[0].id
      loadReport(selectedId.value)
    } else {
      selectedId.value = null
      report.value = null
    }
    updatePolling()
  } finally {
    loadingList = false
  }
}

/** 执行状态轮询：存在执行中任务时，定时刷新趋势与执行列表 */
function updatePolling() {
  const hasRunning = execRows.value.some((r) => r.status === 0)
  if (hasRunning && !pollTimer) {
    pollTimer = setInterval(() => {
      loadTrend()
      loadExecutions()
    }, 3000)
  } else if (!hasRunning && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function loadReport(id) {
  if (!id) return
  loadingReport.value = true
  try {
    report.value = await getReport(id)
  } finally {
    loadingReport.value = false
  }
}

function openAnalysis(row) {
  analysisDetailId.value = row.lastDetailId
  analysisVisible.value = true
}

function onSelect() {
  loadReport(selectedId.value)
}

function observeChartEl() {
  if (!chartEl.value) return
  if (!chartObserver) chartObserver = new ResizeObserver(onChartElResize)
  if (chartObservedEl !== chartEl.value) {
    if (chartObservedEl) chartObserver.unobserve(chartObservedEl)
    chartObserver.observe(chartEl.value)
    chartObservedEl = chartEl.value
  }
}

function onChartElResize() {
  if (!chartEl.value) return
  const { clientWidth, clientHeight } = chartEl.value
  if (clientWidth === 0 || clientHeight === 0) return
  if (chartPending) {
    chartPending = false
    renderTrend()
    return
  }
  chart?.resize()
}

function renderTrend() {
  const el = chartEl.value
  if (!el) return
  const { clientWidth, clientHeight } = el
  if (clientWidth === 0 || clientHeight === 0) {
    // 容器处于隐藏状态（尺寸为 0），先不初始化 ECharts，
    // 等待 ResizeObserver 检测到容器可见并恢复尺寸后再渲染。
    chartPending = true
    observeChartEl()
    return
  }
  if (!chart || chart.getDom() !== el) {
    if (chart) chart.dispose()
    chart = echarts.init(el)
  }
  chartPending = false
  chart.resize()
  const points = trend.value?.points || []
  const x = points.map((p) => `#${p.executionId}\n${fmtTime(p.startedAt)}`)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['通过率(%)', '耗时(ms)'], top: 4 },
    grid: { left: 48, right: 56, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: x, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '通过率(%)', max: 100, axisLabel: { fontSize: 11 } },
      { type: 'value', name: '耗时(ms)', axisLabel: { fontSize: 11 } },
    ],
    series: [
      {
        name: '通过率(%)',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: points.map((p) => p.passRate),
        yAxisIndex: 0,
        itemStyle: { color: '#409eff' },
      },
      {
        name: '耗时(ms)',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: points.map((p) => p.durationMs),
        yAxisIndex: 1,
        itemStyle: { color: '#67c23a' },
      },
    ],
  })
}

function handleResize() {
  chart?.resize()
}

watch(trend, async () => {
  await nextTick()
  renderTrend()
})
watch(() => props.refreshKey, () => {
  loadTrend()
  loadExecutions()
})

onMounted(() => {
  loadTrend()
  loadExecutions()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  window.removeEventListener('resize', handleResize)
  if (chartObserver) {
    chartObserver.disconnect()
    chartObserver = null
    chartObservedEl = null
  }
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<template>
  <div>
    <!-- 项目趋势 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>项目执行趋势（最近 20 次）</span>
          <span v-if="trend" class="trend-summary">
            共 {{ trend.executionCount }} 次执行 · {{ trend.totalCases }} 条用例 · 平均通过率
            {{ trend.avgPassRate }}%
          </span>
        </div>
      </template>
      <div v-loading="loadingTrend" style="min-height: 260px">
        <div v-if="trend && trend.points.length" ref="chartEl" style="width: 100%; height: 280px"></div>
        <el-empty v-else-if="!loadingTrend" description="还没有执行记录，先跑一次执行吧" :image-size="80" />
      </div>
    </el-card>

    <!-- 单次执行报告 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>执行报告</span>
          <el-select
            v-model="selectedId"
            placeholder="选择一次执行"
            style="width: 320px"
            @change="onSelect"
          >
            <el-option
              v-for="row in execRows"
              :key="row.id"
              :label="`#${row.id}  ${fmtTime(row.startedAt)}  ${row.passRate ?? 0}%`"
              :value="row.id"
            />
          </el-select>
        </div>
      </template>

      <div v-loading="loadingReport" style="min-height: 120px">
        <template v-if="report">
          <el-row :gutter="12">
            <el-col :span="4">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">用例总数</div>
                <div class="stat-value">{{ report.execution.totalCases }}</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">通过</div>
                <div class="stat-value" style="color: #67c23a">{{ report.execution.passed }}</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">失败 / 异常</div>
                <div class="stat-value" style="color: #f56c6c">{{ report.execution.failed }}</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">通过率</div>
                <div class="stat-value">{{ report.execution.passRate ?? 0 }}%</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">耗时</div>
                <div class="stat-value" style="font-size: 15px">{{ fmtDuration(report.execution.durationMs) }}</div>
              </el-card>
            </el-col>
            <el-col :span="4">
              <el-card shadow="never" class="stat-card">
                <div class="stat-label">开始时间</div>
                <div class="stat-value" style="font-size: 13px">{{ fmtTime(report.execution.startedAt) }}</div>
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="16" style="margin-top: 16px">
            <el-col :span="12">
              <h4 class="block-title">失败用例 TOP</h4>
              <el-table v-if="report.failedTop.length" :data="report.failedTop" size="small" border max-height="260">
                <el-table-column type="index" label="#" width="40" />
                <el-table-column prop="caseName" label="用例名" min-width="140" show-overflow-tooltip />
                <el-table-column prop="failedCount" label="失败次数" width="80" align="center" />
                <el-table-column prop="lastError" label="最后错误" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="row.lastError" style="color: #f56c6c">{{ row.lastError }}</span>
                    <span v-else style="color: #c0c4cc">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="row.lastDetailId && !readonly" size="small" link type="warning" @click="openAnalysis(row)">
                      归因分析
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="本次执行没有失败用例" :image-size="60" />
            </el-col>
            <el-col :span="12">
              <h4 class="block-title">错误聚合</h4>
              <el-table v-if="report.errorGroups.length" :data="report.errorGroups" size="small" border max-height="260">
                <el-table-column type="index" label="#" width="40" />
                <el-table-column prop="error" label="错误信息" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span style="color: #f56c6c">{{ row.error }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="count" label="次数" width="70" align="center" />
                <el-table-column label="涉及用例" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.cases.join('、') }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="本次执行没有错误" :image-size="60" />
            </el-col>
          </el-row>
        </template>

        <el-empty v-else-if="!loadingReport && !execTotal" description="暂无执行数据" :image-size="80" />
      </div>
    </el-card>
    <FailureAnalysisDialog v-if="analysisVisible" :detail-id="analysisDetailId" @close="analysisVisible = false" />
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.trend-summary {
  color: #909399;
  font-size: 13px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  color: #909399;
  font-size: 12px;
  margin-bottom: 6px;
}
.stat-value {
  font-size: 20px;
  font-weight: 600;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.block-title {
  margin: 0 0 8px;
  font-size: 13px;
}
</style>