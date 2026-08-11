<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getExecution, listExecutionDetails, getExecutionDetail } from '../api/execution'
import FailureAnalysisDialog from './FailureAnalysisDialog.vue'

const props = defineProps({
  executionId: { type: Number, required: true },
  autoRefresh: { type: Boolean, default: false },
})

const summary = ref(null)
const details = ref([])
const detailTotal = ref(0)
const detailLoading = ref(false)
const detailQuery = ref({ page: 1, size: 10, status: null })
const drawerVisible = ref(false)
const analysisVisible = ref(false)
const analysisDetailId = ref(null)
const currentDetail = ref(null)
let timer = null

const statusText = { 1: '通过', 2: '失败', 3: '异常' }
const statusType = { 1: 'success', 2: 'danger', 3: 'warning' }
const triggerText = { 1: '手动', 2: '定时', 3: 'CI' }
const methodType = (m) =>
  ({ GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger', PATCH: 'info' })[m] || 'info'

function fmtDuration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

async function loadSummary() {
  summary.value = await getExecution(props.executionId)
  return summary.value
}

async function loadDetails() {
  detailLoading.value = true
  try {
    const page = await listExecutionDetails(props.executionId, detailQuery.value)
    details.value = page.records
    detailTotal.value = page.total
  } finally {
    detailLoading.value = false
  }
}

async function refresh() {
  const s = await loadSummary()
  await loadDetails()
  if (props.autoRefresh && s.status === 0) {
    timer = setTimeout(refresh, 1000)
  }
}

async function openDetail(row) {
  drawerVisible.value = true
  currentDetail.value = null
  currentDetail.value = await getExecutionDetail(props.executionId, row.id)
}

function openAnalysis(row) {
  analysisDetailId.value = row.id
  analysisVisible.value = true
}

function onStatusChange() {
  detailQuery.value.page = 1
  loadDetails()
}

function onPageChange(p) {
  detailQuery.value.page = p
  loadDetails()
}

onMounted(refresh)
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <div>
    <!-- 汇总 -->
    <el-row v-if="summary" :gutter="12">
      <el-col :span="4">
        <el-card shadow="never" style="text-align: center">
          <div class="stat-label">状态</div>
          <el-tag :type="summary.status === 0 ? 'primary' : 'success'">
            {{ summary.status === 0 ? '执行中' : '已完成' }}
          </el-tag>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" style="text-align: center">
          <div class="stat-label">用例总数</div>
          <div class="stat-value">{{ summary.totalCases }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" style="text-align: center">
          <div class="stat-label">通过</div>
          <div class="stat-value" style="color: #67c23a">{{ summary.passed }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" style="text-align: center">
          <div class="stat-label">失败 / 异常</div>
          <div class="stat-value" style="color: #f56c6c">{{ summary.failed }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" style="text-align: center">
          <div class="stat-label">通过率</div>
          <div class="stat-value">{{ summary.passRate ?? 0 }}%</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" style="text-align: center">
          <div class="stat-label">耗时</div>
          <div class="stat-value" style="font-size: 15px">{{ fmtDuration(summary.durationMs) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="summary" style="margin-top: 12px; color: #909399; font-size: 12px">
      触发方式：{{ triggerText[summary.triggerType] || summary.triggerType }} · 开始于
      {{ fmtTime(summary.startedAt) }} · 结束于 {{ fmtTime(summary.finishedAt) }} ·
      已完成 {{ summary.detailCount }} / {{ summary.totalCases }} 条
    </div>

    <!-- 明细 -->
    <div class="toolbar" style="margin-top: 12px">
      <el-select
        v-model="detailQuery.status"
        placeholder="全部状态"
        clearable
        style="width: 130px"
        @change="onStatusChange"
      >
        <el-option label="通过" :value="1" />
        <el-option label="失败" :value="2" />
        <el-option label="异常" :value="3" />
      </el-select>
      <span style="color: #909399; font-size: 13px">共 {{ detailTotal }} 条用例明细</span>
    </div>

    <el-table v-loading="detailLoading" :data="details" size="small" border style="margin-top: 8px">
      <el-table-column label="用例名" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.caseName || "用例已删除" }}</template>
        </el-table-column>
      <el-table-column label="方法" width="80">
        <template #default="{ row }">
          <el-tag :type="methodType(row.method)" size="small">{{ row.method }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="urlTemplate" label="请求地址" min-width="190" show-overflow-tooltip>
        <template #default="{ row }"><code style="font-size: 12px">{{ row.urlTemplate }}</code></template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusType[row.status]" size="small">{{ statusText[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试" width="60" align="center" />
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ fmtDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMessage" style="color: #f56c6c">{{ row.errorMessage }}</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>`n          <el-button v-if="row.status !== 1" size="small" link type="warning" @click="openAnalysis(row)">归因</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无明细" :image-size="60" />
      </template>
    </el-table>

    <el-pagination
      v-if="detailTotal > 0"
      style="margin-top: 8px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="detailTotal"
      :current-page="detailQuery.page"
      :page-size="detailQuery.size"
      @current-change="onPageChange"
    />

    <!-- 明细详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="执行明细" size="45%">
      <div v-if="currentDetail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="用例名">{{ currentDetail.caseName || "用例已删除" }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType[currentDetail.status]" size="small">
              {{ statusText[currentDetail.status] }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ fmtDuration(currentDetail.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ currentDetail.retryCount }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="block-title">请求</h4>
        <pre class="detail-pre">{{ currentDetail.requestText || '（无请求记录）' }}</pre>

        <h4 class="block-title">响应</h4>
        <pre class="detail-pre">{{ currentDetail.responseText || '（无响应）' }}</pre>

        <template v-if="currentDetail.errorMessage">
          <h4 class="block-title">错误信息</h4>
          <pre class="detail-pre error">{{ currentDetail.errorMessage }}</pre>
        </template>
      </div>
    </el-drawer>

    <FailureAnalysisDialog v-if="analysisVisible" :detail-id="analysisDetailId" @close="analysisVisible = false" />
  </div>
</template>

<style scoped>
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
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.block-title {
  margin: 16px 0 8px;
  font-size: 13px;
}
.detail-pre {
  max-height: 220px;
  overflow: auto;
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  line-height: 1.6;
  font-family: 'JetBrains Mono', Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-all;
}
.detail-pre.error {
  background: #fef0f0;
  border-color: #fde2e2;
  color: #f56c6c;
}
</style>