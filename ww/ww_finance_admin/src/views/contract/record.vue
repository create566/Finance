<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getContractPage,
  getContractDetail,
  revokeContract,
  CONTRACT_TYPE_META,
  CONTRACT_STATUS_META,
  BIZ_TYPE_META,
  type Contract,
  type ContractQuery,
  type ContractDetail,
} from '@/api/contract'

// ------ 搜索 ------
const searchForm = reactive<ContractQuery>({
  contractType: undefined,
  bizType: undefined,
  status: undefined,
  keyword: '',
})

// ------ 列表 ------
const tableData = ref<Contract[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getContractPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      contractType: searchForm.contractType,
      bizType: searchForm.bizType,
      status: searchForm.status,
      keyword: searchForm.keyword,
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}
function handleReset() {
  searchForm.contractType = undefined
  searchForm.bizType = undefined
  searchForm.status = undefined
  searchForm.keyword = ''
  handleSearch()
}
function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}
function handleSizeChange(s: number) {
  pageSize.value = s
  pageNum.value = 1
  fetchList()
}

// ------ 详情 ------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ContractDetail | null>(null)

async function openDetail(row: Contract) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getContractDetail(row.id)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// ------ 作废 ------
const revokeVisible = ref(false)
const revokeSaving = ref(false)
const revokeTarget = ref<Contract | null>(null)
const revokeForm = reactive({ reason: '' })

function openRevoke(row: Contract) {
  revokeTarget.value = row
  revokeForm.reason = ''
  revokeVisible.value = true
}

async function submitRevoke() {
  if (!revokeTarget.value) return
  if (!revokeForm.reason.trim()) {
    ElMessage.warning('请填写作废原因')
    return
  }
  revokeSaving.value = true
  try {
    await revokeContract(revokeTarget.value.id, revokeForm.reason.trim())
    ElMessage.success('合同已作废')
    revokeVisible.value = false
    // 详情抽屉开着时同步刷新，避免看到作废前的旧状态
    if (detailVisible.value && detail.value?.contract.id === revokeTarget.value.id) {
      detail.value = await getContractDetail(revokeTarget.value.id)
    }
    fetchList()
  } catch {
    // 错误提示已由拦截器统一处理
  } finally {
    revokeSaving.value = false
  }
}

// ------ 工具 ------
function typeMeta(t: string) {
  return CONTRACT_TYPE_META[t] || { label: t || '未知', tag: 'info' }
}
function statusMeta(s: number) {
  return CONTRACT_STATUS_META[s] || { label: '未知', tag: 'info' }
}
function bizLabel(bizType: string, bizId: number) {
  return `${BIZ_TYPE_META[bizType] || bizType || '业务'} #${bizId}`
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>合同台账</h2>
      <el-button @click="fetchList">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <el-alert type="info" :closable="false" class="tip-alert">
      <template #title>
        合同由系统按模板 + 业务数据自动渲染生成，<strong>不支持人工修改</strong>。后台职责是台账查询、
        存证哈希校验与异常合同作废 —— 这符合行业惯例：要审的是借款人资质与标的真实性（在「借款列表」「借款人列表」里审），
        而不是已经固化的合同正文。
      </template>
    </el-alert>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="合同类型">
          <el-select v-model="searchForm.contractType" placeholder="全部" clearable style="width: 140px">
            <el-option label="借款合同" value="LOAN" />
            <el-option label="出借协议" value="INVEST" />
            <el-option label="放款确认书" value="PAYMENT" />
            <el-option label="居间服务协议" value="SERVICE" />
            <el-option label="资金存管协议" value="DEPOSIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务对象">
          <el-select v-model="searchForm.bizType" placeholder="全部" clearable style="width: 130px">
            <el-option label="标的" value="LEND" />
            <el-option label="借款申请" value="BORROW_INFO" />
            <el-option label="投资记录" value="LEND_ITEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待签署" :value="0" />
            <el-option label="已签署" :value="1" />
            <el-option label="已作废" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="合同编号 / 甲方 / 乙方"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="page-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="contractNo" label="合同编号" min-width="170" />
        <el-table-column label="合同类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="typeMeta(row.contractType).tag as string" size="small">
              {{ typeMeta(row.contractType).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务对象" min-width="130" align="center">
          <template #default="{ row }">{{ bizLabel(row.bizType, row.bizId) }}</template>
        </el-table-column>
        <el-table-column prop="partyAName" label="甲方" min-width="100" />
        <el-table-column label="乙方" min-width="100">
          <template #default="{ row }">{{ row.partyBName || '—' }}</template>
        </el-table-column>
        <el-table-column label="合同金额" min-width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.amount !== null" class="money">¥{{ Number(row.amount).toLocaleString() }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).tag as string" size="small">
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="165" />
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" type="danger" link @click="openRevoke(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="合同详情 / 签署存证" size="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <!-- 防篡改总判定 -->
          <el-alert
            :type="detail.tamperFree ? 'success' : 'error'"
            :closable="false"
            show-icon
            class="tip-alert"
          >
            <template #title>
              <span v-if="detail.tamperFree">
                哈希校验通过：正文自签署后未被改动，存证有效。
              </span>
              <span v-else>
                <strong>检测到正文与签署时的哈希不一致</strong> —— 该合同在签署后被改动过，存证不可信，
                请立即核查数据库操作记录并联系技术负责人。
              </span>
            </template>
          </el-alert>

          <el-descriptions :column="2" border size="small" class="detail-block">
            <el-descriptions-item label="合同编号">{{ detail.contract.contractNo }}</el-descriptions-item>
            <el-descriptions-item label="合同类型">
              <el-tag :type="typeMeta(detail.contract.contractType).tag as string" size="small">
                {{ typeMeta(detail.contract.contractType).label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="业务对象">
              {{ bizLabel(detail.contract.bizType, detail.contract.bizId) }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusMeta(detail.contract.status).tag as string" size="small">
                {{ statusMeta(detail.contract.status).label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="模板版本">
              v{{ detail.contract.templateVersion }}（模板ID {{ detail.contract.templateId }}）
            </el-descriptions-item>
            <el-descriptions-item label="签署截止">{{ detail.contract.signDeadline || '—' }}</el-descriptions-item>
            <el-descriptions-item label="甲方">{{ detail.contract.partyAName }}</el-descriptions-item>
            <el-descriptions-item label="乙方">{{ detail.contract.partyBName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="合同金额">
              <span v-if="detail.contract.amount !== null" class="money">
                ¥{{ Number(detail.contract.amount).toLocaleString() }}
              </span>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="年化利率">
              {{ detail.contract.lendYearRate !== null ? detail.contract.lendYearRate + '%' : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="期数">
              {{ detail.contract.period !== null ? detail.contract.period + ' 期' : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.contract.createTime }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.contract.revokeReason" label="作废原因" :span="2">
              {{ detail.contract.revokeReason }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 正文（纯文本插值，不走 v-html，避免合同正文里的标签被当成 HTML 执行） -->
          <div class="section-title">合同正文（快照，签署后冻结）</div>
          <pre class="contract-body">{{ detail.content }}</pre>

          <div class="section-title">
            哈希指纹
            <span class="hash-hint">（SHA-256，事后重算一致即证明未被改动）</span>
          </div>
          <div class="hash-box">{{ detail.contract.contentHash }}</div>

          <!-- 签署存证 -->
          <div class="section-title">签署存证（{{ detail.signAudits.length }} 条）</div>
          <el-table v-if="detail.signAudits.length" :data="detail.signAudits" stripe border size="small">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="signerName" label="签署人" min-width="110" />
            <el-table-column prop="signTime" label="签署时间" min-width="165" />
            <el-table-column label="存证哈希" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.valid ? 'success' : 'danger'" size="small">
                  {{ row.valid ? '校验通过' : '已被改动' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="尚无签署存证（合同未签署）" :image-size="70" />

          <!-- 存证明细：IP / UA 用于事后举证 -->
          <template v-if="detail.signRecords.length">
            <div class="section-title">签署环境明细</div>
            <el-descriptions
              v-for="r in detail.signRecords"
              :key="r.id"
              :column="1"
              border
              size="small"
              class="detail-block"
            >
              <el-descriptions-item label="签署人">
                {{ r.signerName }}（{{ r.signerRole === 'PARTY_A' ? '甲方' : '乙方' }}，用户ID {{ r.signerId }}）
              </el-descriptions-item>
              <el-descriptions-item label="签署 IP">{{ r.signIp || '—' }}</el-descriptions-item>
              <el-descriptions-item label="浏览器标识">
                <span class="ua-text">{{ r.userAgent || '—' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="签名图">
                <el-image
                  v-if="r.signImgUrl"
                  :src="r.signImgUrl"
                  :preview-src-list="[r.signImgUrl]"
                  fit="contain"
                  class="sign-img"
                />
                <span v-else>—</span>
              </el-descriptions-item>
            </el-descriptions>
          </template>

          <div class="drawer-footer">
            <el-button
              v-if="detail.contract.status === 0"
              type="danger"
              plain
              @click="openRevoke(detail.contract)"
            >
              作废该合同
            </el-button>
            <el-button @click="detailVisible = false">关闭</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 作废弹窗 -->
    <el-dialog v-model="revokeVisible" title="作废合同" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        title="作废后该合同不可恢复，业务侧会视为未签署（发布 / 投资 / 放款都会被重新拦住）。"
        style="margin-bottom: 16px"
      />
      <el-form label-width="80px">
        <el-form-item label="合同编号">
          <span>{{ revokeTarget?.contractNo }}</span>
        </el-form-item>
        <el-form-item label="作废原因" required>
          <el-input
            v-model="revokeForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="例如：标的要素填错，需重新生成合同"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revokeVisible = false">取消</el-button>
        <el-button type="danger" :loading="revokeSaving" @click="submitRevoke">确认作废</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  width: 100%;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  font-size: 18px;
  font-weight: 500;
  color: #1f2937;
}
.tip-alert {
  margin-bottom: 16px;
}
.search-card {
  margin-bottom: 16px;
  border-radius: 6px;
}
.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.page-card {
  border-radius: 6px;
}
.money {
  color: #f56c6c;
  font-weight: 600;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.detail-block {
  margin-bottom: 16px;
}
.section-title {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
  margin: 18px 0 8px;
}
.hash-hint {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.contract-body {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  margin: 0;
  max-height: 320px;
  overflow-y: auto;
}
.hash-box {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px 12px;
  word-break: break-all;
}
.ua-text {
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}
.sign-img {
  width: 160px;
  height: 70px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}
.drawer-footer {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
