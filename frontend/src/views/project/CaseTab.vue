<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCases,
  getCaseDetail,
  createCase,
  updateCase,
  deleteCase,
  batchStatusCases,
  batchDeleteCases,
  importCasesFile,
  exportCases,
  exportPytest,
} from '../../api/case'
import { listApis } from '../../api/apiInfo'
import ExecutionDialog from '../../components/ExecutionDialog.vue'

const props = defineProps({
  projectId: { type: Number, required: true },
  refreshKey: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false },
})
const emit = defineEmits(['executed'])

const cases = ref([])
const total = ref(0)
const loading = ref(false)
const query = ref({ page: 1, size: 10, keyword: '', scenarioType: '', status: null })
const selection = ref([])

const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref(null)
const form = ref(emptyForm())
const apiOptions = ref([])

const executionVisible = ref(false)
const executionScope = ref(null)
const executionScopeText = ref('')

// 导入导出
const importInput = ref(null)
const importing = ref(false)
const exporting = ref(false)

const scenarioTypes = [
  { value: 'normal', label: '正常' },
  { value: 'boundary', label: '边界' },
  { value: 'exception', label: '异常' },
  { value: 'manual', label: '手动' },
]
const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS']
const methodType = (m) =>
  ({ GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger', PATCH: 'info' })[m] || 'info'

function emptyForm() {
  return {
    apiId: null,
    name: '',
    scenarioType: 'manual',
    method: 'GET',
    urlTemplate: '',
    headers: '',
    queryParams: '',
    body: '',
    asserts: '',
    extractVars: '',
    status: 1,
  }
}

const rules = {
  name: [{ required: true, message: '请输入用例名', trigger: 'blur' }],
  scenarioType: [{ required: true, message: '请选择场景类型', trigger: 'change' }],
  method: [{ required: true, message: '请选择请求方法', trigger: 'change' }],
  urlTemplate: [{ required: true, message: '请输入请求地址', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const page = await listCases(props.projectId, query.value)
    cases.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadApiOptions() {
  const page = await listApis(props.projectId, { page: 1, size: 1000 })
  apiOptions.value = page.records
}

onMounted(() => {
  load()
  loadApiOptions()
})

// AI 生成确认入库后由父组件触发刷新
watch(() => props.refreshKey, () => load())

function openExecSelected() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选用例')
    return
  }
  executionScope.value = { type: 'caseIds', caseIds: selection.value.map((i) => i.id) }
  executionScopeText.value = `${selection.value.length} 条选中用例`
  executionVisible.value = true
}

function openExecAll() {
  executionScope.value = { type: 'all' }
  executionScopeText.value = '项目内全部启用用例'
  executionVisible.value = true
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  const detail = await getCaseDetail(row.id)
  editingId.value = row.id
  fillForm(detail)
  dialogVisible.value = true
}

async function openCopy(row) {
  const detail = await getCaseDetail(row.id)
  editingId.value = null
  fillForm(detail)
  form.value.name = detail.name + '-副本'
  form.value.status = 1
  dialogVisible.value = true
}

function fillForm(detail) {
  form.value = {
    apiId: detail.apiId,
    name: detail.name,
    scenarioType: detail.scenarioType,
    method: detail.method,
    urlTemplate: detail.urlTemplate,
    headers: detail.headers || '',
    queryParams: detail.queryParams || '',
    body: detail.body || '',
    asserts: detail.asserts || '',
    extractVars: detail.extractVars || '',
    status: detail.status,
  }
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      projectId: props.projectId,
      apiId: form.value.apiId || null,
      name: form.value.name,
      scenarioType: form.value.scenarioType,
      method: form.value.method,
      urlTemplate: form.value.urlTemplate,
      headers: form.value.headers || null,
      queryParams: form.value.queryParams || null,
      body: form.value.body || null,
      asserts: form.value.asserts || null,
      extractVars: form.value.extractVars || null,
      status: form.value.status,
    }
    if (editingId.value) {
      await updateCase(editingId.value, payload)
      ElMessage.success('用例已更新')
    } else {
      await createCase(payload)
      ElMessage.success('用例已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除用例「${row.name}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteCase(row.id)
  ElMessage.success('已删除')
  load()
}

async function handleBatchDelete() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选用例')
    return
  }
  await ElMessageBox.confirm(`确定删除选中的 ${selection.value.length} 条用例？`, '批量删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await batchDeleteCases(selection.value.map((i) => i.id))
  ElMessage.success('已删除')
  load()
}

async function handleBatchStatus(status) {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选用例')
    return
  }
  await batchStatusCases(
    selection.value.map((i) => i.id),
    status,
  )
  ElMessage.success(status === 1 ? '已启用' : '已禁用')
  load()
}

async function toggleStatus(row) {
  const detail = await getCaseDetail(row.id)
  const payload = {
    projectId: props.projectId,
    apiId: detail.apiId,
    name: detail.name,
    scenarioType: detail.scenarioType,
    method: detail.method,
    urlTemplate: detail.urlTemplate,
    headers: detail.headers,
    queryParams: detail.queryParams,
    body: detail.body,
    asserts: detail.asserts,
    extractVars: detail.extractVars,
    status: detail.status === 1 ? 0 : 1,
  }
  await updateCase(row.id, payload)
  ElMessage.success(payload.status === 1 ? '已启用' : '已禁用')
  load()
}

function search() {
  query.value.page = 1
  load()
}

function triggerImport() {
  importInput.value?.click()
}

async function handleImportFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  importing.value = true
  try {
    const res = await importCasesFile(props.projectId, file)
    ElMessage.success(`导入成功，共 ${res.total} 条用例`)
    load()
  } finally {
    importing.value = false
    e.target.value = ''
  }
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function doExport(format) {
  exporting.value = true
  try {
    const params = {}
    if (selection.value.length) {
      params.caseIds = selection.value.map((i) => i.id)
    }
    const blob = await exportCases(props.projectId, { ...params, format })
    const ext = format === 'postman' ? 'postman_collection.json' : format === 'openapi' ? 'openapi.json' : 'json'
    downloadBlob(blob, `cases-${props.projectId}.${ext}`)
  } finally {
    exporting.value = false
  }
}

function handleExportCommand(cmd) {
  if (cmd === 'pytest') {
    doExportPytest()
  } else {
    doExport(cmd)
  }
}

async function doExportPytest() {
  const { value: baseUrl } = await ElMessageBox.prompt(
    '请输入被测系统 Base URL（留空则脚本中 BASE_URL 为空，需自行填写）',
    '导出 pytest 脚本',
    { confirmButtonText: '导出', cancelButtonText: '取消', inputPlaceholder: '例如 https://api.example.com' },
  ).catch(() => ({ value: null }))
  if (baseUrl === null) return
  exporting.value = true
  try {
    const params = {}
    if (selection.value.length) {
      params.caseIds = selection.value.map((i) => i.id)
    }
    const blob = await exportPytest(props.projectId, params)
    downloadBlob(blob, 'test_cases.py')
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索用例名 / 请求地址"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.scenarioType" placeholder="场景类型" clearable style="width: 120px" @change="search">
        <el-option v-for="s in scenarioTypes" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="search">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" plain @click="search">搜索</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="!readonly" plain :disabled="!selection.length" @click="handleBatchStatus(1)">批量启用</el-button>
      <el-button v-if="!readonly" plain :disabled="!selection.length" @click="handleBatchStatus(0)">批量禁用</el-button>
      <el-button v-if="!readonly" type="danger" plain :disabled="!selection.length" @click="handleBatchDelete">批量删除</el-button>
      <el-button v-if="!readonly" type="warning" plain :disabled="!selection.length" @click="openExecSelected">
        <el-icon style="margin-right: 4px"><VideoPlay /></el-icon>执行选中
      </el-button>
      <el-button v-if="!readonly" type="warning" plain @click="openExecAll">
        <el-icon style="margin-right: 4px"><CaretRight /></el-icon>执行全部
      </el-button>
      <el-button v-if="!readonly" plain :loading="importing" @click="triggerImport">
        <el-icon style="margin-right: 4px"><Upload /></el-icon>导入用例
      </el-button>
      <el-dropdown v-if="!readonly" trigger="click" :disabled="exporting" @command="handleExportCommand">
        <el-button plain :loading="exporting">
          导出<el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="json">平台 JSON（可再导入）</el-dropdown-item>
            <el-dropdown-item command="postman">Postman Collection</el-dropdown-item>
            <el-dropdown-item command="openapi">OpenAPI 文档</el-dropdown-item>
            <el-dropdown-item command="pytest" divided>pytest 脚本（可独立运行）</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button v-if="!readonly" type="primary" @click="openCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>新建用例
      </el-button>
      <input ref="importInput" type="file" accept=".json" style="display: none" @change="handleImportFile" />
    </div>

    <el-table
      v-loading="loading"
      :data="cases"
      @selection-change="selection = $event"
      style="margin-top: 12px"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column prop="name" label="用例名" min-width="160" show-overflow-tooltip />
      <el-table-column label="场景" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scenarioType === 'exception' ? 'danger' : row.scenarioType === 'boundary' ? 'warning' : 'info'">
            {{ (scenarioTypes.find((s) => s.value === row.scenarioType) || {}).label || row.scenarioType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="方法" width="80">
        <template #default="{ row }">
          <el-tag :type="methodType(row.method)" size="small">{{ row.method }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="apiPath" label="关联接口" min-width="150">
        <template #default="{ row }">
          <code v-if="row.apiPath" style="font-size: 12px">{{ row.apiPath }}</code>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="urlTemplate" label="请求地址" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <code style="font-size: 12px">{{ row.urlTemplate }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!readonly" size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="!readonly" size="small" link @click="openCopy(row)">复制</el-button>
          <el-button v-if="!readonly" size="small" link @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button v-if="!readonly" size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无用例，可手动新建或通过 AI 生成" />
      </template>
    </el-table>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="(p) => { query.page = p; load() }"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用例' : '新建用例'" width="640px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用例名" prop="name">
              <el-input v-model="form.name" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="场景类型" prop="scenarioType">
              <el-select v-model="form.scenarioType" style="width: 100%">
                <el-option v-for="s in scenarioTypes" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="请求方法" prop="method">
              <el-select v-model="form.method" style="width: 100%">
                <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联接口">
              <el-select v-model="form.apiId" clearable placeholder="可选" style="width: 100%">
                <el-option
                  v-for="a in apiOptions"
                  :key="a.id"
                  :label="`${a.method} ${a.path}`"
                  :value="a.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="请求地址" prop="urlTemplate">
          <el-input v-model="form.urlTemplate" placeholder="支持 {{baseUrl}}、{{变量名}} 占位符，如 {{baseUrl}}/api/login" />
        </el-form-item>
        <el-form-item label="请求头">
          <el-input v-model="form.headers" type="textarea" :rows="2" placeholder='JSON，如 {"Content-Type":"application/json"}' />
        </el-form-item>
        <el-form-item label="查询参数">
          <el-input v-model="form.queryParams" type="textarea" :rows="2" placeholder='JSON，如 {"page":1,"size":10}' />
        </el-form-item>
        <el-form-item label="请求体">
          <el-input v-model="form.body" type="textarea" :rows="3" placeholder='JSON，如 {"username":"{{env:username}}","password":"123456"}' />
        </el-form-item>
        <el-form-item label="断言">
          <el-input
            v-model="form.asserts"
            type="textarea"
            :rows="3"
            placeholder='数组，如 [{"type":"statusCode","expect":200},{"type":"field","path":"$.data.token","condition":"notEmpty"}]'
          />
        </el-form-item>
        <el-form-item label="提取变量">
          <el-input
            v-model="form.extractVars"
            type="textarea"
            :rows="2"
            placeholder='数组，如 [{"from":"response","expr":"$.data.token","varName":"token"}]'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <ExecutionDialog
      v-if="executionVisible"
      :project-id="projectId"
      :scope="executionScope"
      :scope-text="executionScopeText"
      @close="executionVisible = false"
      @finished="emit('executed')"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>