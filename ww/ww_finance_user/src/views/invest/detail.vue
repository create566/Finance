<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getLendDetail,
  investLend,
  getSimilarLends,
  type LendDetail,
  type RecommendLend,
} from '@/api/lend'
import { prepareContract } from '@/api/contract'
import { LEND_STATUS_MAP, enumTag } from '@/utils/enumText'
import { openBankForm } from '@/utils/bankForm'

const route = useRoute()
const router = useRouter()

const lendId = Number(route.params.id)
const detail = ref<LendDetail | null>(null)
const loading = ref(false)
const investAmount = ref<number>(1000)
const submitting = ref(false)

/** 相似标的（详情页承接，用 Item-CF + 内容相似度） */
const similar = ref<RecommendLend[]>([])
const similarLoading = ref(false)

/**
 * 未签协议被拦下时，把金额暂存起来；
 * 用户签完回到本页会读到它并自动继续下单，避免"签完还要重新输金额"。
 */
const PENDING_KEY = `pendingInvestAmount:${lendId}`

const hasPendingSign = ref(false)

/**
 * 待签署提示文案。后端 buildInvestForm 有两道关口，顺序是
 * ① 《出借协议》INVEST（每笔投资一份，金额不同即是另一份）
 * ② 《居间服务协议》SERVICE（用户级，首次投标签一次即可）
 * 只引导第一道会把用户卡在第二道上（"去签署"→签完回来→仍被 SERVICE 拦），
 * 所以这里要按后端同一顺序把两道都走完。
 */
const pendingSignHint = ref('请先完成手写签名，签署成功后将自动继续本次投资。')

/**
 * 确保两道关口都已签署。
 * @returns true = 全部已签，可以继续下单；false = 已把用户引导到签署页，请终止本次下单。
 */
async function ensureAgreementsSigned(amount: number): Promise<boolean> {
  // ① 《出借协议》：按 (标的, 金额) 一份。
  const invest = await prepareContract({
    bizType: 'LEND',
    contractType: 'INVEST',
    bizId: lendId,
    amount,
  })
  if (!invest.signed) {
    sessionStorage.setItem(PENDING_KEY, String(amount))
    hasPendingSign.value = true
    pendingSignHint.value = '投资前须先签署《出借协议》。签署完成后将自动返回本页并继续本次投资。'
    try {
      await ElMessageBox.confirm(
        '投资前须先签署《出借协议》。签署完成后将自动返回本页并继续本次投资。',
        '请先签署《出借协议》',
        { confirmButtonText: '去签署', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      sessionStorage.removeItem(PENDING_KEY)
      hasPendingSign.value = false
      return false
    }
    router.push({ path: `/contract/detail/${invest.contract.id}`, query: { sign: '1' } })
    return false
  }

  // ② 《居间服务协议》：用户级一次性协议，biz_type=USER。
  //    后端对 USER 类型不看 bizId（直接取登录态 userId），这里传 0 占位即可。
  //    模板未配置时后端降级返回 data=null（不阻断业务），此时视为无需签署。
  const service = await prepareContract({
    bizType: 'USER',
    contractType: 'SERVICE',
    bizId: 0,
  })
  if (service && !service.signed) {
    sessionStorage.setItem(PENDING_KEY, String(amount))
    hasPendingSign.value = true
    pendingSignHint.value = '投资前须先签署《居间服务协议》。签署完成后将自动返回本页并继续本次投资。'
    try {
      await ElMessageBox.confirm(
        '投资前还须签署《居间服务协议》（平台与你之间的撮合服务协议）。签署完成后将自动返回本页并继续本次投资。',
        '请先签署《居间服务协议》',
        { confirmButtonText: '去签署', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      sessionStorage.removeItem(PENDING_KEY)
      hasPendingSign.value = false
      return false
    }
    router.push({ path: `/contract/detail/${service.contract.id}`, query: { sign: '1' } })
    return false
  }

  return true
}

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await getLendDetail(lendId)
  } catch {
    ElMessage.error('获取标的信息失败')
  } finally {
    loading.value = false
  }
}

async function fetchSimilar() {
  similarLoading.value = true
  try {
    const data = await getSimilarLends(lendId, 5)
    similar.value = data.list || []
  } catch {
    similar.value = []
  } finally {
    similarLoading.value = false
  }
}

const remainingAmount = computed(() => {
  if (!detail.value) return 0
  return Math.max(0, detail.value.amount - (detail.value as any).investAmount)
})

const progressPercent = computed(() => {
  if (!detail.value || detail.value.amount <= 0) return 0
  const invested = (detail.value as any).investAmount || 0
  return Math.min(100, Math.round((invested / detail.value.amount) * 100))
})

// 状态文案统一走 utils/enumText.ts 的真源，避免与后端 LendStatusEnum 漂移
// （原先这张表少了 5/6/7，「还款中」「已完结」「已流标」都会显示成「未知」）
const statusTag = (status: number) => enumTag(LEND_STATUS_MAP, status)

const returnMethodMap: Record<number, string> = {
  1: '等额本息',
  2: '等额本金',
  3: '每月还息一次还本',
  4: '一次还本付息',
}

/**
 * 真正发起下单。
 *
 * 后端返回银行支付表单 HTML 时**开新窗口**提交，而不是把当前标签页整页跳走：
 * 本页（标的详情）留在原地、登录态不丢；用户在新窗口付完款关掉它，本页自动刷新进度。
 * 之所以必须这样，是因为银行回跳是一次全新的整页加载，登录态 cookie 按 host 存，
 * 回跳 host 与实际访问 host 不一致时（127.0.0.1 vs localhost）就会掉到登录页。
 */
async function submitInvest(amount: number) {
  const result = await investLend(lendId, { amount })
  const openResult = openBankForm(result, { onClosed: fetchDetail })
  if (openResult === 'opened' || openResult === 'blocked') {
    // opened ：已在新窗口打开，等用户去银行完成付款后关闭该窗口。
    // blocked：是银行表单但新窗口被拦（多见于"签完协议自动续投"这类没有用户手势的场景），
    //          里面已经提示了放行方式；此时**绝不能谎报成交**，让用户手动再点一次即可。
    return openResult
  }
  ElMessage.success('投资成功')
  router.push('/invest/list')
  return 'not-form' as const
}

/**
 * 下单入口：**先过《出借协议》签署关口**，再过后端已有的余额/满标校验。
 * 关口放在前端是为了给出"去签署"的引导；真正的强校验在后端 buildInvestForm 里，
 * 前端的绕过不会导致未签就成交。
 */
async function handleInvest() {
  if (!investAmount.value || investAmount.value <= 0) {
    ElMessage.warning('请输入投资金额')
    return
  }
  if (investAmount.value > remainingAmount.value) {
    ElMessage.warning('投资金额不能超过剩余可投金额')
    return
  }

  submitting.value = true
  try {
    // 两道关口（《出借协议》→《居间服务协议》）都要过；缺哪道就引导签哪道。
    const ok = await ensureAgreementsSigned(investAmount.value)
    if (!ok) {
      submitting.value = false
      return
    }
    await submitInvest(investAmount.value)
  } catch {
    submitting.value = false
  }
}

/** 从签署页返回后自动续投 */
async function resumePendingInvest() {
  const pending = sessionStorage.getItem(PENDING_KEY)
  if (!pending) return
  hasPendingSign.value = true
  const amount = Number(pending)
  if (!amount || amount <= 0) {
    sessionStorage.removeItem(PENDING_KEY)
    hasPendingSign.value = false
    return
  }
  try {
    // 回来时可能刚签完《出借协议》、但《居间服务协议》还没签，
    // 所以仍走同一套双关口判定，而不是只看 INVEST。
    const ok = await ensureAgreementsSigned(amount)
    if (ok) {
      sessionStorage.removeItem(PENDING_KEY)
      hasPendingSign.value = false
      investAmount.value = amount
      submitting.value = true
      ElMessage.success('协议已签署，继续本次投资')
      const openResult = await submitInvest(amount)
      if (openResult === 'blocked') {
        // 「签完协议自动续投」这条链路没有用户手势，新窗口多半会被浏览器拦截。
        // 金额已经填好，把引导改成交互式的：用户点一次「确认投资」就能走完。
        hasPendingSign.value = true
        pendingSignHint.value = '协议已签署、投资金额已填好。浏览器拦截了新窗口，请点击「确认投资」重新发起。'
        submitting.value = false
      }
    } else {
      submitting.value = false
    }
  } catch {
    submitting.value = false
  }
}

function goSimilar(id: number) {
  router.push(`/invest/detail/${id}`)
}

onMounted(async () => {
  await fetchDetail()
  fetchSimilar()
  await resumePendingInvest()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button type="default" @click="router.push('/invest/list')" :icon="'ArrowLeft'">返回列表</el-button>
    </div>

    <el-alert
      v-if="hasPendingSign"
      type="warning"
      show-icon
      :closable="false"
      class="pending-alert"
      title="协议尚未签署"
      :description="pendingSignHint"
    />

    <el-card shadow="never" v-loading="loading">
      <template v-if="detail">
        <h2 style="margin-top: 0; margin-bottom: 20px;">{{ detail.title }}</h2>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="标的编号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="标的名称">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="借款金额">
            <span class="money">¥{{ detail.amount?.toLocaleString() }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="年化利率">{{ detail.lendYearRate }}%</el-descriptions-item>
          <el-descriptions-item label="借款期限">{{ detail.period }} 个月</el-descriptions-item>
          <el-descriptions-item label="还款方式">
            {{ returnMethodMap[detail.returnMethod] || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detail.status).type">
              {{ statusTag(detail.status).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="借款用途" :span="2">
            {{ detail.lendInfo || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.status === 1" class="invest-section">
          <el-divider />
          <h3>立即投资</h3>
          <div class="invest-progress">
            <span>募集进度：</span>
            <el-progress :percentage="progressPercent" :stroke-width="16" :text-inside="true"
              :color="['#67c23a', '#409eff', '#e6a23c']" style="flex: 1; max-width: 500px;" />
          </div>
          <p class="remain-amount">
            剩余可投：<span class="money">¥{{ remainingAmount.toLocaleString() }}</span>
          </p>

          <el-form :inline="true" @submit.prevent="handleInvest" style="margin-top: 16px;">
            <el-form-item label="投资金额">
              <el-input-number v-model="investAmount" :min="1000" :max="remainingAmount" :step="1000"
                controls-position="right" style="width: 240px;" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleInvest"
                :disabled="remainingAmount <= 0">
                确认投资
              </el-button>
            </el-form-item>
          </el-form>
          <p class="sign-hint">下单前须签署《出借协议》，未签署将被系统拦截。</p>
        </div>

        <div v-else style="margin-top: 20px;">
          <el-alert :title="`该标的当前状态为「${statusTag(detail.status).text}」，暂不支持投资`"
            type="info" show-icon :closable="false" />
        </div>
      </template>

      <el-empty v-else-if="!loading" description="标的不存在或已下架" />
    </el-card>

    <el-card shadow="never" class="similar-card" v-loading="similarLoading">
      <template #header>
        <div class="similar-header">
          <span class="similar-title">相似标的</span>
          <span class="similar-sub">基于投资行为的协同过滤 + 期限/利率内容相似度</span>
        </div>
      </template>

      <div class="similar-row" v-if="similar.length > 0">
        <div v-for="item in similar" :key="item.lend.id" class="similar-item" @click="goSimilar(item.lend.id)">
          <div class="si-title" :title="item.lend.title">{{ item.lend.title }}</div>
          <div class="si-rate">{{ item.lend.lendYearRate }}<i>%</i></div>
          <div class="si-meta">{{ item.lend.period }} 个月 · 剩余 ¥{{ Number(item.remainAmount || 0).toLocaleString() }}</div>
          <div class="si-reason">「{{ item.reason }}」</div>
          <div class="si-tags">
            <el-tag v-for="t in item.tags" :key="t" size="small" effect="light">{{ t }}</el-tag>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!similarLoading" description="暂无相似标的" :image-size="60" />
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  width: 100%;
}
.page-header {
  margin-bottom: 16px;
}
.pending-alert {
  margin-bottom: 16px;
}
.money {
  font-weight: 600;
  color: #e6a23c;
}
.invest-section {
  margin-top: 8px;
}
.invest-section h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.invest-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.remain-amount {
  font-size: 14px;
  color: #606266;
  margin: 4px 0 0 0;
}
.sign-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #909399;
}
.similar-card {
  margin-top: 16px;
}
.similar-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.similar-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.similar-sub {
  font-size: 12px;
  color: #909399;
}
.similar-row {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 6px;
}
.similar-item {
  flex: 0 0 200px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.similar-item:hover {
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.16);
  border-color: #409eff;
}
.si-title {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.si-rate {
  color: #f56c6c;
  font-size: 20px;
  font-weight: 700;
  margin: 8px 0 4px;
}
.si-rate i {
  font-size: 12px;
  font-style: normal;
}
.si-meta {
  font-size: 12px;
  color: #606266;
}
.si-reason {
  font-size: 12px;
  color: #e6a23c;
  margin: 6px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.si-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  min-height: 22px;
}
</style>
