import axios from 'axios'
import { ElMessage } from 'element-plus'

// axios 统一实例：baseURL=/api，与 vite 代理及后端统一前缀约定一致
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

function redirectToLogin() {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

request.interceptors.response.use(
  (response) => {
    // 文件下载（blob）直接返回原始数据，不走统一解包
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const res = response.data
    if (res.code === 0) {
      return res.data
    }
    if (res.code === 30001) {
      ElMessage.error('登录已过期，请重新登录')
      redirectToLogin()
      return Promise.reject(new Error(res.message || '未登录'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default request