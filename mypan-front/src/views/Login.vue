<template>
  <div class="login-body">
    <div class="bg"></div>
    <div class="login-panel">
      <el-form
        class="login-register"
        :model="formData"
        :rules="rules"
        ref="formDataRef"
      >
        <!--   rules属性传入验证规则
               prop属性设置需要校验的字段名
               model属性是用来指定表单使用的数据
        -->
        <div class="login-title">MyPan</div>
        <!--input输入-->
        <el-form-item prop="email">
          <el-input
            size="large"
            clearable
            placeholder="Enter your email"
            v-model.trim="formData.email"
            maxLength="150"
          >
            <template #prefix>
              <span class="iconfont icon-account"></span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 登录密码 -->
        <el-form-item prop="password" v-if="opType == 1">
          <el-input
            type="password"
            size="large"
            placeholder="Enter your password"
            v-model.trim="formData.password"
            show-password
          >
            <template #prefix>
              <span class="iconfont icon-password"></span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 注册 -->
        <div v-if="opType == 0 || opType == 2">
          <el-form-item prop="emailCode">
            <!-- 邮箱验证码 -->
            <div class="send-emali-panel">
              <el-input
                size="large"
                placeholder="Enter the email verification code"
                v-model.trim="formData.emailCode"
              >
                <template #prefix>
                  <span class="iconfont icon-checkcode"></span>
                </template>
              </el-input>

              <el-button
                class="send-mail-btn"
                type="primary"
                size="large"
                @click="getEmailCode"
                >Get Code</el-button
              >
            </div>
            <el-popover placement="left" :width="500" trigger="click">
              <div>
                <p>
                  1. Check your Spam/Junk folder for the verification email.
                </p>
                <p>
                  2. Add the sender to your email allowlist (Settings →
                  Anti-spam/Filters → Allowlist).
                </p>
                <p>
                  3. Add "xxx.com" to your allowlist if you're not sure how to
                  set it up.
                </p>
              </div>
              <template #reference>
                <span class="a-link" :style="{ 'font-size': '14px' }"
                  >Didn't receive the code?</span
                >
              </template>
            </el-popover>
          </el-form-item>

          <!-- 昵称 -->
          <el-form-item prop="nickname" v-if="opType == 0">
            <el-input
              size="large"
              clearable
              placeholder="Enter a nickname"
              v-model.trim="formData.nickname"
              maxLength="20"
            >
              <template #prefix>
                <span class="iconfont icon-account"></span>
              </template>
            </el-input>
          </el-form-item>

          <!-- 输入密码 -->
          <!-- 注册密码，找回密码 -->
          <el-form-item prop="registerPassword">
            <el-input
              type="password"
              size="large"
              placeholder="请输入密码"
              v-model.trim="formData.registerPassword"
              show-password
            >
              <template #prefix>
                <span class="iconfont icon-password"></span>
              </template>
            </el-input>
          </el-form-item>

          <!-- 再次输入密码 -->
          <el-form-item prop="reRegisterPassword">
            <el-input
              type="password"
              size="large"
              placeholder="Re-enter your password"
              v-model.trim="formData.reRegisterPassword"
              show-password
            >
              <template #prefix>
                <span class="iconfont icon-password"></span>
              </template>
            </el-input>
          </el-form-item>
        </div>

        <!-- 验证码 -->
        <el-form-item prop="checkCode">
          <div class="check-code-panel">
            <el-input
              size="large"
              placeholder="Enter the captcha"
              v-model.trim="formData.checkCode"
              @keyup.enter="doSubmit"
            >
              <template #prefix>
                <span class="iconfont icon-checkcode"></span>
              </template>
            </el-input>
            <img
              v-if="checkCodeUrl"
              :src="checkCodeUrl"
              class="check-code"
              @click="changeCheckCode(0)"
            />
          </div>
        </el-form-item>

        <!-- 登录 -->
        <el-form-item v-if="opType == 1">
          <div class="rememberme-panel">
            <el-checkbox v-model="formData.rememberMe">Remember me</el-checkbox>
          </div>
          <div class="no-account">
            <a href="javascript:void(0)" class="a-link" @click="showPanel(2)"
              >Forgot password?</a
            >
            <a href="javascript:void(0)" class="a-link" @click="showPanel(0)"
              >No account?</a
            >
          </div>
        </el-form-item>

        <!-- 找回密码 去登陆 -->
        <el-form-item v-if="opType == 0">
          <a href="javascript:void(0)" class="a-link" @click="showPanel(1)"
            >Already have an account?</a
          >
        </el-form-item>

        <!-- 注册 已有账号 -->
        <el-form-item v-if="opType == 2">
          <a href="javascript:void(0)" class="a-link" @click="showPanel(1)"
            >Back to login</a
          >
        </el-form-item>

        <!-- 登录按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            class="op-btn"
            @click="doSubmit"
            size="large"
          >
            <span v-if="opType == 0">Sign up</span>
            <span v-if="opType == 1">Log in</span>
            <span v-if="opType == 2">Reset password</span>
          </el-button>
        </el-form-item>
        <div class="login-btn-qq" v-if="opType == 1">
          Quick login<img src="@/assets/qq.png" @click="qqLogin" />
        </div>
      </el-form>
    </div>

    <!--发送邮箱验证码-->
    <Dialog
      :show="dialogConfig4SendMailCode.show"
      :title="dialogConfig4SendMailCode.title"
      :buttons="dialogConfig4SendMailCode.buttons"
      width="500px"
      :showCancel="false"
      @close="dialogConfig4SendMailCode.show = false"
    >
      <el-form
        :model="formData4SendMailCode"
        :rules="rules"
        ref="formData4SendMailCodeRef"
        label-width="80px"
      >
        <!--展示邮箱-->
        <el-form-item label="Email">
          {{ formData.email }}
        </el-form-item>

        <!--验证码输入-->
        <el-form-item label="Captcha" prop="checkCode">
          <div class="check-code-panel">
            <el-input
              size="large"
              placeholder="Enter the captcha"
              v-model.trim="formData4SendMailCode.checkCode"
            >
              <template #prefix>
                <span class="iconfont icon-checkcode"></span>
              </template>
            </el-input>
            <img
              v-if="checkCodeUrl4SendMailCode"
              :src="checkCodeUrl4SendMailCode"
              class="check-code"
              @click="changeCheckCode(1)"
            />
          </div>
        </el-form-item>
      </el-form>
    </Dialog>
  </div>
</template>

<script setup>
// nextTick指定的回调在DOM更新之后再执行
// ref 应用在html标签上获取真实的DOM元素。  应用在组件标签上获取组件实例对象
// getCurrentInstance 获取当前组件的实例、上下文来操作router和vuex
import {
  ref,
  reactive,
  getCurrentInstance,
  nextTick,
  onMounted,
} from "vue";
import { useUserStore } from "@/store/user";
import { useRouter, useRoute } from "vue-router";
import md5 from "js-md5";

const userStore = useUserStore()

const { proxy } = getCurrentInstance();

const router = useRouter();
const route = useRoute();

const api = {
  checkCode: "/checkCode",
  sendMailCode: "/sendEmailCode",
  register: "/register",
  login: "/login",
  resetPwd: "/resetPwd",
  qqlogin: "/qqlogin",
  getUserInfo: "/getUserInfo",
  qqloginCallback: "/qqlogin/callback",
};

// 操作类型  0:注册   1:登录   2:重置密码
const opType = ref(1);
const showPanel = (type) => {
  opType.value = type;
  resetForm();
};

onMounted(() => {
  showPanel(1);
});

// 校验再次输入的密码
const checkRePassword = (rule, value, callback) => {
  if (value !== formData.value.registerPassword) {
    callback(new Error(rule.message));
  } else {
    callback();
  }
};

// 登陆界面
const formData = ref({});
const formDataRef = ref();
// 校验规则（all）
const rules = {
  email: [
    { required: true, message: "Please enter your email" },
    {
      validator: proxy.Verify.email,
      message: "Please enter a valid email address",
    },
  ],
  password: [{ required: true, message: "Please enter your password" }],
  emailCode: [
    { required: true, message: "Please enter the email verification code" },
  ],
  nickname: [{ required: true, message: "Please enter a nickname" }],
  registerPassword: [
    { required: true, message: "Please enter your password" },
    {
      validator: proxy.Verify.password,
      message:
        "Password must be 8-18 characters and can include letters, numbers, and special characters",
    },
  ],
  reRegisterPassword: [
    { required: true, message: "Please re-enter your password" },
    {
      validator: checkRePassword,
      message: "The passwords do not match",
    },
  ],
  checkCode: [{ required: true, message: "Please enter the captcha" }],
};

// 连接后台，显示验证码
const checkCodeUrl = ref("");
const checkCodeUrl4SendMailCode = ref("");
const checkCodeKeyLoginOrRegister = ref("");
const checkCodeKeySendEmail = ref("");

// 修改 changeCheckCode 方法
const changeCheckCode = async (type) => {
  let result = await proxy.Request({
    url: api.checkCode,
    method: "get",
    params: {
      type,
      time: new Date().getTime(),
    },
    dataType: "json",
  });
  if (!result || !result.data) return;

  const { checkCode, checkCodeKey } = result.data;

  if (type == 0) {
    checkCodeUrl.value = checkCode;
    checkCodeKeyLoginOrRegister.value = checkCodeKey;
  } else {
    checkCodeUrl4SendMailCode.value = checkCode;
    checkCodeKeySendEmail.value = checkCodeKey;
  }
};

// 注册界面 发送邮箱验证码 定义属性
const formData4SendMailCode = ref({});
const formData4SendMailCodeRef = ref();
const dialogConfig4SendMailCode = reactive({
  show: false,
  title: "Send Email Verification Code",
  buttons: [
    {
      type: "primary",
      text: "Send Code",
      click: () => {
        sendEmailCode();
      },
    },
  ],
});

// 获取邮箱验证码
const getEmailCode = async () => {
  try {
    await formDataRef.value.validateField("email"); // 通过则不抛错
    dialogConfig4SendMailCode.show = true;

    await nextTick();
    changeCheckCode(1);
    formData4SendMailCodeRef.value.resetFields();
    formData4SendMailCode.value = { email: formData.value.email };
  } catch (e) {
    // 校验失败会到这里，不用做事
  }
};

// 发送邮箱验证码
const sendEmailCode = () => {
  formData4SendMailCodeRef.value.validate(async (valid) => {
    if (!valid) return;
    const params = { ...formData4SendMailCode.value };
    params.type = opType.value == 0 ? 0 : 1;
    params.checkCodeKey = checkCodeKeySendEmail.value;

    let result = await proxy.Request({
      url: api.sendMailCode,
      params,
      dataType: "json",
      errorCallback: () => changeCheckCode(1),
    });
    if (!result) return;
    proxy.Message.success("Verification code sent. Please check your inbox.");
    dialogConfig4SendMailCode.show = false;
  });
};

// 重置表单
const resetForm = () => {
  nextTick(() => {
    changeCheckCode(0);
    formDataRef.value.resetFields();
    formData.value = {};

    // 登录
    if (opType.value == 1) {
      const cookieLoginInfo = proxy.VueCookies.get("loginInfo");
      if (cookieLoginInfo) {
        formData.value = {
          ...formData.value,
          email: cookieLoginInfo.email || "",
          rememberMe: !!cookieLoginInfo.rememberMe,
        };
      }
    }
  });
};

// 登录、注册、重置密码、提交表单
const doSubmit = () => {
  formDataRef.value.validate(async (valid) => {
    if (!valid) return;
    let params = { ...formData.value };

    if (opType.value == 0 || opType.value == 2) {
      params.password = params.registerPassword;
      delete params.registerPassword;
      delete params.reRegisterPassword;
    }

    // 注册、登录、重置密码 URL
    let url = null;
    params.checkCodeKey = checkCodeKeyLoginOrRegister.value;
    let dataType = "form";
    if (opType.value == 0) {
      url = api.register;
      dataType = "json";
    } else if (opType.value == 1) {
      url = api.login;
    } else if (opType.value == 2) {
      url = api.resetPwd;
      dataType = "json";
    }

    let result = await proxy.Request({
      url,
      params,
      dataType,
      errorCallback: () => changeCheckCode(0),
    });
    if (!result) return;

    // 注册返回
    if (opType.value == 0) {
      proxy.Message.success("Sign-up successful. Please log in.");
      showPanel(1);
    } else if (opType.value == 1) {
      // 检查是否点击 “记住我”
      if (params.rememberMe) {
        proxy.VueCookies.set(
          "loginInfo",
          { email: params.email, rememberMe: true },
          "7d"
        );
      } else {
        proxy.VueCookies.remove("loginInfo");
      }

      proxy.Message.success("Logged in successfully.");
      await new Promise((r) => setTimeout(r, 0));
      await router.replace(route.query.redirectUrl || "/");
    } else if (opType.value == 2) {
      // 重置密码
      proxy.Message.success("Password reset successfully. Please log in.");
      showPanel(1);
    }
  });
};

// qq登录
const qqLogin = async () => {
  let result = await proxy.Request({
    url: api.qqlogin,
    method: "get",
    params: {
      callbackUrl: route.query.redirectUrl || "",
    },
  });
  if (!result) return;

  // 清空前端用户信息（真正清 cookie 应该由后端提供 /logout 或在 QQ 授权成功后覆盖 token）
  userStore.clearUserInfo();

  document.location.href = result.data;
};

// 登录回调处理函数
const handleQQLoginCallback = async () => {
  const code = route.query.code;
  const state = route.query.state;

  if (!code || !state) return;

  // 调用后端回调接口，获取 token + 用户信息
  let result = await proxy.Request({
    url: api.qqloginCallback,
    method: "get",
    params: { code, state },
  });
  if (!result || !result.data) return;

  const { token, callbackUrl } = result.data;

  // 跳转回 callbackUrl
  document.location.href = callbackUrl || "/";
};
</script>

<style lang="scss" scoped>
.login-body {
  height: calc(100vh);
  // 把背景图像扩展至足够大，以使背景图像完全覆盖背景区域。
  background-size: cover;
  background: url("../assets/login_bg.jpg");
  display: flex;
  .bg {
    flex: 1;
    background-size: cover;
    background-position: center;
    background-size: 800px;
    background-repeat: no-repeat;
    background-image: url("../assets/login_img.png");
  }
  .login-panel {
    width: 430px;
    margin-right: 15%;
    margin-top: calc((100vh - 500px) / 2);
    .login-register {
      padding: 25px;
      background: #fff;
      border-radius: 5px;
      .login-title {
        text-align: center;
        font-size: 18px;
        font-weight: bold;
        margin-bottom: 20px;
      }
      .send-emali-panel {
        display: flex;
        width: 100%;
        justify-content: space-between;
        .send-mail-btn {
          margin-left: 5px;
        }
      }
      .rememberme-panel {
        width: 100%;
      }
      .no-account {
        width: 100%;
        display: flex;
        justify-content: space-between;
      }
      .op-btn {
        width: 100%;
      }
    }
  }

  .check-code-panel {
    width: 100%;
    display: flex;
    .check-code {
      margin-left: 5px;
      cursor: pointer;
    }
  }
  .login-btn-qq {
    margin-top: 20px;
    text-align: center;
    display: flex;
    align-items: center;
    justify-content: center;
    img {
      cursor: pointer;
      margin-left: 10px;
      width: 20px;
    }
  }
}
</style>
