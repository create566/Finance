<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getMyInvestments } from '@/api/lend'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import MetricRow, { type MetricItem } from '@/components/common/MetricRow.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const tableData = ref<any[]>([])
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const result = await getMyInvestments()
    tableData.value = result?.list || []
  } catch {} finally {
    loading.value = false
  }
}

/** 列表不分页，指标在前端聚合即可（避免为一条汇总再开接口） */
function sumOf(field: string): number {
  return tableData.value.reduce((acc, row) => acc + Number(row?.[field] || 0), 0)
}

const metrics = computed<MetricItem[]>(() => [
  { label: '投资笔数', text: String(tableData.value.length), unit: ' 笔' },
  { label: '投资总额', value: sumOf('investAmount') },
  { label: '预期收益合计', value: sumOf('expectAmount'), tone: 'primary' },
  {
    label: '已撤标',
    text: String(tableData.value.filter((row) => row?.status === 1).length),
    unit: ' 笔',
  },
])

onMounted(fetchList)
</script>

<template>
  <AppPage title="我的投资" subtitle="你在全部标的上的投资记录与预期收益">
    <MetricRow :items="metrics" class="my-invest__metrics" />

    <AppCard body-flush>
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="lendId" label="标的ID" min-width="90" align="center" />
        <el-table-column prop="investAmount" label="投资金额" min-width="130" align="right">
          <template #default="{ row }">
            <span class="money"><MoneyText :value="row.investAmount" /></span>
          </template>
        </el-table-column>
        <el-table-column prop="lendYearRate" label="年化利率" width="100" align="center">
          <template #default="{ row }">{{ row.lendYearRate }}%</template>
        </el-table-column>
        <el-table-column prop="expectAmount" label="预期收益" min-width="130" align="right">
          <template #default="{ row }">
            <MoneyText :value="row.expectAmount" colored />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" kind="lendItem" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="投资时间" min-width="170" />

        <template #empty>
          <AppEmpty description="你还没有投资记录" />
        </template>
      </el-table>
    </AppCard>
  </AppPage>
</template>

<style scoped>
.my-invest__metrics {
  margin-bottom: var(--space-4);
}
</style>
