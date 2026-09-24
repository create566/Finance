<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { submitBorrowInfo, getBorrowInfoStatus } from '@/api/borrowInfo'
import { getBorrowerAuthStatus } from '@/api/borrower'

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

const returnMethodOptions = [
  { label: '等额本息', value: 1 },
  { label: '等额本金', value: 2 },
  { label: '每月还息一次还本', value: 3 },
  { label: '一次还本付息', value: 4 },
]

const returnMethodMap: Record<number, string> = {
  1: '等额本息',
  2: '等额本金',
  3: '每月还息一次还本',
  4: '一次还本付息',
}

const moneyUseOptions = [
  { label: '消费', value: 1 },
  { label: '经营', value: 2 },
  { label: '教育', value: 3 },
  { label: '医疗', value: 4 },
  { label: '其他', value: 5 },
]

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
  <div class="page-container">
    <div class="page-header"><h2>借款申请</h2></div>

    <el-alert
      v-if="authStatus !== 2"
      title="您还未通过借款人认证，请先完成认证后再申请借款"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card v-if="borrowStatus?.hasApply" shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">申请状态</span>
          <el-tag v-if="borrowStatus.borrowInfo?.status === 0" type="info">草稿</el-tag>
          <el-tag v-else-if="borrowStatus.borrowInfo?.status === 1" type="warning">审核中</el-tag>
          <el-tag v-else-if="borrowStatus.borrowInfo?.status === 2" type="success">审核通过</el-tag>
          <el-tag v-else-if="borrowStatus.borrowInfo?.status === -1" type="danger">审核拒绝</el-tag>
          <el-tag v-else type="info">未知</el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="借款金额">¥{{ borrowStatus.borrowInfo?.amount?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="借款期限">{{ borrowStatus.borrowInfo?.period }} 个月</el-descriptions-item>
        <el-descriptions-item label="年化利率">{{ borrowStatus.borrowInfo?.borrowYearRate }}%</el-descriptions-item>
        <el-descriptions-item label="还款方式">
          {{ returnMethodMap[borrowStatus.borrowInfo?.returnMethod] || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ borrowStatus.borrowInfo?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ borrowStatus.borrowInfo?.updateTime }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="借款金额(元)" prop="amount">
          <el-input-number v-model="formData.amount" :min="1000" :step="1000" :max="100000" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="借款期限(月)" prop="period">
          <el-select v-model="formData.period" placeholder="请选择期限" style="width: 100%">
            <el-option :label="'3个月'" :value="3" />
            <el-option :label="'6个月'" :value="6" />
            <el-option :label="'12个月'" :value="12" />
            <el-option :label="'18个月'" :value="18" />
            <el-option :label="'24个月'" :value="24" />
            <el-option :label="'36个月'" :value="36" />
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
          <el-button type="primary" :loading="loading" :disabled="authStatus !== 2" @click="handleSubmit">提交申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
</style>