<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProfile, updateProfile, uploadAvatar } from '../api/user'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const profile = ref(null)
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const formRef = ref(null)

const form = ref({ nickname: '', email: '', phone: '' })
const avatarUrl = ref('')

const rules = {
  nickname: [{ max: 50, message: '昵称最长 50 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ max: 50, message: '联系方式最长 50 个字符', trigger: 'blur' }],
}

const roleText = computed(() => ({ 1: '普通用户', 2: '管理员', 3: '超级管理员' })[profile.value?.role] || '—')

async function load() {
  loading.value = true
  try {
    profile.value = await getProfile()
    form.value = {
      nickname: profile.value.nickname || '',
      email: profile.value.email || '',
      phone: profile.value.phone || '',
    }
    avatarUrl.value = profile.value.avatarUrl || ''
  } finally {
    loading.value = false
  }
}

onMounted(load)


async function handleAvatarUpload({ file, onSuccess, onError }) {
  uploading.value = true
  try {
    const url = await uploadAvatar(file)
    avatarUrl.value = url
    authStore.updateUserInfo({ avatarUrl: url })
    ElMessage.success('头像已更新')
    onSuccess()
  } catch (e) {
    onError(e)
  } finally {
    uploading.value = false
  }
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    await updateProfile(form.value)
    authStore.updateUserInfo({ nickname: form.value.nickname })
    ElMessage.success('个人资料已保存')
    await load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="profile-page">
    <el-card v-loading="loading" class="profile-card">
      <template #header>个人中心</template>
      <div class="profile-body">
        <div class="avatar-col">
          <el-upload
            :show-file-list="false"
            :before-upload="(f) => {
              const okType = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(f.type)
              const okSize = f.size <= 2 * 1024 * 1024
              if (!okType) ElMessage.warning('仅支持 jpg / png / webp / gif 图片')
              if (!okSize) ElMessage.warning('头像大小不能超过 2MB')
              return okType && okSize
            }"
            :http-request="handleAvatarUpload"
            :disabled="uploading"
          >
            <el-avatar :size="96" :src="avatarUrl || undefined" class="avatar">
              {{ (form.nickname || profile?.username || '?').slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div class="avatar-tip">
              <el-icon v-if="uploading" class="is-loading"><Loading /></el-icon>
              <span>{{ uploading ? '上传中…' : '点击上传头像' }}</span>
            </div>
          </el-upload>
          <p class="avatar-note">支持 jpg / png / webp / gif，≤ 2MB</p>
          <div class="user-meta">
            <div class="meta-row"><span class="meta-label">用户名</span><span>{{ profile?.username }}</span></div>
            <div class="meta-row"><span class="meta-label">角色</span><el-tag size="small">{{ roleText }}</el-tag></div>
            <div class="meta-row"><span class="meta-label">上次更新</span><span>{{ profile?.updatedAt || '—' }}</span></div>
          </div>
        </div>

        <el-divider direction="vertical" class="profile-divider" />

        <div class="form-col">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" style="max-width: 420px">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" placeholder="展示给其他成员的名称" maxlength="50" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="选填，用于联系与后续邮件通知" maxlength="100" />
            </el-form-item>
            <el-form-item label="联系方式" prop="phone">
              <el-input v-model="form.phone" placeholder="选填，手机号 / 微信等" maxlength="50" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSave">保存资料</el-button>
            </el-form-item>
          </el-form>
          <p class="form-note">填写资料后，项目成员列表会展示头像与昵称，方便团队协作时互相认识。</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  justify-content: center;
}
.profile-card {
  width: 760px;
  max-width: 100%;
}
.profile-body {
  display: flex;
  gap: 32px;
}
.avatar-col {
  width: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.avatar {
  cursor: pointer;
  font-size: 34px;
  background: #ecf5ff;
  color: #409eff;
}
.avatar-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
  font-size: 13px;
}
.avatar-note {
  color: #c0c4cc;
  font-size: 12px;
  margin: 0;
}
.user-meta {
  width: 100%;
  border-top: 1px dashed #ebeef5;
  padding-top: 12px;
  font-size: 13px;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  color: #606266;
}
.meta-label {
  color: #909399;
}
.profile-divider {
  height: auto;
}
.form-col {
  flex: 1;
  min-width: 0;
}
.form-note {
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 4px;
}
</style>