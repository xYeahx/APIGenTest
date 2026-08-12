import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layout/AppLayout.vue'

const routes = [
  {
    path: '/',
    component: AppLayout,
    children: [
      { path: '', redirect: '/dashboard' },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { requiresAuth: true, title: '概览' },
      },
      {
        path: 'projects',
        name: 'projects',
        component: () => import('../views/project/Projects.vue'),
        meta: { requiresAuth: true, title: '项目管理' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/Profile.vue'),
        meta: { requiresAuth: true, title: '个人中心' },
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('../views/Settings.vue'),
        meta: { requiresAuth: true, adminOnly: true, title: '系统设置' },
      },      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('../views/AdminUsers.vue'),
        meta: { requiresAuth: true, adminOnly: true, title: '用户管理' },
      },
      {
        path: 'projects/:id',
        name: 'project-detail',
        component: () => import('../views/project/ProjectDetail.vue'),
        meta: { requiresAuth: true, title: '项目详情' },
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/auth/AuthPage.vue'),
    props: { mode: 'login' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/auth/AuthPage.vue'),
    props: { mode: 'register' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：未登录跳登录页；已登录访问登录/注册页则回首页
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return { path: '/' }
  }
  if (to.meta.adminOnly) {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || 'null')
    if (!userInfo || userInfo.role < 2) {
      return { path: '/dashboard' }
    }
  }
})

export default router