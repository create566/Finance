import { request } from '@/utils/request'

export interface BorrowerAuthDto {
  name: string
  idCard: string
  idCardFrontUrl: string
  idCardBackUrl: string
  sex?: number
  age?: number
  education?: number
  isMarry?: number
  industry?: number
  income?: number
  returnSource?: number
  contactsName?: string
  contactsMobile?: string
  contactsRelation?: number
}

export function submitBorrowerAuth(data: BorrowerAuthDto): Promise<number> {
  return request<number>({
    url: '/core/borrower/auth/submit',
    method: 'post',
    data,
  })
}

export interface BorrowerAuthStatus {
  borrowAuthStatus: number
  borrowerId: number
}

export function getBorrowerAuthStatus(): Promise<BorrowerAuthStatus> {
  return request<BorrowerAuthStatus>({
    url: '/core/borrower/auth/status',
    method: 'get',
  })
}