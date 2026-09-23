import { request } from '@/utils/request'

export interface BindInfo {
  bindCode: string
  bindStatus: number
}

export function getBindInfo(): Promise<BindInfo> {
  return request<BindInfo>({
    url: '/user/bind',
    method: 'get',
  })
}

export function getBindStatus(): Promise<any> {
  return request<any>({
    url: '/user/bindStatus',
    method: 'get',
  })
}

export interface BindDirectParams {
  bankNo: string
  bankType?: string
  name?: string
  idCard?: string
}

export function bindDirect(data: BindDirectParams): Promise<null> {
  return request<null>({
    url: '/user/bindDirect',
    method: 'post',
    data,
  })
}