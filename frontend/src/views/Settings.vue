<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listConfigs, updateConfig, testLlmConfig, getCiToken, regenerateCiToken, listAuditLogs } from '../api/admin'
import request from '../api/request'

const configs = ref([])
const loading = ref(false)
const savingKey = ref('')
const testing = ref(false)

// CI 集成状态
const ciInfo = ref({ configured: false, tokenMasked: null, operatorName: null, updatedAt: null })
const ciLoading = ref(false)
const newToken = ref(null)
const tokenDialogVisible = ref(false)

// Webhook 配置
const webhookUrl = ref('')
const webhookEnabled = ref(false)
const webhookSaving = ref(false)
const webhookTesting = ref(false)

// 审计日志
const auditRows = ref([])
const auditTotal = ref(0)
const auditPage = ref(1)
const auditSize = ref(20)
const auditLoading = ref(false)
const auditAction = ref('')
const auditActionLabels = {
  UPDATE_CONFIG: '修改系统配置',
  UPDATE_USER_STATUS: '启用/禁用用户',
  UPDATE_USER_ROLE: '变更用户角色',
  RESET_PASSWORD: '重置密码',
  DELETE_USER: '删除用户',
  REGENERATE_CI_TOKEN: '重新生成CI Token',
}

const keyMeta = {
  llm_api_key: { label: 'LLM API Key', desc: '大模型 API Key（加密存储，仅管理员可见）' },
  llm_model: { label: 'LLM 模型', desc: '例如 qwen-plus / deepseek-chat' },
  llm_base_url: { label: 'LLM Base URL', desc: 'OpenAI 兼容接口地址' },
  default_timeout: { label: '默认超时(ms)', desc: '执行用例默认超时时间' },
  default_retry: { label: '默认重试次数', desc: '用例失败重试次数' },
  llm_temperature: { label: '生成温度', desc: 'AI 生成用例/归因使用的温度参数（0~1，P2 实验对比）' },
  super_admin_invite_code: { label: '超管注册码', desc: '注册页填写该注册码的账号将注册为超级管理员（脱敏存储，可随时修改/清空）' },
}

const ciSample = `curl -X POST http://localhost:8081/api/ci/run \\
  -H "Content-Type: application/json" \\
  -H "X-CI-Token: <CI_TOKEN>" \\
  -d '{"projectId":1,"environmentId":2,"scope":{"type":"all"}}'`

async function load() {
  loading.value = true
  try {
    configs.value = await listConfigs()
  } finally {
    loading.value = false
  }
}

async function loadCi() {
  ciLoading.value = true
  try {
    ciInfo.value = await getCiToken()
  } finally {
    ciLoading.value = false
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const params = { page: auditPage.value, size: auditSize.value }
    if (auditAction.value) params.action = auditAction.value
    const page = await listAuditLogs(params)
    auditRows.value = page.records || []
    auditTotal.value = page.total || 0
  } finally {
    auditLoading.value = false
  }
}

function handleAuditFilter() {
  auditPage.value = 1
  loadAuditLogs()
}

function handleAuditPage(p) {
  auditPage.value = p
  loadAuditLogs()
}

function auditTagType(action) {
  return (
    {
      UPDATE_CONFIG: 'warning',
      UPDATE_USER_STATUS: 'success',
      UPDATE_USER_ROLE: 'primary',
      RESET_PASSWORD: 'warning',
      DELETE_USER: 'danger',
      REGENERATE_CI_TOKEN: 'info',
    }[action] || 'info'
  )
}

onMounted(() => {
  load()
  loadCi()
  loadAuditLogs()
})

async function handleTest() {
  if (testing.value) return
  testing.value = true
  try {
    const res = await testLlmConfig()
    ElMessage.success(`连接成功（${res.model}）模型回复：${res.reply}`)
  } catch (e) {
    // 失败原因已由拦截器弹出
  } finally {
    testing.value = false
  }
}

async function handleSave(row) {
  if (row.configKey === 'llm_api_key' && !row.inputValue) {
    ElMessage.warning('请输入新的 API Key（留空表示不修改）')
    return
  }
  savingKey.value = row.configKey
  try {
    await updateConfig(row.configKey, row.inputValue ?? '')
    ElMessage.success(`${keyMeta[row.configKey]?.label || row.configKey} 已保存`)
    load()
  } finally {
    savingKey.value = ''
  }
}

async function handleRegenerate() {
  await ElMessageBox.confirm(
    '重新生成后旧 Token 立即失效，正在运行的 CI 流水线需要更新配置，确定继续？',
    '重新生成 CI Token',
    { type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消' },
  )
  const res = await regenerateCiToken()
  newToken.value = res.token
  tokenDialogVisible.value = true
  loadCi()
}

function formatAuditTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 19) : '—'
}

async function handleCopy(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function sampleWithToken() {
  return ciSample.replace('<CI_TOKEN>', ciInfo.value.tokenMasked || '<CI_TOKEN>')
}
</script>

<template>
  <div class="settings-page">
    <el-card v-loading="loading">
      <template #header>LLM 配置（仅管理员）</template>
      <el-alert
        type="info"
        :closable="false"
        title="LLM 配置用于 AI 用例生成与失败归因；API Key 仅管理员可见（脱敏展示）"
        style="margin-bottom: 16px"
      />
      <div class="test-bar">
        <span class="test-tip">基于当前已保存的配置发送一条最小请求，验证 API Key / Base URL / 模型是否可用</span>
        <el-button type="primary" plain :loading="testing" @click="handleTest">
          <el-icon style="margin-right: 4px"><Connection /></el-icon>测试 LLM 连接
        </el-button>
      </div>
      <el-table :data="configs" style="margin-top: 12px">
        <el-table-column label="配置项" width="200">
          <template #default="{ row }">{{ keyMeta[row.configKey]?.label || row.configKey }}</template>
        </el-table-column>
        <el-table-column label="说明" min-width="240">
          <template #default="{ row }">
            <span style="color: #909399; font-size: 13px">{{ keyMeta[row.configKey]?.desc || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前值" min-width="220">
          <template #default="{ row }">
            <el-input
              v-model="row.inputValue"
              :type="row.isSecret === 1 ? 'password' : 'text'"
              :placeholder="row.configValue ? `当前：${row.configValue}` : '未配置'"
              :show-password="row.isSecret !== 1"
              clearable
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" :loading="savingKey === row.configKey" @click="handleSave(row)">
              保存
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p style="color: #c0c4cc; font-size: 12px; margin-top: 12px">
        提示：LLM API Key 为敏感配置，输入新值后点击保存即覆盖；留空表示不修改。保存后可在项目详情中使用「AI 生成用例」验证。
      </p>
    </el-card>

    <el-card v-loading="ciLoading" style="margin-top: 16px">
      <template #header>CI 集成（仅管理员）</template>
      <el-alert
        type="info"
        :closable="false"
        title="生成 CI Token 后，可在 Jenkins / GitLab CI 流水线中通过 HTTP 调用触发用例执行，无需登录态"
        style="margin-bottom: 16px"
      />
      <div class="ci-row">
        <span class="ci-label">CI Token</span>
        <el-tag v-if="ciInfo.configured" type="success" size="small">{{ ciInfo.tokenMasked }}</el-tag>
        <el-tag v-else type="info" size="small">未配置</el-tag>
        <span v-if="ciInfo.operatorName" style="color: #909399; font-size: 12px">
          生成人：{{ ciInfo.operatorName }} · 更新时间：{{ ciInfo.updatedAt || '—' }}
        </span>
        <div style="flex: 1"></div>
        <el-button type="primary" plain :loading="ciLoading" @click="handleRegenerate">
          <el-icon style="margin-right: 4px"><Refresh /></el-icon>重新生成
        </el-button>
      </div>

      <h4 style="margin: 16px 0 8px">调用示例（Jenkins / GitLab CI）</h4>
      <pre class="code-block">{{ ciSample }}</pre>
      <el-button size="small" @click="handleCopy(ciSample)">复制示例命令</el-button>

      <h4 style="margin: 16px 0 8px">说明</h4>
      <ul class="ci-notes">
        <li>请求头 <code>X-CI-Token</code> 携带 Token，请求体与页面「执行测试」一致：<code>projectId / environmentId / scope</code>。</li>
        <li>scope.type 支持 <code>all</code>（项目全部启用用例）与 <code>caseIds</code>（指定用例集）。</li>
        <li>触发后返回 <code>executionId</code>，可轮询 <code>GET /api/executions/{id}</code> 获取执行状态；执行记录触发方式显示为「CI」。</li>
        <li>Token 仅管理员可见（脱敏），重新生成后旧 Token 立即失效。</li>
      </ul>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>Webhook 通知（仅管理员）</template>
      <el-alert
        type="info"
        :closable="false"
        title="执行完成 / 定时任务失败 / AI 生成完成时，向企业微信、钉钉或任意 HTTP 服务推送结果摘要"
        style="margin-bottom: 16px"
      />
      <el-form label-width="120px" style="max-width: 720px">
        <el-form-item label="Webhook URL">
          <el-input
            v-model="webhookUrl"
            placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx 或钉钉机器人地址"
          />
        </el-form-item>
        <el-form-item label="启用推送">
          <el-switch v-model="webhookEnabled" />
          <span style="color: #909399; font-size: 12px; margin-left: 8px">
            {{ webhookEnabled ? '已启用，事件发生时将推送 JSON' : '已停用' }}
          </span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="webhookSaving" @click="handleWebhookSave">保存配置</el-button>
          <el-button plain :loading="webhookTesting" @click="handleWebhookTest">发送测试消息</el-button>
        </el-form-item>
      </el-form>
      <p style="color: #c0c4cc; font-size: 12px; margin-top: 4px">
        推送 JSON 示例：{"event":"execution_finished","executionId":1,"projectId":1,"totalCases":10,"passed":9,"failed":1,"passRate":90.0}
      </p>
    </el-card>
    <el-card style="margin-top: 16px">
      <template #header>操作审计日志（仅管理员）</template>
      <el-alert
        type="info"
        :closable="false"
        title="记录系统配置修改、用户状态/角色变更、重置密码、删除用户、重新生成 CI Token 等管理操作"
        style="margin-bottom: 16px"
      />
      <div class="test-bar">
        <el-select
          v-model="auditAction"
          placeholder="按操作类型筛选"
          clearable
          style="width: 220px"
          @change="handleAuditFilter"
        >
          <el-option v-for="(label, key) in auditActionLabels" :key="key" :label="label" :value="key" />
        </el-select>
        <div style="flex: 1"></div>
        <el-button plain :loading="auditLoading" @click="loadAuditLogs">
          <el-icon style="margin-right: 4px"><Refresh /></el-icon>刷新
        </el-button>
      </div>
      <el-table v-loading="auditLoading" :data="auditRows" style="margin-top: 12px" empty-text="暂无审计记录">
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatAuditTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">{{ row.username || 'system' }}</template>
        </el-table-column>
        <el-table-column label="操作类型" width="160">
          <template #default="{ row }">
            <el-tag size="small" :type="auditTagType(row.action)">{{ auditActionLabels[row.action] || row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作对象" min-width="160">
          <template #default="{ row }">{{ row.target || '—' }}</template>
        </el-table-column>
        <el-table-column label="补充说明" min-width="220">
          <template #default="{ row }">
            <span style="color: #606266">{{ row.detail || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="auditTotal > 0"
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="auditTotal"
        :current-page="auditPage"
        :page-size="auditSize"
        @current-change="handleAuditPage"
      />
    </el-card>
    <el-dialog v-model="tokenDialogVisible" title="新 CI Token（仅显示一次）" width="560px" top="10vh">
      <el-alert
        type="warning"
        :closable="false"
        title="请立即复制保存，关闭后无法再次查看完整 Token"
        style="margin-bottom: 12px"
      />
      <pre class="code-block">{{ newToken }}</pre>
      <template #footer>
        <el-button @click="handleCopy(newToken)">复制 Token</el-button>
        <el-button type="primary" @click="tokenDialogVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.test-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.test-tip {
  color: #909399;
  font-size: 13px;
  line-height: 1.5;
}
.ci-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ci-label {
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}
.code-block {
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;
  line-height: 1.7;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.ci-notes {
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
  padding-left: 20px;
}
.ci-notes code {
  background: #f0f2f5;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
</style>