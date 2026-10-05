<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLendList, getRecommendLends, type RecommendLend } from '@/api/lend'
import { LEND_STATUS_MAP, enumTag } from '@/utils/enumText'

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

// 状态文案统一走 utils/enumText.ts 的真源，避免与后端 LendStatusEnum 漂移
// （原先这张表少了 5/6/7，库里「还款中」的标的会显示成「未知」）
const statusTag = (status: number) => enumTag(LEND_STATUS_MAP, status)

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

onMounted(() => {
  fetchList()
  fetchRecommends()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>标的列表</h2></div>

    <el-card shadow="never" class="recommend-card" v-loading="recommendLoading">
      <template #header>
        <div class="recommend-header">
          <span class="recommend-title">为你推荐</span>
          <span class="recommend-sub">
            综合收益率、期限偏好、募集进度与相似投资人行为打分
          </span>
        </div>
      </template>

      <div class="recommend-row" v-if="recommends.length > 0">
        <div
          v-for="item in recommends"
          :key="item.lend.id"
          class="recommend-item"
          @click="goDetail(item.lend.id)"
        >
          <div class="item-top">
            <span class="item-title" :title="item.lend.title">{{ item.lend.title }}</span>
            <el-tag size="small" effect="plain" type="info">{{ sourceText[item.source] || item.source }}</el-tag>
          </div>

          <div class="item-rate">
            <span class="rate-num">{{ item.lend.lendYearRate }}<i>%</i></span>
            <span class="rate-label">{{ item.lend.period }} 个月</span>
          </div>

          <div class="item-reason">「{{ item.reason }}」</div>

          <el-progress
            :percentage="progressOf(item)"
            :stroke-width="8"
            :show-text="false"
            class="item-progress"
          />
          <div class="item-foot">
            <span>剩余 ¥{{ Number(item.remainAmount || 0).toLocaleString() }}</span>
            <span>得分 {{ Number(item.score).toFixed(3) }}</span>
          </div>

          <div class="item-tags">
            <el-tag
              v-for="t in item.tags"
              :key="t"
              size="small"
              effect="light"
              :type="t === '复投' ? 'danger' : t === '协同推荐' ? 'primary' : 'warning'"
            >
              {{ t }}
            </el-tag>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!recommendLoading" description="暂无推荐标的" :image-size="70" />
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
          <el-table-column type="index" label="序号" width="70" align="center" />
          <el-table-column prop="title" label="标的名称" min-width="160" />
          <el-table-column prop="amount" label="借款金额" min-width="120" align="right">
            <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="lendYearRate" label="年化利率" width="100" align="center">
            <template #default="{ row }">{{ row.lendYearRate }}%</template>
          </el-table-column>
          <el-table-column prop="period" label="期限(月)" width="90" align="center">
            <template #default="{ row }">{{ row.period }}个月</template>
          </el-table-column>
          <el-table-column prop="investAmount" label="已募金额" min-width="120" align="right">
            <template #default="{ row }">¥{{ row.investAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="investNum" label="投资人数" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type">
              {{ statusTag(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row.id)">投资</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          background
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }

.recommend-card { margin-bottom: 16px; }
.recommend-header { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.recommend-title { font-size: 15px; font-weight: 600; color: #1f2937; }
.recommend-sub { font-size: 12px; color: #909399; }
.recommend-row {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 6px;
}
.recommend-item {
  flex: 0 0 220px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
  background: #fff;
}
.recommend-item:hover {
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.16);
  border-color: #409eff;
  transform: translateY(-2px);
}
.item-top { display: flex; justify-content: space-between; align-items: center; gap: 6px; }
.item-title {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-rate { margin: 10px 0 6px; display: flex; align-items: baseline; gap: 8px; }
.rate-num { color: #f56c6c; font-size: 22px; font-weight: 700; }
.rate-num i { font-size: 12px; font-style: normal; margin-left: 1px; }
.rate-label { font-size: 12px; color: #909399; }
.item-reason {
  font-size: 12px;
  color: #e6a23c;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-progress { margin-bottom: 6px; }
.item-foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}
.item-tags { display: flex; gap: 4px; flex-wrap: wrap; min-height: 22px; }
</style>
