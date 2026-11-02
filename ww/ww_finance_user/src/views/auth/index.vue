<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { submitBorrowerAuth, getBorrowerAuthStatus } from '@/api/borrower'
import { submitInvestorAuth, getInvestorAuthStatus } from '@/api/investor'
import { uploadFile } from '@/api/upload'
import { getUserInfo } from '@/api/user'
import AppCard from '@/components/common/AppCard.vue'
import GradientBanner from '@/components/common/GradientBanner.vue'
import SplitPane from '@/components/common/SplitPane.vue'
import StatusTag from '@/components/common/StatusTag.vue'

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
  <div class="auth">
    <GradientBanner
      title="认证中心"
      desc="选择认证类型，填写真实信息并通过平台审核后，即可享受对应服务"
    />

    <SplitPane aside="narrow">
      <div class="auth__main">
        <AppCard title="认证类型" subtitle="投资人与借款人的认证资料分开审核">
          <el-select v-model="authType" style="width: 100%" @change="handleTypeChange">
            <el-option label="投资人认证（认证后可参与投标）" value="investor" />
            <el-option label="借款人认证（认证后可申请借款）" value="borrower" />
          </el-select>
        </AppCard>

        <AppCard title="认证流程">
          <el-steps :active="stepActive" finish-status="success" align-center>
            <el-step
              :title="authType === 'investor' ? '填写投资人信息' : '填写借款人信息'"
              description="提交个人信息与身份证资料"
            />
            <el-step title="提交平台审核" description="管理员审核认证资料" />
            <el-step
              title="等待认证结果"
              :description="authType === 'investor' ? '审核通过后可参与投标' : '审核通过后可申请借款'"
            />
          </el-steps>
        </AppCard>

        <!-- 认证通过 -->
        <AppCard v-if="currentStatus === 2" title="认证结果">
          <div class="auth-result auth-result--ok">
            <el-icon class="auth-result__icon"><CircleCheckFilled /></el-icon>
            <div class="auth-result__body">
              <div class="auth-result__title">您的认证审批已通过</div>
              <div class="auth-result__desc">
                {{
                  authType === 'investor'
                    ? '恭喜您已通过投资人认证，现在可以参与投标了。'
                    : '恭喜您已通过借款人认证，现在可以申请借款了。'
                }}
              </div>
            </div>
            <div class="auth-result__actions">
              <el-button v-if="authType === 'borrower'" type="primary" @click="goBorrowApply">
                我要借款
              </el-button>
              <el-button :type="authType === 'investor' ? 'primary' : 'default'" @click="goInvestList">
                浏览标的
              </el-button>
            </div>
          </div>
        </AppCard>

        <!-- 认证中 -->
        <AppCard v-else-if="currentStatus === 1" title="认证结果">
          <div class="auth-result auth-result--pending">
            <el-icon class="auth-result__icon"><Clock /></el-icon>
            <div class="auth-result__body">
              <div class="auth-result__title">认证审核中</div>
              <div class="auth-result__desc">您的认证申请已提交，请耐心等待管理员审核。</div>
            </div>
            <div class="auth-result__actions">
              <el-tag type="warning">审核中</el-tag>
            </div>
          </div>
        </AppCard>

        <!-- 认证失败 -->
        <AppCard v-else-if="currentStatus === -1" title="认证结果">
          <div class="auth-result auth-result--fail">
            <el-icon class="auth-result__icon"><CircleCloseFilled /></el-icon>
            <div class="auth-result__body">
              <div class="auth-result__title">认证未通过</div>
              <div class="auth-result__desc">您的认证审核未通过，请修改信息后重新提交。</div>
            </div>
            <div class="auth-result__actions">
              <el-button
                type="primary"
                @click="authType === 'investor' ? (investStatus = 0) : (borrowStatus = 0)"
              >
                重新认证
              </el-button>
            </div>
          </div>
        </AppCard>

        <!-- 未认证：填写表单 -->
        <AppCard
          v-else
          :title="authType === 'investor' ? '填写投资人信息' : '填写借款人信息'"
          subtitle="带 * 为必填项，资料仅用于平台审核"
        >
          <!-- ===== 投资人表单 ===== -->
          <el-form
            v-if="authType === 'investor'"
            ref="investorFormRef"
            :model="investorForm"
            :rules="investorRules"
            label-width="110px"
            size="default"
          >
            <div class="section-head">
              <span class="section-bar" />
              <el-icon class="section-head__icon"><User /></el-icon>
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

            <div class="section-head">
              <span class="section-bar" />
              <el-icon class="section-head__icon"><CreditCard /></el-icon>
              <span>身份证照片（必填）</span>
            </div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="身份证正面" prop="idCardFrontUrl">
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    accept="image/*"
                    :on-change="(f: any) => handleUpload(investorForm, 'idCardFrontUrl', f.raw)"
                  >
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
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    accept="image/*"
                    :on-change="(f: any) => handleUpload(investorForm, 'idCardBackUrl', f.raw)"
                  >
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

            <el-form-item class="auth__submit">
              <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">
                提交认证
              </el-button>
            </el-form-item>
          </el-form>

          <!-- ===== 借款人表单 ===== -->
          <el-form
            v-else
            ref="borrowerFormRef"
            :model="borrowerForm"
            :rules="borrowerRules"
            label-width="110px"
            size="default"
          >
            <div class="section-head">
              <span class="section-bar" />
              <el-icon class="section-head__icon"><User /></el-icon>
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

            <div class="section-head">
              <span class="section-bar" />
              <el-icon class="section-head__icon"><Phone /></el-icon>
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

            <div class="section-head">
              <span class="section-bar" />
              <el-icon class="section-head__icon"><CreditCard /></el-icon>
              <span>身份证照片（必填）</span>
            </div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="身份证正面" prop="idCardFrontUrl">
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    accept="image/*"
                    :on-change="(f: any) => handleUpload(borrowerForm, 'idCardFrontUrl', f.raw)"
                  >
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
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    accept="image/*"
                    :on-change="(f: any) => handleUpload(borrowerForm, 'idCardBackUrl', f.raw)"
                  >
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

            <el-form-item class="auth__submit">
              <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">
                提交认证
              </el-button>
            </el-form-item>
          </el-form>
        </AppCard>
      </div>

      <template #aside>
        <AppCard title="认证状态" subtitle="两类认证独立审核">
          <div class="kv">
            <span class="kv__label">投资人认证</span>
            <StatusTag :status="investStatus" kind="auth" />
          </div>
          <div class="kv">
            <span class="kv__label">借款人认证</span>
            <StatusTag :status="borrowStatus" kind="auth" />
          </div>
        </AppCard>

        <AppCard title="办理须知" subtitle="认证资料仅用于风控审核" class="auth__tips">
          <ul class="tips">
            <li>身份证照片须清晰、四角完整，避免反光遮挡。</li>
            <li>借款人认证需额外提供联系人与收入信息。</li>
            <li>审核通过后即可使用对应业务，无需重复提交。</li>
          </ul>
        </AppCard>
      </template>
    </SplitPane>
  </div>
</template>

<style scoped>
.auth {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.auth__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
}

/* 分节标题：品牌色条 + 图标 + 文本 */
.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: var(--space-5) 0 var(--space-4);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--border-light);
}
.section-head:first-child {
  margin-top: 0;
}
.section-head__icon {
  color: var(--color-primary);
  font-size: 15px;
}
.auth__submit {
  margin-top: var(--space-5);
}
.auth__submit :deep(.el-button) {
  width: 200px;
}

/* 认证结果面板 */
.auth-result {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  border-radius: var(--radius-lg);
}
.auth-result--ok {
  background: var(--color-success-bg);
  border: 1px solid var(--color-success-border);
}
.auth-result--pending {
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
}
.auth-result--fail {
  background: var(--color-danger-bg);
  border: 1px solid var(--color-danger-border);
}
.auth-result__icon {
  font-size: 30px;
  flex-shrink: 0;
}
.auth-result--ok .auth-result__icon {
  color: var(--color-success);
}
.auth-result--pending .auth-result__icon {
  color: var(--color-warning);
}
.auth-result--fail .auth-result__icon {
  color: var(--color-danger);
}
.auth-result__body {
  flex: 1;
  min-width: 0;
}
.auth-result__title {
  font-size: var(--font-md);
  font-weight: 600;
  color: var(--text-primary);
}
.auth-result__desc {
  font-size: var(--font-sm);
  color: var(--text-secondary);
  margin-top: 4px;
}
.auth-result__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

/* 身份证上传 */
.id-card-upload {
  width: 148px;
  height: 100px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-base);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--text-placeholder);
  cursor: pointer;
  transition: border-color var(--transition-fast), color var(--transition-fast);
}
.id-card-upload:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.id-card-upload span {
  font-size: var(--font-xs);
}
.id-card-preview {
  width: 148px;
  height: 100px;
  border-radius: var(--radius-base);
  overflow: hidden;
  border: 1px solid var(--border-light);
}
.id-card-preview :deep(.el-image) {
  width: 100%;
  height: 100%;
}

/* 侧栏须知 */
.auth__tips {
  margin-top: var(--space-4);
}
.tips {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.tips li {
  font-size: var(--font-xs);
  color: var(--text-secondary);
  line-height: var(--lh-base);
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-light);
}
.kv:last-child {
  border-bottom: none;
}
.kv__label {
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
</style>
