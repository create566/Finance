<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getMemberList, addMember, updateMemberStatus, type MemberQuery } from '@/api/member'

// ------ 搜索 ------
const searchForm = reactive<MemberQuery>({
  keyword: '',
  status: undefined,
})

// ------ 列表数据 ------
const tableData = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMemberList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword,
      status: searchForm.status,
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

// ------ 状态切换 ------
function toggleStatus(row: any) {
  const action = row.status === 1 ? '禁用' : '启用'
  ElMessageBox.confirm(`确定${action}会员「${row.nickName || row.mobile}」吗？`, '提示', {
    type: 'warning',
  })
    .then(async () => {
      await updateMemberStatus(row.id, row.status === 1 ? 0 : 1)
      ElMessage.success(`${action}成功`)
      fetchList()
    })
    .catch(() => {})
}

// ------ 新增弹窗 ------
const addDialogVisible = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = reactive({
  phone: '',
  nickname: '',
  realName: '',
  idCard: '',
  score: 0,
})

const addRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' },
  ],
}

function openAddDialog() {
  Object.assign(addForm, {
    phone: '',
    nickname: '',
    realName: '',
    idCard: '',
    score: 0,
  })
  addDialogVisible.value = true
}

function closeAddDialog() {
  addDialogVisible.value = false
  addFormRef.value?.resetFields()
}

async function submitAdd() {
  if (!addFormRef.value) return
  await addFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await addMember({
        phone: addForm.phone,
        nickname: addForm.nickname,
        realName: addForm.realName,
        idCard: addForm.idCard,
        score: addForm.score,
        status: 1,
      })
      ElMessage.success('新增成功，初始密码 123456')
      closeAddDialog()
      fetchList()
    } catch {
      // error 已由拦截器处理
    }
  })
}

// ------ 详情弹窗 ------
const detailVisible = ref(false)
const detailData = ref<any>(null)

function openDetail(row: any) {
  detailData.value = row
  detailVisible.value = true
}

// ------ 工具函数 ------
// 角色：0普通 1投资者 2借款人
const roleMap: Record<number, { text: string; type: string }> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '投资者', type: 'primary' },
  2: { text: '借款人', type: 'warning' },
}
// 认证状态：0未认证 1认证中 2已通过 -1失败
const authMap: Record<number, { text: string; type: string }> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '认证中', type: 'warning' },
  2: { text: '已通过', type: 'success' },
  [-1]: { text: '认证失败', type: 'danger' },
}

// ------ 生命周期 ------
onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>会员列表</h2>
      <el-button type="primary" @click="openAddDialog">
        <el-icon><Plus /></el-icon>
        新增会员
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="手机号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 100px">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
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
        <el-table-column prop="mobile" label="手机号" min-width="130" />
        <el-table-column prop="nickName" label="昵称" min-width="120" />
        <el-table-column prop="name" label="真实姓名" min-width="100">
          <template #default="{ row }">{{ row.name || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(roleMap[row.userType]?.type as string) || 'info'">
              {{ roleMap[row.userType]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实名认证" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(authMap[row.borrowAuthStatus]?.type as string) || 'info'">
              {{ authMap[row.borrowAuthStatus]?.text || '未认证' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="integral" label="积分" min-width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button :type="row.status === 1 ? 'danger' : 'success'" link @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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

    <!-- 新增弹窗 -->
    <el-dialog
      v-model="addDialogVisible"
      title="新增会员"
      width="520px"
      :close-on-click-modal="false"
      @close="closeAddDialog"
    >
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="90px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addForm.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="addForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="addForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="addForm.idCard" placeholder="请输入身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item label="初始积分" prop="score">
          <el-input-number v-model="addForm.score" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeAddDialog">取消</el-button>
        <el-button type="primary" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="会员详情"
      width="560px"
      :close-on-click-modal="true"
    >
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item label="会员ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailData.mobile }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailData.nickName }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ detailData.name || '—' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detailData.idCard || '—' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detailData.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag :type="(roleMap[detailData.userType]?.type as string) || 'info'">
            {{ roleMap[detailData.userType]?.text || '未知' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="实名认证">
          <el-tag :type="(authMap[detailData.borrowAuthStatus]?.type as string) || 'info'">
            {{ authMap[detailData.borrowAuthStatus]?.text || '未认证' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="绑卡状态">{{ detailData.bindStatus === 1 ? '已绑卡' : '未绑卡' }}</el-descriptions-item>
        <el-descriptions-item label="积分">{{ detailData.integral }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailData.status === 1 ? 'success' : 'info'">
            {{ detailData.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间" :span="2">{{ detailData.updateTime }}</el-descriptions-item>
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
