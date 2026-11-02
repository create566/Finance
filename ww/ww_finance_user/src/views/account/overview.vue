<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccount, recharge, withdraw } from '@/api/account'
import { openBankForm } from '@/utils/bankForm'
import AppPage from '@/components/common/AppPage.vue'
import GradientBanner from '@/components/common/GradientBanner.vue'
import MoneyText from '@/components/common/MoneyText.vue'

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
  <AppPage title="账户总览" subtitle="可用余额同步自存管银行账户，实时可用">
    <template #actions>
      <el-button type="primary" @click="handleRecharge">充值</el-button>
      <el-button @click="handleWithdraw">提现</el-button>
      <el-button link type="primary" @click="router.push('/account/flow')">资金流水</el-button>
    </template>

    <div v-loading="loading" class="ov">
      <GradientBanner
        class="ov__hero"
        :title="`¥${balance().toFixed(2)}`"
        desc="可用余额 · 数据来自存管银行账户"
      >
        <div class="ov__freeze">
          <div class="ov__freeze-label">冻结金额</div>
          <div class="ov__freeze-value">
            <MoneyText :value="account?.freezeAmount ?? 0" size="lg" strong />
          </div>
        </div>
      </GradientBanner>

      <div v-if="account && account.bindStatus !== 1" class="ov__notice">
        <el-icon class="ov__notice-icon"><WarningFilled /></el-icon>
        <div class="ov__notice-text">
          <strong>您还未完成绑卡</strong>
          <span>绑卡后方可充值与提现，资金由存管银行独立管理。</span>
        </div>
        <el-button type="primary" plain size="small" @click="router.push('/profile')">
          去绑卡
        </el-button>
      </div>
    </div>
  </AppPage>
</template>

<style scoped>
.ov {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* 余额为主视觉，放大渐变头中的数值 */
.ov__hero :deep(.gbanner__title) {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.5px;
  font-variant-numeric: tabular-nums;
}
.ov__freeze {
  text-align: right;
  color: #fff;
}
.ov__freeze-label {
  font-size: var(--font-xs);
  color: rgba(255, 255, 255, 0.85);
}
.ov__freeze-value {
  margin-top: 6px;
  font-size: var(--font-xl);
}

.ov__notice {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
}
.ov__notice-icon {
  font-size: 18px;
  color: var(--color-warning);
  flex-shrink: 0;
}
.ov__notice-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.ov__notice-text strong {
  font-size: var(--font-sm);
  color: var(--text-primary);
  font-weight: 600;
}
.ov__notice-text span {
  font-size: var(--font-xs);
  color: var(--text-secondary);
}
</style>
