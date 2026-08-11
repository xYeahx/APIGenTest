<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { listExecutions } from '../../api/execution'
import ExecutionResultView from '../../components/ExecutionResultView.vue'

const props = defineProps({
  projectId: { type: Number, required: true },
  refreshKey: { type: Number, default: 0 },
})

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = ref({ page: 1, size: 10 })
const viewVisible = ref(false)
const viewExecutionId = ref(null)
let pollTimer = null

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

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const page = await listExecutions(props.projectId, query.value)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
  updatePolling()
}

/** 存在执行中的记录时轮询刷新，执行全部结束后停止 */
function updatePolling() {
  const hasRunning = rows.value.some((r) => r.status === 0)
  if (hasRunning && !pollTimer) {
    pollTimer = setInterval(() => load(true), 3000)
  } else if (!hasRunning && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function onPageChange(p) {
  query.value.page = p
  load()
}

function openView(row) {
  viewExecutionId.value = row.id
  viewVisible.value = true
}

watch(() => props.refreshKey, () => load())
onMounted(() => load())
onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div>
    <el-table v-loading="loading" :data="rows" style="margin-top: 4px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="触发方式" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.triggerType === 1 ? 'primary' : row.triggerType === 2 ? 'warning' : 'info'">
            {{ triggerText[row.triggerType] || row.triggerType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 0 ? 'primary' : 'success'">
            {{ row.status === 0 ? '执行中' : '已完成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalCases" label="总数" width="70" align="center" />
      <el-table-column prop="passed" label="通过" width="70" align="center">
        <template #default="{ row }">
          <span style="color: #67c23a">{{ row.passed }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="failed" label="失败/异常" width="90" align="center">
        <template #default="{ row }">
          <span style="color: #f56c6c">{{ row.failed }}</span>
        </template>
      </el-table-column>
      <el-table-column label="通过率" width="90" align="center">
        <template #default="{ row }">
          <span style="font-family: 'JetBrains Mono', Consolas, monospace">{{ row.passRate ?? 0 }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100" align="center">
        <template #default="{ row }">{{ fmtDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="openView(row)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有执行记录，去「用例管理」执行一次吧" />
      </template>
    </el-table>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="onPageChange"
    />

    <el-dialog
      v-model="viewVisible"
      title="执行结果"
      width="860px"
      top="4vh"
      destroy-on-close
    >
      <ExecutionResultView v-if="viewExecutionId" :execution-id="viewExecutionId" />
    </el-dialog>
  </div>
</template>