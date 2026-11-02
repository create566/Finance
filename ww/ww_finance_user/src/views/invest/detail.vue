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
import { returnMethodText, LEND_STATUS_MAP, enumTag } from '@/utils/enumText'
import { openBankForm } from '@/utils/bankForm'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import LendCard from '@/components/common/LendCard.vue'
import MetricRow, { type MetricItem } from '@/components/common/MetricRow.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import SplitPane from '@/components/common/SplitPane.vue'
import StatusTag from '@/components/common/StatusTag.vue'

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

/** 状态文案（供「暂不支持投资」提示复用，与 StatusTag 同源） */
const statusText = computed(() =>
  detail.value ? enumTag(LEND_STATUS_MAP, detail.value.status).text : '未知',
)

/** 概览指标（年化利率等非金额项走 text+unit，避免被当成金额加 ¥） */
const overviewMetrics = computed<MetricItem[]>(() => {
  const d = detail.value
  if (!d) return []
  return [
    { label: '年化利率', text: Number(d.lendYearRate || 0).toFixed(2), unit: '%' },
    { label: '借款期限', text: String(d.period ?? '-'), unit: ' 个月' },
    { label: '借款金额', value: d.amount },
    { label: '剩余可投', value: remainingAmount.value, tone: 'danger' },
  ]
})

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
  <AppPage :title="detail?.title || '标的详情'" :subtitle="`标的编号 ${lendId}`">
    <template #actions>
      <StatusTag v-if="detail" :status="detail.status" kind="lend" size="default" />
      <el-button @click="router.push('/invest/list')">
        <el-icon><ArrowLeft /></el-icon>返回列表
      </el-button>
    </template>

    <el-alert
      v-if="hasPendingSign"
      type="warning"
      show-icon
      :closable="false"
      class="detail__alert"
      title="协议尚未签署"
      :description="pendingSignHint"
    />

    <SplitPane aside="wide" v-loading="loading">
      <template v-if="detail">
        <div class="detail__main">
          <MetricRow :items="overviewMetrics" :cols="2" />

          <AppCard title="标的信息" subtitle="借款用途与还款安排">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="标的编号">{{ detail.id }}</el-descriptions-item>
              <el-descriptions-item label="标的名称">{{ detail.title }}</el-descriptions-item>
              <el-descriptions-item label="还款方式">
                {{ returnMethodText(detail.returnMethod) }}
              </el-descriptions-item>
              <el-descriptions-item label="借款用途">
                {{ detail.lendInfo || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </AppCard>
        </div>
      </template>

      <template #aside>
        <AppCard v-if="detail" title="立即投资" subtitle="签署协议后前往银行完成支付">
          <div v-if="detail.status === 1" class="order">
            <div class="order__progress">
              <span class="order__progress-label">募集进度</span>
              <el-progress :percentage="progressPercent" :stroke-width="14" :text-inside="true" />
            </div>

            <div class="order__row">
              <span class="order__row-label">剩余可投</span>
              <MoneyText :value="remainingAmount" strong />
            </div>

            <div class="order__field">
              <span class="order__row-label">投资金额</span>
              <el-input-number
                v-model="investAmount"
                :min="1000"
                :max="remainingAmount"
                :step="1000"
                controls-position="right"
                style="width: 100%"
              />
            </div>

            <el-button
              type="primary"
              class="order__submit"
              :loading="submitting"
              :disabled="remainingAmount <= 0"
              @click="handleInvest"
            >
              确认投资
            </el-button>

            <p class="order__hint">
              下单前须签署《出借协议》，未签署将被系统拦截；支付由存管银行完成。
            </p>
          </div>

          <AppEmpty
            v-else
            size="sm"
            :description="`该标的当前状态为「${statusText}」，暂不支持投资`"
          />
        </AppCard>
      </template>
    </SplitPane>

    <AppCard
      v-loading="similarLoading"
      title="相似标的"
      subtitle="基于投资行为的协同过滤 + 期限/利率内容相似度"
      class="detail__similar"
    >
      <div v-if="similar.length > 0" class="similar-row">
        <LendCard
          v-for="item in similar"
          :key="item.lend.id"
          mode="similar"
          :name="item.lend.title"
          :status="item.lend.status"
          :rate="Number(item.lend.lendYearRate || 0)"
          :period="`${item.lend.period} 个月`"
          :remain-amount="Number(item.remainAmount || 0)"
          :reason="item.reason"
          :tags="item.tags"
          action-text="查看详情"
          action-plain
          @select="goSimilar(item.lend.id)"
          @action="goSimilar(item.lend.id)"
        />
      </div>
      <AppEmpty v-else-if="!similarLoading" description="暂无相似标的" size="sm" />
    </AppCard>

    <AppEmpty v-if="!detail && !loading" description="标的不存在或已下架" />
  </AppPage>
</template>

<style scoped>
.detail__alert {
  margin-bottom: var(--space-4);
}
.detail__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.detail__similar {
  margin-top: var(--space-4);
}

/* ---------- 右侧下单卡 ---------- */
.order {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.order__progress-label,
.order__row-label {
  display: block;
  font-size: var(--font-xs);
  color: var(--text-placeholder);
  margin-bottom: 6px;
}
.order__row {
  display: flex;
  flex-direction: column;
}
.order__field {
  display: flex;
  flex-direction: column;
}
.order__submit {
  width: 100%;
  height: 40px;
}
.order__hint {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
  line-height: var(--lh-base);
  margin: 0;
}

.similar-row {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 210px;
  gap: var(--space-3);
  overflow-x: auto;
  padding-bottom: 6px;
}
</style>
