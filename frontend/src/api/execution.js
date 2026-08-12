import request from './request'

export const runExecution = (data) => request.post('/executions/run', data)
export const listExecutions = (projectId, params) =>
  request.get('/executions', { params: { projectId, ...params } })
export const getExecution = (id) => request.get(`/executions/${id}`)
export const listExecutionDetails = (id, params) =>
  request.get(`/executions/${id}/details`, { params })
export const getExecutionDetail = (id, detailId) =>
  request.get(`/executions/${id}/details/${detailId}`)
// 单条用例调试重放（同步执行，不落执行记录）
export const debugRunCase = (id, data) => request.post(`/cases/${id}/debug`, data)