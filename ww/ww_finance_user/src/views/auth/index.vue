<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { submitBorrowerAuth, getBorrowerAuthStatus } from '@/api/borrower'
import { submitInvestorAuth, getInvestorAuthStatus } from '@/api/investor'
import { uploadFile } from '@/api/upload'
import { getUserInfo } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const userInfo = ref<any>(null)

// 借款人/投资人各自的认证状态
const borrowStatus = ref<number>(0)
const investStatus = ref<number>(0)

// 认证类型（下拉选择）：investor 投资人认证 / borrower 借款人认证
const authType = ref<'investor' | 'borrower'>('investor')

// 当前类型对应的状态
const currentStatus = computed(() =>
  authType.value === 'investor' ? investStatus.value : borrowStatus.value
)

// ------ 借款人表单（完整字段） ------
const borrowerFormRef = ref<FormInstance>()
const borrowerForm = reactive({
  name: '',
  idCard: '',
  idCardFrontUrl: '',
  idCardBackUrl: '',
  sex: 1,
  age: 25,
  education: 3,
  isMarry: 0,
  industry: 1,
  income: 10000,
  returnSource: 1,
  contactsName: '',
  contactsMobile: '',
  contactsRelation: 1,
})

const educationOptions = [
  { label: '高中', value: 1 },
  { label: '大专', value: 2 },
  { label: '本科', value: 3 },
  { label: '硕士', value: 4 },
  { label: '博士', value: 5 },
]
const industryOptions = [
  { label: 'IT/互联网', value: 1 },
  { label: '金融', value: 2 },
  { label: '教育', value: 3 },
  { label: '医疗', value: 4 },
  { label: '制造业', value: 5 },
  { label: '服务业', value: 6 },
  { label: '其他', value: 7 },
]
const returnSourceOptions = [
  { label: '工资收入', value: 1 },
  { label: '经营收入', value: 2 },
  { label: '投资收益', value: 3 },
  { label: '其他', value: 4 },
]
const contactRelationOptions = [
  { label: '配偶', value: 1 },
  { label: '父母', value: 2 },
  { label: '兄弟姐妹', value: 3 },
  { label: '朋友', value: 4 },
  { label: '同事', value: 5 },
]

const borrowerRules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' },
  ],
  idCardFrontUrl: [{ required: true, message: '请上传身份证正面照片', trigger: 'change' }],
  idCardBackUrl: [{ required: true, message: '请上传身份证反面照片', trigger: 'change' }],
  contactsName: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  contactsMobile: [
    { required: true, message: '请输入联系人手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

// ------ 投资人表单（精简字段） ------
const investorFormRef = ref<FormInstance>()
const investorForm = reactive({
  name: '',
  idCard: '',
  idCardFrontUrl: '',
  idCardBackUrl: '',
  sex: 1,
  age: 25,
})

const investorRules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' },
  ],
  idCardFrontUrl: [{ required: true, message: '请上传身份证正面照片', trigger: 'change' }],
  idCardBackUrl: [{ required: true, message: '请上传身份证反面照片', trigger: 'change' }],
}

// ------ 数据加载 ------
async function fetchStatus() {
  try {
    const [borrower, investor, user] = await Promise.all([
      getBorrowerAuthStatus().catch(() => null),
      getInvestorAuthStatus().catch(() => null),
      getUserInfo().catch(() => null),
    ])
    if (borrower) borrowStatus.value = (borrower as any).borrowAuthStatus ?? 0
    if (investor) investStatus.value = (investor as any).investAuthStatus ?? 0
    if (user) {
      userInfo.value = user
      // 注册成为投资人（userType=1）默认选中投资人认证，否则默认借款人认证
      authType.value = user.userType === 1 ? 'investor' : 'borrower'
      // 用已有认证信息回填公共字段
      const src = authType.value === 'investor'
        ? (investor as any)?.investor
        : (borrower as any)?.borrower
      if (src) {
        fillCommon(src)
      }
    }
  } catch {}
}

/** 用已有认证档案回填公共字段，切换类型时同步 */
function fillCommon(src: any) {
  if (!src) return
  const common = {
    name: src.name || '',
    idCard: src.idCard || '',
    sex: src.sex ?? 1,
    age: src.age ?? 25,
  }
  Object.assign(investorForm, common)
  Object.assign(borrowerForm, common)
}

/** 切换认证类型时，把已填的公共字段带到另一种类型，避免重复填写 */
function handleTypeChange() {
  const src = authType.value === 'investor' ? borrowerForm : investorForm
  const dst = authType.value === 'investor' ? investorForm : borrowerForm
  dst.name = src.name
  dst.idCard = src.idCard
  dst.sex = src.sex
  dst.age = src.age
}

// ------ 提交 ------
function handleSubmit() {
  const formRef = authType.value === 'investor' ? investorFormRef.value : borrowerFormRef.value
  if (!formRef) return
  formRef.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      if (authType.value === 'investor') {
        await submitInvestorAuth(investorForm)
        ElMessage.success('投资人认证申请已提交，请等待审核')
        investStatus.value = 1
      } else {
        await submitBorrowerAuth(borrowerForm)
        ElMessage.success('借款人认证申请已提交，请等待审核')
        borrowStatus.value = 1
      }
    } catch {} finally {
      loading.value = false
    }
  })
}

// ------ 认证通过后的跳转 ------
function goBorrowApply() {
  router.push('/borrow/apply')
}
function goInvestList() {
  router.push('/invest/list')
}

// ------ 身份证上传 ------
const uploadLoading = ref(false)

async function handleUpload(form: any, field: 'idCardFrontUrl' | 'idCardBackUrl', file: File) {
  uploadLoading.value = true
  try {
    const url = await uploadFile(file)
    form[field] = url
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
        <h2>实名认证</h2>
        <p>选择认证类型，填写真实信息并通过平台审核后，即可享受对应服务</p>
      </div>
    </div>

    <div class="auth-body">
      <!-- 认证类型下拉 -->
      <el-card shadow="never" class="type-card">
        <div class="type-row">
          <span class="type-label">认证类型</span>
          <el-select v-model="authType" style="width: 260px" @change="handleTypeChange">
            <el-option label="投资人认证（认证后可参与投标）" value="investor" />
            <el-option label="借款人认证（认证后可申请借款）" value="borrower" />
          </el-select>
        </div>
      </el-card>

      <!-- 三步进度条 -->
      <el-card shadow="never" class="steps-card">
        <el-steps :active="stepActive" finish-status="success" align-center>
          <el-step :title="authType === 'investor' ? '填写投资人信息' : '填写借款人信息'" description="提交个人信息与身份证资料" />
          <el-step title="提交平台审核" description="管理员审核认证资料" />
          <el-step title="等待认证结果" :description="authType === 'investor' ? '审核通过后可参与投标' : '审核通过后可申请借款'" />
        </el-steps>
      </el-card>

      <!-- 认证通过 -->
      <el-card shadow="never" v-if="currentStatus === 2" class="result-card">
        <el-result icon="success" title="您的认证审批已通过"
          :sub-title="authType === 'investor' ? '恭喜您已通过投资人认证，现在可以参与投标了。' : '恭喜您已通过借款人认证，现在可以申请借款了。'">
          <template #extra>
            <div style="display: flex; gap: 12px; justify-content: center">
              <el-button v-if="authType === 'investor'" type="primary" size="large" @click="goInvestList">浏览标的</el-button>
              <el-button v-else type="primary" size="large" @click="goBorrowApply">我要借款</el-button>
              <el-button size="large" @click="goInvestList">浏览标的</el-button>
            </div>
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
            <el-button type="primary" @click="authType === 'investor' ? (investStatus = 0) : (borrowStatus = 0)">重新认证</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 未认证：填写表单 -->
      <el-card shadow="never" v-else class="form-card">
        <!-- ===== 投资人表单 ===== -->
        <el-form v-if="authType === 'investor'" ref="investorFormRef" :model="investorForm" :rules="investorRules" label-width="110px" size="default">
          <div class="section-title">
            <el-icon><User /></el-icon>
            <span>基本信息</span>
          </div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="姓名" prop="name">
                <el-input v-model="investorForm.name" placeholder="请输入真实姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证号" prop="idCard">
                <el-input v-model="investorForm.idCard" placeholder="请输入身份证号" maxlength="18" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="性别">
                <el-radio-group v-model="investorForm.sex">
                  <el-radio :value="1">男</el-radio>
                  <el-radio :value="0">女</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="年龄">
                <el-input-number v-model="investorForm.age" :min="18" :max="70" style="width: 100%" />
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
                <el-upload :auto-upload="false" :show-file-list="false" accept="image/*"
                  :on-change="(f: any) => handleUpload(investorForm, 'idCardFrontUrl', f.raw)">
                  <div v-if="investorForm.idCardFrontUrl" class="id-card-preview">
                    <el-image :src="investorForm.idCardFrontUrl" fit="cover" />
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
                <el-upload :auto-upload="false" :show-file-list="false" accept="image/*"
                  :on-change="(f: any) => handleUpload(investorForm, 'idCardBackUrl', f.raw)">
                  <div v-if="investorForm.idCardBackUrl" class="id-card-preview">
                    <el-image :src="investorForm.idCardBackUrl" fit="cover" />
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

        <!-- ===== 借款人表单 ===== -->
        <el-form v-else ref="borrowerFormRef" :model="borrowerForm" :rules="borrowerRules" label-width="110px" size="default">
          <div class="section-title">
            <el-icon><User /></el-icon>
            <span>基本信息</span>
          </div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="姓名" prop="name">
                <el-input v-model="borrowerForm.name" placeholder="请输入真实姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="身份证号" prop="idCard">
                <el-input v-model="borrowerForm.idCard" placeholder="请输入身份证号" maxlength="18" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="性别">
                <el-radio-group v-model="borrowerForm.sex">
                  <el-radio :value="1">男</el-radio>
                  <el-radio :value="0">女</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="年龄">
                <el-input-number v-model="borrowerForm.age" :min="18" :max="70" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="婚姻状况">
                <el-radio-group v-model="borrowerForm.isMarry">
                  <el-radio :value="0">未婚</el-radio>
                  <el-radio :value="1">已婚</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="学历">
                <el-select v-model="borrowerForm.education" style="width: 100%" placeholder="请选择">
                  <el-option v-for="item in educationOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="行业">
                <el-select v-model="borrowerForm.industry" style="width: 100%" placeholder="请选择">
                  <el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="月收入(元)">
                <el-input-number v-model="borrowerForm.income" :min="0" :max="9999999" style="width: 100%" placeholder="请输入月收入" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="还款来源">
            <el-select v-model="borrowerForm.returnSource" style="width: 300px" placeholder="请选择">
              <el-option v-for="item in returnSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <div class="section-title">
            <el-icon><Phone /></el-icon>
            <span>联系人信息</span>
          </div>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="联系人" prop="contactsName">
                <el-input v-model="borrowerForm.contactsName" placeholder="联系人姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="联系人电话" prop="contactsMobile">
                <el-input v-model="borrowerForm.contactsMobile" placeholder="联系人手机号" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="关系">
                <el-select v-model="borrowerForm.contactsRelation" style="width: 100%" placeholder="请选择">
                  <el-option v-for="item in contactRelationOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
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
                <el-upload :auto-upload="false" :show-file-list="false" accept="image/*"
                  :on-change="(f: any) => handleUpload(borrowerForm, 'idCardFrontUrl', f.raw)">
                  <div v-if="borrowerForm.idCardFrontUrl" class="id-card-preview">
                    <el-image :src="borrowerForm.idCardFrontUrl" fit="cover" />
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
                <el-upload :auto-upload="false" :show-file-list="false" accept="image/*"
                  :on-change="(f: any) => handleUpload(borrowerForm, 'idCardBackUrl', f.raw)">
                  <div v-if="borrowerForm.idCardBackUrl" class="id-card-preview">
                    <el-image :src="borrowerForm.idCardBackUrl" fit="cover" />
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
  background: linear-gradient(135deg, #667eea 0%, #38ef7d 100%);
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
.type-card {
  margin-bottom: 20px;
}
.type-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.type-label {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
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
