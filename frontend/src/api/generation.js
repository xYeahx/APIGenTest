import request from './request'

export const submitGeneration = (apiIds, businessDesc) =>
  request.post('/apis/generate', { apiIds, businessDesc })
export const getGeneration = (taskId) => request.get(`/generations/${taskId}`)
export const confirmGeneration = (taskId) => request.post(`/generations/${taskId}/confirm`, {})