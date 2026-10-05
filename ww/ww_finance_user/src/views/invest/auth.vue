<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { submitInvestorAuth, getInvestorAuthStatus } from '@/api/investor'
import { uploadFile } from '@/api/upload'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const currentStatus = ref<number>(0)

const formData = reactive({
  name: '',
  idCard: '',
  idCardFrontUrl: '',
  idCardBackUrl: '',
  sex: 1,
  age: 25,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' },
  ],
  idCardFrontUrl: [{ required: true, message: '请上传身份证正面照片', trigger: 'change' }],
  idCardBackUrl: [{ required: true, message: '请上传身份证反面照片', trigger: 'change' }],
}

async function fetchStatus() {
  try {
    const data = await getInvestorAuthStatus()
    currentStatus.value = data.investAuthStatus
  } catch {}
}

function handleSubmit() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await submitInvestorAuth(formData)
      ElMessage.success('认证申请已提交，请等待审核')
      currentStatus.value = 1
    } catch {} finally {
      loading.value = false
    }
  })
}

function goInvestList() {
  router.push('/invest/list')
}

const uploadLoading = ref(false)

async function handleUpload(field: 'idCardFrontUrl' | 'idCardBackUrl', file: File) {
  uploadLoading.value = true
  try {
    const url = await uploadFile(file)
    ;(formData as any)[field] = url
    ElMessage.success('上传成功')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploadLoading.value = false
  }
  return false
}

const stepActive = computed(() => {
  if (currentStatus.value === 0) return 0
  if (currentStatus.value === 1) return 1
  return 2
})

onMounted(fetchStatus)
</script>

<template>
  <div class="auth-page">
    <div class="auth-banner">
      <div class="banner-content">
        <h2>投资人信息认证</h2>
        <p>完成实名认证后即可参与投标，资金安全更有保障</p>
      </div>
    </div>

    <div class="auth-body">
      <!-- 三步进度条 -->
      <el-card shadow="never" class="steps-card">
        <el-steps :active="stepActive" finish-status="success" align-center>
          <el-step title="填写投资人信息" description="提交个人信息与身份证资料" />
          <el-step title="提交平台审核" description="管理员审核认证资料" />
          <el-step title="等待认证结果" description="审核通过后即可投标" />
        </el-steps>
      </el-card>

      <!-- 认证通过 -->
      <el-card shadow="never" v-if="currentStatus === 2" class="result-card">
        <el-result icon="success" title="您的认证审批已通过" sub-title="恭喜您已通过认证，现在可以参与投标了。">
          <template #extra>
            <el-button type="primary" size="large" @click="goInvestList">浏览标的</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 认证中 -->
      <el-card shadow="never" v-else-if="currentStatus === 1" class="result-card">
        <el-result icon="warning" title="认证审核中" sub-title="您的认证申请已提交，请耐心等待管理员审核。">
          <template #extra>
            <el-tag type="warning" size="large">审核中</el-tag>
          </template>
        </el-result>
      </el-card>

      <!-- 认证失败 -->
      <el-card shadow="never" v-else-if="currentStatus === -1" class="result-card">
        <el-result icon="error" title="认证未通过" sub-title="您的认证审核未通过，请修改信息后重新提交。">
          <template #extra>
            <el-button type="primary" @click="currentStatus = 0">重新认证</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 未认证：填写表单 -->
      <el-card shadow="never" v-else class="form-card">
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px" size="default">
          <div class="section-title">
            <el-icon><User /></el-icon>
            <span>基本信息</span>
          </div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="姓名" prop="name">
                <el-input v-model="formData.name" placeholder="请输入真实姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证号" prop="idCard">
                <el-input v-model="formData.idCard" placeholder="请输入身份证号" maxlength="18" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="性别">
                <el-radio-group v-model="formData.sex">
                  <el-radio :value="1">男</el-radio>
                  <el-radio :value="0">女</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="年龄">
                <el-input-number v-model="formData.age" :min="18" :max="70" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <div class="section-title">
            <el-icon><CreditCard /></el-icon>
            <span>身份证照片（必填）</span>
          </div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="身份证正面" prop="idCardFrontUrl">
                <el-upload
                  :auto-upload="false"
                  :show-file-list="false"
                  accept="image/*"
                  :on-change="(f: any) => handleUpload('idCardFrontUrl', f.raw)"
                >
                  <div v-if="formData.idCardFrontUrl" class="id-card-preview">
                    <el-image :src="formData.idCardFrontUrl" fit="cover" />
                  </div>
                  <div v-else class="id-card-upload">
                    <el-icon :size="28"><Plus /></el-icon>
                    <span>点击上传</span>
                  </div>
                </el-upload>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证反面" prop="idCardBackUrl">
                <el-upload
                  :auto-upload="false"
                  :show-file-list="false"
                  accept="image/*"
                  :on-change="(f: any) => handleUpload('idCardBackUrl', f.raw)"
                >
                  <div v-if="formData.idCardBackUrl" class="id-card-preview">
                    <el-image :src="formData.idCardBackUrl" fit="cover" />
                  </div>
                  <div v-else class="id-card-upload">
                    <el-icon :size="28"><Plus /></el-icon>
                    <span>点击上传</span>
                  </div>
                </el-upload>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item style="margin-top: 24px">
            <el-button type="primary" size="large" :loading="loading" @click="handleSubmit" style="width: 200px">
              提交认证
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  width: 100%;
  min-height: 100%;
}
.auth-banner {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  padding: 28px 32px;
  border-radius: 8px;
  margin-bottom: 20px;
}
.banner-content h2 {
  font-size: 22px;
  font-weight: 600;
  color: #fff;
  margin: 0 0 6px;
}
.banner-content p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
}
.auth-body {
  max-width: 960px;
}
.steps-card {
  margin-bottom: 20px;
}
.result-card {
  margin-bottom: 20px;
}
.form-card {
  margin-bottom: 20px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin: 20px 0 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e5e7eb;
}
.section-title:first-child {
  margin-top: 0;
}
.id-card-upload {
  width: 148px;
  height: 100px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #8c939d;
  cursor: pointer;
  transition: border-color 0.3s;
}
.id-card-upload:hover {
  border-color: #409eff;
}
.id-card-upload span {
  font-size: 12px;
}
.id-card-preview {
  width: 148px;
  height: 100px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}
.id-card-preview :deep(.el-image) {
  width: 100%;
  height: 100%;
}
</style>
