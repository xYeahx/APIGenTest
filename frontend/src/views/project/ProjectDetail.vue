<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProject } from '../../api/project'
import {
  listApis,
  importApiFile,
  importApiUrl,
  getApiDetail,
  batchDeleteApis,
} from '../../api/apiInfo'
import {
  listEnvironments,
  createEnvironment,
  updateEnvironment,
  deleteEnvironment,
} from '../../api/environment'
import CaseTab from './CaseTab.vue'
import ExecutionHistory from './ExecutionHistory.vue'
import TaskTab from './TaskTab.vue'
import ReportTab from './ReportTab.vue'
import ExecutionDialog from '../../components/ExecutionDialog.vue'
import GenerationDialog from '../../components/GenerationDialog.vue'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)
const activeTab = ref(route.query.tab || 'api')
const projectInfo = ref(null)

async function loadProject() {
  try {
    projectInfo.value = await getProject(projectId)
  } catch (e) {
    // 无权限等情况由拦截器提示
  }
}
loadProject()
const casesRefreshKey = ref(0)
const generationVisible = ref(false)
const execHistoryRefreshKey = ref(0)
const reportRefreshKey = ref(0)
const execVisible = ref(false)
const execScope = ref(null)
const execScopeText = ref('')

// ---------- 接口管理 ----------
const apis = ref([])
const apiTotal = ref(0)
const apiLoading = ref(false)
const query = ref({ page: 1, size: 10, keyword: '', tag: '' })
const selection = ref([])

const importVisible = ref(false)
const importMode = ref('file')
const importFile = ref(null)
const importUrl = ref('')
const importing = ref(false)

const detailVisible = ref(false)
const detail = ref(null)
const detailLoading = ref(false)

const methodType = (m) =>
  ({ GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger', PATCH: 'info' })[m] || 'info'

async function loadApis() {
  apiLoading.value = true
  try {
    const page = await listApis(projectId, query.value)
    apis.value = page.records
    apiTotal.value = page.total
  } finally {
    apiLoading.value = false
  }
}

function handleFileChange(file) {
  importFile.value = file.raw
}

function handleFileRemove() {
  importFile.value = null
}

async function handleImport() {
  if (importMode.value === 'file' && !importFile.value) {
    ElMessage.warning('请先选择文档文件')
    return
  }
  if (importMode.value === 'url' && !importUrl.value.trim()) {
    ElMessage.warning('请输入文档地址')
    return
  }
  importing.value = true
  try {
    const res =
      importMode.value === 'file'
        ? await importApiFile(projectId, importFile.value)
        : await importApiUrl(projectId, importUrl.value.trim())
    ElMessage.success(`导入成功，共 ${res.total} 个接口`)
    importVisible.value = false
    importFile.value = null
    importUrl.value = ''
    query.value.page = 1
    loadApis()
  } finally {
    importing.value = false
  }
}

async function showDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getApiDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function handleBatchDelete() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选要删除的接口')
    return
  }
  await ElMessageBox.confirm(`确定删除选中的 ${selection.value.length} 个接口？`, '批量删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await batchDeleteApis(selection.value.map((i) => i.id))
  ElMessage.success('已删除')
  loadApis()
}

function search() {
  query.value.page = 1
  loadApis()
}

// ---------- 环境管理 ----------
const envs = ref([])
const envLoading = ref(false)
const envDialogVisible = ref(false)
const editingEnvId = ref(null)
const envSaving = ref(false)
const envFormRef = ref(null)
const envForm = ref({ name: '', baseUrl: '', variables: '' })

const envRules = {
  name: [{ required: true, message: '请输入环境名', trigger: 'blur' }],
  baseUrl: [{ max: 255, message: 'Base URL 最长 255 个字符', trigger: 'blur' }],
}

async function loadEnvs() {
  envLoading.value = true
  try {
    envs.value = await listEnvironments(projectId)
  } finally {
    envLoading.value = false
  }
}

function openEnvCreate() {
  editingEnvId.value = null
  envForm.value = { name: '', baseUrl: '', variables: '' }
  envDialogVisible.value = true
}

function openEnvEdit(row) {
  editingEnvId.value = row.id
  envForm.value = {
    name: row.name,
    baseUrl: row.baseUrl || '',
    variables: row.variables || '',
  }
  envDialogVisible.value = true
}

async function handleEnvSave() {
  await envFormRef.value.validate()
  envSaving.value = true
  try {
    const payload = {
      name: envForm.value.name,
      baseUrl: envForm.value.baseUrl,
      variables: envForm.value.variables || null,
    }
    if (editingEnvId.value) {
      await updateEnvironment(editingEnvId.value, payload)
      ElMessage.success('环境已更新')
    } else {
      await createEnvironment(projectId, payload)
      ElMessage.success('环境已创建')
    }
    envDialogVisible.value = false
    loadEnvs()
  } finally {
    envSaving.value = false
  }
}

async function handleEnvDelete(row) {
  await ElMessageBox.confirm(`确定删除环境「${row.name}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteEnvironment(row.id)
  ElMessage.success('已删除')
  loadEnvs()
}

function openGeneration() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选要生成用例的接口')
    return
  }
  generationVisible.value = true
}

function handleGenerated() {
  casesRefreshKey.value++
  activeTab.value = 'cases'
}

function openExecByApis() {
  if (!selection.value.length) {
    ElMessage.warning('请先勾选要执行的接口')
    return
  }
  execScope.value = { type: 'apiIds', apiIds: selection.value.map((i) => i.id) }
  execScopeText.value = `${selection.value.length} 个选中接口`
  execVisible.value = true
}

function handleExecuted() {
  execHistoryRefreshKey.value++
  reportRefreshKey.value++
}
function prettySpec(spec) {
  if (!spec) return ''
  try {
    return JSON.stringify(JSON.parse(spec), null, 2)
  } catch {
    return spec
  }
}

onMounted(() => {
  loadApis()
  loadEnvs()
})
</script>

<template>
  <div>
    <el-page-header @back="router.push('/projects')" style="margin-bottom: 16px">
      <template #content>
        <div class="proj-head">
          <span style="font-weight: 600">{{ projectInfo?.name || ('项目 #' + projectId) }}</span>
          <el-tag v-if="projectInfo" size="small" type="info" style="margin-left: 8px">{{ projectInfo.apiCount }} 接口</el-tag>
          <el-tag v-if="projectInfo" size="small" type="success" style="margin-left: 4px">{{ projectInfo.caseCount }} 用例</el-tag>
          <el-tag v-if="projectInfo" size="small" type="warning" style="margin-left: 4px">{{ projectInfo.envCount }} 环境</el-tag>
        </div>
      </template>
    </el-page-header>

    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="接口管理" name="api">
          <div class="toolbar">
            <el-input
              v-model="query.keyword"
              placeholder="搜索路径 / 名称 / 描述"
              clearable
              style="width: 260px"
              @keyup.enter="search"
              @clear="search"
            />
            <el-button type="primary" plain @click="search">搜索</el-button>
            <div style="flex: 1"></div>
            <el-button type="danger" plain :disabled="!selection.length" @click="handleBatchDelete">
              批量删除
            </el-button>
            <el-button type="success" :disabled="!selection.length" @click="openGeneration">
              <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI 生成用例
            </el-button>
            <el-button type="warning" :disabled="!selection.length" @click="openExecByApis">
              <el-icon style="margin-right: 4px"><VideoPlay /></el-icon>执行接口用例
            </el-button>
            <el-button type="primary" @click="importVisible = true">
              <el-icon style="margin-right: 4px"><Upload /></el-icon>导入 OpenAPI
            </el-button>
          </div>

          <el-table
            v-loading="apiLoading"
            :data="apis"
            @selection-change="selection = $event"
            style="margin-top: 12px"
          >
            <el-table-column type="selection" width="44" />
            <el-table-column label="方法" width="90">
              <template #default="{ row }">
                <el-tag :type="methodType(row.method)" size="small">{{ row.method }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="path" label="路径" min-width="200" />
            <el-table-column prop="summary" label="名称" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.summary || '—' }}</template>
            </el-table-column>
            <el-table-column prop="tags" label="分组" width="140">
              <template #default="{ row }">{{ row.tags || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="showDetail(row)">详情</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="尚未导入接口文档" />
            </template>
          </el-table>

          <el-pagination
            v-if="apiTotal > 0"
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="apiTotal"
            :current-page="query.page"
            :page-size="query.size"
            @current-change="(p) => { query.page = p; loadApis() }"
          />
        </el-tab-pane>

        <el-tab-pane label="用例管理" name="cases">
          <CaseTab :project-id="projectId" :refresh-key="casesRefreshKey" @executed="handleExecuted" />
        </el-tab-pane>

        <el-tab-pane label="执行历史" name="exec">
          <ExecutionHistory :project-id="projectId" :refresh-key="execHistoryRefreshKey" />
        </el-tab-pane>

        <el-tab-pane label="测试报告" name="report" lazy>
          <ReportTab :project-id="projectId" :refresh-key="reportRefreshKey" />
        </el-tab-pane>

        <el-tab-pane label="定时任务" name="tasks">
          <TaskTab :project-id="projectId" />
        </el-tab-pane>

        <el-tab-pane label="环境管理" name="env">
          <div class="toolbar">
            <span style="color: #909399; font-size: 13px">配置测试 / 预发环境与变量</span>
            <div style="flex: 1"></div>
            <el-button type="primary" @click="openEnvCreate">
              <el-icon style="margin-right: 4px"><Plus /></el-icon>新建环境
            </el-button>
          </div>

          <el-table v-loading="envLoading" :data="envs" style="margin-top: 12px">
            <el-table-column prop="name" label="环境名" width="160" />
            <el-table-column prop="baseUrl" label="Base URL" min-width="220">
              <template #default="{ row }">{{ row.baseUrl || '—' }}</template>
            </el-table-column>
            <el-table-column prop="variables" label="变量" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <code v-if="row.variables" style="font-size: 12px">{{ row.variables }}</code>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openEnvEdit(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleEnvDelete(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="还没有环境，点击右上角「新建环境」" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 导入 OpenAPI 对话框 -->
    <el-dialog v-model="importVisible" title="导入 OpenAPI / Swagger 文档" width="520px">
      <el-radio-group v-model="importMode" style="margin-bottom: 16px">
        <el-radio-button value="file">上传文件</el-radio-button>
        <el-radio-button value="url">填写文档地址</el-radio-button>
      </el-radio-group>

      <el-upload
        v-if="importMode === 'file'"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".json,.yaml,.yml"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        style="width: 100%"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 OpenAPI 3.x / Swagger 2.0，JSON 或 YAML 格式</div>
        </template>
      </el-upload>

      <el-input
        v-else
        v-model="importUrl"
        placeholder="https://example.com/swagger/v3/api-docs"
        clearable
      />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 接口详情对话框 -->
    <el-dialog v-model="detailVisible" title="接口详情" width="680px" top="6vh">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="方法">
              <el-tag :type="methodType(detail.method)" size="small">{{ detail.method }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="分组">{{ detail.tags || '—' }}</el-descriptions-item>
            <el-descriptions-item label="路径" :span="2">
              <code>{{ detail.path }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="名称" :span="2">{{ detail.summary || '—' }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ detail.description || '—' }}</el-descriptions-item>
          </el-descriptions>
          <h4 style="margin: 16px 0 8px">原始 OpenAPI 定义（供 AI 生成使用）</h4>
          <pre class="spec-pre">{{ prettySpec(detail.spec) }}</pre>
        </template>
      </div>
    </el-dialog>

    <!-- AI 生成对话框 -->
    <GenerationDialog
      v-if="generationVisible"
      :project-id="projectId"
      :api-ids="selection.map((i) => i.id)"
      @close="generationVisible = false"
      @confirmed="handleGenerated"
    />

    <!-- 按接口执行对话框 -->
    <ExecutionDialog
      v-if="execVisible"
      :project-id="projectId"
      :scope="execScope"
      :scope-text="execScopeText"
      @close="execVisible = false"
      @finished="handleExecuted"
    />

    <!-- 环境编辑对话框 -->
    <el-dialog v-model="envDialogVisible" :title="editingEnvId ? '编辑环境' : '新建环境'" width="480px">
      <el-form ref="envFormRef" :model="envForm" :rules="envRules" label-width="90px">
        <el-form-item label="环境名" prop="name">
          <el-input v-model="envForm.name" placeholder="例如：测试环境 / 预发环境" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="envForm.baseUrl" placeholder="https://api.example.com" />
        </el-form-item>
        <el-form-item label="环境变量" prop="variables">
          <el-input
            v-model="envForm.variables"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式，例如 {"username":"admin","password":"123456"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="envDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="envSaving" @click="handleEnvSave">保存</el-button>
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

.spec-pre {
  max-height: 300px;
  overflow: auto;
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
}
</style>