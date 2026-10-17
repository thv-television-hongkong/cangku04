<template>
  <div class="login-page">
    <div class="login-brand">
      <h1>校园公益众筹存证溯源系统</h1>
      <p class="subtitle">校园公益 · 财务确认 · 摘要存证 · 全流程监督</p>
      <ul class="feature-list">
        <li><i class="el-icon-check" /> 项目发起 → 管理员审核上线 → 师生捐赠</li>
        <li><i class="el-icon-check" /> 模拟支付 → 学校财务确认 → 生成有效捐赠</li>
        <li><i class="el-icon-check" /> 本地 MOCK 存证，可扩展连接 Fabric 测试网络</li>
        <li><i class="el-icon-check" /> 原件存服务器，存证只提交摘要；无实际扣款</li>
      </ul>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="本系统仅面向校园内部公益众筹，不面向社会公开募捐"
      />
    </div>

    <div class="login-panel">
      <div class="mobile-brand">
        <i class="el-icon-s-promotion" />
        <h1>校园公益</h1>
        <p>让每一份善意，都有迹可循</p>
        <span>校内原型 · 模拟支付 · 不发生实际扣款</span>
      </div>
      <el-card shadow="always">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="登录" name="login">
            <el-form ref="loginForm" :model="loginForm" :rules="loginRules" label-position="top"
                     autocomplete="on" @submit.prevent="handleLogin">
              <el-form-item label="账号" prop="account">
                <el-input v-model="loginForm.account" prefix-icon="el-icon-user" autocomplete="username"
                          placeholder="学号 / 社团编号 / 管理员账号" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="loginForm.password" prefix-icon="el-icon-lock" show-password autocomplete="current-password"
                          placeholder="请输入登录密码" @keyup.enter="handleLogin" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" native-type="submit" style="width: 100%" :loading="loading" :disabled="loading"
                  @click="handleLogin">
                  {{ loading ? '正在登录...' : '登录' }}
                </el-button>
              </el-form-item>
              <el-alert v-if="loginError" type="error" :closable="false" show-icon :title="loginError" />
            </el-form>
            <div class="demo-accounts">
              <span>演示账号（密码 123456）：</span>
              <div class="account-choices">
                <button type="button" @click="fill('20210001')">学生 <small>20210001</small></button>
                <button type="button" @click="fill('club001')">公益社团 <small>club001</small></button>
                <button type="button" @click="fill('admin')">学校管理 <small>admin</small></button>
                <button type="button" @click="fill('auditor')">学校监督 <small>auditor</small></button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form ref="registerForm" :model="registerForm" :rules="registerRules" label-position="top">
              <el-form-item label="账号类型">
                <el-radio-group v-model="registerForm.role">
                  <el-radio label="STUDENT">普通学生（捐赠者）</el-radio>
                  <el-radio label="CLUB">公益社团（项目发起方）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="账号" prop="account">
                <el-input v-model="registerForm.account" placeholder="学号 / 社团编号" />
              </el-form-item>
              <el-form-item label="姓名 / 社团名称" prop="realName">
                <el-input v-model="registerForm.realName" />
              </el-form-item>
              <el-form-item label="学号 / 社团编号" prop="identityNo">
                <el-input v-model="registerForm.identityNo" placeholder="用于校内身份校验" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="registerForm.phone" placeholder="用于找回密码，展示时自动脱敏" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="registerForm.email" />
              </el-form-item>
              <el-form-item label="所属学院 / 单位">
                <el-input v-model="registerForm.college" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="registerForm.password" show-password />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="registerForm.confirmPassword" show-password />
              </el-form-item>
              <el-form-item label="验证码">
                <el-input v-model="registerForm.code" placeholder="点击获取演示验证码">
                  <template #append><el-button :disabled="codeCountdown > 0" @click="sendRegisterCode">
                    {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码' }}
                  </el-button></template>
                </el-input>
              </el-form-item>
              <el-alert
                v-if="registerForm.role === 'CLUB'"
                type="info"
                :closable="false"
                show-icon
                title="社团账号需提交资质材料，经平台管理员审核通过后方可发起众筹项目"
              />
              <el-form-item style="margin-top: 12px">
                <el-button type="primary" style="width: 100%" :loading="loading" @click="handleRegister">注册</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="找回密码" name="forgot">
            <el-form ref="forgotForm" :model="forgotForm" :rules="forgotRules" label-position="top">
              <el-form-item label="账号" prop="account">
                <el-input v-model="forgotForm.account" />
              </el-form-item>
              <el-form-item label="验证码" prop="code">
                <el-input v-model="forgotForm.code" placeholder="点击获取演示验证码">
                  <template #append><el-button :disabled="codeCountdown > 0" @click="sendForgotCode">
                    {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码' }}
                  </el-button></template>
                </el-input>
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="forgotForm.newPassword" show-password />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="forgotForm.confirmPassword" show-password />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" style="width: 100%" :loading="loading" @click="handleReset">重置密码</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script>
import { sendCode, resetPassword, register } from '@/api/auth'
import { buildRouter, roleHome } from '@/permission'
import { removeToken, removeUser } from '@/utils/auth'

export default {
  name: 'Login',
  data() {
    const validateConfirm = (rule, value, callback) => {
      if (value !== this.registerForm.password) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }
    const validateForgotConfirm = (rule, value, callback) => {
      if (value !== this.forgotForm.newPassword) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }
    return {
      activeTab: 'login',
      loading: false,
      loginError: '',
      codeCountdown: 0,
      loginForm: { account: '', password: '' },
      registerForm: {
        account: '', password: '', confirmPassword: '', realName: '', role: 'STUDENT',
        identityNo: '', phone: '', email: '', college: '', code: ''
      },
      forgotForm: { account: '', code: '', newPassword: '', confirmPassword: '' },
      loginRules: {
        account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      },
      registerRules: {
        account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
        realName: [{ required: true, message: '请输入姓名 / 社团名称', trigger: 'blur' }],
        identityNo: [{ required: true, message: '请输入学号 / 社团编号', trigger: 'blur' }],
        password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
        confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
      },
      forgotRules: {
        account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
        code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
        newPassword: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
        confirmPassword: [{ required: true, validator: validateForgotConfirm, trigger: 'blur' }]
      }
    }
  },
  methods: {
    fill(account) {
      this.loginForm.account = account
      this.loginForm.password = '123456'
    },
    syncAutofilledLogin() {
      // Chrome may paint saved credentials without dispatching an input event.
      // Read the rendered values before ElementUI validates the Vue model.
      const inputs = this.$refs.loginForm && this.$refs.loginForm.$el
        ? this.$refs.loginForm.$el.querySelectorAll('input') : []
      if (inputs[0] && inputs[0].value) this.loginForm.account = inputs[0].value
      if (inputs[1] && inputs[1].value) this.loginForm.password = inputs[1].value
    },
    async handleLogin() {
      // The explicit click handler covers browsers where ElementUI's native
      // form submit event is not forwarded reliably. The loading guard keeps
      // click + submit from issuing duplicate login requests.
      if (this.loading) return
      this.loginError = ''
      this.syncAutofilledLogin()
      if (!this.loginForm.account || !this.loginForm.password) {
        this.$message.warning('请填写账号和密码')
        return
      }
      this.loading = true
      // Do not let a token from a previous account affect this login attempt.
      removeToken()
      removeUser()
      this.$store.commit('user/SET_TOKEN', '')
      this.$store.commit('user/SET_USER', {})
      let user
      try {
        user = await this.$store.dispatch('user/login', this.loginForm)
      } catch (error) {
        const status = error && error.response && error.response.status
        if (error && error.code === 1003) {
          this.loginError = '账号或密码错误，请检查后重试'
        } else if (error && (error.code === 401 || status === 401)) {
          this.loginError = '登录状态异常，请刷新页面后重试'
        } else if (error && (error.code === 403 || status === 403)) {
          this.loginError = '当前账号无权登录，请联系平台管理员'
        } else if (error && (error.code === 'ECONNABORTED' || error.message === 'timeout')) {
          this.loginError = '服务器响应超时，请确认电脑服务已启动后重试'
        } else {
          this.loginError = '登录失败，请检查局域网连接后重试'
        }
        this.loading = false
        return
      }

      this.$message.success('登录成功，欢迎 ' + (user.realName || user.account))
      try {
        // Keep the token saved by Vuex and navigate in the SPA. A hard
        // reload can race with the login-entry cleanup and discard it.
        buildRouter()
        this.$store.commit('user/SET_ROUTES_LOADED', true)
        await this.$router.replace(roleHome(user.role))
      } catch (error) {
        // Keep the browser console useful when a role route or lazy chunk
        // fails during development; the user-facing text remains concise.
        console.error('登录后后台路由加载失败', error)
        this.loginError = '登录成功，但后台页面加载失败：' + ((error && error.message) || '路由未匹配')
      } finally {
        this.loading = false
      }
    },
    async handleRegister() {
      this.$refs.registerForm.validate(async valid => {
        if (!valid) return
        this.loading = true
        try {
          const res = await register(this.registerForm)
          this.$message.success(res.message || '注册成功，请登录')
          if (this.registerForm.role === 'CLUB') {
            this.$message.info('社团账号已提交资质审核，审核通过后即可发起项目')
          }
          this.activeTab = 'login'
          this.loginForm.account = this.registerForm.account
          this.loginForm.password = this.registerForm.password
        } finally {
          this.loading = false
        }
      })
    },
    async handleReset() {
      this.$refs.forgotForm.validate(async valid => {
        if (!valid) return
        this.loading = true
        try {
          await resetPassword(this.forgotForm)
          this.$message.success('密码重置成功，请使用新密码登录')
          this.activeTab = 'login'
          this.loginForm.account = this.forgotForm.account
        } finally {
          this.loading = false
        }
      })
    },
    async sendRegisterCode() {
      if (!this.registerForm.account) {
        this.$message.warning('请先填写账号')
        return
      }
      const res = await sendCode({ target: this.registerForm.account, bizType: 'REGISTER' })
      this.$message.success('验证码已发送：' + (res.data || ''))
      this.startCountdown()
    },
    async sendForgotCode() {
      if (!this.forgotForm.account) {
        this.$message.warning('请先填写账号')
        return
      }
      const res = await sendCode({ target: this.forgotForm.account, bizType: 'RESET_PASSWORD' })
      this.$message.success('验证码已发送：' + (res.data || ''))
      this.startCountdown()
    },
    startCountdown() {
      this.codeCountdown = 60
      const timer = setInterval(() => {
        this.codeCountdown--
        if (this.codeCountdown <= 0) clearInterval(timer)
      }, 1000)
    }
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: #edf5f3;
}
.login-brand {
  flex: 1 1 auto;
  background: #12343b;
  color: #fff;
  padding: 72px clamp(32px, 7vw, 110px);
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.login-brand h1 {
  max-width: 580px;
  font-size: 34px;
  line-height: 1.25;
  margin: 0 0 14px;
}
.login-brand .subtitle {
  max-width: 560px;
  color: #b5d2ce;
  line-height: 1.7;
  margin: 0 0 30px;
}
.feature-list {
  max-width: 580px;
  list-style: none;
  padding: 0;
  margin: 0 0 30px;
}
.feature-list li {
  margin-bottom: 15px;
  color: #d8e9e6;
  line-height: 1.5;
}
.feature-list i {
  color: #65d7c5;
  margin-right: 8px;
}
.login-panel {
  width: min(440px, 44vw);
  box-sizing: border-box;
  padding: 32px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: #fff;
  overflow-y: auto;
}
.login-panel ::v-deep .el-card {
  width: 100%;
  border: 1px solid #dce8e5;
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(18, 52, 59, 0.08) !important;
}
.demo-accounts {
  font-size: 12px;
  color: #909399;
  line-height: 2;
}
.mobile-brand { display: none; }
.account-choices { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 8px; }
.account-choices button { background: #f3f8f6; border: 1px solid #dce8e5; border-radius: 8px; padding: 8px; text-align: left; color: #20453f; cursor: pointer; font: inherit; line-height: 1.6; }
.account-choices button:hover { background: #e5f3ef; border-color: #91c8bb; }
.account-choices small { display: block; color: #71848a; }

@media (max-width: 860px) {
  .login-brand {
    padding: 48px 32px;
  }

  .login-brand h1 {
    font-size: 28px;
  }

  .login-panel {
    width: min(400px, 48vw);
    padding: 20px;
  }
}

@media (max-width: 640px) {
  .login-brand {
    display: none;
  }

  .login-panel {
    width: 100%;
    min-height: 100vh;
    padding: 30px 18px;
    background: #edf5f3;
    justify-content: flex-start;
  }
  .mobile-brand { display: block; width: 100%; margin: 0 0 24px; color: #12343b; }
  .mobile-brand i { display: inline-block; background: #0f766e; color: white; font-size: 24px; padding: 10px; border-radius: 12px; float: left; margin-right: 12px; }
  .mobile-brand h1 { margin: 0 0 4px; font-size: 24px; }
  .mobile-brand p { margin: 0 0 16px; font-size: 12px; color: #56736d; }
  .mobile-brand span { font-size: 12px; color: #71848a; }
}
</style>
