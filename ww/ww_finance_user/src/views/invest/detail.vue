<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLendDetail, investLend, type LendDetail } from '@/api/lend'

const route = useRoute()
const router = useRouter()

const lendId = Number(route.params.id)
const detail = ref<LendDetail | null>(null)
const loading = ref(false)
const investAmount = ref<number>(1000)
const submitting = ref(false)

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

const remainingAmount = computed(() => {
  if (!detail.value) return 0
  return Math.max(0, detail.value.amount - (detail.value as any).investAmount)
})

const progressPercent = computed(() => {
  if (!detail.value || detail.value.amount <= 0) return 0
  const invested = (detail.value as any).investAmount || 0
  return Math.min(100, Math.round((invested / detail.value.amount) * 100))
})

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待发布', type: 'info' },
  1: { text: '募资中', type: 'primary' },
  2: { text: '已完成', type: 'success' },
  3: { text: '已逾期', type: 'danger' },
  4: { text: '已下架', type: 'info' },
}

const returnMethodMap: Record<number, string> = {
  1: '等额本息',
  2: '等额本金',
  3: '每月还息一次还本',
  4: '一次还本付息',
}

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
    const result = await investLend(lendId, { amount: investAmount.value })
    if (typeof result === 'string' && result.includes('<form')) {
      const div = document.createElement('div')
      div.innerHTML = result
      document.body.appendChild(div)
      const form = div.querySelector('form') as HTMLFormElement
      if (form) {
        form.submit()
        return
      }
    }
    ElMessage.success('投资成功')
    router.push('/invest/list')
  } catch {
    submitting.value = false
  }
}

onMounted(fetchDetail)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button type="default" @click="router.push('/invest/list')" :icon="'ArrowLeft'">返回列表</el-button>
    </div>

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
            <el-tag :type="(statusMap[detail.status]?.type as string) || 'info'">
              {{ statusMap[detail.status]?.text || '未知' }}
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
        </div>

        <div v-else style="margin-top: 20px;">
          <el-alert :title="`该标的当前状态为「${statusMap[detail.status]?.text || '未知'}」，暂不支持投资`"
            type="info" show-icon :closable="false" />
        </div>
      </template>

      <el-empty v-else-if="!loading" description="标的不存在或已下架" />
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
</style>