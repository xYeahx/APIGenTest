import { defineStore } from 'pinia'
import request from '../api/request'

// 认证状态：token 与用户信息持久化到 localStorage
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null'),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    nickname: (state) => state.userInfo?.nickname || state.userInfo?.username || '',
  },
  actions: {
    async login(payload) {
      const data = await request.post('/auth/login', payload)
      this.token = data.token
      this.userInfo = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('userInfo', JSON.stringify(data.user))
    },
    async register(payload) {
      return request.post('/auth/register', payload)
    },
    async refreshUserInfo() {
      const me = await request.get('/auth/me')
      this.userInfo = { ...this.userInfo, ...me }
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
      return me
    },
    updateUserInfo(patch) {
      this.userInfo = { ...this.userInfo, ...patch }
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    },
  },
})