import request from './request'

export const listCases = (projectId, params) => request.get(`/projects/${projectId}/cases`, { params })
export const getCaseDetail = (id) => request.get(`/cases/${id}`)
export const createCase = (data) => request.post('/cases', data)
export const updateCase = (id, data) => request.put(`/cases/${id}`, data)
export const deleteCase = (id) => request.delete(`/cases/${id}`)
export const batchStatusCases = (ids, status) => request.put('/cases/batch-status', { ids, status })
export const batchDeleteCases = (ids) => request.delete('/cases/batch', { data: { ids } })

// 导入导出
export const importCasesFile = (projectId, file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/projects/${projectId}/cases/import`, form)
}
export const exportCases = (projectId, params) =>
  request.get(`/projects/${projectId}/cases/export`, { params, responseType: 'blob' })
export const exportPytest = (projectId, params) =>
  request.get(`/projects/${projectId}/cases/export-pytest`, { params, responseType: 'blob' })