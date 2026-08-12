import request from './request'

export const getProfile = () => request.get('/user/profile')
export const updateProfile = (data) => request.put('/user/profile', data)
export const uploadAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/user/avatar', form)
}