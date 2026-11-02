<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Iphone, Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/user'
import { grantEntry } from '@/utils/entryGuard'
import AuthIntroPanel from '@/components/AuthIntroPanel.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  mobile: '',
  password: '',
})

const formRules: FormRules = {
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码不少于4位', trigger: 'blur' },
  ],
}

const slogan = ['专业金融科技平台', '让借贷与投资更简单']
const introDesc =
  '旺旺信贷打通借款申请、智能风控、标的撮合与到期回款全流程，资金由存管银行独立保管，平台不触碰资金。'
const steps = [
  { title: '借款人发标，透明披露', desc: '申请经风控审核后上架，金额、利率、期限全量可见' },
  { title: '投资人自主投标', desc: '按标的利率与期限出借，资金直达存管银行账户' },
  { title: '满标放款，按期回款', desc: '系统按还款计划自动结算，本息到期自动到账' },
]

function setTokenCookie(token: string) {
  const expires = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toUTCString()
  document.cookie = `user_token=${encodeURIComponent(token)}; expires=${expires}; path=/`
}

function handleLogin() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const token = await login({ mobile: formData.mobile, password: formData.password })
      setTokenCookie(token)
      // 发放本标签页的入口通行证：这是进入项目的唯一途径
      grantEntry()
      ElMessage.success('登录成功')
      router.push('/')
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
      <AuthIntroPanel :slogan="slogan" :desc="introDesc" :steps="steps" />
    </div>

    <div class="form-side">
      <div class="form-box">
        <div class="login-header">
          <h1>账号登录</h1>
          <p>欢迎回来，请使用手机号登录</p>
        </div>

        <el-form
          ref="formRef"
          :model="formData"
          :rules="formRules"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="mobile">
            <el-input
              v-model="formData.mobile"
              placeholder="请输入手机号"
              :prefix-icon="Iphone"
              clearable
            />
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
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form>

        <div class="login-footer">
          还没有账号？<router-link to="/register" class="link">立即注册</router-link>
        </div>

        <p class="copyright">© 2026 旺旺信贷 · 安全借贷 · 理性投资</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 满屏分栏：左侧介绍铺满左半屏，右侧表单铺满右半屏，无外边距、无卡片留白 */
.auth-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-card);
}
.intro-side {
  flex: 0 0 58%;
  height: 100%;
  min-width: 0;
}
.form-side {
  flex: 1 1 42%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 64px;
  overflow-y: auto;
}
.form-box {
  width: 100%;
  max-width: 396px;
}
.login-header {
  margin-bottom: 36px;
}
.login-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 10px;
  letter-spacing: 1px;
}
.login-header p {
  font-size: var(--font-base);
  color: var(--text-placeholder);
  margin: 0;
}
.login-btn {
  width: 100%;
  height: 46px;
  font-size: 16px;
  letter-spacing: 6px;
  margin-top: 8px;
}
.login-footer {
  margin-top: 22px;
  text-align: center;
  font-size: var(--font-sm);
  color: var(--text-placeholder);
}
.link {
  color: var(--color-primary);
  cursor: pointer;
}
.link:hover {
  text-decoration: underline;
}
.copyright {
  margin: 32px 0 0;
  text-align: center;
  font-size: var(--font-xs);
  color: var(--text-placeholder);
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
    padding: 36px 24px 56px;
  }
}
</style>
