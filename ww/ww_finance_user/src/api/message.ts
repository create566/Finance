import { request } from '@/utils/request'

export interface MessageItem {
  id: number
  userId: number
  title: string
  content: string
  /** 消息类型，见 MessageTypeMeta */
  messageType: number
  /** 0未读 1已读 —— 后端字段名是 readFlag（映射列 is_read），不是 isRead */
  readFlag: number
  createTime: string
}

export interface MessageListResult {
  list: MessageItem[]
  total: number
}

export function getMyMessages(params?: { pageNum?: number; pageSize?: number }): Promise<MessageListResult> {
  return request<MessageListResult>({
    url: '/core/message/myList',
    method: 'get',
    params: {
      pageNum: params?.pageNum ?? 1,
      pageSize: params?.pageSize ?? 10,
    },
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

/** 消息类型展示元数据，与后端 MessageTypeEnum 一一对应 */
export const MESSAGE_TYPE_META: Record<number, { text: string; tag: string }> = {
  1: { text: '逾期催收', tag: 'danger' },
  2: { text: '系统通知', tag: 'info' },
  3: { text: '审批通知', tag: 'warning' },
  11: { text: '注册成功', tag: 'info' },
  12: { text: '绑卡成功', tag: 'info' },
  13: { text: '充值成功', tag: 'success' },
  14: { text: '提现成功', tag: 'success' },
  15: { text: '借款审批', tag: 'warning' },
  16: { text: '实名认证', tag: 'warning' },
  17: { text: '标的发布', tag: 'primary' },
  18: { text: '投资成功', tag: 'primary' },
  19: { text: '满标放款', tag: 'success' },
  20: { text: '还款成功', tag: 'success' },
  21: { text: '标的结清', tag: 'success' },
  22: { text: '标的流标', tag: 'info' },
  23: { text: '投资回款', tag: 'success' },
  24: { text: '合同签署', tag: 'primary' },
  25: { text: '撤标', tag: 'info' },
  26: { text: '提现失败', tag: 'danger' },
  27: { text: '充值失败', tag: 'danger' },
  28: { text: '投资人认证', tag: 'warning' },
  29: { text: '标的已下架', tag: 'info' },
  30: { text: '满标待签确认书', tag: 'warning' },
}

export function messageTypeText(type: number): string {
  return MESSAGE_TYPE_META[type]?.text || '通知'
}
