import request from './request'

export const getReport = (executionId) => request.get(`/reports/${executionId}`)
export const getTrend = (projectId, limit) =>
  request.get(`/projects/${projectId}/stats/trend`, { params: { limit } })