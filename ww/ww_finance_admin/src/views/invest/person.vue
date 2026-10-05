<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getInvestorList, auditInvestor, type InvestorQuery, type Investor } from '@/api/investor'

// ------ 搜索 ------
const searchForm = reactive<InvestorQuery>({
  keyword: '',
  auditStatus: undefined,
})

// ------ 列表 ------
const tableData = ref<Investor[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getInvestorList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword,
      auditStatus: searchForm.auditStatus,
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {
    tableData.value = []
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
  searchForm.auditStatus = undefined
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

// ------ 审核弹窗 ------
const auditDialogVisible = ref(false)
const auditRow = ref<any>(null)
const auditForm = reactive({
  auditStatus: 2,
  idCardCorrect: 1,
})

function openAuditDialog(row: any) {
  auditRow.value = row
  auditForm.auditStatus = 2
  auditForm.idCardCorrect = 1
  auditDialogVisible.value = true
}

async function submitAudit() {
  if (!auditRow.value) return
  try {
    await auditInvestor(auditRow.value.id, auditForm.auditStatus)
    ElMessage.success(auditForm.auditStatus === 2 ? '审核通过' : '已拒绝')
    auditDialogVisible.value = false
    fetchList()
  } catch {
    // error 已由拦截器处理
  }
}

// ------ 详情弹窗 ------
const detailVisible = ref(false)
const detailData = ref<any>(null)

function openDetail(row: any) {
  detailData.value = row
  detailVisible.value = true
}

// ------ 工具 ------
const auditMap: Record<number, { text: string; type: string }> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '认证中', type: 'warning' },
  2: { text: '已通过', type: 'success' },
  [-1]: { text: '已拒绝', type: 'danger' },
}
function genderText(g: number) {
  return g === 1 ? '男' : g === 0 ? '女' : '未知'
}

// ------ 生命周期 ------
onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>投资人列表</h2>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="姓名/手机号/身份证"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="searchForm.auditStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="认证中" :value="1" />
            <el-option label="已通过" :value="2" />
            <el-option label="已拒绝" :value="-1" />
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
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="idCard" label="身份证号" min-width="180" />
        <el-table-column prop="mobile" label="手机号" min-width="130" />
        <el-table-column label="性别" width="70" align="center">
          <template #default="{ row }">{{ genderText(row.sex) }}</template>
        </el-table-column>
        <el-table-column label="年龄" width="70" align="center" prop="age" />
        <el-table-column label="认证状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(auditMap[row.status]?.type as string) || 'info'">
              {{ auditMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 1" type="warning" link @click="openAuditDialog(row)">审核</el-button>
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

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditDialogVisible" title="投资人审批" width="500px" :close-on-click-modal="false">
      <el-form :model="auditForm" label-width="140px">
        <el-form-item label="是否通过">
          <el-radio-group v-model="auditForm.auditStatus">
            <el-radio :value="2">通过</el-radio>
            <el-radio :value="-1">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="身份证信息是否正确">
          <el-radio-group v-model="auditForm.idCardCorrect">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
          <span style="margin-left: 8px; color: #9ca3af; font-size: 12px">正确可得20积分</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">返回列表</el-button>
        <el-button type="primary" @click="submitAudit">提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="投资人详情" width="580px">
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item label="ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detailData.userId }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detailData.name }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ genderText(detailData.sex) }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detailData.idCard }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailData.mobile }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ detailData.age }}</el-descriptions-item>
        <el-descriptions-item label="认证状态">
          <el-tag :type="(auditMap[detailData.status]?.type as string) || 'info'">
            {{ auditMap[detailData.status]?.text || '未知' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData.updateTime }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
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
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
