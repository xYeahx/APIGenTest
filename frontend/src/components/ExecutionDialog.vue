<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listEnvironments } from '../api/environment'
import { runExecution } from '../api/execution'
import ExecutionResultView from './ExecutionResultView.vue'

const props = defineProps({
  projectId: { type: Number, required: true },
  scope: { type: Object, required: true },
  scopeText: { type: String, default: '' },
})
const emit = defineEmits(['close', 'finished'])

const envs = ref([])
const envId = ref(null)
const visible = ref(true)
const submitting = ref(false)
const running = ref(false)
const executionId = ref(null)
const resultKey = ref(0)

async function loadEnvs() {
  envs.value = await listEnvironments(props.projectId)
  if (envs.value.length === 1) {
    envId.value = envs.value[0].id
  }
}
onMounted(loadEnvs)

async function start() {
  if (!envId.value) {
    ElMessage.warning('请先选择执行环境')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await runExecution({
      projectId: props.projectId,
      environmentId: envId.value,
      scope: props.scope,
    })
    executionId.value = res.executionId
    running.value = true
    resultKey.value++
    emit('finished')
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  visible.value = false
  if (running.value) {
    ElMessage.warning('任务仍在后台执行，可在「执行历史」中查看结果')
  }
  emit('close')
}

const scopeDesc = {
  caseIds: '选中的用例',
  apiIds: '选中接口下的启用用例',
  all: '项目内全部启用用例',
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="执行测试"
    width="860px"
    top="4vh"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <!-- 提交阶段 -->
    <template v-if="!running">
      <el-alert
        type="info"
        :closable="false"
        :title="`执行范围：${scopeText || scopeDesc[scope.type] || scope.type}`"
        style="margin-bottom: 16px"
      />
      <el-form label-width="90px">
        <el-form-item label="执行环境" required>
          <el-select v-model="envId" placeholder="请选择执行环境" style="width: 320px">
            <el-option v-for="e in envs" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="!envs.length"
        type="warning"
        :closable="false"
        title="该项目还没有环境，请先到「环境管理」创建（Base URL 指向被测系统）"
      />
    </template>

    <!-- 执行结果阶段 -->
    <ExecutionResultView
      v-else
      :key="resultKey"
      :execution-id="executionId"
      auto-refresh
    />

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="!running" type="primary" :loading="submitting" :disabled="!envs.length" @click="start">
        开始执行
      </el-button>
    </template>
  </el-dialog>
</template>