import request from './request'

export const listConfigs = () => request.get('/admin/configs')
export const updateConfig = (key, value) => request.put(`/admin/configs/${key}`, { value })
export const testLlmConfig = () => request.post('/admin/configs/test-llm', {})
export const getCiToken = () => request.get('/admin/ci/token')
export const regenerateCiToken = () => request.post('/admin/ci/token/regenerate')
export const listAdminUsers = (params) => request.get('/admin/users', { params })
export const updateUserStatus = (id, status) => request.put(`/admin/users/${id}/status`, { status })
export const resetUserPassword = (id, password) => request.put(`/admin/users/${id}/reset-password`, { password })
export const deleteAdminUser = (id) => request.delete(`/admin/users/${id}`)
export const updateUserRole = (id, role) => request.put(`/admin/users/${id}/role`, { role })
export const listAuditLogs = (params) => request.get('/admin/audit-logs', { params })