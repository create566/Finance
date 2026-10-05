<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addChatModelBinding,
  deleteChatModelBinding,
  getChatModelBindings,
  getChatModelScenes,
  getEffectiveConfigs,
  getSelectOptions,
  toggleChatModelBinding,
  updateChatModelBinding,
} from '@/api/chatModel'
import type { ChatModelBinding, EffectiveConfig, SceneOption } from '@/api/chatModel'

const tableData = ref<ChatModelBinding[]>([])
const sceneList = ref<SceneOption[]>([])
const effectiveList = ref<EffectiveConfig[]>([])
const loading = ref(false)

// 下拉选项
const modelOptions = ref<{ id: number; name: string; modelName: string }[]>([])
const promptOptions = ref<{ id: number; promptKey: string; name: string; scene: string }[]>([])
const kbOptions = ref<{ id: number; name: string }[]>([])

async function fetchData() {
  loading.value = true
  try {
    const [bindings, options, scenes, effective] = await Promise.all([
      getChatModelBindings(),
      getSelectOptions(),
      getChatModelScenes(),
      getEffectiveConfigs(),
    ])
    tableData.value = bindings || []
    modelOptions.value = options.models
    promptOptions.value = options.prompts
    kbOptions.value = options.knowledgeBases
    sceneList.value = scenes || []
    effectiveList.value = effective || []
  } finally {
    loading.value = false
  }
}

// 尚未配置绑定的对话场景（用于"新增绑定"下拉）
const unboundScenes = computed(() => sceneList.value.filter((s) => !s.bound))

/** 展示用的模型名：优先取模型配置的中文名，兜底展示模型标识 */
function modelLabel(row: ChatModelBinding): string {
  const hit = modelOptions.value.find((m) => m.id === row.modelId)
  return hit ? hit.name : (row.modelName || '未配置')
}

/**
 * 该行绑定的模型是否已不在"可绑定"列表里（未测试通过 / 已禁用 / 非对话模型）。
 * 这些都是"测试门禁"生效之前留下的历史绑定，运行时仍会用它，必须显式提示管理员补测。
 */
function modelNotVerified(row: ChatModelBinding): boolean {
  if (!row.modelId || row.enabled !== 1) return false
  return !modelOptions.value.some((m) => m.id === row.modelId)
}

/** 同场景的指令排在前面，便于"选本场景对应的提示词"；跨场景绑定仍允许 */
function promptsForScene(scene: string) {
  const same = promptOptions.value.filter((p) => p.scene === scene)
  const other = promptOptions.value.filter((p) => p.scene !== scene)
  return [...same, ...other]
}

/** 场景对应的默认指令（scene 与 promptKey 一一对应时直接命中默认值） */
function defaultPromptId(scene: string): number {
  const hit = promptOptions.value.find((p) => p.scene === scene && p.promptKey && p.promptKey !== '')
  return hit?.id ? Number(hit.id) : 0
}

// ---------------- 弹窗 ----------------
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)

const form = reactive({
  id: 0,
  scene: '',
  sceneName: '',
  modelId: null as number | null,
  promptId: 0,
  kbId: 0, // 0 = 不检索
  enabled: 1,
  remark: '',
})

function resetForm() {
  Object.assign(form, {
    id: 0, scene: '', sceneName: '', modelId: null,
    promptId: 0, kbId: 0, enabled: 1, remark: '',
  })
}

function openCreate() {
  if (!unboundScenes.value.length) {
    ElMessage.warning('所有对话场景均已配置，如需调整请直接点击「配置」')
    return
  }
  if (!modelOptions.value.length) {
    ElMessage.warning('暂无可绑定的模型：请先到「模型配置」新增一个对话模型并测试通过')
    return
  }
  resetForm()
  isEdit.value = false
  form.scene = unboundScenes.value[0].scene
  form.sceneName = unboundScenes.value[0].sceneName
  form.promptId = defaultPromptId(form.scene)
  dialogVisible.value = true
}

function openEdit(row: ChatModelBinding) {
  resetForm()
  isEdit.value = true
  Object.assign(form, {
    id: row.id ?? 0,
    scene: row.scene,
    sceneName: row.sceneName || row.scene,
    modelId: row.modelId ?? null,
    promptId: row.promptId ?? 0,
    kbId: row.kbId ?? 0,
    enabled: row.enabled ?? 1,
    remark: row.remark || '',
  })
  // 当前绑定的模型若已不可绑定（未测试通过/已禁用/非对话模型），仍需在下拉里可见，
  // 否则会显示为空、一保存就被清掉
  if (form.modelId && !modelOptions.value.some((m) => m.id === form.modelId)) {
    modelOptions.value.unshift({
      id: form.modelId,
      name: `${row.modelName}（未通过测试或已禁用，请到「模型配置」处理）`,
      modelName: row.modelName || '',
    })
  }
  dialogVisible.value = true
}

function onSceneChange(scene: string) {
  form.sceneName = sceneList.value.find((s) => s.scene === scene)?.sceneName || scene
  // 换场景后把指令切到该场景对应的默认指令，避免"换了场景还绑着上一个场景的指令"
  form.promptId = defaultPromptId(scene)
}

function closeDialog() {
  dialogVisible.value = false
}

async function submitForm() {
  if (!form.scene) {
    ElMessage.warning('请选择处理环节')
    return
  }
  if (!form.modelId) {
    ElMessage.warning('请选择使用模型')
    return
  }
  submitting.value = true
  try {
    const payload: ChatModelBinding = {
      scene: form.scene,
      modelId: form.modelId,
      promptId: form.promptId || 0,
      kbId: form.kbId || null,
      enabled: form.enabled,
      remark: form.remark,
    }
    if (isEdit.value) {
      payload.id = form.id
      await updateChatModelBinding(payload)
      ElMessage.success('配置已更新')
    } else {
      await addChatModelBinding(payload)
      ElMessage.success('绑定已新增')
    }
    closeDialog()
    fetchData()
  } catch {
    // error 已由拦截器处理
  } finally {
    submitting.value = false
  }
}

// ---------------- 启停 / 删除 ----------------
async function handleToggleEnabled(row: ChatModelBinding) {
  try {
    await toggleChatModelBinding(row.id!)
    ElMessage.success(row.enabled === 1 ? '已禁用' : '已启用')
    fetchData()
  } catch {
    // error 已由拦截器处理
  }
}

function handleDelete(row: ChatModelBinding) {
  ElMessageBox.confirm(
    `确定删除「${row.sceneName}」的模型绑定吗？删除后该环节将回退到 Prompt 配置中的模型。`,
    '提示',
    { type: 'warning' },
  )
    .then(async () => {
      await deleteChatModelBinding(row.id!)
      ElMessage.success('删除成功')
      fetchData()
    })
    .catch(() => {})
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>对话模型选择</h2>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增绑定
      </el-button>
    </div>

    <el-alert type="info" :closable="false" class="info-alert">
      智能客服采用两阶段流程：<b>意图路由</b>判断问题类型并决定是否检索知识库，<b>闲聊应答</b>与
      <b>知识库问答</b>分别输出最终回复。此处为每个对话场景指定实际调用的模型、提示词与知识库。
      <br />
      这里的配置是<b>覆盖层</b>：运行时优先用本页绑定的模型与提示词，未绑定/未启用才回退到「Prompt 设置」里该场景的默认指令。
      模型必须在「模型配置」中<b>测试通过</b>后才会出现在下拉里（向量/重排模型不能用于对话）。
    </el-alert>

    <!-- 当前生效配置：把"配了之后线上到底跑什么"直接摆出来 -->
    <el-card shadow="never" class="page-card eff-card">
      <template #header>
        <div class="eff-header">
          <span class="eff-header-title">当前生效配置</span>
          <span class="eff-header-hint">来源优先级：对话模型选择（已启用绑定） → 场景 Prompt 指令中的模型 → 代码内置默认值</span>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col v-for="e in effectiveList" :key="e.scene" :xs="24" :md="12">
          <div class="eff-item">
            <div class="eff-title">
              <span class="scene-name">{{ e.sceneName }}</span>
              <code class="scene-code">{{ e.scene }}</code>
            </div>
            <div class="eff-row">
              <span class="eff-label">生效模型</span>
              <el-tag effect="plain" size="small">{{ e.modelName }}</el-tag>
              <el-tooltip v-if="e.modelTestStatus !== 1" content="该模型尚未通过连通性测试，请到「模型配置」测试" placement="top">
                <el-tag type="danger" size="small" effect="light">未验证</el-tag>
              </el-tooltip>
            </div>
            <div class="eff-row">
              <span class="eff-label">配置来源</span>
              <el-tag
                size="small"
                :type="e.source === 'binding' ? 'success' : e.source === 'prompt' ? 'warning' : 'info'"
              >
                {{ e.sourceText }}
              </el-tag>
            </div>
            <div class="eff-row">
              <span class="eff-label">Prompt 指令</span>
              <el-tag v-if="e.promptKey" effect="plain" size="small" type="success">{{ e.promptKey }}</el-tag>
              <span v-else class="muted">未绑定</span>
            </div>
            <div class="eff-row">
              <span class="eff-label">知识库</span>
              <span v-if="!e.kbId" class="muted">不检索</span>
              <el-tag v-else effect="plain" size="small" type="warning">{{ e.kbName }}</el-tag>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="page-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="处理环节" width="130">
          <template #default="{ row }">
            <div class="scene-cell">
              <span class="scene-name">{{ row.sceneName }}</span>
              <code class="scene-code">{{ row.scene }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="使用模型" min-width="200">
          <template #default="{ row }">
            <div class="model-cell">
              <span class="model-label">
                {{ modelLabel(row) }}
                <el-tooltip
                  v-if="modelNotVerified(row)"
                  :content="row.modelTestMessage || '该模型未通过连通性测试，请到「模型配置」测试通过后再保存本绑定'"
                  placement="top"
                >
                  <el-tag type="danger" size="small" effect="light">未验证</el-tag>
                </el-tooltip>
              </span>
              <code class="model-code">{{ row.modelName || '—' }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Prompt 指令" min-width="170">
          <template #default="{ row }">
            <el-tag v-if="row.promptKey" effect="plain" size="small" type="success">{{ row.promptKey }}</el-tag>
            <span v-else class="muted">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column label="知识库" min-width="120">
          <template #default="{ row }">
            <span v-if="!row.kbId" class="muted">不检索</span>
            <el-tag v-else effect="plain" size="small" type="warning">{{ row.kbName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="handleToggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="warning" link @click="openEdit(row)">配置</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 配置弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? `配置 - ${form.sceneName}` : '新增模型绑定'"
      width="560px"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="处理环节">
          <el-select
            v-if="!isEdit"
            v-model="form.scene"
            placeholder="请选择处理环节"
            style="width: 100%"
            @change="onSceneChange"
          >
            <el-option
              v-for="s in unboundScenes"
              :key="s.scene"
              :label="`${s.sceneName} (${s.scene})`"
              :value="s.scene"
            />
          </el-select>
          <template v-else>
            <el-tag>{{ form.sceneName }}</el-tag>
            <code class="scene-code inline">{{ form.scene }}</code>
          </template>
        </el-form-item>
        <el-form-item label="使用模型" required>
          <el-select v-model="form.modelId" placeholder="请选择模型" style="width: 100%" filterable>
            <el-option
              v-for="m in modelOptions"
              :key="m.id"
              :label="`${m.name} (${m.modelName})`"
              :value="m.id"
            />
          </el-select>
          <div v-if="!modelOptions.length" class="form-hint warn">
            暂无可绑定的模型：请先到「模型配置」新增一个<b>对话模型</b>并点击「测试」通过
          </div>
          <div v-else class="form-hint">
            仅列出「对话模型 + 测试通过 + 已启用」的模型；向量/重排模型不具备对话能力，不会出现在这里
          </div>
        </el-form-item>
        <el-form-item label="Prompt 指令">
          <el-select v-model="form.promptId" placeholder="不绑定（回退该场景默认指令）" clearable style="width: 100%">
            <el-option
              v-for="p in promptsForScene(form.scene)"
              :key="p.id"
              :label="`${p.name} (${p.promptKey})${p.scene === form.scene ? '' : ' · ' + p.scene}`"
              :value="p.id"
            />
          </el-select>
          <div class="form-hint">
            绑定后运行时<b>优先使用该指令</b>；不绑定则使用「Prompt 设置」中 {{ form.sceneName || form.scene }} 场景的默认指令。
            列表已把本场景的指令排在前面。
          </div>
        </el-form-item>
        <el-form-item label="知识库">
          <el-select v-model="form.kbId" placeholder="不检索" style="width: 100%">
            <el-option label="不检索" :value="0" />
            <el-option
              v-for="k in kbOptions"
              :key="k.id"
              :label="k.name"
              :value="k.id"
            />
          </el-select>
          <div class="form-hint">
            作为<b>默认检索范围</b>生效：用户端在会话中显式选择了知识库时以用户的选择为准，未选择时用这里的设定
          </div>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="说明（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
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
.info-alert {
  margin-bottom: 16px;
  border-radius: 6px;
}
.page-card {
  border-radius: 6px;
}
.eff-card {
  margin-bottom: 16px;
}
.eff-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.eff-header-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}
.eff-header-hint {
  font-size: 12px;
  color: #909399;
}
.eff-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #fafbfc;
}
.eff-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.eff-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 2;
}
.eff-label {
  flex: none;
  width: 76px;
  color: #909399;
}
.scene-cell,
.model-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.scene-name {
  font-weight: 500;
  color: #303133;
}
.scene-code,
.model-code {
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 3px;
  padding: 0 4px;
  width: fit-content;
}
.scene-code.inline {
  margin-left: 8px;
}
.muted {
  color: #c0c4cc;
  font-size: 13px;
}
.model-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.form-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
}
.form-hint.warn {
  color: #e6a23c;
}
</style>
