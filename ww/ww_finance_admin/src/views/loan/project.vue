<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getLoanProjectList,
  publishLoanProject,
  publishLoan,
  offlineLoanProject,
  getInvestmentList,
  makeLoan,
  type LoanProjectQuery,
} from '@/api/loanProject'
import type { LoanProject, Investment } from '@/api/loanProject'

// ------ 搜索 ------
const searchForm = reactive<LoanProjectQuery>({
  keyword: '',
  status: undefined,
})

// ------ 列表 ------
const tableData = ref<LoanProject[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getLoanProjectList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword,
      status: searchForm.status,
    })
    tableData.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}
function handleReset() {
  searchForm.keyword = ''
  searchForm.status = undefined
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

// ------ 发布标的弹窗（测试功能） ------
const publishDialogVisible = ref(false)
const publishFormRef = ref<FormInstance>()
const publishForm = reactive({
  title: '',
  borrowerName: '',
  borrowerId: 0,
  amount: 10000,
  rate: 8.0,
  term: 30,
  purpose: '',
  riskLevel: 1,
})

const publishRules: FormRules = {
  title: [{ required: true, message: '请输入标的名称', trigger: 'blur' }],
  borrowerName: [{ required: true, message: '请输入借款人姓名', trigger: 'blur' }],
  borrowerId: [{ required: true, message: '请输入借款人ID', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入标的金额', trigger: 'blur' }],
  rate: [{ required: true, message: '请输入年化利率', trigger: 'blur' }],
  term: [{ required: true, message: '请输入借款期限', trigger: 'blur' }],
  purpose: [{ required: true, message: '请输入借款用途', trigger: 'blur' }],
}

function openPublishDialog() {
  Object.assign(publishForm, {
    title: '',
    borrowerName: '',
    borrowerId: 0,
    amount: 10000,
    rate: 8.0,
    term: 30,
    purpose: '',
    riskLevel: 1,
  })
  publishDialogVisible.value = true
}

function closePublishDialog() {
  publishDialogVisible.value = false
  publishFormRef.value?.resetFields()
}

async function submitPublish() {
  if (!publishFormRef.value) return
  await publishFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await publishLoanProject({
        title: publishForm.title,
        borrowerName: publishForm.borrowerName,
        borrowerId: publishForm.borrowerId,
        amount: publishForm.amount,
        rate: publishForm.rate,
        term: publishForm.term,
        purpose: publishForm.purpose,
        riskLevel: publishForm.riskLevel,
      })
      ElMessage.success('发布成功')
      closePublishDialog()
      fetchList()
    } catch {
      // error 已由拦截器处理
    }
  })
}

// ------ 满标放款 ------
function handleMakeLoan(row: LoanProject) {
  ElMessageBox.confirm(`确定对「${row.title}」执行满标放款吗？资金将从投资人冻结账户划转给借款人。`, '放款确认', {
    type: 'warning',
    confirmButtonText: '确认放款',
  })
    .then(async () => {
      try {
        const formHtml = await makeLoan(row.id)
        // 在新窗口提交银行表单
        const newWindow = window.open('', '_blank')
        if (newWindow) {
          newWindow.document.write(formHtml)
          newWindow.document.close()
          ElMessage.success('放款表单已生成，请在弹出的银行页面完成操作')
        } else {
          ElMessage.warning('请允许弹出窗口以完成放款')
        }
      } catch {
        // error 已由拦截器处理
      }
    })
    .catch(() => {})
}

// ------ 下架 ------
function handleOffline(row: LoanProject) {
  // 有投资记录时必须明确告知"钱会退回去"：这一步会解冻并退还投资人的冻结资金，
  // 且操作人会被记入审计留痕（lend.offline_admin_id），确认框不能只写"确定下架吗"。
  const raised = Number(row.raisedAmount) || 0
  const tip =
    raised > 0
      ? `确定下架「${row.title}」吗？该标的已募集 ¥${raised.toLocaleString()}，`
        + '下架后将通知存管银行解冻，资金原路退回投资人，本次操作会记录操作人员。'
      : `确定下架「${row.title}」吗？`
  ElMessageBox.confirm(tip, raised > 0 ? '资金处置确认' : '提示', {
    type: 'warning',
    confirmButtonText: raised > 0 ? '确认下架并退款' : '确定下架',
  })
    .then(async () => {
      await offlineLoanProject(row.id)
      ElMessage.success(raised > 0 ? '下架成功，冻结资金已退回投资人' : '下架成功')
      fetchList()
    })
    .catch(() => {})
}

// ------ 发布（待发布→募资中）------
function handlePublish(row: LoanProject) {
  ElMessageBox.confirm(`确定发布「${row.title}」吗？发布后用户将可以看到并投资该标的。`, '提示', {
    type: 'warning',
    confirmButtonText: '确定发布',
  })
    .then(async () => {
      await publishLoan(row.id)
      ElMessage.success('发布成功')
      fetchList()
    })
    .catch(() => {})
}

// ------ 详情弹窗 ------
const detailVisible = ref(false)
const detailData = ref<LoanProject | null>(null)

function openDetail(row: LoanProject) {
  detailData.value = row
  detailVisible.value = true
}

// ------ 投资列表弹窗 ------
const investVisible = ref(false)
const investLoading = ref(false)
const investData = ref<Investment[]>([])
const investProject = ref<LoanProject | null>(null)

async function openInvestList(row: LoanProject) {
  investProject.value = row
  investVisible.value = true
  investLoading.value = true
  try {
    investData.value = await getInvestmentList(row.id)
  } finally {
    investLoading.value = false
  }
}

// 投资状态（lend_item.status）—— 与用户端 invest/my.vue 同口径，真源是后端代码：
//   0=募资中（标的不满标）  1=已撤标  2=满标待放款（满标瞬间整标的记录统一置 2）
// 原先把 1 写成「持有中」（实际是「已撤标」，资金已退回），3=已回款 后端从未写过，是死映射。
const investStatusMap: Record<number, { text: string; type: string }> = {
  0: { text: '募资中', type: 'warning' },
  1: { text: '已撤标', type: 'info' },
  2: { text: '满标待放款', type: 'success' },
}

// 投资统计
const investSummary = computed(() => {
  const total = investData.value.reduce((sum, i) => sum + i.amount, 0)
  const expectedReturn = investData.value.reduce((sum, i) => sum + i.expectedReturn, 0)
  return { count: investData.value.length, total, expectedReturn }
})

// ------ 工具 ------
const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待发布', type: 'info' },
  1: { text: '募资中', type: 'warning' },
  2: { text: '满标待放款', type: 'success' },
  3: { text: '已逾期', type: 'danger' },
  4: { text: '已下架', type: 'info' },
  5: { text: '还款中', type: 'primary' },
  6: { text: '已完结', type: 'info' },
  7: { text: '已流标', type: 'danger' },
}
const riskMap: Record<number, { text: string; type: string }> = {
  1: { text: '低风险', type: 'success' },
  2: { text: '中风险', type: 'warning' },
  3: { text: '高风险', type: 'danger' },
}

// ------ 生命周期 ------
onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>标的列表</h2>
      <el-button type="primary" @click="openPublishDialog">
        <el-icon><Plus /></el-icon>
        发布标的
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="标的名称/借款人"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待发布" :value="0" />
            <el-option label="募资中" :value="1" />
            <el-option label="满标待放款" :value="2" />
            <el-option label="已逾期" :value="3" />
            <el-option label="已下架" :value="4" />
            <el-option label="还款中" :value="5" />
            <el-option label="已完结" :value="6" />
            <el-option label="已流标" :value="7" />
          </el-select>
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
        <el-table-column prop="title" label="标的名称" min-width="150" />
        <el-table-column prop="borrowerName" label="借款人" min-width="90" />
        <el-table-column label="标的金额" min-width="120" align="center">
          <template #default="{ row }">
            <span class="money">¥{{ row.amount.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="年化利率" width="90" align="center">
          <template #default="{ row }">{{ row.rate }}%</template>
        </el-table-column>
        <el-table-column label="期限" width="80" align="center">
          <template #default="{ row }">{{ row.term }}天</template>
        </el-table-column>
        <el-table-column label="风险等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="riskMap[row.riskLevel]?.type as string" size="small">
              {{ riskMap[row.riskLevel]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="募集进度" min-width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="14" :text-inside="true" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type as string">
              {{ statusMap[row.status]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上架审核" width="160" align="center">
          <template #default="{ row }">
            <div v-if="row.checkTime || row.checkAdminName" class="audit-cell">
              <div>{{ row.checkAdminName || '—' }}</div>
              <div class="audit-time">{{ row.checkTime || '—' }}</div>
            </div>
            <span v-else class="audit-none">未上架</span>
          </template>
        </el-table-column>
        <el-table-column label="下架处置" width="160" align="center">
          <template #default="{ row }">
            <div v-if="row.offlineTime || row.offlineAdminName" class="audit-cell">
              <div>{{ row.offlineAdminName || '系统自动处置' }}</div>
              <div class="audit-time">{{ row.offlineTime || '—' }}</div>
            </div>
            <span v-else class="audit-none">未处置</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="290" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button type="success" link @click="openInvestList(row)">投资列表</el-button>
            <el-button v-if="row.status === 0" type="warning" link @click="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" type="danger" link @click="handleOffline(row)">下架</el-button>
            <el-button v-if="row.status === 2" type="primary" link @click="handleMakeLoan(row)">放款</el-button>
            <el-button v-if="row.status === 2" type="danger" link @click="handleOffline(row)">退资下架</el-button>
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

    <!-- 发布标的弹窗 -->
    <el-dialog
      v-model="publishDialogVisible"
      title="发布标的（测试）"
      width="520px"
      :close-on-click-modal="false"
      @close="closePublishDialog"
    >
      <el-alert
        type="warning"
        :closable="false"
        title="正常业务中标的是由借款人发布的，此处仅供后台测试使用"
        style="margin-bottom: 16px"
      />
      <el-form ref="publishFormRef" :model="publishForm" :rules="publishRules" label-width="90px">
        <el-form-item label="标的名称" prop="title">
          <el-input v-model="publishForm.title" placeholder="请输入标的名称" />
        </el-form-item>
        <el-form-item label="借款人" prop="borrowerName">
          <el-input v-model="publishForm.borrowerName" placeholder="借款人姓名" />
        </el-form-item>
        <el-form-item label="借款人ID" prop="borrowerId">
          <el-input-number v-model="publishForm.borrowerId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="标的金额" prop="amount">
          <el-input-number v-model="publishForm.amount" :min="1000" :step="1000" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="年化利率" prop="rate">
          <el-input-number v-model="publishForm.rate" :min="0" :step="0.5" :precision="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="借款期限" prop="term">
          <el-input-number v-model="publishForm.term" :min="1" :step="30" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="借款用途" prop="purpose">
          <el-input v-model="publishForm.purpose" placeholder="请输入借款用途" />
        </el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-radio-group v-model="publishForm.riskLevel">
            <el-radio :value="1">低风险</el-radio>
            <el-radio :value="2">中风险</el-radio>
            <el-radio :value="3">高风险</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closePublishDialog">取消</el-button>
        <el-button type="primary" @click="submitPublish">发布</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="标的详情" width="600px">
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item label="标的编号">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="标的名称">{{ detailData.title }}</el-descriptions-item>
        <el-descriptions-item label="借款人">{{ detailData.borrowerName }}</el-descriptions-item>
        <el-descriptions-item label="借款人ID">{{ detailData.borrowerId }}</el-descriptions-item>
        <el-descriptions-item label="标的金额">
          <span class="money">¥{{ detailData.amount.toLocaleString() }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="年化利率">{{ detailData.rate }}%</el-descriptions-item>
        <el-descriptions-item label="借款期限">{{ detailData.term }} 天</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskMap[detailData.riskLevel]?.type as string" size="small">
            {{ riskMap[detailData.riskLevel]?.text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="借款用途">{{ detailData.purpose }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detailData.status]?.type as string">
            {{ statusMap[detailData.status]?.text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已募集金额">¥{{ detailData.raisedAmount.toLocaleString() }}</el-descriptions-item>
        <el-descriptions-item label="募集进度">{{ detailData.progress }}%</el-descriptions-item>
        <el-descriptions-item label="上架审核人">{{ detailData.checkAdminName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="上架审核时间">{{ detailData.checkTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="放款发起人" :span="2">{{ detailData.paymentAdminName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="下架操作人">
          <template v-if="detailData.status === 4">
            <el-tag v-if="detailData.offlineAdminName" size="small">{{ detailData.offlineAdminName }}</el-tag>
            <el-tag v-else size="small" type="info">系统自动处置</el-tag>
          </template>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="下架处置时间">{{ detailData.offlineTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detailData.publishTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="募集截止" :span="2">{{ detailData.endTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 募集进度条 -->
      <div v-if="detailData && (detailData.status === 1 || detailData.status === 2)" class="raise-progress">
        <span class="progress-label">募集进度</span>
        <el-progress
          :percentage="detailData.progress"
          :stroke-width="18"
          :text-inside="true"
          :color="['#67c23a', '#409eff', '#e6a23c']"
        />
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 投资列表弹窗 -->
    <el-dialog
      v-model="investVisible"
      :title="investProject ? `投资列表 - ${investProject.title}` : '投资列表'"
      width="760px"
      :close-on-click-modal="true"
    >
      <!-- 标的概览 -->
      <div v-if="investProject" class="invest-overview">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="标的金额">¥{{ investProject.amount.toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="已募集">¥{{ investProject.raisedAmount.toLocaleString() }}</el-descriptions-item>
          <el-descriptions-item label="募集进度">{{ investProject.progress }}%</el-descriptions-item>
          <el-descriptions-item label="投资人数">{{ investSummary.count }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 统计 -->
      <div class="invest-summary">
        <el-statistic title="总投资金额" :value="investSummary.total" :precision="0" prefix="¥" />
        <el-statistic title="预期总收益" :value="investSummary.expectedReturn" :precision="0" prefix="¥" />
        <el-statistic title="投资笔数" :value="investSummary.count" />
      </div>

      <!-- 投资列表表格 -->
      <el-table :data="investData" v-loading="investLoading" stripe border size="small" style="margin-top: 16px">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="investorName" label="投资人" min-width="100" />
        <el-table-column prop="investorId" label="投资人ID" width="100" align="center" />
        <el-table-column label="投资金额" min-width="120" align="center">
          <template #default="{ row }">
            <span class="money">¥{{ row.amount.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预期收益" min-width="110" align="center">
          <template #default="{ row }">
            <span class="return-text">¥{{ row.expectedReturn.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="投资期限" width="90" align="center">
          <template #default="{ row }">{{ row.term }} 天</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="investStatusMap[row.status]?.type as string" size="small">
              {{ investStatusMap[row.status]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="investTime" label="投资时间" min-width="170" />
      </el-table>

      <div v-if="investData.length === 0 && !investLoading" class="empty-tip">
        暂无投资记录
      </div>

      <template #footer>
        <el-button @click="investVisible = false">关闭</el-button>
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
.audit-cell {
  line-height: 1.5;
}
.audit-time {
  font-size: 12px;
  color: #909399;
}
.audit-none {
  font-size: 12px;
  color: #c0c4cc;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.raise-progress {
  margin-top: 16px;
}
.progress-label {
  font-size: 14px;
  color: #606266;
  margin-right: 12px;
}
.invest-overview {
  margin-bottom: 16px;
}
.invest-summary {
  display: flex;
  gap: 40px;
  padding: 12px 0;
}
.return-text {
  color: #67c23a;
  font-weight: 500;
}
.empty-tip {
  text-align: center;
  padding: 32px 0;
  color: #909399;
  font-size: 14px;
}
</style>