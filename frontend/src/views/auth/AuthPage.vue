<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../store/auth'

const props = defineProps({
  mode: { type: String, default: 'login' }, // login | register
})

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref(null)
const loading = ref(false)
const isLogin = ref(props.mode === 'login')

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  confirmPassword: '',
  inviteCode: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32', trigger: 'blur' },
  ],
  nickname: [{ max: 50, message: '昵称最长 50 个字符', trigger: 'blur' }],
  inviteCode: [{ max: 64, message: '注册码最长 64 个字符', trigger: 'blur' }],
  confirmPassword: [
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    if (isLogin.value) {
      await authStore.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      router.push(route.query.redirect || '/')
    } else {
      await authStore.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        inviteCode: form.inviteCode || undefined,
      })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    }
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}

function switchMode() {
  router.push(isLogin.value ? '/register' : '/login')
}
</script>

<template>
  <div class="auth-page">
    <!-- 代码行装饰：接口调用风格 -->
    <div class="code-line code-line-top">
      <span class="dot"></span>
      <span class="method">{{ isLogin ? 'POST' : 'POST' }}</span>
      <span class="path">{{ isLogin ? '/api/auth/login' : '/api/auth/register' }}</span>
      <span class="arrow">&rarr;</span>
      <span class="status">{{ isLogin ? '200 OK' : '201 Created' }}</span>
    </div>
    <div class="code-line code-line-bottom">// APIGenTest · 接口文档 &rarr; 可执行用例 &rarr; 报告</div>

    <div class="auth-card">
      <div class="brand">
        <span class="brand-mark">{ }</span>
        <h1>APIGenTest</h1>
        <p>基于大模型的接口自动化测试平台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <el-form-item v-if="!isLogin" label="昵称（可选）" prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称，默认使用用户名" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item v-if="!isLogin" label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入密码"
          />
        </el-form-item>
        <el-form-item v-if="!isLogin" label="超级管理员注册码（选填）" prop="inviteCode">
          <el-input
            v-model="form.inviteCode"
            type="password"
            show-password
            placeholder="填写正确注册码则注册为超级管理员，留空为普通用户"
          />
        </el-form-item>
        <el-button
          class="submit-btn"
          type="primary"
          size="large"
          :loading="loading"
          @click="handleSubmit"
        >
          {{ isLogin ? '登 录' : '注 册' }}
        </el-button>
      </el-form>

      <div class="switch-line">
        {{ isLogin ? '还没有账号？' : '已有账号？' }}
        <el-link type="primary" @click="switchMode">{{ isLogin ? '去注册' : '去登录' }}</el-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f9fc;
  overflow: hidden;
}

.code-line {
  position: absolute;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  color: rgba(96, 111, 130, 0.45);
  letter-spacing: 0.3px;
  user-select: none;
}

.code-line-top {
  top: 48px;
  right: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.code-line-bottom {
  bottom: 40px;
  left: 56px;
}

.code-line .method {
  color: rgba(64, 158, 255, 0.6);
}

.code-line .path {
  color: rgba(96, 111, 130, 0.55);
}

.code-line .status {
  color: rgba(103, 194, 58, 0.75);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67c23a;
  animation: pulse 1.6s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.45);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(103, 194, 58, 0);
  }
}

.auth-card {
  width: 400px;
  padding: 40px 36px 32px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(31, 45, 61, 0.08);
}

.brand {
  text-align: center;
  margin-bottom: 28px;
}

.brand-mark {
  display: inline-block;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 8px;
  padding: 4px 10px;
  margin-bottom: 12px;
}

.brand h1 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0.5px;
}

.brand p {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.switch-line {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
</style>