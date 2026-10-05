<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getPromptList, addPrompt, updatePrompt, deletePrompt, type PromptQuery, type PromptConfig } from '@/api/promptConfig'
import { getModelConfigList } from '@/api/modelConfig'

// 绑定模型下拉：直接读「模型配置」里启用中的对话模型，避免硬编码出现平台并不存在的模型
const chatModels = ref<{ id: number; name: string; modelName: string }[]>([])
const DEFAULT_MODEL = 'qwen3.8-max'

async function loadChatModels() {
  try {
    const list = await getModelConfigList({ modelType: 'chat' })
    chatModels.value = (list || [])
      .filter((m) => m.status === 1 && m.id != null)
      .map((m) => ({ id: m.id as number, name: m.name, modelName: m.modelName }))
  } catch {
    chatModels.value = []
  }
}

// ------ 搜索 ------
const searchForm = reactive<PromptQuery>({
  keyword: '',
  scene: '',
  contentType: '',
})

// ------ 列表 ------
const tableData = ref<PromptConfig[]>([])
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const res = await getPromptList({
      keyword: searchForm.keyword,
      scene: searchForm.scene,
      contentType: searchForm.contentType,
    })
    tableData.value = res || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchList()
}
function handleReset() {
  searchForm.keyword = ''
  searchForm.scene = ''
  searchForm.contentType = ''
  fetchList()
}

// ------ 新增/编辑弹窗 ------
const dialogVisible = ref(false)
const dialogTitle = ref('新增指令')
const formRef = ref<FormInstance>()
const formData = reactive({
  id: 0,
  promptKey: '',
  name: '',
  scene: 'chat',
  contentType: 'prompt',
  systemPrompt: '',
  temperature: 0.7,
  maxTokens: 2048,
  topP: 0.9,
  model: DEFAULT_MODEL,
  isDefault: 0,
  status: 1,
  remark: '',
})

const formRules: FormRules = {
  promptKey: [
    { required: true, message: '请输入指令标识', trigger: 'blur' },
    { pattern: /^[A-Z_][A-Z0-9_]*$/i, message: '仅支持字母、数字、下划线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入指令名称', trigger: 'blur' }],
  scene: [{ required: true, message: '请选择使用场景', trigger: 'change' }],
  contentType: [{ required: true, message: '请选择内容类型', trigger: 'change' }],
  systemPrompt: [{ required: true, message: '请输入指令内容', trigger: 'blur' }],
}

// 场景与运行时接管情况必须一致：
// ReActService 实际只取用 intent(意图路由) / chat(客服对话) / rag(知识库问答) 三个场景；
// 其余场景在数据库里有记录但没有任何代码读取，配了不生效，必须显式标注，避免"以为改了就有用"。
const sceneOptions = [
  { label: '意图路由 (intent)', value: 'intent', runtime: true },
  { label: '闲聊应答 (chat)', value: 'chat', runtime: true },
  { label: '知识库问答 (rag)', value: 'rag', runtime: true },
  { label: '风控审核 (risk)', value: 'risk', runtime: false },
  { label: '借款审核 (audit)', value: 'audit', runtime: false },
  { label: '对话摘要 (summary)', value: 'summary', runtime: false },
]
const contentTypeOptions = [
  { label: '系统指令 (prompt)', value: 'prompt' },
  { label: '关键词列表 (keywords)', value: 'keywords' },
]
const sceneText: Record<string, string> = { intent: '意图路由', chat: '闲聊应答', rag: '知识库问答', risk: '风控审核', audit: '借款审核', summary: '对话摘要' }

/** 运行时真正会读取的场景（其余为"未接入运行时"，仅保留配置） */
const RUNTIME_SCENES = ['intent', 'chat', 'rag']

/** 关键词表在运行时的实际作用，用于列表提示（与后端 ContentGuard 行为一致） */
const KEYWORD_KEYS: Record<string, string> = {
  UNSAFE_KEYWORDS: '命中即拦截，回复合规话术',
  HANDOFF_KEYWORDS: '命中即转人工，回复转接提示',
}

function runtimeStateOf(row: PromptConfig): { text: string; type: 'success' | 'warning' | 'info' } {
  if (row.contentType === 'keywords') {
    return KEYWORD_KEYS[row.promptKey]
      ? { text: '已接入对话拦截', type: 'success' }
      : { text: '未接入运行时', type: 'info' }
  }
  return RUNTIME_SCENES.includes(row.scene)
    ? { text: '运行中', type: 'success' }
    : { text: '未接入运行时', type: 'warning' }
}

// 关键词类型不需要模型参数
const isKeywords = computed(() => formData.contentType === 'keywords')
// 当前表单所选场景是否已被运行时接管（仅用于给出"保存了也没用"的提示）
const isRuntimeScene = computed(() => RUNTIME_SCENES.includes(formData.scene))

function openDialog(row?: PromptConfig) {
  if (row) {
    dialogTitle.value = '编辑指令'
    Object.assign(formData, row)
  } else {
    dialogTitle.value = '新增指令'
    Object.assign(formData, {
      id: 0, promptKey: '', name: '', scene: 'chat', contentType: 'prompt',
      systemPrompt: '', temperature: 0.7, maxTokens: 2048, topP: 0.9, model: DEFAULT_MODEL,
      isDefault: 0, status: 1, remark: '',
    })
  }
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.resetFields()
}

function onContentTypeChange() {
  if (isKeywords.value) {
    formData.temperature = 0
    formData.maxTokens = 0
    formData.topP = 0
    formData.model = '-'
  } else {
    formData.temperature = 0.7
    formData.maxTokens = 2048
    formData.topP = 0.9
    formData.model = DEFAULT_MODEL
  }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (formData.id) {
        await updatePrompt(formData as PromptConfig)
        ElMessage.success('修改成功')
      } else {
        await addPrompt({
          promptKey: formData.promptKey,
          name: formData.name,
          scene: formData.scene,
          contentType: formData.contentType,
          systemPrompt: formData.systemPrompt,
          temperature: formData.temperature,
          maxTokens: formData.maxTokens,
          topP: formData.topP,
          model: formData.model,
          isDefault: formData.isDefault,
          status: formData.status,
          remark: formData.remark,
        })
        ElMessage.success('新增成功')
      }
      closeDialog()
      fetchList()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : ''
      if (msg) ElMessage.error(msg)
    }
  })
}

// ------ 删除 ------
function handleDelete(row: PromptConfig) {
  if (row.isDefault === 1) {
    ElMessage.warning('默认指令不可删除，请先取消默认')
    return
  }
  ElMessageBox.confirm(`确定删除「${row.name}」(${row.promptKey}) 吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deletePrompt(row.id!)
      ElMessage.success('删除成功')
      fetchList()
    })
    .catch(() => {})
}

// ------ 设默认 ------
async function handleSetDefault(row: PromptConfig) {
  await updatePrompt({ ...row, isDefault: 1 })
  ElMessage.success(`已将「${row.name}」设为${sceneText[row.scene]}场景默认指令`)
  fetchList()
}

// ------ 查看指令内容 ------
const contentVisible = ref(false)
const contentData = ref<PromptConfig | null>(null)

function openContent(row: PromptConfig) {
  contentData.value = row
  contentVisible.value = true
}

// ------ 关键词数量统计 ------
// 必须与后端 KeywordUtils.split 的切分规则完全一致（逗号/中文逗号/顿号/分号/换行），
// 否则页面显示"1 个关键词"而运行时实际按 14 个匹配，管理员会误判词表没生效
function splitKeywords(text: string): string[] {
  if (!text) return []
  return text
    .split(/[,，、;；\n\r]+/)
    .map((t) => t.trim())
    .filter((t) => t.length > 0)
}
function keywordCount(text: string): number {
  return splitKeywords(text).length
}
/** 展示关键词前若干个，便于管理员快速核对词表内容 */
function keywordPreview(text: string, max = 8): string {
  const tokens = splitKeywords(text)
  return tokens.length <= max ? tokens.join('、') : tokens.slice(0, max).join('、') + ` …等 ${tokens.length} 个`
}

// ------ 生命周期 ------
onMounted(() => {
  fetchList()
  loadChatModels()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>Prompt 设置</h2>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>
        新增指令
      </el-button>
    </div>

    <el-alert type="info" :closable="false" class="info-alert">
      <b>运行时到底用哪条指令？</b>若该场景在「对话模型选择」里绑定了具体指令，则以<b>绑定的那条</b>为准；
      没有绑定（或绑定已停用）时，才使用本场景的默认指令 —— 表格中带 <b>场景默认</b> 标记的即为该默认项
      （同场景优先取勾选「默认」的，没有默认则取最近修改的一条）。
      <br />
      已绑定到「对话模型选择」的指令不可删除；正在被启用中的绑定使用的指令不可停用。
      <br />
      <b>接入状态</b>列说明哪些配置真正生效：运行时只接管
      <b>意图路由 / 闲聊应答 / 知识库问答</b> 三个场景，以及
      <b>风控违规关键词 / 转人工关键词</b> 两张词表（命中即在调用大模型前拦截）；标注「未接入运行时」的配置仅作留存，改动不会影响线上对话。
    </el-alert>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="名称/标识" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="searchForm.scene" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="o in sceneOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.contentType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="o in contentTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
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
        <el-table-column label="指令名称" min-width="200">
          <template #default="{ row }">
            <div class="name-cell">
              <div class="name-main">
                <span>{{ row.name }}</span>
                <el-tag v-if="row.isDefault === 1" type="success" size="small" style="margin-left: 6px">默认</el-tag>
                <el-tag v-if="row.effective === 1" type="primary" size="small" style="margin-left: 6px">场景默认</el-tag>
              </div>
              <el-tag effect="plain" size="small" class="name-key">{{ row.promptKey }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="场景" width="100" align="center">
          <template #default="{ row }">{{ sceneText[row.scene] || row.scene }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.contentType === 'keywords' ? 'warning' : 'primary'" size="small">
              {{ row.contentType === 'keywords' ? '关键词' : '系统指令' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接入状态" width="130" align="center">
          <template #default="{ row }">
            <el-tooltip
              :content="
                row.contentType === 'keywords'
                  ? (KEYWORD_KEYS[row.promptKey] || '该词表没有运行时代码读取，改动不影响线上')
                  : (RUNTIME_SCENES.includes(row.scene)
                      ? '运行时 ReAct 流程会读取该场景的指令'
                      : '运行时没有任何代码读取该场景，仅作留存')
              "
              placement="top"
            >
              <el-tag :type="runtimeStateOf(row).type" size="small" effect="light">
                {{ runtimeStateOf(row).text }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="内容预览" min-width="260">
          <template #default="{ row }">
            <template v-if="row.contentType === 'keywords'">
              <span class="content-preview">
                共 {{ keywordCount(row.systemPrompt) }} 个关键词
                <el-tag v-if="keywordCount(row.systemPrompt) === 0" type="danger" size="small" style="margin-left: 6px">
                  词表为空，拦截不生效
                </el-tag>
              </span>
              <div v-if="keywordCount(row.systemPrompt) > 0" class="keyword-preview">
                {{ keywordPreview(row.systemPrompt) }}
              </div>
            </template>
            <span v-else class="content-preview">{{ row.systemPrompt.slice(0, 50) }}...</span>
          </template>
        </el-table-column>
        <el-table-column label="模型" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.model !== '-'" effect="plain" size="small">{{ row.model }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openContent(row)">查看</el-button>
            <el-button v-if="row.isDefault === 0" type="success" link @click="handleSetDefault(row)">设默认</el-button>
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
      width="720px"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="指令标识" prop="promptKey">
              <el-input v-model="formData.promptKey" placeholder="如 CS_SYSTEM_PROMPT" :disabled="formData.id !== 0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指令名称" prop="name">
              <el-input v-model="formData.name" placeholder="如 客服系统指令" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="使用场景" prop="scene">
              <el-select
                v-model="formData.scene"
                placeholder="请选择"
                style="width: 100%"
                :disabled="isKeywords"
              >
                <el-option
                  v-for="o in sceneOptions"
                  :key="o.value"
                  :label="o.runtime ? o.label : o.label + '（未接入运行时）'"
                  :value="o.value"
                  :disabled="!o.runtime"
                />
              </el-select>
              <div v-if="isKeywords" class="form-hint">
                关键词表不按场景匹配，运行时按「指令标识」识别：UNSAFE_KEYWORDS=违规词拦截，HANDOFF_KEYWORDS=转人工词
              </div>
              <div v-else-if="!isRuntimeScene" class="form-hint warn">
                该场景未接入运行时，保存后不会影响线上对话（仅作配置留存）
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="内容类型" prop="contentType">
              <el-radio-group v-model="formData.contentType" @change="onContentTypeChange">
                <el-radio v-for="o in contentTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="isKeywords ? '关键词' : '系统指令'" prop="systemPrompt">
          <el-input
            v-model="formData.systemPrompt"
            type="textarea"
            :rows="isKeywords ? 8 : 10"
            :placeholder="isKeywords ? '每行一个关键词' : '输入系统 Prompt 指令内容...'"
            maxlength="5000"
            show-word-limit
          />
          <div v-if="isKeywords" class="form-hint">
            支持每行一个，也支持逗号、顿号、分号分隔（与运行时解析规则一致）；命中后在调用大模型前直接拦截。
            当前共 {{ keywordCount(formData.systemPrompt) }} 个关键词
          </div>
        </el-form-item>
        <!-- 关键词类型不显示模型参数 -->
        <el-row v-if="!isKeywords" :gutter="20">
          <el-col :span="12">
            <el-form-item label="绑定模型" prop="model">
              <el-select v-model="formData.model" placeholder="请选择" filterable allow-create style="width: 100%">
                <el-option
                  v-for="m in chatModels"
                  :key="m.id"
                  :label="`${m.name} (${m.modelName})`"
                  :value="m.modelName"
                />
              </el-select>
              <div class="form-hint">
                兜底模型：仅当「对话模型选择」中该场景未启用绑定时才生效，选项来自「模型配置」中已启用的对话模型
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设为默认">
              <el-switch v-model="formData.isDefault" :active-value="1" :inactive-value="0" />
              <span class="form-hint">同场景仅一个默认</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="!isKeywords" :gutter="20">
          <el-col :span="8">
            <el-form-item label="温度" prop="temperature">
              <el-input-number v-model="formData.temperature" :min="0" :max="2" :step="0.1" :precision="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="TopP" prop="topP">
              <el-input-number v-model="formData.topP" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="MaxTokens" prop="maxTokens">
              <el-input-number v-model="formData.maxTokens" :min="1" :step="256" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用状态">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看指令内容弹窗 -->
    <el-dialog v-model="contentVisible" title="指令详情" width="640px">
      <template v-if="contentData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="指令标识">
            <el-tag effect="plain" size="small">{{ contentData.promptKey }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="指令名称">{{ contentData.name }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ sceneText[contentData.scene] }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="contentData.contentType === 'keywords' ? 'warning' : 'primary'" size="small">
              {{ contentData.contentType === 'keywords' ? '关键词列表' : '系统指令' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="contentData.model !== '-'" label="模型">{{ contentData.model }}</el-descriptions-item>
          <el-descriptions-item v-if="contentData.model !== '-'" label="温度">{{ contentData.temperature }}</el-descriptions-item>
          <el-descriptions-item v-if="contentData.model !== '-'" label="TopP">{{ contentData.topP }}</el-descriptions-item>
          <el-descriptions-item v-if="contentData.model !== '-'" label="MaxTokens">{{ contentData.maxTokens }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="contentData.status === 1 ? 'success' : 'info'" size="small">
              {{ contentData.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="默认">
            <el-tag v-if="contentData.isDefault === 1" type="success" size="small">默认</el-tag>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="接入状态">
            <el-tag :type="runtimeStateOf(contentData).type" size="small">{{ runtimeStateOf(contentData).text }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="contentData.contentType === 'keywords' && keywordCount(contentData.systemPrompt) === 0"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        >
          该词表内容为空，运行时不会命中任何关键词（拦截/转人工不会生效）
        </el-alert>
        <h4>{{ contentData.contentType === 'keywords' ? '关键词列表' : '系统指令' }}</h4>
        <pre v-if="contentData.contentType === 'keywords'" class="keyword-content">{{ contentData.systemPrompt }}</pre>
        <pre v-else class="prompt-content">{{ contentData.systemPrompt }}</pre>
        <div v-if="contentData.remark" style="margin-top: 12px">
          <h4>备注</h4>
          <p class="remark-text">{{ contentData.remark }}</p>
        </div>
      </template>
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
.page-header h2 {
  font-size: 18px;
  font-weight: 500;
  color: #1f2937;
}
.search-card {
  margin-bottom: 16px;
  border-radius: 6px;
}
.info-alert {
  margin-bottom: 16px;
  border-radius: 6px;
  line-height: 1.7;
}
.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.page-card {
  border-radius: 6px;
}
.name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name-main {
  font-weight: 500;
  color: #303133;
}
.name-key {
  width: fit-content;
}
.content-preview {
  color: #909399;
  font-size: 13px;
}
.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.form-hint.warn {
  color: #e6a23c;
}
.keyword-preview {
  margin-top: 2px;
  font-size: 12px;
  color: #b88230;
  line-height: 1.6;
  word-break: break-all;
}
.content-viewer h4 {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}
.prompt-content {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 16px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.8;
  color: #303133;
  max-height: 350px;
  overflow-y: auto;
}
.keyword-content {
  background: #fdf6ec;
  border: 1px solid #f0d784;
  border-radius: 6px;
  padding: 12px 16px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  max-height: 350px;
  overflow-y: auto;
}
.remark-text {
  font-size: 14px;
  color: #606266;
}
</style>