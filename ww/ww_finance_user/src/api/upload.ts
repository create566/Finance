import { request } from '@/utils/request'

export function uploadFile(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return request<string>({
    url: '/oss/file/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteFile(url: string): Promise<null> {
  return request<null>({
    url: '/oss/file/remove',
    method: 'delete',
    params: { url },
  })
}