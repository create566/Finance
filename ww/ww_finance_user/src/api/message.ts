import { request } from '@/utils/request'

export interface MessageItem {
  id: number
  title: string
  content: string
  isRead: number
  createTime: string
}

export interface MessageListResult {
  list: MessageItem[]
  total: number
}

export function getMyMessages(params?: { page?: number; limit?: number }): Promise<MessageListResult> {
  return request<MessageListResult>({
    url: '/core/message/myList',
    method: 'get',
    params: { page: params?.page ?? 1, limit: params?.limit ?? 10 },
  })
}

export function getUnreadCount(): Promise<number> {
  return request<number>({
    url: '/core/message/unreadCount',
    method: 'get',
  })
}

export function readMessage(id: number): Promise<null> {
  return request<null>({
    url: `/core/message/read/${id}`,
    method: 'put',
  })
}