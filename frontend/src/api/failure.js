import request from './request'

export const getFailure = (detailId) => request.get(`/failures/${detailId}`)
export const analyzeFailure = (detailId) => request.post(`/failures/${detailId}/analyze`, null, { timeout: 90000 })
export const confirmFailure = (id) => request.put(`/failures/${id}/confirm`)