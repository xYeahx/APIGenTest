<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listTasks, createTask, updateTask, updateTaskStatus, deleteTask, runTaskNow, previewCron } from '../../api/task'
import { listEnvironments } from '../../api/environment'
import { listCases } from '../../api/case'

const props = defineProps({
  projectId: { type: Number, required: true },
})

const tasks = ref([])
const loading = ref(false)
const envs = ref([])
const cases = ref([])

const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref(null)
const form = ref({
  name: '',
  cron: '0 2 * * *',
  environmentId: null,
  scopeType: 'all',
  caseIds: [],
  enabled: true,
})
const cronPreview = ref(null)
const cronError = ref('')

const scopeText = computed(() => {
  if (form.value.scopeType === 'caseIds') {
    const names = cases.value.filter((c) => form.value.caseIds.includes(c.id)).map((c) => c.name)
    return `指定 ${names.length} 条用例`
  }
  return '项目内全部启用用例'
})

async function loadTasks() {
  loading.value = true
  try {
    const page = await listTasks(props.projectId, { page: 1, size: 50 })
    tasks.value = page.records
  } finally {
    loading.value = false
  }
}

async function loadEnvs() {
  envs.value = await listEnvironments(props.projectId)
}

async function loadCases() {
  const page = await listCases(props.projectId, { page: 1, size: 1000, status: 1 })
  cases.value = page.records
}

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 19) : '—'
}

function formatScope(row) {
  if (!row.scope) return '—'
  try {
    const s = JSON.parse(row.scope)
    if (s.type === 'caseIds') return `指定 ${(s.caseIds || []).length} 条用例`
    return '全部启用用例'
  } catch (e) {
    return row.scope
  }
}

async function handleCronChange() {
  cronPreview.value = null
  cronError.value = ''
  const cron = (form.value.cron || '').trim()
  if (!cron) return
  try {
    const res = await previewCron(cron)
    cronPreview.value = res
  } catch (e) {
    cronError.value = e.message || 'cron 表达式无效'
  }
}

function openCreate() {
  editingId.value = null
  form.value = { name: '', cron: '0 2 * * *', environmentId: null, scopeType: 'all', caseIds: [], enabled: true }
  cronPreview.value = null
  cronError.value = ''
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  let scopeType = 'all'
  let caseIds = []
  try {
    const s = JSON.parse(row.scope || '{"type":"all"}')
    scopeType = s.type === 'caseIds' ? 'caseIds' : 'all'
    caseIds = s.caseIds || []
  } catch (e) {
    // ignore
  }
  form.value = {
    name: row.name,
    cron: row.cron,
    environmentId: row.environmentId,
    scopeType,
    caseIds,
    enabled: row.enabled === 1,
  }
  cronPreview.value = null
  cronError.value = ''
  dialogVisible.value = true
  handleCronChange()
}

async function handleSave() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入任务名')
    return
  }
  if (!form.value.environmentId) {
    ElMessage.warning('请选择执行环境')
    return
  }
  if (form.value.scopeType === 'caseIds' && !form.value.caseIds.length) {
    ElMessage.warning('请选择要执行的用例')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      cron: form.value.cron.trim(),
      environmentId: form.value.environmentId,
      scope:
        form.value.scopeType === 'caseIds'
          ? { type: 'caseIds', caseIds: form.value.caseIds }
          : { type: 'all' },
      enabled: form.value.enabled ? 1 : 0,
    }
    if (editingId.value) {
      await updateTask(editingId.value, payload)
      ElMessage.success('任务已更新')
    } else {
      await createTask(props.projectId, payload)
      ElMessage.success('任务已创建')
    }
    dialogVisible.value = false
    loadTasks()
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    saving.value = false
  }
}

async function handleStatusChange(row, val) {
  try {
    await updateTaskStatus(row.id, val ? 1 : 0)
    row.enabled = val ? 1 : 0
    ElMessage.success(val ? '任务已启用' : '任务已停用')
  } catch (e) {
    row.enabled = val ? 0 : 1
  }
}

async function handleRun(row) {
  if (row.enabled !== 1) {
    ElMessage.warning('任务已停用，请先启用')
    return
  }
  try {
    const res = await runTaskNow(row.id)
    ElMessage.success(`已触发执行（executionId: ${res.executionId}），可到「执行历史」查看进度`)
    loadTasks()
  } catch (e) {
    // 错误提示已由拦截器处理
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除定时任务「${row.name}」？`, '删除任务', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteTask(row.id)
  ElMessage.success('已删除')
  loadTasks()
}

onMounted(() => {
  loadTasks()
  loadEnvs()
  loadCases()
})
</script>

<template>
  <div>
    <div class="toolbar">
      <span style="color: #909399; font-size: 13px">按 cron 定时执行用例集，完成后站内信通知执行结果</span>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>新建任务
      </el-button>
    </div>

    <el-table v-loading="loading" :data="tasks" style="margin-top: 12px">
      <el-table-column prop="name" label="任务名" min-width="150" show-overflow-tooltip />
      <el-table-column label="cron 表达式" width="150">
        <template #default="{ row }">
          <code style="font-size: 12px">{{ row.cron }}</code>
        </template>
      </el-table-column>
      <el-table-column label="下次执行" width="160">
        <template #default="{ row }">
          <span>{{ formatTime(row.nextRunAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="envName" label="执行环境" width="150" />
      <el-table-column label="执行范围" min-width="130">
        <template #default="{ row }">{{ formatScope(row) }}</template>
      </el-table-column>
      <el-table-column label="上次执行" width="160">
        <template #default="{ row }">
          <span style="color: #909399">{{ formatTime(row.lastRunAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建人" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled === 1"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="handleRun(row)">立即执行</el-button>
          <el-button size="small" link @click="openEdit(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有定时任务，点击右上角「新建任务」创建" />
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑定时任务' : '新建定时任务'" width="600px" top="6vh">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="任务名" required>
          <el-input v-model="form.name" placeholder="例如：每晚 2 点回归测试" maxlength="100" />
        </el-form-item>
        <el-form-item label="cron 表达式" required>
          <el-input v-model="form.cron" placeholder="0 2 * * * 或 0 0 2 * * *" @change="handleCronChange" />
          <div style="width: 100%">
            <span v-if="cronPreview" style="font-size: 12px; color: #67c23a">
              下次执行：{{ formatTime(cronPreview.nextRunAt) }}
            </span>
            <span v-else-if="cronError" style="font-size: 12px; color: #f56c6c">{{ cronError }}</span>
          </div>
        </el-form-item>
        <el-form-item label="执行环境" required>
          <el-select v-model="form.environmentId" placeholder="请选择执行环境" style="width: 100%">
            <el-option v-for="e in envs" :key="e.id" :label="`${e.name}（${e.baseUrl || '无 Base URL'}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行范围" required>
          <el-radio-group v-model="form.scopeType">
            <el-radio value="all">全部启用用例</el-radio>
            <el-radio value="caseIds">指定用例</el-radio>
          </el-radio-group>
          <el-select
            v-if="form.scopeType === 'caseIds'"
            v-model="form.caseIds"
            multiple
            filterable
            placeholder="选择要执行的用例"
            style="width: 100%; margin-top: 8px"
          >
            <el-option v-for="c in cases" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <div style="width: 100%; font-size: 12px; color: #909399">{{ scopeText }}</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
          <span style="margin-left: 8px; font-size: 12px; color: #909399">停用后不再自动触发</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>