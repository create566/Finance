<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccount, recharge, withdraw } from '@/api/account'

const account = ref<any>(null)
const loading = ref(false)

async function fetchAccount() {
  loading.value = true
  try {
    account.value = await getAccount()
  } catch {} finally {
    loading.value = false
  }
}

function handleRecharge() {
  ElMessageBox.prompt('请输入充值金额', '充值', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^\d+(\.\d{1,2})?$/,
    inputErrorMessage: '请输入正确金额',
  }).then(async ({ value }) => {
    try {
      const htmlForm = await recharge({ amount: parseFloat(value) })
      if (htmlForm && typeof htmlForm === 'string' && htmlForm.includes('<form')) {
        const newWindow = window.open('', '_blank')
        if (newWindow) {
          newWindow.document.write(htmlForm)
          newWindow.document.close()
        } else {
          ElMessage.warning('请允许弹出窗口以完成充值')
        }
      } else {
        ElMessage.success('充值申请已提交')
        fetchAccount()
      }
    } catch (error: any) {
      if (error.message?.includes('请先完成绑卡')) {
        ElMessage.warning('请先完成银行卡绑定')
      } else {
        ElMessage.error(error.message || '充值失败，请重试')
      }
    }
  }).catch(() => {})
}

function handleWithdraw() {
  ElMessageBox.prompt('请输入提现金额', '提现', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^\d+(\.\d{1,2})?$/,
    inputErrorMessage: '请输入正确金额',
  }).then(async ({ value }) => {
    try {
      const htmlForm = await withdraw({ amount: parseFloat(value) })
      if (htmlForm && typeof htmlForm === 'string' && htmlForm.includes('<form')) {
        const newWindow = window.open('', '_blank')
        if (newWindow) {
          newWindow.document.write(htmlForm)
          newWindow.document.close()
        } else {
          ElMessage.warning('请允许弹出窗口以完成提现')
        }
      } else {
        ElMessage.success('提现申请已提交')
        fetchAccount()
      }
    } catch (error: any) {
      if (error.message?.includes('请先完成绑卡')) {
        ElMessage.warning('请先完成银行卡绑定')
      } else {
        ElMessage.error(error.message || '提现失败，请重试')
      }
    }
  }).catch(() => {})
}

onMounted(fetchAccount)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>账户总览</h2>
      <div>
        <el-button type="primary" @click="handleRecharge">充值</el-button>
        <el-button @click="handleWithdraw">提现</el-button>
      </div>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="12">
        <el-card shadow="hover" class="amount-card">
          <div class="amount-label">可用余额</div>
          <div class="amount-value">¥{{ account?.amount?.toFixed(2) ?? '0.00' }}</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" class="amount-card">
          <div class="amount-label">冻结金额</div>
          <div class="amount-value">¥{{ account?.freezeAmount?.toFixed(2) ?? '0.00' }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
.amount-card { border-radius: 8px; text-align: center; }
.amount-label { font-size: 13px; color: #9ca3af; margin-bottom: 8px; }
.amount-value { font-size: 24px; font-weight: 600; color: #1f2937; }
</style>