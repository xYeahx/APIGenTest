import request from './request'

export const getDashboardOverview = () => request.get('/dashboard/overview')