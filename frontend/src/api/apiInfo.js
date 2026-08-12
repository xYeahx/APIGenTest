import request from './request'

// 文件方式导入 OpenAPI
export const importApiFile = (projectId, file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/projects/${projectId}/import`, form)
}

// URL 方式导入 OpenAPI
export const importApiUrl = (projectId, url) => request.post(`/projects/${projectId}/import`, { url })

export const listApis = (projectId, params) => request.get(`/projects/${projectId}/apis`, { params })
export const getApiDetail = (apiId) => request.get(`/apis/${apiId}`)
export const getApiCoverage = (projectId) => request.get(`/projects/${projectId}/coverage`)
export const batchDeleteApis = (ids) => request.delete('/apis/batch', { data: { ids } })