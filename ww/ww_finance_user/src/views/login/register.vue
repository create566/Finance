<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Iphone, Lock, Message } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { sendCode, register } from '@/api/user'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)

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

let countdownTimer: ReturnType<typeof setInterval> | null = null

function handleSendCode() {
  if (!formData.mobile || !/^1[3-9]\d{9}$/.test(formData.mobile)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  codeSending.value = true
  sendCode({ mobile: formData.mobile })
    .then(() => {
      ElMessage.success('验证码已发送')
      codeCountdown.value = 60
      countdownTimer = setInterval(() => {
        codeCountdown.value--
        if (codeCountdown.value <= 0) {
          clearInterval(countdownTimer!)
          countdownTimer = null
        }
      }, 1000)
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
  <div class="register-container">
    <div class="register-box">
      <div class="register-header">
        <div class="logo-icon">旺</div>
        <h1>旺旺信贷</h1>
        <p>用户注册</p>
      </div>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        size="large"
      >
        <el-form-item prop="mobile">
          <el-input v-model="formData.mobile" placeholder="请输入手机号" :prefix-icon="Iphone" clearable />
        </el-form-item>
        <el-form-item prop="code">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="formData.code" placeholder="请输入验证码" :prefix-icon="Message" style="flex: 1" />
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
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="passwordto">
          <el-input v-model="formData.passwordto" type="password" placeholder="请确认密码" :prefix-icon="Lock" show-password />
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

      <div class="register-footer">
        已有账号？<router-link to="/login" class="link">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #36d1dc 0%, #5b86e5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}
.register-container::before {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  width: 500px;
  height: 500px;
  top: -200px;
  left: -150px;
}
.register-box {
  width: 400px;
  padding: 40px 36px 32px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  position: relative;
  z-index: 1;
}
.register-header {
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
.register-header h1 {
  font-size: 22px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 4px;
}
.register-header p {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
}
.register-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}
.register-footer {
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