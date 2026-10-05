<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import {
  getKnowledgeBaseList,
  getKnowledgeDocList,
  addKnowledgeDocText,
  addKnowledgeDocPdf,
  deleteKnowledgeDoc,
} from '@/api/knowledge'
import type { KnowledgeBase, KnowledgeDoc } from '@/api/knowledge'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)

const kbInfo = ref<KnowledgeBase | null>(null)
const tableData = ref<KnowledgeDoc[]>([])
const loading = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const kbList = await getKnowledgeBaseList()
    kbInfo.value = kbList.find((k) => k.id === kbId) || null
    tableData.value = await getKnowledgeDocList(kbId)
  } finally {
    loading.value = false
  }
}

// ------ 添加文本知识弹窗 ------
const textDialogVisible = ref(false)
const textFormRef = ref<FormInstance>()
const textForm = reactive({
  title: '',
  content: '',
})
const textRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入知识内容', trigger: 'blur' }],
}

function openTextDialog() {
  Object.assign(textForm, { title: '', content: '' })
  textDialogVisible.value = true
}

function closeTextDialog() {
  textDialogVisible.value = false
  textFormRef.value?.resetFields()
}

async function submitText() {
  if (!textFormRef.value) return
  await textFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await addKnowledgeDocText({
        kbId,
        title: textForm.title,
        content: textForm.content,
      })
      ElMessage.success('添加成功')
      closeTextDialog()
      fetchData()
    } catch {
      // error 已由拦截器处理
    }
  })
}

// ------ 上传 PDF 弹窗 ------
const pdfDialogVisible = ref(false)
const pdfForm = reactive({
  title: '',
  fileName: '',
  fileSize: 0,
})
const selectedFile = ref<File | null>(null)
const pdfFormRef = ref<FormInstance>()
const pdfRules: FormRules = {
  title: [{ required: true, message: '请输入文档标题', trigger: 'blur' }],
}

function openPdfDialog() {
  Object.assign(pdfForm, { title: '', fileName: '', fileSize: 0 })
  selectedFile.value = null
  pdfDialogVisible.value = true
}

function closePdfDialog() {
  pdfDialogVisible.value = false
  pdfFormRef.value?.resetFields()
}

// 文件选择回调
function handleFileChange(file: UploadFile) {
  pdfForm.fileName = file.name
  pdfForm.fileSize = Math.ceil((file.size || 0) / 1024)
  if (!pdfForm.title) pdfForm.title = file.name.replace(/\.pdf$/i, '')
  selectedFile.value = file.raw || null
}

async function submitPdf() {
  if (!pdfFormRef.value) return
  await pdfFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!selectedFile.value) {
      ElMessage.warning('请选择 PDF 文件')
      return
    }
    try {
      await addKnowledgeDocPdf(kbId, pdfForm.title, selectedFile.value)
      ElMessage.success('上传成功，等待处理')
      closePdfDialog()
      fetchData()
    } catch {
      // error 已由拦截器处理
    }
  })
}

// ------ 删除文档 ------
function handleDelete(row: KnowledgeDoc) {
  ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '提示', {
    type: 'warning',
  })
    .then(async () => {
      await deleteKnowledgeDoc(row.id)
      ElMessage.success('删除成功')
      fetchData()
    })
    .catch(() => {})
}

// ------ 查看内容弹窗 ------
const contentVisible = ref(false)
const contentData = ref<KnowledgeDoc | null>(null)

function openContent(row: KnowledgeDoc) {
  contentData.value = row
  contentVisible.value = true
}

// ------ 工具 ------
const sourceMap: Record<string, { text: string; type: string }> = {
  pdf: { text: 'PDF', type: 'danger' },
  text: { text: '文本', type: 'success' },
  url: { text: 'URL', type: 'warning' },
}
const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '处理中', type: 'warning' },
  1: { text: '就绪', type: 'success' },
  2: { text: '失败', type: 'danger' },
}

function fileSizeText(kb: number) {
  if (kb === 0) return '—'
  if (kb < 1024) return `${kb} KB`
  return `${(kb / 1024).toFixed(1)} MB`
}

// ------ 自动刷新：有处理中文档时每3秒轮询 ------
let pollingTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  if (pollingTimer) return
  pollingTimer = setInterval(async () => {
    const hasProcessing = tableData.value.some((d) => d.status === 0)
    if (hasProcessing) {
      await fetchData()
    } else {
      stopPolling()
    }
  }, 3000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// ------ 生命周期 ------
onMounted(async () => {
  await fetchData()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="page-container">
    <!-- 顶部信息 + 操作 -->
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="'ArrowLeft'" @click="router.push('/knowledge/list')">返回</el-button>
        <h2 v-if="kbInfo">{{ kbInfo.name }}</h2>
      </div>
      <div>
        <el-button type="success" @click="openTextDialog">
          <el-icon><Document /></el-icon>
          添加文本知识
        </el-button>
        <el-button type="danger" @click="openPdfDialog">
          <el-icon><Upload /></el-icon>
          上传 PDF
        </el-button>
      </div>
    </div>

    <!-- 知识库概览 -->
    <el-card v-if="kbInfo" shadow="never" class="kb-info-card">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="文档数">{{ kbInfo.docCount }}</el-descriptions-item>
        <el-descriptions-item label="分块数">{{ kbInfo.chunkCount }}</el-descriptions-item>
        <el-descriptions-item label="向量模型">{{ kbInfo.embeddingModel }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="kbInfo.status === 1 ? 'success' : 'info'">
            {{ kbInfo.status === 1 ? '启用' : '未启用' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 文档列表 -->
    <el-card shadow="never" class="page-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="文档标题" min-width="180">
          <template #default="{ row }">
            <el-link v-if="row.source === 'text'" type="primary" @click="openContent(row)">{{ row.title }}</el-link>
            <span v-else>{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="sourceMap[row.source]?.type as string" size="small">
              {{ sourceMap[row.source]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件" min-width="140">
          <template #default="{ row }">
            <span v-if="row.source === 'pdf'">{{ row.fileName }}（{{ fileSizeText(row.fileSize) }}）</span>
            <span v-else class="text-preview">{{ row.content.slice(0, 40) }}...</span>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type as string">
              {{ statusMap[row.status]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="添加时间" min-width="170" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.source === 'text'" type="primary" link @click="openContent(row)">查看</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加文本知识弹窗 -->
    <el-dialog
      v-model="textDialogVisible"
      title="添加文本知识"
      width="560px"
      :close-on-click-modal="false"
      @close="closeTextDialog"
    >
      <el-form ref="textFormRef" :model="textForm" :rules="textRules" label-width="70px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="textForm.title" placeholder="请输入知识标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="textForm.content"
            type="textarea"
            :rows="8"
            placeholder="请输入知识内容，支持长文本"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeTextDialog">取消</el-button>
        <el-button type="primary" @click="submitText">添加</el-button>
      </template>
    </el-dialog>

    <!-- 上传 PDF 弹窗 -->
    <el-dialog
      v-model="pdfDialogVisible"
      title="上传 PDF 文档"
      width="480px"
      :close-on-click-modal="false"
      @close="closePdfDialog"
    >
      <el-form ref="pdfFormRef" :model="pdfForm" :rules="pdfRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="pdfForm.title" placeholder="请输入文档标题" />
        </el-form-item>
        <el-form-item label="PDF文件">
          <el-upload
            :auto-upload="false"
            accept=".pdf"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
          >
            <el-button type="primary" plain>选择 PDF 文件</el-button>
            <template #tip>
              <div class="upload-tip">仅支持 PDF 格式，单个文件最大 50MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closePdfDialog">取消</el-button>
        <el-button type="primary" @click="submitPdf">上传</el-button>
      </template>
    </el-dialog>

    <!-- 查看文本内容弹窗 -->
    <el-dialog v-model="contentVisible" title="知识内容" width="600px">
      <div v-if="contentData" class="content-viewer">
        <h3>{{ contentData.title }}</h3>
        <el-divider />
        <pre>{{ contentData.content }}</pre>
      </div>
      <template #footer>
        <el-button @click="contentVisible = false">关闭</el-button>
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
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-left h2 {
  font-size: 18px;
  font-weight: 500;
  color: #1f2937;
  margin: 0;
}
.kb-info-card {
  margin-bottom: 16px;
  border-radius: 6px;
}
.page-card {
  border-radius: 6px;
}
.text-preview {
  color: #909399;
  font-size: 13px;
}
.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.content-viewer h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}
.content-viewer pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  max-height: 400px;
  overflow-y: auto;
}
</style>