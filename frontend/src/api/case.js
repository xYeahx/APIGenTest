import request from './request'

export const listCases = (projectId, params) => request.get(`/projects/${projectId}/cases`, { params })
export const getCaseDetail = (id) => request.get(`/cases/${id}`)
export const createCase = (data) => request.post('/cases', data)
export const updateCase = (id, data) => request.put(`/cases/${id}`, data)
export const deleteCase = (id) => request.delete(`/cases/${id}`)
export const batchStatusCases = (ids, status) => request.put('/cases/batch-status', { ids, status })
export const batchDeleteCases = (ids) => request.delete('/cases/batch', { data: { ids } })