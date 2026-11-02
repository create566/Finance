<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Iphone, Lock, Message } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { sendCode, register } from '@/api/user'
import AuthIntroPanel from '@/components/AuthIntroPanel.vue'
import SliderCaptcha from '@/components/SliderCaptcha.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)
const captchaVisible = ref(false)

const formData = reactive({
  mobile: '',
  code: '',
  password: '',
  passwordto: '',
  userType: 1,
})

const formRules: FormRules = {
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码不少于6位', trigger: 'blur' },
  ],
  passwordto: [{ required: true, message: '请确认密码', trigger: 'blur' }],
}

const slogan = ['注册旺旺信贷', '开启借贷与投资之旅']
const introDesc =
  '注册即享资金存管、智能风控、透明借贷一体化服务，投资与借款在同一账号内完成。'
const features = [
  { title: '手机号快速注册', desc: '短信验证码校验，30 秒完成' },
  { title: '投资人 / 借款人双角色', desc: '注册后可按需完成双向认证' },
  { title: '银行存管账户', desc: '绑卡后资金由存管银行独立管理' },
]

let countdownTimer: ReturnType<typeof setInterval> | null = null

function startCountdown() {
  codeCountdown.value = 60
  countdownTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer!)
      countdownTimer = null
    }
  }, 1000)
}

/** 点「获取验证码」先起人机验证，通过后才真正发短信 */
function handleSendCode() {
  if (!formData.mobile || !/^1[3-9]\d{9}$/.test(formData.mobile)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  captchaVisible.value = true
}

function onCaptchaSuccess() {
  codeSending.value = true
  sendCode({ mobile: formData.mobile })
    .then(() => {
      ElMessage.success('验证码已发送')
      startCountdown()
    })
    .catch(() => {})
    .finally(() => {
      codeSending.value = false
    })
}

function handleRegister() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    if (formData.password !== formData.passwordto) {
      ElMessage.error('两次密码不一致')
      return
    }
    loading.value = true
    try {
      await register({
        mobile: formData.mobile,
        code: formData.code,
        password: formData.password,
        passwordto: formData.passwordto,
        userType: formData.userType,
      })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch {
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="auth-page">
    <div class="intro-side">
      <AuthIntroPanel :slogan="slogan" :desc="introDesc" :features="features" />
    </div>

    <div class="form-side">
      <div class="form-box">
        <div class="register-header">
          <h1>用户注册</h1>
          <p>已有账号？<router-link to="/login" class="link">直接登录</router-link></p>
        </div>

        <el-form ref="formRef" :model="formData" :rules="formRules" size="large">
          <el-form-item prop="mobile">
            <el-input
              v-model="formData.mobile"
              placeholder="请输入手机号"
              :prefix-icon="Iphone"
              clearable
            />
          </el-form-item>
          <el-form-item prop="code">
            <div style="display: flex; gap: 8px; width: 100%">
              <el-input
                v-model="formData.code"
                placeholder="请输入验证码"
                :prefix-icon="Message"
                style="flex: 1"
              />
              <el-button
                :loading="codeSending"
                :disabled="codeCountdown > 0"
                @click="handleSendCode"
                style="width: 120px"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="passwordto">
            <el-input
              v-model="formData.passwordto"
              type="password"
              placeholder="请确认密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-radio-group v-model="formData.userType" style="width: 100%">
              <el-radio :value="1" size="large">投资人</el-radio>
              <el-radio :value="2" size="large">借款人</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-button type="primary" :loading="loading" class="register-btn" @click="handleRegister">
            注 册
          </el-button>
        </el-form>

        <p class="copyright">© 2026 旺旺信贷 · 安全借贷 · 理性投资</p>
      </div>
    </div>

    <SliderCaptcha v-model:visible="captchaVisible" @success="onCaptchaSuccess" />
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-card);
}
.intro-side {
  flex: 0 0 46%;
  height: 100%;
  min-width: 0;
}
.form-side {
  flex: 1 1 54%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 56px;
  overflow-y: auto;
}
.form-box {
  width: 100%;
  max-width: 420px;
}
.register-header {
  margin-bottom: 22px;
}
.register-header h1 {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
}
.register-header p {
  font-size: 13px;
  color: var(--text-placeholder);
  margin: 0;
}
.register-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
  margin-top: 4px;
}
.copyright {
  margin: 26px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-placeholder);
}
.link {
  color: var(--color-primary);
  cursor: pointer;
}
.link:hover {
  text-decoration: underline;
}
@media (max-width: 900px) {
  .auth-page {
    flex-direction: column;
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }
  .intro-side {
    flex: none;
    height: auto;
  }
  .form-side {
    flex: none;
    height: auto;
    padding: 32px 24px 48px;
  }
}
</style>
