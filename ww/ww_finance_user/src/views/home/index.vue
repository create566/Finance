<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/api/user'
import { getAccount } from '@/api/account'
import { getBorrowerAuthStatus } from '@/api/borrower'

const router = useRouter()
const userInfo = ref<any>(null)
const account = ref<any>(null)
const authStatus = ref<any>(null)

async function fetchData() {
  try {
    userInfo.value = await getUserInfo()
  } catch {}
  try {
    account.value = await getAccount()
  } catch {}
  try {
    authStatus.value = await getBorrowerAuthStatus()
  } catch {}
}

const isInvestor = computed(() => userInfo.value?.userType === 1)
const isBorrower = computed(() => userInfo.value?.userType === 2)

const authStatusMap: Record<number, { text: string; type: string }> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '认证中', type: 'warning' },
  2: { text: '已通过', type: 'success' },
  [-1]: { text: '认证失败', type: 'danger' },
}

function goInvest() {
  router.push('/invest/list')
}

function goBorrowAuth() {
  router.push('/borrow/auth')
}

function goBorrowApply() {
  router.push('/borrow/apply')
}

function goProfile() {
  router.push('/profile')
}

onMounted(fetchData)
</script>

<template>
  <div class="home-page">
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #36d1dc, #5b86e5)">
            <el-icon :size="28"><Wallet /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">可用余额</div>
            <div class="stat-value">¥{{ account?.amount?.toFixed(2) ?? '0.00' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c)">
            <el-icon :size="28"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">投资金额</div>
            <div class="stat-value">¥{{ account?.investAmount?.toFixed(2) ?? '0.00' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #a18cd1, #fbc2eb)">
            <el-icon :size="28"><Money /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">借款金额</div>
            <div class="stat-value">¥{{ account?.lendAmount?.toFixed(2) ?? '0.00' }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; align-items: center; justify-content: space-between">
              <span style="font-weight: 500">用户信息</span>
              <el-tag
                v-if="userInfo?.userType"
                :type="userInfo.userType === 1 ? 'primary' : 'warning'"
                size="small"
              >
                {{ userInfo.userType === 1 ? '投资人' : '借款人' }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border v-if="userInfo">
            <el-descriptions-item label="手机号">{{ userInfo.mobile }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ userInfo.nickName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="真实姓名">{{ userInfo.name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="积分">{{ userInfo.integral ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="绑卡状态">
              <el-tag :type="userInfo.bindStatus === 1 ? 'success' : 'info'" size="small">
                {{ userInfo.bindStatus === 1 ? '已绑卡' : '未绑卡' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span style="font-weight: 500">认证与账户</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="借款人认证">
              <el-tag :type="(authStatusMap[authStatus?.borrowAuthStatus]?.type as string) || 'info'" size="small">
                {{ authStatusMap[authStatus?.borrowAuthStatus]?.text || '未认证' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="冻结金额">¥{{ account?.freezeAmount?.toFixed(2) ?? '0.00' }}</el-descriptions-item>
            <el-descriptions-item label="待提现金额">¥{{ account?.noWithdrawAmount?.toFixed(2) ?? '0.00' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷操作区 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never" class="action-card">
          <template #header><span style="font-weight: 500">投资服务</span></template>
          <div class="action-grid">
            <div class="action-item" @click="goInvest">
              <div class="action-icon" style="background: linear-gradient(135deg, #36d1dc, #5b86e5)">
                <el-icon :size="24"><TrendCharts /></el-icon>
              </div>
              <span>浏览标的</span>
            </div>
            <div class="action-item" @click="router.push('/invest/my')">
              <div class="action-icon" style="background: linear-gradient(135deg, #667eea, #764ba2)">
                <el-icon :size="24"><DataLine /></el-icon>
              </div>
              <span>我的投资</span>
            </div>
            <div class="action-item" @click="router.push('/invest/return')">
              <div class="action-icon" style="background: linear-gradient(135deg, #fbc2eb, #a18cd1)">
                <el-icon :size="24"><Coin /></el-icon>
              </div>
              <span>回款计划</span>
            </div>
            <div class="action-item" @click="goProfile">
              <div class="action-icon" style="background: linear-gradient(135deg, #d4fc79, #96e6a1)">
                <el-icon :size="24"><CreditCard /></el-icon>
              </div>
              <span>绑卡充值</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="action-card">
          <template #header><span style="font-weight: 500">借款服务</span></template>
          <div class="action-grid">
            <div class="action-item" @click="goBorrowAuth">
              <div class="action-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c)">
                <el-icon :size="24"><Checked /></el-icon>
              </div>
              <span>借款人认证</span>
            </div>
            <div class="action-item" @click="goBorrowApply">
              <div class="action-icon" style="background: linear-gradient(135deg, #ffecd2, #fcb69f)">
                <el-icon :size="24"><EditPen /></el-icon>
              </div>
              <span>借款申请</span>
            </div>
            <div class="action-item" @click="router.push('/borrow/my')">
              <div class="action-icon" style="background: linear-gradient(135deg, #a1c4fd, #c2e9fb)">
                <el-icon :size="24"><List /></el-icon>
              </div>
              <span>我的借款</span>
            </div>
            <div class="action-item" @click="router.push('/borrow/repay')">
              <div class="action-icon" style="background: linear-gradient(135deg, #fad0c4, #ffd1ff)">
                <el-icon :size="24"><Timer /></el-icon>
              </div>
              <span>还款计划</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-card {
  border-radius: 8px;
}
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.stat-label {
  font-size: 13px;
  color: #9ca3af;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #1f2937;
}
.action-card {
  border-radius: 8px;
}
.action-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.action-item:hover {
  background: #f5f7fa;
}
.action-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.action-item span {
  font-size: 13px;
  color: #374151;
}
</style>