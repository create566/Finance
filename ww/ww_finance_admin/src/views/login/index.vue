<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/login'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  username: '',
  password: '',
})

const formRules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码不少于 4 位', trigger: 'blur' },
  ],
}

// 开发调试模式：本地假登录开关
const DEV_MOCK = import.meta.env.VITE_DEV_MOCK_LOGIN === 'true'
const MOCK_USER = import.meta.env.VITE_MOCK_ADMIN_USERNAME || 'admin'
const MOCK_PASS = import.meta.env.VITE_MOCK_ADMIN_PASSWORD || '123456'
// 页面底部提示文字（debug 模式显示默认账号）
const debugTip = DEV_MOCK ? `调试模式 · 默认账号 ${MOCK_USER} / ${MOCK_PASS}` : ''

// 设置 cookie（和前台 ww-front 保持一致）
function setTokenCookie(token: string) {
  const expires = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toUTCString()
  document.cookie = `admin_token=${encodeURIComponent(token)}; expires=${expires}; path=/`
}

// 开发调试模式下，不调后端，直接本地比对
function mockLogin(username: string, password: string): Promise<string> {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (username === MOCK_USER && password === MOCK_PASS) {
        resolve('dev-mock-token-' + Date.now())
      } else {
        reject(new Error('账号或密码错误'))
      }
    }, 400)
  })
}

function handleLogin() {
  if (!formRef.value) return
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      let token: string
      if (DEV_MOCK) {
        // 调试模式：本地假登录
        token = await mockLogin(formData.username, formData.password)
      } else {
        // 真实后端接口
        token = await login({ username: formData.username, password: formData.password })
      }
      setTokenCookie(token)
      ElMessage.success('登录成功')
      router.push('/')
    } catch (e) {
      if (DEV_MOCK) {
        // 调试模式下手动提示
        ElMessage.error((e as Error).message)
      }
      // 真实模式：错误已由 request 拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo-icon">旺</div>
        <h1>旺旺信贷</h1>
        <p>金融管理后台</p>
      </div>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="formData.username"
            placeholder="请输入账号"
            :prefix-icon="User"
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

      <div class="login-tip" :class="{ 'debug-mode': DEV_MOCK }">
        {{ debugTip || '账号由后端管理员提供' }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 50%, #0050b3 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-container::before,
.login-container::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}
.login-container::before {
  width: 500px;
  height: 500px;
  top: -200px;
  left: -150px;
}
.login-container::after {
  width: 400px;
  height: 400px;
  bottom: -180px;
  right: -120px;
}

.login-box {
  width: 400px;
  padding: 40px 36px 32px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo-icon {
  width: 56px;
  height: 56px;
  line-height: 56px;
  margin: 0 auto 12px;
  background: linear-gradient(135deg, #1890ff, #096dd9);
  color: #fff;
  font-size: 26px;
  font-weight: bold;
  border-radius: 12px;
}
.login-header h1 {
  font-size: 22px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 4px;
}
.login-header p {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}

.login-tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}
.login-tip.debug-mode {
  color: #f59e0b;
  font-weight: 500;
}
</style>