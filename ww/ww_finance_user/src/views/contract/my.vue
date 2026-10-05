<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getMyContracts,
  syncMyContracts,
  CONTRACT_TYPE_META,
  CONTRACT_STATUS_META,
  contractTypeText,
  type Contract,
  type ContractType,
} from '@/api/contract'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const syncing = ref(false)
const list = ref<Contract[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filterType = ref<ContractType | ''>('')
const filterStatus = ref<number | ''>('')

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyContracts({
      contractType: filterType.value || undefined,
      status: filterStatus.value === '' ? undefined : Number(filterStatus.value),
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    list.value = data.list || []
    total.value = data.total || 0
  } catch {
    // request.ts 已统一提示
  } finally {
    loading.value = false
  }
}

/**
 * 补建历史缺失合同。
 * 合同模块上线前已有的标的/投资记录没有合同，若直接去发布/投资会被关口拦下却无从下手，
 * 所以在进入列表时先静默同步一次（幂等，已存在不会重复生成）。
 */
async function syncContracts(showToast = false) {
  syncing.value = true
  try {
    const res = await syncMyContracts()
    if (showToast) {
      ElMessage.success(
        res.created > 0
          ? `同步完成，新增 ${res.created} 份待签合同${res.skipped ? `，跳过 ${res.skipped} 份` : ''}`
          : '同步完成，没有需要补签的合同',
      )
    }
    await fetchList()
  } catch {
    // 同步失败不阻塞列表展示
  } finally {
    syncing.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  filterType.value = ''
  filterStatus.value = ''
  handleSearch()
}

function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}

function goDetail(row: Contract, autoSign = false) {
  router.push({
    path: `/contract/detail/${row.id}`,
    query: autoSign && row.status === 0 ? { sign: '1' } : undefined,
  })
}

function typeTag(t: string) {
  return CONTRACT_TYPE_META[t as ContractType]?.tag || 'info'
}

function statusTag(s: number) {
  return CONTRACT_STATUS_META[s]?.tag || 'info'
}

function statusText(s: number) {
  return CONTRACT_STATUS_META[s]?.text || '未知'
}

onMounted(async () => {
  if (route.query.type) {
    filterType.value = String(route.query.type) as ContractType
  }
  await syncContracts(false)
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>我的合同</h2>
      <p class="sub">
        合同正文在生成时即冻结并做 SHA-256 存证，签署后不可篡改；未签署将无法发布标的、投资或放款。
      </p>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="合同类型">
          <el-select v-model="filterType" placeholder="全部" clearable style="width: 160px" @change="handleSearch">
            <el-option v-for="(meta, key) in CONTRACT_TYPE_META" :key="key" :label="meta.text" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option v-for="(meta, key) in CONTRACT_STATUS_META" :key="key" :label="meta.text" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button :loading="syncing" @click="syncContracts(true)">同步待签合同</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="contractNo" label="合同编号" min-width="190" />
        <el-table-column label="合同类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.contractType)" effect="light">
              {{ contractTypeText(row.contractType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合同金额" min-width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.amount !== null && row.amount !== undefined" class="money">
              ¥{{ Number(row.amount).toFixed(2) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="签署方" min-width="150">
          <template #default="{ row }">
            <div class="party">甲方：{{ row.partyAName || '-' }}</div>
            <div class="party" v-if="row.partyBName">乙方：{{ row.partyBName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="dark">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="生成时间" min-width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">查看</el-button>
            <el-button v-if="row.status === 0" type="danger" link @click="goDetail(row, true)">去签署</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="暂无合同记录" />

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
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
.page-header h2 {
  font-size: 18px;
  font-weight: 500;
  color: #1f2937;
}
.page-header .sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
}
.filter-bar {
  margin-bottom: 12px;
}
.money {
  font-weight: 600;
  color: #e6a23c;
}
.party {
  font-size: 13px;
  line-height: 18px;
  color: #606266;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
