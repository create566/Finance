<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getKnowledgeBaseList,
  addKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
} from '@/api/knowledge'
import type { KnowledgeBase } from '@/api/knowledge'

const router = useRouter()

const tableData = ref<KnowledgeBase[]>([])
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    tableData.value = await getKnowledgeBaseList()
  } finally {
    loading.value = false
  }
}

// ------ 新增/编辑弹窗 ------
const dialogVisible = ref(false)
const dialogTitle = ref('新增知识库')
const formRef = ref<FormInstance>()
const formData = reactive({
  id: 0,
  name: '',
  description: '',
  embeddingModel: 'text-embedding-ada-002',
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  embeddingModel: [{ required: true, message: '请选择向量模型', trigger: 'change' }],
}

function openDialog(row?: KnowledgeBase) {
  if (row) {
    dialogTitle.value = '编辑知识库'
    Object.assign(formData, { id: row.id, name: row.name, description: row.description, embeddingModel: row.embeddingModel })
  } else {
    dialogTitle.value = '新增知识库'
    Object.assign(formData, { id: 0, name: '', description: '', embeddingModel: 'text-embedding-ada-002' })
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
        await updateKnowledgeBase({
          ...formData,
          id: formData.id,
        } as KnowledgeBase)
        ElMessage.success('修改成功')
      } else {
        await addKnowledgeBase({
          name: formData.name,
          description: formData.description,
          embeddingModel: formData.embeddingModel,
        })
        ElMessage.success('新增成功')
      }
      closeDialog()
      fetchList()
    } catch {
      // error 已由拦截器处理
    }
  })
}

function handleDelete(row: KnowledgeBase) {
  ElMessageBox.confirm(`确定删除「${row.name}」吗？知识库下的所有文档将被一并删除`, '提示', {
    type: 'warning',
  })
    .then(async () => {
      await deleteKnowledgeBase(row.id)
      ElMessage.success('删除成功')
      fetchList()
    })
    .catch(() => {})
}

function goDetail(row: KnowledgeBase) {
  router.push(`/knowledge/detail/${row.id}`)
}

// ------ 生命周期 ------
onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>知识库列表</h2>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>
        新增知识库
      </el-button>
    </div>

    <el-card shadow="never" class="page-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="name" label="知识库名称" min-width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="docCount" label="文档数" width="90" align="center" />
        <el-table-column prop="chunkCount" label="分块数" width="90" align="center" />
        <el-table-column prop="embeddingModel" label="向量模型" min-width="160" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '未启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row)">管理文档</el-button>
            <el-button type="warning" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="知识库描述（选填）" />
        </el-form-item>
        <el-form-item label="向量模型" prop="embeddingModel">
          <el-select v-model="formData.embeddingModel" placeholder="请选择向量模型" style="width: 100%">
            <el-option label="text-embedding-ada-002" value="text-embedding-ada-002" />
            <el-option label="m3e-base" value="m3e-base" />
            <el-option label="bge-large-zh" value="bge-large-zh" />
            <el-option label="text2vec-base-chinese" value="text2vec-base-chinese" />
          </el-select>
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
</style>