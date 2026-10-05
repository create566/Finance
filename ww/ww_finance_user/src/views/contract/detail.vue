<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContractDetail,
  signContract,
  CONTRACT_TYPE_META,
  CONTRACT_STATUS_META,
  contractTypeText,
  type ContractDetail,
  type ContractType,
} from '@/api/contract'
import SignPad from '@/components/SignPad.vue'

const route = useRoute()
const router = useRouter()

const contractId = Number(route.params.id)
const loading = ref(false)
const detail = ref<ContractDetail | null>(null)

const signDialogVisible = ref(false)
const submitting = ref(false)
const signPadRef = ref<InstanceType<typeof SignPad> | null>(null)

/** 大额合同（≥5万）在签名之外再加一道显式确认，贴合"必要时手动签名保证安全" */
const LARGE_AMOUNT = 50000

const contract = computed(() => detail.value?.contract || null)
const isSigned = computed(() => contract.value?.status === 1)
const isRevoked = computed(() => contract.value?.status === 2)
const typeMeta = computed(() => CONTRACT_TYPE_META[(contract.value?.contractType || 'LOAN') as ContractType])
const hashValid = computed(() => detail.value?.hashValid !== false)
const shortHash = computed(() => {
  const h = contract.value?.contentHash || ''
  return h.length > 24 ? `${h.slice(0, 12)}…${h.slice(-8)}` : h
})
const isLargeAmount = computed(() => {
  const a = contract.value?.amount
  return a !== null && a !== undefined && Number(a) >= LARGE_AMOUNT
})

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await getContractDetail(contractId)
  } catch {
    // request.ts 已统一提示
  } finally {
    loading.value = false
  }
}

function openSignDialog() {
  signDialogVisible.value = true
}

function closeSignDialog() {
  signDialogVisible.value = false
  // 关闭时清空画布，避免残留上一次的笔迹被误提交
  signPadRef.value?.clear()
}

async function handleSign() {
  const pad = signPadRef.value
  if (!pad) return
  if (pad.isEmpty()) {
    ElMessage.warning('请先手写签名')
    return
  }
  const img = pad.exportBase64()
  if (!img) {
    ElMessage.warning('签名内容为空，请重新书写')
    return
  }

  // 大额合同：签名之外再要一次显式确认，避免误点
  if (isLargeAmount.value) {
    try {
      await ElMessageBox.confirm(
        `本合同金额为 ¥${Number(contract.value?.amount).toFixed(2)}，属于大额合同。签署后即产生法律效力，确认继续？`,
        '大额合同二次确认',
        { type: 'warning', confirmButtonText: '确认签署', cancelButtonText: '再想想' },
      )
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    const res = await signContract({ contractId, signImgBase64: img })
    ElMessage.success(`《${typeMeta.value.text}》签署成功，合同编号 ${res.contractNo}`)
    signDialogVisible.value = false
    await fetchDetail()
  } catch {
    // request.ts 已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await fetchDetail()
  if (route.query.sign === '1' && contract.value?.status === 0) {
    openSignDialog()
  }
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <el-button @click="router.push('/contract/my')">返回我的合同</el-button>
    </div>

    <template v-if="contract && detail">
      <el-card shadow="never" class="head-card">
        <div class="head-row">
          <div>
            <h2>
              《{{ typeMeta.text }}》
              <el-tag :type="CONTRACT_STATUS_META[contract.status]?.tag || 'info'" effect="dark" class="status-tag">
                {{ CONTRACT_STATUS_META[contract.status]?.text || '未知' }}
              </el-tag>
            </h2>
            <p class="tip">{{ typeMeta.hint }}</p>
          </div>
          <div class="head-actions">
            <el-button v-if="!isSigned && !isRevoked" type="danger" size="large" @click="openSignDialog">
              立即签署
            </el-button>
          </div>
        </div>

        <el-descriptions :column="2" border class="meta">
          <el-descriptions-item label="合同编号">{{ contract.contractNo }}</el-descriptions-item>
          <el-descriptions-item label="合同类型">{{ contractTypeText(contract.contractType) }}</el-descriptions-item>
          <el-descriptions-item label="甲方（出借方）">{{ contract.partyAName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="乙方（借款方）">{{ contract.partyBName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="合同金额">
            <span v-if="contract.amount !== null" class="money">¥{{ Number(contract.amount).toFixed(2) }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="年化利率">
            {{ contract.lendYearRate !== null ? contract.lendYearRate + '%' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="借款期数">{{ contract.period !== null ? contract.period + ' 期' : '-' }}</el-descriptions-item>
          <el-descriptions-item label="模板版本">v{{ contract.templateVersion }}</el-descriptions-item>
          <el-descriptions-item label="生成时间" :span="2">{{ contract.createTime }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="content-card">
        <template #header>
          <div class="card-header">
            <span>合同正文</span>
            <div class="hash-box">
              <el-tag :type="hashValid ? 'success' : 'danger'" effect="plain" size="small">
                {{ hashValid ? '哈希校验通过 · 正文未被篡改' : '警告：正文哈希与存证不一致' }}
              </el-tag>
              <span class="hash-text" :title="contract.contentHash">SHA-256：{{ shortHash }}</span>
            </div>
          </div>
        </template>
        <!-- 纯文本插值渲染，不用 v-html，从根上避免模板变量带 XSS -->
        <pre class="contract-body">{{ detail.content }}</pre>
      </el-card>

      <el-card shadow="never" class="sign-card">
        <template #header>
          <span>签署存证</span>
        </template>

        <template v-if="detail.signRecord">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="签署人">{{ detail.signRecord.signerName }}</el-descriptions-item>
            <el-descriptions-item label="签署角色">
              {{ detail.signRecord.signerRole === 'PARTY_A' ? '甲方' : '乙方' }}
            </el-descriptions-item>
            <el-descriptions-item label="证件号">{{ detail.signRecord.signerIdCard || '-' }}</el-descriptions-item>
            <el-descriptions-item label="签署时间">{{ detail.signRecord.signTime }}</el-descriptions-item>
            <el-descriptions-item label="签署 IP">{{ detail.signRecord.signIp || '-' }}</el-descriptions-item>
            <el-descriptions-item label="客户端">{{ detail.signRecord.userAgent || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="sign-img-wrap">
            <p class="sign-img-label">手写签名影像</p>
            <img :src="detail.signRecord.signImgUrl" alt="手写签名" class="sign-img" />
          </div>
        </template>

        <el-alert v-else type="warning" :closable="false" show-icon
          title="本合同尚未签署"
          :description="isRevoked ? '合同已作废，无法签署。' : '请点击右上角「立即签署」完成手写签名。'" />
      </el-card>
    </template>

    <el-empty v-else-if="!loading" description="合同不存在或无权查看" />

    <!-- 手写签名弹窗 -->
    <el-dialog
      v-model="signDialogVisible"
      title="手写签名"
      width="680px"
      :close-on-click-modal="false"
      @close="closeSignDialog"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="dialog-alert"
        title="签名即代表您已阅读并同意本合同全部条款，签署后不可撤销。"
        description="系统将记录签名影像、签署时间、IP 与客户端信息，并对合同正文做 SHA-256 存证，可用于事后验真。"
      />
      <SignPad ref="signPadRef" :width="600" :height="220" />
      <p class="after-sign-tip">
        合同编号：{{ contract?.contractNo }} ·
        金额：{{ contract?.amount !== null ? '¥' + Number(contract?.amount).toFixed(2) : '-' }}
      </p>

      <template #footer>
        <el-button @click="closeSignDialog">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSign">确认签署</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  width: 100%;
}
.page-header {
  margin-bottom: 16px;
}
.head-card,
.content-card,
.sign-card {
  margin-bottom: 16px;
}
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.head-row h2 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}
.status-tag {
  font-weight: 400;
}
.tip {
  margin: 8px 0 0;
  font-size: 13px;
  color: #909399;
}
.meta {
  margin-top: 16px;
}
.money {
  font-weight: 600;
  color: #e6a23c;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.hash-box {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hash-text {
  font-size: 12px;
  color: #909399;
  font-family: Consolas, Monaco, monospace;
}
.contract-body {
  margin: 0;
  max-height: 460px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 2;
  color: #303133;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 20px 24px;
  /* 降低"随手复制正文"的可能（不是安全边界，真正的防篡改靠哈希） */
  user-select: text;
}
.sign-img-wrap {
  margin-top: 16px;
}
.sign-img-label {
  margin: 0 0 8px;
  font-size: 13px;
  color: #606266;
}
.sign-img {
  max-width: 320px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}
.dialog-alert {
  margin-bottom: 14px;
}
.after-sign-tip {
  margin: 12px 0 0;
  font-size: 13px;
  color: #909399;
}
</style>
