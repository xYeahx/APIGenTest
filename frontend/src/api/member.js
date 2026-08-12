import request from './request'

export const listMembers = (projectId) => request.get(`/projects/${projectId}/members`)
export const addMember = (projectId, data) => request.post(`/projects/${projectId}/members`, data)
export const updateMember = (id, data) => request.put(`/members/${id}`, data)
export const removeMember = (id) => request.delete(`/members/${id}`)