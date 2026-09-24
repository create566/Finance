<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Iphone, Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/user'

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
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo-icon">旺</div>
        <h1>旺旺信贷</h1>
        <p>用户端</p>
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
    </div>
  </div>
</template>

<style scoped>
.login-container {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #36d1dc 0%, #5b86e5 100%);
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
  background: linear-gradient(135deg, #36d1dc, #5b86e5);
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
.login-footer {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}
.link {
  color: #5b86e5;
  cursor: pointer;
}
.link:hover {
  text-decoration: underline;
}
</style>