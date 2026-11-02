<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUserInfo, updateProfile } from '@/api/user'
import { getBindStatus, bindDirect } from '@/api/bind'
import { BANK_BIND_STATUS_MAP } from '@/utils/enumText'
import AppCard from '@/components/common/AppCard.vue'
import AppPage from '@/components/common/AppPage.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const router = useRouter()
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
    } catch (e: any) {
      // 【P9-61】绑卡即开立资金存管账户，未签《资金存管协议》会被后端拦下。
      // 这里给一条"能照做"的路径，而不是让用户只看到一个失败提示。
      const msg = e?.msg || e?.message || ''
      if (typeof msg === 'string' && msg.includes('资金存管协议')) {
        ElMessageBox.confirm(
          `${msg}\n\n是否现在前往「我的合同」签署？`,
          '请先签署《资金存管协议》',
          { confirmButtonText: '去签署', cancelButtonText: '稍后', type: 'warning' },
        )
          .then(() => router.push('/contract/my'))
          .catch(() => {})
      } else {
        ElMessage.error(msg || '绑卡失败，请稍后重试')
      }
    } finally {
      bindLoading.value = false
    }
  })
}

const isRealNameAuthed = computed(
  () => userInfo.value?.borrowAuthStatus === 2 || userInfo.value?.investAuthStatus === 2
)

/** 卡号脱敏：只保留前 4 后 4 */
const maskedBankNo = computed(() => {
  const no = bindInfo.value?.bankNo
  if (!no) return '—'
  return String(no).replace(/^(.{4}).*(.{4})$/, '$1 **** **** $2')
})

onMounted(fetchUserInfo)
</script>

<template>
  <AppPage title="个人中心" subtitle="账号资料、实名认证与银行卡存管信息">
    <div class="pf">
      <aside class="pf__side">
        <AppCard class="pf__card">
          <div class="idcard">
            <el-avatar
              :size="76"
              src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
            />
            <div class="idcard__name">{{ userInfo?.nickName || '用户' }}</div>
            <div class="idcard__mobile">{{ userInfo?.mobile || '—' }}</div>
            <div class="idcard__tags">
              <StatusTag :status="userInfo?.userType" kind="userType" />
              <StatusTag :status="userInfo?.bindStatus" :map="BANK_BIND_STATUS_MAP" />
            </div>
          </div>
        </AppCard>

        <AppCard title="认证与绑定" subtitle="关口决定可用的业务范围">
          <div class="kv">
            <span class="kv__label">投资人认证</span>
            <StatusTag :status="userInfo?.investAuthStatus" kind="auth" />
          </div>
          <div class="kv">
            <span class="kv__label">借款人认证</span>
            <StatusTag :status="userInfo?.borrowAuthStatus" kind="auth" />
          </div>
          <div class="kv">
            <span class="kv__label">绑卡状态</span>
            <StatusTag :status="userInfo?.bindStatus" :map="BANK_BIND_STATUS_MAP" />
          </div>
          <el-button
            v-if="userInfo?.borrowAuthStatus !== 2 || userInfo?.investAuthStatus !== 2"
            link
            type="primary"
            class="kv__link"
            @click="router.push('/auth')"
          >
            前往认证中心
          </el-button>
        </AppCard>
      </aside>

      <div class="pf__main">
        <AppCard title="基本信息" subtitle="实名信息与账号标识">
          <el-descriptions v-if="userInfo" :column="2" border>
            <el-descriptions-item label="用户ID">{{ userInfo.id }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userInfo.mobile }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ userInfo.nickName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="真实姓名">{{ userInfo.name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="身份证号">{{ userInfo.idCard || '—' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userInfo.email || '—' }}</el-descriptions-item>
            <el-descriptions-item label="积分">{{ userInfo.integral ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ userInfo.createTime || '—' }}</el-descriptions-item>
          </el-descriptions>
        </AppCard>

        <AppCard title="修改信息" subtitle="昵称与联系方式">
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
        </AppCard>

        <AppCard title="银行卡绑定" subtitle="绑卡即开立资金存管账户，资金由存管银行独立管理">
          <template v-if="userInfo?.bindStatus === 1">
            <div class="bind-ok">
              <el-icon class="bind-ok__icon"><CircleCheckFilled /></el-icon>
              <div>
                <div class="bind-ok__title">已绑定银行卡</div>
                <div class="bind-ok__desc">您可以进行充值、提现、投资、借款等操作。</div>
              </div>
            </div>
            <el-descriptions v-if="bindInfo" :column="1" border size="small" class="bind-info">
              <el-descriptions-item label="银行">{{ bindInfo.bankType || '—' }}</el-descriptions-item>
              <el-descriptions-item label="卡号">{{ maskedBankNo }}</el-descriptions-item>
              <el-descriptions-item label="持卡人">{{ bindInfo.name || '—' }}</el-descriptions-item>
            </el-descriptions>
          </template>

          <template v-else>
            <div v-if="!isRealNameAuthed" class="pf__notice">
              <el-icon class="pf__notice-icon"><WarningFilled /></el-icon>
              <div class="pf__notice-text">
                <strong>请先完成实名认证后再绑卡</strong>
                <span>实名认证通过后方可开立资金存管账户。</span>
              </div>
              <el-button type="primary" plain size="small" @click="router.push('/auth')">
                去认证
              </el-button>
            </div>
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
                  :disabled="!isRealNameAuthed"
                  @click="handleBindDirect"
                >
                  确认绑卡
                </el-button>
              </el-form-item>
            </el-form>
          </template>
        </AppCard>
      </div>
    </div>
  </AppPage>
</template>

<style scoped>
.pf {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: var(--space-4);
  align-items: start;
}
@media (max-width: 1080px) {
  .pf {
    grid-template-columns: minmax(0, 1fr);
  }
}
.pf__side {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.pf__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
}

.idcard {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) 0;
}
.idcard__name {
  font-size: var(--font-md);
  font-weight: 600;
  color: var(--text-primary);
  margin-top: var(--space-2);
}
.idcard__mobile {
  font-size: var(--font-sm);
  color: var(--text-placeholder);
}
.idcard__tags {
  display: flex;
  gap: 6px;
  margin-top: var(--space-2);
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-light);
}
.kv:last-of-type {
  border-bottom: none;
}
.kv__label {
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.kv__link {
  margin-top: var(--space-2);
}

.bind-ok {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-base);
  background: var(--color-success-bg);
  border: 1px solid var(--color-success-border);
}
.bind-ok__icon {
  font-size: 20px;
  color: var(--color-success);
  flex-shrink: 0;
}
.bind-ok__title {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.bind-ok__desc {
  font-size: var(--font-xs);
  color: var(--text-secondary);
  margin-top: 2px;
}
.bind-info {
  margin-top: var(--space-4);
}

.pf__notice {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-base);
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
  margin-bottom: var(--space-4);
}
.pf__notice-icon {
  font-size: 18px;
  color: var(--color-warning);
  flex-shrink: 0;
}
.pf__notice-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.pf__notice-text strong {
  font-size: var(--font-sm);
  color: var(--text-primary);
  font-weight: 600;
}
.pf__notice-text span {
  font-size: var(--font-xs);
  color: var(--text-secondary);
}
</style>
