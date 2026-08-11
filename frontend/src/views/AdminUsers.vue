<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import { listAdminUsers, updateUserStatus, resetUserPassword, deleteAdminUser } from '../api/admin'

const authStore = useAuthStore()
const users = ref([])
const total = ref(0)
const loading = ref(false)
const query = ref({ page: 1, size: 10, keyword: '', status: null })
const resetDialogVisible = ref(false)
const resetTarget = ref(null)
const resetForm = ref({ password: '' })
const resetting = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await listAdminUsers(query.value)
    users.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.page = 1
  load()
}

function openReset(row) {
  resetTarget.value = row
  resetForm.value = { password: '' }
  resetDialogVisible.value = true
}

async function handleReset() {
  resetting.value = true
  try {
    const pwd = resetForm.value.password.trim()
    await resetUserPassword(resetTarget.value.id, pwd || '123456')
    ElMessage.success(`已重置「${resetTarget.value.username}」的密码${pwd ? '' : '（默认 123456）'}`)
    resetDialogVisible.value = false
  } finally {
    resetting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」？删除后不可恢复`,
      '删除用户',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch (e) {
    return
  }
  try {
    await deleteAdminUser(row.id)
    ElMessage.success('已删除用户')
    load()
  } catch (e) {
    // 错误提示由拦截器处理
  }
}

async function handleStatusChange(row, val) {
  const action = val ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定${action}用户「${row.username}」？${val ? '' : '禁用后该用户将无法登录'}`,
      action + '用户',
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' },
    )
  } catch (e) {
    row.status = val ? 0 : 1
    return
  }
  try {
    await updateUserStatus(row.id, val ? 1 : 0)
    row.status = val ? 1 : 0
    ElMessage.success(`用户已${action}`)
  } catch (e) {
    row.status = val ? 0 : 1
  }
}

const isSelf = (row) => authStore.userInfo?.id === row.id

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索用户名 / 昵称"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.status" placeholder="账号状态" clearable style="width: 140px" @change="search">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" plain @click="search">搜索</el-button>
      <div style="flex: 1"></div>
      <span style="color: #909399; font-size: 13px">共 {{ total }} 个账号</span>
    </div>

    <el-table v-loading="loading" :data="users" style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="130" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.role === 2" type="warning" size="small">管理员</el-tag>
          <el-tag v-else type="info" size="small">普通用户</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            :disabled="isSelf(row)"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">
          <span style="color: #909399">{{ row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" :disabled="isSelf(row)" @click="openReset(row)">
            重置密码
          </el-button>
          <el-button size="small" link type="danger" :disabled="isSelf(row)" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无用户" />
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

    <el-dialog v-model="resetDialogVisible" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="目标用户">
          <span>{{ resetTarget?.username }}（{{ resetTarget?.nickname }}）</span>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="resetForm.password"
            type="password"
            show-password
            placeholder="留空则重置为默认密码 123456（6~20 位）"
            maxlength="20"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="handleReset">确认重置</el-button>
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