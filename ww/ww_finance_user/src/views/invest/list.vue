<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLendList, getRecommendLends, type RecommendLend } from '@/api/lend'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import LendCard from '@/components/common/LendCard.vue'

const router = useRouter()
const tableData = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

/** 为你推荐：规则打分 + Item-CF 融合，后端保证非空 */
const recommends = ref<RecommendLend[]>([])
const recommendLoading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getLendList({ page: pageNum.value, limit: pageSize.value })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {} finally {
    loading.value = false
  }
}

async function fetchRecommends() {
  recommendLoading.value = true
  try {
    const data = await getRecommendLends(6)
    recommends.value = data.list || []
  } catch {
    // 推荐失败不影响全量列表展示（推荐位不能拖垮主流程）
    recommends.value = []
  } finally {
    recommendLoading.value = false
  }
}

function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}

function goDetail(id: number) {
  router.push(`/invest/detail/${id}`)
}

/** 推荐来源标签：让"为什么推这个"在界面上可见，也便于验证融合策略真的生效 */
const sourceText: Record<string, string> = {
  RULE: '规则推荐',
  ITEM_CF: '协同过滤',
  FUSION: '规则+协同',
  FALLBACK_HOT: '热门',
}

function progressOf(item: RecommendLend): number {
  return Math.min(100, Math.max(0, Math.round(Number(item.progress || 0) * 100)))
}

/** 列表行：剩余可投 = 借款金额 − 已募金额 */
function remainOf(row: any): number {
  return Math.max(0, Number(row?.amount || 0) - Number(row?.investAmount || 0))
}

/** 列表行：募集进度（按借款金额口径） */
function rowProgress(row: any): number {
  const amount = Number(row?.amount || 0)
  if (!amount) return 0
  return Math.min(100, Math.max(0, Math.round((Number(row?.investAmount || 0) / amount) * 100)))
}

function moneyText(v: unknown): string {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(() => {
  fetchList()
  fetchRecommends()
})
</script>

<template>
  <AppPage title="标的列表" subtitle="全部在售借款标的，年化利率与募集进度一目了然">
    <template #actions>
      <el-button link type="primary" @click="fetchList">刷新</el-button>
    </template>

    <AppCard
      v-loading="recommendLoading"
      title="为你推荐"
      subtitle="综合收益率、期限偏好、募集进度与相似投资人行为打分"
      class="rec"
    >
      <div v-if="recommends.length > 0" class="rec__row">
        <LendCard
          v-for="item in recommends"
          :key="item.lend.id"
          mode="similar"
          :name="item.lend.title"
          :badge="sourceText[item.source] || item.source"
          :status="item.lend.status"
          :rate="Number(item.lend.lendYearRate || 0)"
          :period="`${item.lend.period} 个月`"
          :remain-amount="Number(item.remainAmount || 0)"
          :progress="progressOf(item)"
          :progress-text="`剩余可投 ¥${moneyText(item.remainAmount)}`"
          :foot-text="`匹配度 ${Number(item.score || 0).toFixed(3)}`"
          :reason="item.reason"
          :tags="item.tags"
          action-text="查看详情"
          action-plain
          @select="goDetail(item.lend.id)"
          @action="goDetail(item.lend.id)"
        />
      </div>
      <AppEmpty v-else-if="!recommendLoading" description="暂无推荐标的" size="sm" />
    </AppCard>

    <div v-loading="loading" class="lend-grid">
      <LendCard
        v-for="row in tableData"
        :key="row.id"
        :name="row.title"
        :status="row.status"
        :rate="Number(row.lendYearRate || 0)"
        :period="`${row.period} 个月`"
        :remain-amount="remainOf(row)"
        :progress="rowProgress(row)"
        :progress-text="`已募 ¥${moneyText(row.investAmount)}`"
        :foot-text="`目标 ¥${moneyText(row.amount)} · ${row.investNum ?? 0} 人已投`"
        action-text="立即投资"
        @select="goDetail(row.id)"
        @action="goDetail(row.id)"
      />
    </div>

    <AppEmpty v-if="!loading && tableData.length === 0" description="暂无在售标的" />

    <div v-if="total > 0" class="list__pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        background
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </AppPage>
</template>

<style scoped>
.rec {
  margin-bottom: var(--space-4);
}
.rec__row {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 232px;
  gap: var(--space-3);
  overflow-x: auto;
  padding-bottom: 6px;
}

.lend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-4);
  min-height: 80px;
}

.list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-4);
}
</style>
