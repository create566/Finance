import { request } from '@/utils/request'

export interface InvestorAuthDto {
  name: string
  idCard: string
  idCardFrontUrl: string
  idCardBackUrl: string
  sex?: number
  age?: number
}

export function submitInvestorAuth(data: InvestorAuthDto): Promise<number> {
  return request<number>({
    url: '/core/investor/auth/submit',
    method: 'post',
    data,
  })
}

export interface InvestorAuthStatus {
  investAuthStatus: number
  investorId: number
}

export function getInvestorAuthStatus(): Promise<InvestorAuthStatus> {
  return request<InvestorAuthStatus>({
    url: '/core/investor/auth/status',
    method: 'get',
  })
}
