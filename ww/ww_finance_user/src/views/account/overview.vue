<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccount, recharge, withdraw } from '@/api/account'
import { openBankForm } from '@/utils/bankForm'

const router = useRouter()

const account = ref<any>(null)
const loading = ref(false)

async function fetchAccount() {
  loading.value = true
  try {
    account.value = await getAccount()
  } catch {
  } finally {
    loading.value = false
  }
}

/** 可用余额（来自银行） */
function balance(): number {
  return account.value?.amount ?? 0
}

/**
 * 提交银行表单：后端返回的是一整页 HTML，里面有自动提交的 form。
 *
 * 统一走 {@link openBankForm} —— **新窗口**提交，账户总览本页留在原地、登录态不丢。
 * 被拦时 openBankForm 内部已提示放行方式，这里不能再说"申请已提交"。
 * 若返回的不是表单（说明业务校验失败），axios 拦截器已经弹过错误提示，这里不再重复。
 */
function submitBankForm(htmlForm: unknown, successTip: string) {
  const openResult = openBankForm(htmlForm, { onClosed: fetchAccount })
  if (openResult === 'opened' || openResult === 'blocked') return
  ElMessage.success(successTip)
  fetchAccount()
}

function promptAmount(title: string, onOk: (amount: number) => Promise<void>) {
  ElMessageBox.prompt(`请输入${title}金额`, title, {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^\d+(\.\d{1,2})?$/,
    inputErrorMessage: '请输入正确金额',
  })
    .then(async ({ value }) => {
      await onOk(parseFloat(value))
    })
    .catch(() => {})
}

function handleRecharge() {
  promptAmount('充值', async (amount) => {
    const htmlForm = await recharge({ amount })
    submitBankForm(htmlForm, '充值申请已提交，请到资金流水查看进度')
  })
}

function handleWithdraw() {
  promptAmount('提现', async (amount) => {
    // 提现前先比一次余额：能提前拦住就没必要把单子递到银行再被打回
    if (amount > balance()) {
      ElMessage.warning(
        `可用余额不足：本次需 ¥${amount.toFixed(2)}，当前可用 ¥${balance().toFixed(2)}`,
      )
      return
    }
    const htmlForm = await withdraw({ amount })
    submitBankForm(htmlForm, '提现申请已提交，请到资金流水查看进度')
  })
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
        <el-button link type="primary" @click="router.push('/account/flow')">资金流水</el-button>
      </div>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="12">
        <el-card shadow="hover" class="amount-card">
          <div class="amount-label">可用余额</div>
          <div class="amount-value">¥{{ balance().toFixed(2) }}</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" class="amount-card">
          <div class="amount-label">冻结金额</div>
          <div class="amount-value">¥{{ (account?.freezeAmount ?? 0).toFixed(2) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert
      v-if="account && account.bindStatus !== 1"
      title="您还未完成绑卡，无法充值和提现"
      type="warning"
      show-icon
      :closable="false"
      style="margin-top: 16px"
    />
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
