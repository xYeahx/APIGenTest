import request from './request'

export const listEnvironments = (projectId) => request.get(`/projects/${projectId}/environments`)
export const createEnvironment = (projectId, data) => request.post(`/projects/${projectId}/environments`, data)
export const updateEnvironment = (id, data) => request.put(`/environments/${id}`, data)
export const deleteEnvironment = (id) => request.delete(`/environments/${id}`)