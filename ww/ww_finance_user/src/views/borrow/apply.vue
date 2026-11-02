<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { submitBorrowInfo, getBorrowInfoStatus } from '@/api/borrowInfo'
import { getBorrowerAuthStatus } from '@/api/borrower'
import { RETURN_METHOD_TEXT, returnMethodText } from '@/utils/enumText'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import SplitPane from '@/components/common/SplitPane.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)
const authStatus = ref(0)
const borrowStatus = ref<any>(null)

const formData = reactive({
  amount: 10000,
  period: 12,
  returnMethod: 1,
  moneyUse: 1,
})

const formRules: FormRules = {
  amount: [
    { required: true, message: '请输入借款金额', trigger: 'blur' },
    { type: 'number', min: 1000, message: '借款金额不能低于1000元', trigger: 'blur' },
  ],
  period: [
    { required: true, message: '请选择借款期限', trigger: 'change' },
  ],
  returnMethod: [
    { required: true, message: '请选择还款方式', trigger: 'change' },
  ],
  moneyUse: [
    { required: true, message: '请选择资金用途', trigger: 'change' },
  ],
}

/** 还款方式选项由 utils/enumText.ts 的真源派生，避免与其它页面各写一份 */
const returnMethodOptions = Object.entries(RETURN_METHOD_TEXT).map(([value, label]) => ({
  label,
  value: Number(value),
}))

const moneyUseOptions = [
  { label: '消费', value: 1 },
  { label: '经营', value: 2 },
  { label: '教育', value: 3 },
  { label: '医疗', value: 4 },
  { label: '其他', value: 5 },
]

const appliedInfo = computed(() => borrowStatus.value?.borrowInfo ?? null)

async function fetchStatus() {
  try {
    const auth = await getBorrowerAuthStatus()
    authStatus.value = auth.borrowAuthStatus
  } catch {}
  try {
    const info = await getBorrowInfoStatus()
    borrowStatus.value = info
  } catch {}
}

function handleSubmit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    if (authStatus.value !== 2) {
      ElMessage.warning('请先完成借款人认证')
      return
    }
    loading.value = true
    try {
      await submitBorrowInfo(formData)
      ElMessage.success('借款申请已提交')
      fetchStatus()
    } catch {} finally {
      loading.value = false
    }
  })
}

onMounted(fetchStatus)
</script>

<template>
  <AppPage title="借款申请" subtitle="填写借款需求，平台审核通过后生成募资标的">
    <template #actions>
      <el-button link type="primary" @click="router.push('/borrow/my')">我的借款</el-button>
    </template>

    <div v-if="authStatus !== 2" class="apply__notice">
      <el-icon class="apply__notice-icon"><WarningFilled /></el-icon>
      <div class="apply__notice-text">
        <strong>您还未通过借款人认证</strong>
        <span>完成借款人认证后方可提交借款申请。</span>
      </div>
      <el-button type="primary" plain size="small" @click="router.push('/auth')">去认证</el-button>
    </div>

    <SplitPane aside="normal">
      <AppCard title="填写借款需求" subtitle="金额、期限、还款方式将决定后续还款计划">
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
          <el-form-item label="借款金额(元)" prop="amount">
            <el-input-number
              v-model="formData.amount"
              :min="1000"
              :step="1000"
              :max="100000"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="借款期限(月)" prop="period">
            <el-select v-model="formData.period" placeholder="请选择期限" style="width: 100%">
              <el-option v-for="m in [3, 6, 12, 18, 24, 36]" :key="m" :label="`${m}个月`" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="还款方式" prop="returnMethod">
            <el-select v-model="formData.returnMethod" placeholder="请选择还款方式" style="width: 100%">
              <el-option
                v-for="item in returnMethodOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="资金用途" prop="moneyUse">
            <el-select v-model="formData.moneyUse" placeholder="请选择用途" style="width: 100%">
              <el-option
                v-for="item in moneyUseOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              :disabled="authStatus !== 2"
              @click="handleSubmit"
            >
              提交申请
            </el-button>
          </el-form-item>
        </el-form>
      </AppCard>

      <template #aside>
        <AppCard title="申请状态" subtitle="最近一次借款申请">
          <template #extra>
            <StatusTag
              v-if="borrowStatus?.hasApply"
              :status="appliedInfo?.status"
              kind="borrowApply"
            />
          </template>

          <el-descriptions v-if="borrowStatus?.hasApply" :column="1" border size="small">
            <el-descriptions-item label="借款金额">
              <MoneyText :value="appliedInfo?.amount" />
            </el-descriptions-item>
            <el-descriptions-item label="借款期限">
              {{ appliedInfo?.period }} 个月
            </el-descriptions-item>
            <el-descriptions-item label="年化利率">
              {{ appliedInfo?.borrowYearRate }}%
            </el-descriptions-item>
            <el-descriptions-item label="还款方式">
              {{ returnMethodText(appliedInfo?.returnMethod) }}
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">
              {{ appliedInfo?.createTime }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ appliedInfo?.updateTime }}
            </el-descriptions-item>
          </el-descriptions>

          <AppEmpty v-else description="暂无借款申请记录" size="sm" />
        </AppCard>
      </template>
    </SplitPane>
  </AppPage>
</template>

<style scoped>
.apply__notice {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
  margin-bottom: var(--space-4);
}
.apply__notice-icon {
  font-size: 18px;
  color: var(--color-warning);
  flex-shrink: 0;
}
.apply__notice-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.apply__notice-text strong {
  font-size: var(--font-sm);
  color: var(--text-primary);
  font-weight: 600;
}
.apply__notice-text span {
  font-size: var(--font-xs);
  color: var(--text-secondary);
}
</style>
