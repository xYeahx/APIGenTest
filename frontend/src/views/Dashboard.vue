<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const health = ref(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    health.value = await request.get('/health')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <el-card>
      <template #header>欢迎，{{ authStore.nickname }}</template>
      <p style="color: #606266; line-height: 1.8">
        APIGenTest：导入 OpenAPI 文档，使用大模型自动生成可执行的接口测试用例，
        完成用例执行、报告与失败归因的自动化闭环。
      </p>
      <p style="color: #909399; font-size: 13px; margin-top: 8px">
        下一步：进入「项目管理」创建项目并导入接口文档。
      </p>
    </el-card>

    <el-card v-loading="loading" style="margin-top: 16px">
      <template #header>系统健康状态（/api/health）</template>
      <pre v-if="health">{{ JSON.stringify(health, null, 2) }}</pre>
      <el-empty v-else-if="!loading" description="后端未连通" />
    </el-card>
  </div>
</template>