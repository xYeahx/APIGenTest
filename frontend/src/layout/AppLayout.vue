<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { listNotifications, getUnreadCount, markNotificationRead, markAllNotificationsRead } from '../api/notification'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const unread = ref(0)
const notifications = ref([])
const notifLoading = ref(false)
const bellVisible = ref(false)
let timer = null

const activeMenu = computed(() => {
  if (route.path.startsWith('/projects')) return '/projects'
  if (route.path.startsWith('/settings')) return '/settings'
  return route.path
})

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(5, 19) : ''
}

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    unread.value = res.count || 0
  } catch (e) {
    // ignore
  }
}

async function openBell() {
  bellVisible.value = true
  notifLoading.value = true
  try {
    const page = await listNotifications({ page: 1, size: 20 })
    notifications.value = page.records
    loadUnread()
  } finally {
    notifLoading.value = false
  }
}

async function handleNotifClick(item) {
  if (item.isRead !== 1) {
    await markNotificationRead(item.id)
    item.isRead = 1
    unread.value = Math.max(0, unread.value - 1)
  }
  bellVisible.value = false
  if (item.projectId) {
    router.push({ path: `/projects/${item.projectId}`, query: { tab: 'exec' } })
  }
}

async function handleReadAll() {
  await markAllNotificationsRead()
  notifications.value.forEach((n) => (n.isRead = 1))
  unread.value = 0
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 30000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">
        <span class="brand-mark">{ }</span>
        <span class="brand-name">APIGenTest</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>概览</span>
        </el-menu-item>
        <el-menu-item index="/projects">
          <el-icon><Folder /></el-icon>
          <span>项目管理</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.userInfo?.role === 2" index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="authStore.userInfo?.role === 2" index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title || 'APIGenTest' }}</span>
        <div class="user-area">
          <el-popover v-model:visible="bellVisible" placement="bottom-end" width="380" trigger="click" @show="openBell">
            <template #reference>
              <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-badge">
                <el-icon class="bell-icon" :size="18"><Bell /></el-icon>
              </el-badge>
            </template>
            <div class="notif-panel">
              <div class="notif-head">
                <span style="font-weight: 600">通知</span>
                <el-button size="small" link type="primary" :disabled="!unread" @click="handleReadAll">
                  全部已读
                </el-button>
              </div>
              <div v-loading="notifLoading" class="notif-list">
                <template v-if="notifications.length">
                  <div
                    v-for="n in notifications"
                    :key="n.id"
                    class="notif-item"
                    :class="{ unread: n.isRead !== 1 }"
                    @click="handleNotifClick(n)"
                  >
                    <div class="notif-title">
                      <span>{{ n.title }}</span>
                      <span v-if="n.isRead !== 1" class="dot" />
                    </div>
                    <div class="notif-content">{{ n.content }}</div>
                    <div class="notif-time">{{ formatTime(n.createdAt) }}</div>
                  </div>
                </template>
                <el-empty v-else description="暂无通知" :image-size="60" />
              </div>
            </div>
          </el-popover>
          <el-tag v-if="authStore.userInfo?.role === 2" size="small" type="warning">管理员</el-tag>
          <span class="user-nick">{{ authStore.nickname }}</span>
          <el-button size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}

.brand {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  border-bottom: 1px solid #e4e7ed;
}

.brand-mark {
  font-family: Consolas, 'Courier New', monospace;
  font-weight: 700;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 15px;
}

.brand-name {
  font-weight: 600;
  color: #303133;
}

.menu {
  border-right: none;
}

.header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-nick {
  font-size: 14px;
  color: #606266;
}

.main {
  background: #f5f7fa;
}

.bell-badge {
  cursor: pointer;
  line-height: 1;
}

.bell-icon {
  color: #606266;
}

.bell-icon:hover {
  color: #409eff;
}

.notif-panel {
  display: flex;
  flex-direction: column;
}

.notif-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  border-bottom: 1px solid #f0f2f5;
}

.notif-list {
  max-height: 360px;
  overflow: auto;
}

.notif-item {
  padding: 10px 6px;
  border-bottom: 1px solid #f5f7fa;
  cursor: pointer;
}

.notif-item:hover {
  background: #f5f7fa;
}

.notif-item.unread {
  background: #ecf5ff;
}

.notif-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.notif-content {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}

.notif-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}
</style>