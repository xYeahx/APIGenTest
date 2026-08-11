<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { submitGeneration, getGeneration, confirmGeneration } from '../api/generation'

const props = defineProps({
  projectId: { type: Number, required: true },
  apiIds: { type: Array, required: true },
})

const emit = defineEmits(['confirmed', 'close'])

const visible = ref(true)
const submitting = ref(false)
const polling = ref(false)
const task = ref(null)
const taskId = ref('')
const businessDesc = ref('')
let timer = null

const scenarioTypeLabel = { normal: '正常', boundary: '边界', exception: '异常' }
const methodType = (m) => ({ GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger', PATCH: 'info' })[m] || 'info'
const isRunning = computed(() => ['PENDING', 'RUNNING'].includes(task.value?.status))
const isFinished = computed(() => ['SUCCESS', 'PARTIAL_FAILED', 'FAILED'].includes(task.value?.status))
const canConfirm = computed(() => ['SUCCESS', 'PARTIAL_FAILED'].includes(task.value?.status) && task.value?.status !== 'CONFIRMED')
const progress = computed(() => {
  if (!task.value || task.value.total === 0) return 0
  return Math.round((task.value.done / task.value.total) * 100)
})

async function start() {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await submitGeneration(props.apiIds, businessDesc.value.trim())
    taskId.value = res.taskId
    submitting.value = false
    polling.value = true
    poll()
  } catch (e) {
    submitting.value = false
  }
}

async function poll() {
  try {
    const t = await getGeneration(taskId.value)
    task.value = t
    if (['SUCCESS', 'PARTIAL_FAILED', 'FAILED'].includes(t.status)) {
      polling.value = false
    } else {
      timer = setTimeout(poll, 1200)
    }
  } catch (e) {
    polling.value = false
  }
}

async function handleConfirm() {
  const res = await confirmGeneration(taskId.value)
  ElMessage.success(`已确认入库 ${res.saved} 条用例`)
  emit('confirmed', res.saved)
}

function handleClose() {
  visible.value = false
  if (isRunning.value) {
    ElMessage.warning('生成任务仍在进行，关闭后任务结果将无法查看（当前版本任务不持久化）')
  }
  emit('close')
}

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="AI 用例生成"
    width="720px"
    top="4vh"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <template v-if="!task">
      <el-alert
        type="info"
        :closable="false"
        :title="`已选择 ${apiIds.length} 个接口，将调用大模型为每个接口生成正常 / 边界 / 异常场景用例`"
        style="margin-bottom: 16px"
      />
      <el-form label-width="80px">
        <el-form-item label="业务描述">
          <el-input
            v-model="businessDesc"
            type="textarea"
            :rows="3"
            placeholder="可选。补充业务背景可提升用例质量，例如：登录成功后返回 token，供后续接口使用"
          />
        </el-form-item>
      </el-form>
    </template>

    <div v-else-if="isRunning" style="text-align: center; padding: 24px 0">
      <el-progress :percentage="progress" :stroke-width="12" style="max-width: 480px; margin: 0 auto" />
      <p style="color: #909399; font-size: 13px; margin-top: 12px">
        正在为 {{ task.total }} 个接口生成用例，已完成 {{ task.done }}（成功 {{ task.success }} / 失败 {{ task.failed }}）…
      </p>
    </div>

    <div v-else-if="isFinished" style="max-height: 56vh; overflow: auto">
      <el-alert
        v-if="task.status === 'SUCCESS'"
        type="success"
        :closable="false"
        title="全部接口生成成功"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-else-if="task.status === 'PARTIAL_FAILED'"
        type="warning"
        :closable="false"
        title="部分接口生成成功，其余失败"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-else
        type="error"
        :closable="false"
        :title="task.error || '全部接口生成失败'"
        style="margin-bottom: 12px"
      />

      <div v-for="r in task.results" :key="r.apiId" style="margin-bottom: 16px">
        <h4 style="margin-bottom: 8px">接口 #{{ r.apiId }}（{{ r.cases.length }} 条用例）</h4>
        <el-table :data="r.cases" size="small" border>
          <el-table-column prop="name" label="用例名" min-width="150" show-overflow-tooltip />
          <el-table-column label="场景" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.scenarioType === 'exception' ? 'danger' : row.scenarioType === 'boundary' ? 'warning' : 'info'">
                {{ scenarioTypeLabel[row.scenarioType] || row.scenarioType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="方法" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="methodType(row.method)">{{ row.method }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="urlTemplate" label="请求地址" min-width="170" show-overflow-tooltip>
            <template #default="{ row }"><code style="font-size: 12px">{{ row.urlTemplate }}</code></template>
          </el-table-column>
          <el-table-column prop="asserts" label="断言" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.asserts || '—' }}</template>
          </el-table-column>
        </el-table>
      </div>

      <el-alert
        v-if="task.failures && task.failures.length"
        type="error"
        :closable="false"
        style="margin-top: 8px"
      >
        <template #title>
          失败接口：
          <span v-for="f in task.failures" :key="f.apiId">#{{ f.apiId }} {{ f.error }}；</span>
        </template>
      </el-alert>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button
        v-if="!task"
        type="primary"
        :loading="submitting"
        :disabled="!apiIds.length"
        @click="start"
      >
        开始生成
      </el-button>
      <el-button v-if="canConfirm" type="success" :loading="polling" @click="handleConfirm">
        确认入库
      </el-button>
    </template>
  </el-dialog>
</template>