<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { getDashboardOverview } from '../api/dashboard'
import { createProject } from '../api/project'
import { importApiFile } from '../api/apiInfo'
import { useAuthStore } from '../store/auth'
import dummySpec from '../assets/dummyjson-openapi.json'

const router = useRouter()
const authStore = useAuthStore()

const overview = ref(null)
const health = ref(null)
const loading = ref(false)
const importing = ref(false)

const stats = computed(() => overview.value?.stats || {})
const projects = computed(() => overview.value?.projects || [])
const trend = computed(() => overview.value?.trend || [])
const failTop = computed(() => overview.value?.failTop || [])
const recentExecutions = computed(() => overview.value?.recentExecutions || [])
const activities = computed(() => overview.value?.activities || [])
const hasProjects = computed(() => projects.value.length > 0)

const roleText = (r) => ({ 0: '所有者', 1: '成员', 2: '只读' })[r] || '未知'
const roleType = (r) => ({ 0: 'primary', 1: 'success', 2: 'info' })[r] || 'info'
const triggerText = (t) => ({ 1: '手动', 2: '定时', 3: 'CI' })[t] || '未知'

function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}
function fmtDuration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}
function fmtRate(v) {
  if (v == null) return '—'
  return `${Number(v).toFixed(1)}%`
}
// 项目最近执行状态徽标
function lastExecTag(card) {
  if (card.lastStatus == null) return { text: '从未执行', type: 'info' }
  if (card.lastStatus === 0) return { text: '执行中', type: 'warning' }
  if (card.lastPassRate >= 99.9) return { text: '全部通过', type: 'success' }
  return { text: '有失败', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    overview.value = await getDashboardOverview()
    await nextTick()
    renderChart()
  } catch (e) {
    overview.value = null
  } finally {
    loading.value = false
  }
}

async function loadHealth() {
  try {
    health.value = await request.get('/health')
  } catch (e) {
    health.value = null
  }
}

async function importSample() {
  importing.value = true
  try {
    const proj = await createProject({
      name: 'DummyJSON 示例项目',
      description: '一键导入的示例项目：演示 OpenAPI 导入、AI 生成用例与执行报告（基于 DummyJSON 免费公开 API）',
    })
    const file = new File([JSON.stringify(dummySpec)], 'dummyjson-openapi.json', { type: 'application/json' })
    await importApiFile(proj.id, file)
    ElMessage.success('示例项目导入成功，共 7 个接口')
    await load()
  } catch (e) {
    ElMessage.error('导入失败：' + (e?.message || '请稍后重试'))
  } finally {
    importing.value = false
  }
}

function goProject(id) {
  router.push(`/projects/${id}`)
}
function goProjects() {
  router.push('/projects')
}

// ---------- ECharts 趋势图 ----------
const chartEl = ref(null)
let chart = null

function renderChart() {
  if (!chartEl.value) return
  if (!trend.value.length) {
    if (chart) {
      chart.dispose()
      chart = null
    }
    return
  }
  if (!chart) {
    chart = echarts.init(chartEl.value)
  }
  const labels = trend.value.map((p, i) => `#${i + 1}`)
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter(params) {
        const idx = params[0]?.dataIndex ?? 0
        const p = trend.value[idx]
        if (!p) return ''
        const lines = params.map((it) => `${it.marker}${it.seriesName}：${it.value}`).join('<br/>')
        return [
          `<b>${p.projectName || ''}</b>`,
          `时间：${fmtTime(p.startedAt)}`,
          lines,
          `用例数：${p.totalCases ?? '—'} · 耗时：${fmtDuration(p.durationMs)}`,
        ].join('<br/>')
      },
    },
    legend: { data: ['通过率 %', '耗时 ms'] },
    grid: { left: 10, right: 10, top: 36, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { color: '#909399' },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
    },
    yAxis: [
      { type: 'value', name: '通过率 %', min: 0, max: 100, splitLine: { lineStyle: { color: '#f0f2f5' } } },
      { type: 'value', name: '耗时 ms', splitLine: { show: false } },
    ],
    series: [
      {
        name: '通过率 %',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trend.value.map((p) => p.passRate),
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: '#409eff' },
      },
      {
        name: '耗时 ms',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: '40%',
        data: trend.value.map((p) => p.durationMs ?? 0),
        itemStyle: { color: '#67c23a', opacity: 0.55 },
      },
    ],
  })
}

function onResize() {
  chart?.resize()
}

onMounted(async () => {
  await Promise.all([load(), loadHealth()])
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

<template>
  <div v-loading="loading" class="dashboard">
    <!-- ============ 空状态：引导 + 一键示例 ============ -->
    <div v-if="!hasProjects" class="empty-wrap">
      <el-card class="empty-card">
        <template #header>
          <div class="card-header">
            <span>欢迎，{{ authStore.nickname }}</span>
            <el-tag type="success" effect="plain">APIGenTest</el-tag>
          </div>
        </template>
        <p class="empty-desc">
          基于大模型的接口自动化测试平台：导入 OpenAPI 文档，AI 自动生成可执行测试用例，
          一键执行并生成报告与失败归因。还没有项目，先花 1 分钟上手：
        </p>
        <el-steps direction="vertical" :active="1" class="empty-steps">
          <el-step title="新建项目" description="在「项目管理」中创建一个测试项目" />
          <el-step title="导入接口文档" description="导入 OpenAPI / Swagger 文档，系统自动解析出接口列表" />
          <el-step title="AI 生成并执行用例" description="大模型自动生成用例，执行后查看报告与失败归因" />
        </el-steps>
        <div class="empty-actions">
          <el-button type="primary" size="large" :loading="importing" @click="importSample">
            <el-icon style="margin-right: 6px"><Download /></el-icon>
            {{ importing ? '正在导入…' : '一键导入示例项目（DummyJSON）' }}
          </el-button>
          <el-button size="large" @click="goProjects">前往项目管理</el-button>
        </div>
      </el-card>
    </div>

    <!-- ============ 有数据：完整工作台 ============ -->
    <template v-else>
      <!-- KPI 统计 -->
      <el-row :gutter="16">
        <el-col :span="4" v-for="kpi in [
          { label: '项目数', value: stats.projectCount ?? 0, icon: 'Folder', color: '#409eff' },
          { label: '接口总数', value: stats.apiCount ?? 0, icon: 'Document', color: '#67c23a' },
          { label: '用例总数', value: stats.caseCount ?? 0, icon: 'Tickets', color: '#e6a23c' },
          { label: '累计执行', value: stats.executionCount ?? 0, icon: 'VideoPlay', color: '#909399' },
          { label: '整体通过率', value: fmtRate(stats.passRate), icon: 'TrendCharts', color: (stats.passRate ?? 0) >= 90 ? '#67c23a' : '#f56c6c' },
          { label: '未读通知', value: stats.unreadCount ?? 0, icon: 'Bell', color: '#f56c6c' },
        ]" :key="kpi.label">
          <el-card class="kpi-card" shadow="hover">
            <div class="kpi-inner">
              <el-icon :size="30" :color="kpi.color"><component :is="kpi.icon" /></el-icon>
              <div class="kpi-meta">
                <div class="kpi-value">{{ kpi.value }}</div>
                <div class="kpi-label">{{ kpi.label }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 趋势图 + 最近动态 -->
      <el-row :gutter="16" class="row-gap">
        <el-col :span="14">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>执行通过率 / 耗时趋势（最近 {{ trend.length }} 次）</span>
                <el-tag v-if="stats.taskCount > 0" type="warning" effect="plain" size="small">
                  启用定时任务 {{ stats.taskCount }}
                </el-tag>
              </div>
            </template>
            <div v-if="trend.length" ref="chartEl" class="chart-box" />
            <el-empty v-else description="暂无执行记录，去项目里跑一次用例吧" :image-size="80" />
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card class="activity-card">
            <template #header>最近动态</template>
            <div class="activity-scroll" :class="{ 'is-empty': !activities.length }">
              <el-timeline v-if="activities.length">
                <el-timeline-item
                  v-for="(a, i) in activities"
                  :key="i"
                  :timestamp="fmtTime(a.time)"
                  :type="a.type === 'execution' ? 'primary' : 'warning'"
                  size="small"
                >
                  <div class="activity-title">{{ a.title }}</div>
                  <div v-if="a.content" class="activity-content">{{ a.content }}</div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无动态" :image-size="60" />
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 项目概览（覆盖率卡片） -->
      <el-card class="row-gap">
        <template #header>
          <div class="card-header">
            <span>项目概览</span>
            <el-button link type="primary" @click="goProjects">全部项目 →</el-button>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :span="8" v-for="card in projects" :key="card.projectId" class="proj-col">
            <el-card class="proj-card" shadow="hover" @click="goProject(card.projectId)">
              <div class="proj-head">
                <div class="proj-name" :title="card.name">{{ card.name }}</div>
                <el-tag :type="roleType(card.myRole)" size="small" effect="plain">{{ roleText(card.myRole) }}</el-tag>
              </div>
              <div class="proj-body">
                <el-progress
                  type="dashboard"
                  :width="88"
                  :percentage="Math.min(100, Number(card.coverageRate || 0))"
                  :color="(card.coverageRate || 0) >= 90 ? '#67c23a' : '#409eff'"
                  :format="() => `${Number(card.coverageRate || 0).toFixed(1)}%`"
                />
                <div class="proj-stats">
                  <div class="stat-row"><span class="stat-label">接口</span><b>{{ card.apiCount }}</b></div>
                  <div class="stat-row"><span class="stat-label">用例</span><b>{{ card.caseCount }}</b></div>
                  <div class="stat-row"><span class="stat-label">已覆盖接口</span><b>{{ card.coveredApis }}</b></div>
                  <div class="stat-row">
                    <span class="stat-label">最近执行</span>
                    <el-tag :type="lastExecTag(card).type" size="small">{{ lastExecTag(card).text }}</el-tag>
                  </div>
                  <div class="stat-row" v-if="card.lastPassRate != null">
                    <span class="stat-label">通过率</span><b>{{ fmtRate(card.lastPassRate) }}</b>
                  </div>
                  <div class="stat-row" v-if="card.lastExecutedAt">
                    <span class="stat-label">时间</span><span class="stat-time">{{ fmtTime(card.lastExecutedAt) }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-card>

      <!-- 最近执行 + 失败 TOP -->
      <el-row :gutter="16" class="row-gap">
        <el-col :span="14">
          <el-card>
            <template #header>最近执行</template>
            <el-table :data="recentExecutions" size="small" :show-header="true">
              <el-table-column label="时间" width="150">
                <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
              </el-table-column>
              <el-table-column label="项目" prop="projectName" min-width="130" show-overflow-tooltip />
              <el-table-column label="触发" width="70">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ triggerText(row.triggerType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 1 ? (row.failed > 0 ? 'danger' : 'success') : 'warning'">
                    {{ row.status === 1 ? (row.failed > 0 ? '有失败' : '全部通过') : '执行中' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="通过/失败" width="90">
                <template #default="{ row }">
                  <span class="pass">{{ row.passed ?? 0 }}</span> / <span class="fail">{{ row.failed ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column label="耗时" width="90">
                <template #default="{ row }">{{ fmtDuration(row.durationMs) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!recentExecutions.length" description="暂无执行记录" :image-size="70" />
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card>
            <template #header>失败用例 TOP{{ failTop.length || '' }}</template>
            <div v-if="failTop.length" class="fail-top">
              <div v-for="(f, i) in failTop" :key="f.caseId" class="fail-item">
                <div class="fail-rank" :class="i < 3 ? 'hot' : ''">{{ i + 1 }}</div>
                <div class="fail-body">
                  <div class="fail-name" :title="f.caseName">{{ f.caseName }}</div>
                  <div class="fail-meta">
                    <el-tag size="small" type="danger" effect="plain">{{ f.failCount }} 次</el-tag>
                    <span class="fail-project">{{ f.projectName }}</span>
                  </div>
                  <div v-if="f.lastError" class="fail-error" :title="f.lastError">{{ f.lastError }}</div>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无失败用例 🎉" :image-size="70" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 系统状态 -->
      <el-card class="row-gap">
        <template #header>系统状态</template>
        <div class="health-row">
          <el-tag :type="health?.status === 'ok' ? 'success' : 'danger'">
            {{ health?.status === 'ok' ? '后端在线' : '后端离线' }}
          </el-tag>
          <el-tag :type="health?.db ? 'success' : 'danger'" effect="plain">数据库 {{ health?.db ? '正常' : '异常' }}</el-tag>
          <span v-if="health?.time" class="health-time">服务器时间：{{ health.time }}</span>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  min-height: 60vh;
}
.row-gap {
  margin-top: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kpi-card {
  text-align: left;
}
.kpi-inner {
  display: flex;
  align-items: center;
  gap: 12px;
}
.kpi-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.kpi-label {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.chart-box {
  height: 300px;
}
.activity-scroll {
  height: 300px;
  overflow-y: auto;
  padding-right: 6px;
}
.activity-scroll.is-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}
.activity-scroll .el-timeline {
  padding-left: 4px;
}
.activity-scroll::-webkit-scrollbar {
  width: 6px;
}
.activity-scroll::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}
.activity-title {
  font-size: 13px;
  color: #303133;
}
.activity-content {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.proj-card {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.proj-card:hover {
  transform: translateY(-2px);
}
.proj-col {
  margin-bottom: 16px;
}
.proj-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}
.proj-name {
  font-weight: 600;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.proj-body {
  display: flex;
  align-items: center;
  gap: 16px;
}
.proj-stats {
  flex: 1;
  min-width: 0;
}
.stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  line-height: 2;
}
.stat-label {
  color: #909399;
}
.stat-time {
  color: #909399;
  font-size: 12px;
}
.pass {
  color: #67c23a;
  font-weight: 600;
}
.fail {
  color: #f56c6c;
  font-weight: 600;
}
.fail-top {
  max-height: 330px;
  overflow-y: auto;
}
.fail-item {
  display: flex;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px dashed #ebeef5;
}
.fail-item:last-child {
  border-bottom: none;
}
.fail-rank {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  text-align: center;
  line-height: 22px;
  font-size: 12px;
  flex-shrink: 0;
}
.fail-rank.hot {
  background: #fef0f0;
  color: #f56c6c;
  font-weight: 600;
}
.fail-body {
  min-width: 0;
  flex: 1;
}
.fail-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 2px 0;
}
.fail-project {
  font-size: 12px;
  color: #909399;
}
.fail-error {
  font-size: 12px;
  color: #f56c6c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.health-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.health-time {
  color: #909399;
  font-size: 13px;
}
.empty-wrap {
  max-width: 720px;
  margin: 40px auto 0;
}
.empty-desc {
  color: #606266;
  line-height: 1.8;
  margin-bottom: 16px;
}
.empty-steps {
  margin-bottom: 20px;
}
.empty-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>