<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getTemplatePage,
  createTemplate,
  updateTemplate,
  toggleTemplateStatus,
  CONTRACT_TYPE_META,
  type ContractTemplate,
  type TemplateQuery,
} from '@/api/contract'

// ------ 搜索 ------
const searchForm = reactive<TemplateQuery>({
  contractType: undefined,
  status: undefined,
})

// ------ 列表 ------
const tableData = ref<ContractTemplate[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getTemplatePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      contractType: searchForm.contractType,
      status: searchForm.status,
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

// ------ 新增 / 修改 ------
const formVisible = ref(false)
const formSaving = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const editId = ref<number | null>(null)

const form = reactive({
  templateNo: '',
  contractType: 'LOAN',
  title: '',
  contentTpl: '',
  variables: '',
})

const rules: FormRules = {
  contractType: [{ required: true, message: '请选择合同类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入合同标题', trigger: 'blur' }],
  contentTpl: [{ required: true, message: '请输入合同正文模板', trigger: 'blur' }],
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  Object.assign(form, {
    templateNo: '',
    contractType: searchForm.contractType || 'LOAN',
    title: '',
    contentTpl: '',
    variables: '',
  })
  formVisible.value = true
}

function openEdit(row: ContractTemplate) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    templateNo: row.templateNo,
    contractType: row.contractType,
    title: row.title,
    contentTpl: row.contentTpl,
    variables: row.variables || '',
  })
  formVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formSaving.value = true
    try {
      if (isEdit.value && editId.value !== null) {
        const res = await updateTemplate(editId.value, { ...form })
        ElMessage.success(`已生成新版本 v${res?.version ?? ''}，旧版本自动退役`)
      } else {
        await createTemplate({ ...form })
        ElMessage.success('模板新增成功')
      }
      formVisible.value = false
      fetchList()
    } catch {
      // 错误提示已由拦截器统一处理
    } finally {
      formSaving.value = false
    }
  })
}

// ------ 启用 / 停用 ------
async function handleToggle(row: ContractTemplate) {
  const next = row.status === 1 ? 0 : 1
  if (next === 0) {
    ElMessageBox.confirm(
      `停用后，新生成的《${CONTRACT_TYPE_META[row.contractType]?.label || row.contractType}》将找不到可用模板，` +
        `相关业务动作（发布 / 投资 / 放款）会因为"无法生成合同"而失败。确定停用吗？`,
      '停用模板',
      { type: 'warning', confirmButtonText: '确认停用' },
    )
      .then(async () => {
        await toggleTemplateStatus(row.id, 0)
        ElMessage.success('已停用')
        fetchList()
      })
      .catch(() => {})
    return
  }
  await toggleTemplateStatus(row.id, 1)
  ElMessage.success('已启用')
  fetchList()
}

// ------ 查看正文 ------
const bodyVisible = ref(false)
const bodyData = ref<ContractTemplate | null>(null)

function openBody(row: ContractTemplate) {
  bodyData.value = row
  bodyVisible.value = true
}

// ------ 工具 ------
function typeLabel(t: string) {
  return CONTRACT_TYPE_META[t]?.label || t || '未知'
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>合同模板管理</h2>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增模板
      </el-button>
    </div>

    <el-alert type="info" :closable="false" class="tip-alert">
      <template #title>
        正文用 <strong>${变量名}</strong> 占位，生成合同时按业务数据渲染并<strong>冻结为快照</strong>。
        修改模板<strong>不会覆盖历史</strong>，而是生成 v+1 新版本并让旧版本退役 ——
        已签署的合同不受影响（正文快照和哈希在签署时已锁定）。同一模板编号下始终只有 1 个启用版本，
        这是运行时的取值依据（取启用版本中版本号最大的那条）。
      </template>
    </el-alert>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="合同类型">
          <el-select v-model="searchForm.contractType" placeholder="全部" clearable style="width: 150px">
            <el-option label="借款合同" value="LOAN" />
            <el-option label="出借协议" value="INVEST" />
            <el-option label="放款确认书" value="PAYMENT" />
            <el-option label="居间服务协议" value="SERVICE" />
            <el-option label="资金存管协议" value="DEPOSIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
        <el-table-column prop="templateNo" label="模板编号" min-width="140" />
        <el-table-column label="合同类型" width="110" align="center">
          <template #default="{ row }">{{ typeLabel(row.contractType) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="合同标题" min-width="130" />
        <el-table-column label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="占位变量" min-width="220">
          <template #default="{ row }">
            <span class="var-text">{{ row.variables || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="handleToggle(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="165" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openBody(row)">正文</el-button>
            <el-button type="warning" link @click="openEdit(row)">改版</el-button>
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

    <!-- 新增 / 改版弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '修改模板（将生成新版本）' : '新增模板'"
      width="760px"
      :close-on-click-modal="false"
      top="6vh"
    >
      <el-alert
        v-if="isEdit"
        type="warning"
        :closable="false"
        title="保存后会生成 v+1 新版本，当前版本自动退役；已生成的合同仍指向签署时的模板版本，不受影响。"
        style="margin-bottom: 16px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模板编号" prop="templateNo">
          <el-input
            v-model="form.templateNo"
            :disabled="isEdit"
            :placeholder="isEdit ? '' : '留空则自动生成 TPL_<类型>'"
          />
        </el-form-item>
        <el-form-item label="合同类型" prop="contractType">
          <el-select v-model="form.contractType" :disabled="isEdit" style="width: 100%">
            <el-option label="借款合同 LOAN" value="LOAN" />
            <el-option label="出借协议 INVEST" value="INVEST" />
            <el-option label="放款确认书 PAYMENT" value="PAYMENT" />
            <el-option label="居间服务协议 SERVICE" value="SERVICE" />
            <el-option label="资金存管协议 DEPOSIT" value="DEPOSIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同标题" prop="title">
          <el-input v-model="form.title" placeholder="例如：借款合同" />
        </el-form-item>
        <el-form-item label="正文模板" prop="contentTpl">
          <el-input
            v-model="form.contentTpl"
            type="textarea"
            :rows="14"
            placeholder="用 ${变量名} 占位，例如：&#10;借款金额：人民币 ${amount} 元&#10;借款期限：${period} 期&#10;签署人：${borrowerName}    签署日期：${signDate}"
          />
        </el-form-item>
        <el-form-item label="占位变量">
          <el-input v-model="form.variables" placeholder="逗号分隔，例如：amount,period,borrowerName,signDate" />
          <div class="field-hint">
            仅作说明与核对用。正文里出现但业务数据没有的变量，渲染后会变成空串。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSaving" @click="submitForm">
          {{ isEdit ? '保存并生成新版本' : '新增' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 正文查看 -->
    <el-dialog
      v-model="bodyVisible"
      :title="bodyData ? `正文模板 - ${bodyData.title}（v${bodyData.version}）` : '正文模板'"
      width="720px"
    >
      <pre class="body-box">{{ bodyData?.contentTpl }}</pre>
      <template #footer>
        <el-button @click="bodyVisible = false">关闭</el-button>
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
.var-text {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}
.field-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.body-box {
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
  max-height: 480px;
  overflow-y: auto;
}
</style>
