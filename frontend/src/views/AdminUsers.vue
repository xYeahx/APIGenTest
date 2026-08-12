<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import { listAdminUsers, updateUserStatus, resetUserPassword, deleteAdminUser, updateUserRole } from '../api/admin'

const authStore = useAuthStore()
const users = ref([])
const total = ref(0)
const loading = ref(false)
const query = ref({ page: 1, size: 10, keyword: '', status: null, role: null })
const resetDialogVisible = ref(false)
const resetTarget = ref(null)
const resetForm = ref({ password: '' })
const resetting = ref(false)
const roleChangingId = ref(null)

const myRole = computed(() => authStore.userInfo?.role || 0)
const isSuper = computed(() => myRole.value === 3)
const isSelf = (row) => authStore.userInfo?.id === row.id
// 仅可管理角色严格低于自己的账号
const canManage = (row) => !isSelf(row) && myRole.value > (row.role || 1)

const roleText = (r) => ({ 1: '普通用户', 2: '管理员', 3: '超级管理员' })[r] || '未知'
const roleType = (r) => ({ 1: 'info', 2: 'warning', 3: 'danger' })[r] || 'info'

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

async function handleRoleChange(row, targetRole) {
  const action = targetRole === 2 ? '设为管理员' : '取消管理员（降为普通用户）'
  try {
    await ElMessageBox.confirm(
      `确定将「${row.username}」${action}？`,
      '角色变更',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch (e) {
    return
  }
  roleChangingId.value = row.id
  try {
    await updateUserRole(row.id, targetRole)
    ElMessage.success(`已将「${row.username}」${action}`)
    load()
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    roleChangingId.value = null
  }
}

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
      <el-select v-if="isSuper" v-model="query.role" placeholder="角色" clearable style="width: 150px" @change="search">
        <el-option label="普通用户" :value="1" />
        <el-option label="管理员" :value="2" />
        <el-option label="超级管理员" :value="3" />
      </el-select>
      <el-button type="primary" plain @click="search">搜索</el-button>
      <div style="flex: 1"></div>
      <span style="color: #909399; font-size: 13px">共 {{ total }} 个账号</span>
    </div>

    <el-alert
      type="info"
      :closable="false"
      style="margin-top: 12px"
      title="三级权限：超级管理员可管理管理员与普通用户；管理员仅可管理普通用户；不能操作同级或更高级别的账号。"
    />

    <el-table v-loading="loading" :data="users" style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="130" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="roleType(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            :disabled="!canManage(row)"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">
          <span style="color: #909399">{{ row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <template v-if="canManage(row)">
            <el-button v-if="isSuper && row.role === 1" size="small" link type="success" :loading="roleChangingId === row.id" @click="handleRoleChange(row, 2)">
              设为管理员
            </el-button>
            <el-button v-if="isSuper && row.role === 2" size="small" link type="warning" :loading="roleChangingId === row.id" @click="handleRoleChange(row, 1)">
              取消管理员
            </el-button>
            <el-button size="small" link type="primary" @click="openReset(row)">重置密码</el-button>
            <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
          <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
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