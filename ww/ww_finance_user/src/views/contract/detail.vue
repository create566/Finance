<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContractDetail,
  signContract,
  exportContract,
  CONTRACT_TYPE_META,
  CONTRACT_STATUS_META,
  contractTypeText,
  type ContractDetail,
  type ContractType,
} from '@/api/contract'
import { adaptTagMap } from '@/utils/enumText'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import SplitPane from '@/components/common/SplitPane.vue'
import StatusTag from '@/components/common/StatusTag.vue'
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

/** 合同状态表由 api/contract.ts 拥有，这里只做形状适配（不复制内容） */
const contractStatusMap = adaptTagMap(CONTRACT_STATUS_META)

/**
 * 是否是本合同的签署方。
 * 《借款合同》《放款确认书》的乙方是"全体出借人"（party_b_id 为空），
 * 投资人能看到它（只读可见）但无权签 —— 由后端 detail 返回的 signable 决定，
 * 前后端同一判据，避免前端凭 partyBName 之类的展示字段猜。
 */
const isSignable = computed(() => detail.value?.signable === true)

/** 甲乙方标签随合同类型变化：《借款合同》甲方是借款人，别一律写成"出借方" */
const partyALabel = computed(() => {
  switch (contract.value?.contractType) {
    case 'INVEST':
      return '甲方（出借人 / 投资人）'
    case 'SERVICE':
    case 'DEPOSIT':
      return '甲方（用户）'
    default:
      return '甲方（借款人）'
  }
})
const partyBLabel = computed(() => {
  switch (contract.value?.contractType) {
    case 'INVEST':
      return '乙方（借款人）'
    case 'SERVICE':
      return '乙方（居间服务方）'
    case 'DEPOSIT':
      return '乙方（存管方）'
    default:
      return '乙方（全体出借人）'
  }
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

/** 导出合同正文为本地文件（P9-65）。用 Blob 落地，避免另开一条 blob 下载通道 */
async function handleExport() {
  if (!contract.value) return
  try {
    const res = await exportContract(contractId)
    const blob = new Blob([res.content ?? ''], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = res.fileName || `${contract.value.contractNo}.txt`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    // request.ts 已统一提示
  }
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
  <AppPage
    :title="contract ? `《${typeMeta.text}》` : '合同详情'"
    :subtitle="typeMeta?.hint"
  >
    <template #actions>
      <template v-if="contract && detail">
        <StatusTag :status="contract.status" :map="contractStatusMap" size="default" />
        <el-button @click="router.push('/contract/my')">返回我的合同</el-button>
        <el-button @click="handleExport">下载合同</el-button>
        <el-button v-if="!isSigned && !isRevoked && isSignable" type="danger" @click="openSignDialog">
          立即签署
        </el-button>
        <el-tag v-else-if="!isSigned && !isRevoked && !isSignable" type="info">非签署方（只读）</el-tag>
      </template>
    </template>

    <div v-loading="loading">
      <SplitPane v-if="contract && detail" aside="wide">
        <div class="cd__main">
          <AppCard title="合同正文" subtitle="正文在生成时冻结，可对照下方哈希验真">
            <template #extra>
              <el-tag :type="hashValid ? 'success' : 'danger'" effect="plain" size="small">
                {{ hashValid ? '哈希校验通过 · 正文未被篡改' : '警告：正文哈希与存证不一致' }}
              </el-tag>
              <span class="hash-text" :title="contract.contentHash">SHA-256：{{ shortHash }}</span>
            </template>
            <!-- 纯文本插值渲染，不用 v-html，从根上避免模板变量带 XSS -->
            <pre class="contract-body">{{ detail.content }}</pre>
          </AppCard>

          <AppCard title="签署存证" subtitle="签名影像、时间、IP 与客户端信息">
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

            <div v-else class="sign-pending">
              <el-icon class="sign-pending__icon"><WarningFilled /></el-icon>
              <div>
                <div class="sign-pending__title">本合同尚未签署</div>
                <div class="sign-pending__desc">
                  {{ isRevoked ? '合同已作废，无法签署。' : '请点击右上角「立即签署」完成手写签名。' }}
                </div>
              </div>
            </div>
          </AppCard>
        </div>

        <template #aside>
          <AppCard title="合同要素">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="合同编号">{{ contract.contractNo }}</el-descriptions-item>
              <el-descriptions-item label="合同类型">
                {{ contractTypeText(contract.contractType) }}
              </el-descriptions-item>
              <el-descriptions-item :label="partyALabel">{{ contract.partyAName || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="partyBLabel">{{ contract.partyBName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="合同金额">
                <MoneyText v-if="contract.amount !== null" :value="contract.amount" />
                <span v-else class="muted">-</span>
              </el-descriptions-item>
              <el-descriptions-item label="年化利率">
                {{ contract.lendYearRate !== null ? contract.lendYearRate + '%' : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="借款期数">
                {{ contract.period !== null ? contract.period + ' 期' : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="模板版本">v{{ contract.templateVersion }}</el-descriptions-item>
              <el-descriptions-item label="生成时间">{{ contract.createTime }}</el-descriptions-item>
            </el-descriptions>
          </AppCard>
        </template>
      </SplitPane>

      <AppEmpty v-else-if="!loading" description="合同不存在或无权查看" />
    </div>

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
  </AppPage>
</template>

<style scoped>
.cd__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.muted {
  color: var(--text-disabled);
}
.hash-text {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
  font-family: Consolas, Monaco, monospace;
}
.contract-body {
  margin: 0;
  max-height: 460px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: var(--font-base);
  line-height: 2;
  color: var(--text-regular);
  background: var(--bg-sunken);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  padding: var(--space-5);
  /* 降低"随手复制正文"的可能（不是安全边界，真正的防篡改靠哈希） */
  user-select: text;
}
.sign-img-wrap {
  margin-top: var(--space-4);
}
.sign-img-label {
  margin: 0 0 var(--space-2);
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.sign-img {
  max-width: 320px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: #fff;
}
.sign-pending {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-base);
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
}
.sign-pending__icon {
  font-size: 18px;
  color: var(--color-warning);
  flex-shrink: 0;
}
.sign-pending__title {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.sign-pending__desc {
  font-size: var(--font-xs);
  color: var(--text-secondary);
  margin-top: 2px;
}
.dialog-alert {
  margin-bottom: var(--space-4);
}
.after-sign-tip {
  margin: var(--space-3) 0 0;
  font-size: var(--font-sm);
  color: var(--text-placeholder);
}
</style>
