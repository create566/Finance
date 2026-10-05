<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getPointLevelList,
  addPointLevel,
  updatePointLevel,
  deletePointLevel,
} from '@/api/pointLevel'

// ------ 列表数据 ------
const tableData = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getPointLevelList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
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

// ------ 弹窗表单 ------
const dialogVisible = ref(false)
const dialogTitle = ref('新增积分等级')
const formRef = ref<FormInstance>()
const formData = reactive({
  id: 0,
  integralStart: 0,
  integralEnd: 0,
  borrowAmount: 0,
})

const formRules: FormRules = {
  integralStart: [{ required: true, message: '请输入起始积分', trigger: 'blur' }],
  integralEnd: [{ required: true, message: '请输入结束积分', trigger: 'blur' }],
  borrowAmount: [{ required: true, message: '请输入借款额度', trigger: 'blur' }],
}

function openDialog(row?: any) {
  if (row) {
    dialogTitle.value = '编辑积分等级'
    Object.assign(formData, row)
  } else {
    dialogTitle.value = '新增积分等级'
    Object.assign(formData, { id: 0, integralStart: 0, integralEnd: 0, borrowAmount: 0 })
  }
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.resetFields()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (formData.id) {
        await updatePointLevel(formData as any)
        ElMessage.success('修改成功')
      } else {
        await addPointLevel({
          integralStart: formData.integralStart,
          integralEnd: formData.integralEnd,
          borrowAmount: formData.borrowAmount,
        } as any)
        ElMessage.success('新增成功')
      }
      closeDialog()
      fetchList()
    } catch {
      // error 已由拦截器处理
    }
  })
}

function handleDelete(row: any) {
  ElMessageBox.confirm(`确定删除该积分等级吗？`, '提示', {
    type: 'warning',
  })
    .then(async () => {
      await deletePointLevel(row.id)
      ElMessage.success('删除成功')
      fetchList()
    })
    .catch(() => {})
}

// ------ 生命周期 ------
onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>积分等级列表</h2>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>
        新增积分等级
      </el-button>
    </div>

    <el-card shadow="never" class="page-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="积分区间" min-width="130" align="center">
          <template #default="{ row }">
            {{ row.integralStart }} - {{ row.integralEnd }}
          </template>
        </el-table-column>
        <el-table-column label="可借额度" min-width="130" align="center">
          <template #default="{ row }">
            <span class="money">¥{{ row.borrowAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column prop="updateTime" label="更新时间" min-width="180" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="起始积分" prop="integralStart">
          <el-input-number v-model="formData.integralStart" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束积分" prop="integralEnd">
          <el-input-number v-model="formData.integralEnd" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="可借额度" prop="borrowAmount">
          <el-input-number
            v-model="formData.borrowAmount"
            :min="0"
            :step="1000"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
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
</style>
