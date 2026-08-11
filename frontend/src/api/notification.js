import request from './request'

export const listNotifications = (params) => request.get('/notifications', { params })
export const getUnreadCount = () => request.get('/notifications/unread-count')
export const markNotificationRead = (id) => request.put(`/notifications/read/${id}`)
export const markAllNotificationsRead = () => request.put('/notifications/read-all')