<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getUserInfo, updateProfile } from '@/api/user'
import { getBindStatus, bindDirect } from '@/api/bind'

const formRef = ref<FormInstance>()
const bindFormRef = ref<FormInstance>()
const loading = ref(false)
const bindLoading = ref(false)
const userInfo = ref<any>(null)
const bindInfo = ref<any>(null)

const formData = reactive({
  nickName: '',
  email: '',
})

const bindFormData = reactive({
  bankNo: '',
  bankType: '工商银行',
})

const formRules: FormRules = {
  nickName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
}

const bindFormRules: FormRules = {
  bankNo: [
    { required: true, message: '请输入银行卡号', trigger: 'blur' },
    { pattern: /^\d{16,19}$/, message: '请输入16-19位纯数字的银行卡号', trigger: 'blur' },
  ],
  bankType: [{ required: true, message: '请选择银行', trigger: 'change' }],
}

const bankList = [
  '工商银行', '建设银行', '农业银行', '中国银行', '交通银行',
  '招商银行', '浦发银行', '民生银行', '兴业银行', '光大银行',
  '华夏银行', '中信银行', '邮储银行',
]

async function fetchUserInfo() {
  try {
    userInfo.value = await getUserInfo()
    formData.nickName = userInfo.value.nickName || ''
    formData.email = userInfo.value.email || ''
  } catch {}
  try {
    bindInfo.value = await getBindStatus()
  } catch {}
}

function handleSubmit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await updateProfile(formData)
      ElMessage.success('修改成功')
      fetchUserInfo()
    } catch {} finally {
      loading.value = false
    }
  })
}

function handleBindDirect() {
  if (!bindFormRef.value) return
  bindFormRef.value.validate(async (valid) => {
    if (!valid) return
    bindLoading.value = true
    try {
      await bindDirect(bindFormData)
      ElMessage.success('绑卡成功')
      fetchUserInfo()
    } catch {} finally {
      bindLoading.value = false
    }
  })
}

const authStatusMap: Record<number, { text: string; type: string }> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '认证中', type: 'warning' },
  2: { text: '已通过', type: 'success' },
  [-1]: { text: '认证失败', type: 'danger' },
}

const roleMap: Record<number, { text: string; type: string }> = {
  0: { text: '未认证', type: 'info' },
  1: { text: '投资人', type: 'primary' },
  2: { text: '借款人', type: 'warning' },
}

onMounted(fetchUserInfo)
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>个人中心</h2></div>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <div style="text-align: center; padding: 20px 0">
            <el-avatar :size="80" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
            <h3 style="margin-top: 12px">{{ userInfo?.nickName || '用户' }}</h3>
            <p style="color: #9ca3af; font-size: 13px">{{ userInfo?.mobile }}</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header><span style="font-weight: 500">基本信息</span></template>
          <el-descriptions v-if="userInfo" :column="2" border>
            <el-descriptions-item label="用户ID">{{ userInfo.id }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userInfo.mobile }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ userInfo.nickName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="真实姓名">{{ userInfo.name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="身份证号">{{ userInfo.idCard || '—' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userInfo.email || '—' }}</el-descriptions-item>
            <el-descriptions-item label="积分">{{ userInfo.integral ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag :type="(roleMap[userInfo.userType]?.type as string) || 'info'">
                {{ roleMap[userInfo.userType]?.text || '未认证' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="借款人认证">
              <el-tag :type="(authStatusMap[userInfo.borrowAuthStatus]?.type as string) || 'info'">
                {{ authStatusMap[userInfo.borrowAuthStatus]?.text || '未认证' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="绑卡状态">
              <el-tag :type="userInfo.bindStatus === 1 ? 'success' : 'info'">
                {{ userInfo.bindStatus === 1 ? '已绑卡' : '未绑卡' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span style="font-weight: 500">修改信息</span></template>
          <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
            <el-form-item label="昵称" prop="nickName">
              <el-input v-model="formData.nickName" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="formData.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span style="font-weight: 500">银行卡绑定</span></template>
          <div v-if="userInfo?.bindStatus === 1">
            <el-result icon="success" title="已绑定银行卡" sub-title="您可以进行充值、提现、投资、借款等操作">
              <template #extra>
                <el-descriptions v-if="bindInfo" :column="1" border size="small">
                  <el-descriptions-item label="银行">{{ bindInfo.bankType || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="卡号">{{ bindInfo.bankNo ? bindInfo.bankNo.replace(/^(.{4}).*(.{4})$/, '$1 **** **** $2') : '—' }}</el-descriptions-item>
                  <el-descriptions-item label="持卡人">{{ bindInfo.name || '—' }}</el-descriptions-item>
                </el-descriptions>
              </template>
            </el-result>
          </div>
          <div v-else>
            <el-alert
              v-if="userInfo?.borrowAuthStatus !== 2"
              title="请先完成借款人实名认证后再绑卡"
              type="warning"
              show-icon
              :closable="false"
              style="margin-bottom: 16px"
            />
            <el-form ref="bindFormRef" :model="bindFormData" :rules="bindFormRules" label-width="100px">
              <el-form-item label="银行卡号" prop="bankNo">
                <el-input v-model="bindFormData.bankNo" placeholder="请输入银行卡号" maxlength="30" />
              </el-form-item>
              <el-form-item label="开户银行" prop="bankType">
                <el-select v-model="bindFormData.bankType" placeholder="请选择银行" style="width: 100%">
                  <el-option v-for="bank in bankList" :key="bank" :label="bank" :value="bank" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  :loading="bindLoading"
                  :disabled="userInfo?.borrowAuthStatus !== 2"
                  @click="handleBindDirect"
                >
                  确认绑卡
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
</style>