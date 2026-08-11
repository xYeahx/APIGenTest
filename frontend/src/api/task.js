import request from './request'

export const listTasks = (projectId, params) =>
  request.get(`/projects/${projectId}/tasks`, { params })
export const createTask = (projectId, data) =>
  request.post(`/projects/${projectId}/tasks`, data)
export const updateTask = (id, data) => request.put(`/tasks/${id}`, data)
export const updateTaskStatus = (id, enabled) =>
  request.put(`/tasks/${id}/status`, { enabled })
export const deleteTask = (id) => request.delete(`/tasks/${id}`)
export const runTaskNow = (id) => request.post(`/tasks/${id}/run`)
export const previewCron = (cron) => request.post('/tasks/cron-preview', { cron })