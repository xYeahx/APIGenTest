<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFailure, analyzeFailure, confirmFailure } from '../api/failure'

const props = defineProps({
  detailId: { type: Number, required: true },
})
const emit = defineEmits(['close'])

const visible = ref(true)
const loading = ref(false)
const analyzing = ref(false)
const confirming = ref(false)
const analysis = ref(null)

const categoryMap = {
  assert_error: { label: '断言问题', type: 'warning' },
  data_error: { label: '数据异常', type: 'danger' },
  env_error: { label: '环境问题', type: 'info' },
  real_defect: { label: '真实缺陷', type: 'danger' },
}

async function load() {
  loading.value = true
  try {
    analysis.value = await getFailure(props.detailId)
  } finally {
    loading.value = false
  }
}

async function startAnalyze() {
  analyzing.value = true
  try {
    analysis.value = await analyzeFailure(props.detailId)
    ElMessage.success('归因分析完成')
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    analyzing.value = false
  }
}

async function handleConfirm() {
  confirming.value = true
  try {
    analysis.value = await confirmFailure(analysis.value.id)
    ElMessage.success('已确认归因结果')
  } finally {
    confirming.value = false
  }
}

function handleClose() {
  visible.value = false
  emit('close')
}

onMounted(load)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="失败归因分析"
    width="620px"
    top="6vh"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-loading="loading" style="min-height: 160px">
      <!-- 未分析 -->
      <template v-if="!loading && !analysis && !analyzing">
        <el-empty description="该失败用例尚未进行归因分析" :image-size="70" />
        <el-alert
          type="info"
          :closable="false"
          title="将调用大模型，基于实际请求 / 响应 / 断言 / 错误信息分析失败原因，并给出定位建议"
          style="margin-top: 8px"
        />
      </template>

      <!-- 分析中 -->
      <div v-else-if="analyzing" style="text-align: center; padding: 32px 0">
        <el-icon class="is-loading" :size="28" style="color: #409eff"><Loading /></el-icon>
        <p style="color: #909399; font-size: 13px; margin-top: 12px">大模型正在分析失败原因（约需 10~30 秒）…</p>
      </div>

      <!-- 分析结果 -->
      <template v-else-if="analysis">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="用例">{{ analysis.caseName || '（用例已删除）' }}</el-descriptions-item>
          <el-descriptions-item label="失败类别">
            <el-tag :type="(categoryMap[analysis.category] || {}).type" size="small">
              {{ (categoryMap[analysis.category] || {}).label || analysis.category }}
            </el-tag>
            <el-tag v-if="analysis.confirmed === 1" type="success" size="small" style="margin-left: 8px">已确认</el-tag>
            <el-tag v-else type="info" size="small" style="margin-left: 8px">待确认</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="错误信息">
            <span style="color: #f56c6c">{{ analysis.errorMessage || '—' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h4 style="margin: 16px 0 8px">分析结论</h4>
        <div class="analysis-box">
          <p><b>原因：</b>{{ analysis.reason }}</p>
          <p style="margin-top: 8px"><b>建议：</b>{{ analysis.suggestion }}</p>
        </div>
        <p style="color: #c0c4cc; font-size: 12px; margin-top: 8px">模型：{{ analysis.llmModel || '—' }}</p>
      </template>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="!analysis && !analyzing" type="primary" :loading="analyzing" @click="startAnalyze">
        开始分析
      </el-button>
      <el-button
        v-else-if="analysis && analysis.confirmed !== 1"
        type="success"
        :loading="confirming"
        @click="handleConfirm"
      >
        确认归因
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.analysis-box {
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.7;
}
</style>