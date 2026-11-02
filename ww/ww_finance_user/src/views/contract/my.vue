<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getMyContracts,
  syncMyContracts,
  CONTRACT_TYPE_META,
  CONTRACT_STATUS_META,
  type Contract,
  type ContractType,
} from '@/api/contract'
import { adaptTagMap } from '@/utils/enumText'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import StatusTag from '@/components/common/StatusTag.vue'

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

/** 合同状态/类型表由 api/contract.ts 拥有，这里只做形状适配（不复制内容） */
const contractStatusMap = adaptTagMap(CONTRACT_STATUS_META)
const contractTypeMap = adaptTagMap(CONTRACT_TYPE_META)

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

onMounted(async () => {
  if (route.query.type) {
    filterType.value = String(route.query.type) as ContractType
  }
  await syncContracts(false)
})
</script>

<template>
  <AppPage
    title="我的合同"
    subtitle="合同正文生成时即冻结并做 SHA-256 存证，签署后不可篡改；未签署将无法发布标的、投资或放款"
  >
    <template #actions>
      <el-button :loading="syncing" @click="syncContracts(true)">同步待签合同</el-button>
    </template>

    <div class="filter">
      <div class="filter__item">
        <span class="filter__label">合同类型</span>
        <el-select v-model="filterType" placeholder="全部" clearable style="width: 160px" @change="handleSearch">
          <el-option v-for="(meta, key) in CONTRACT_TYPE_META" :key="key" :label="meta.text" :value="key" />
        </el-select>
      </div>
      <div class="filter__item">
        <span class="filter__label">状态</span>
        <el-select v-model="filterStatus" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
          <el-option
            v-for="(meta, key) in CONTRACT_STATUS_META"
            :key="key"
            :label="meta.text"
            :value="Number(key)"
          />
        </el-select>
      </div>
      <div class="filter__actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <AppCard body-flush>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="contractNo" label="合同编号" min-width="190" />
        <el-table-column label="合同类型" width="120" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.contractType" :map="contractTypeMap" />
          </template>
        </el-table-column>
        <el-table-column label="合同金额" min-width="130" align="right">
          <template #default="{ row }">
            <MoneyText v-if="row.amount !== null && row.amount !== undefined" :value="row.amount" />
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="签署方" min-width="150">
          <template #default="{ row }">
            <div class="party">甲方：{{ row.partyAName || '-' }}</div>
            <div v-if="row.partyBName" class="party">乙方：{{ row.partyBName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" :map="contractStatusMap" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="生成时间" min-width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">查看</el-button>
            <el-button v-if="row.status === 0" type="danger" link @click="goDetail(row, true)">去签署</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <AppEmpty description="暂无合同记录" />
        </template>
      </el-table>

      <div v-if="total > 0" class="pager">
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
    </AppCard>
  </AppPage>
</template>

<style scoped>
.filter {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
  padding: var(--space-3) var(--space-4);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  margin-bottom: var(--space-4);
}
.filter__item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.filter__label {
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.filter__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}
.party {
  font-size: var(--font-sm);
  line-height: 20px;
  color: var(--text-regular);
}
.muted {
  color: var(--text-disabled);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--border-light);
}
</style>
