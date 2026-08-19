import request from './request'

export const getGenerationQuality = (projectId) =>
  request.get('/stats/generation-quality', { params: { projectId } })
export const getAttributionAccuracy = (projectId) =>
  request.get('/stats/attribution-accuracy', { params: { projectId } })
export const getGenerationRecords = (projectId, page, size) =>
  request.get('/stats/generation-records', { params: { projectId, page, size } })
