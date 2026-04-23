<template>
  <div>
    <Dialog
      :show="dialogConfig.show"
      :title="dialogConfig.title"
      :buttons="dialogConfig.buttons"
      width="500px"
      :showCancel="true"
      @close="dialogConfig.show = false"
    >
      <el-form
        :model="formData"
        :rules="rules"
        ref="formDataRef"
        label-width="160px"
        label-position="left"
        @submit.prevent
      >
        <!-- 原密码 -->
        <el-form-item label="Current Password" prop="oldPassword">
          <el-input
            type="password"
            size="large"
            placeholder="Enter current password"
            v-model.trim="formData.oldPassword"
            show-password
            autocomplete="current-password"
          >
            <template #prefix>
              <span class="iconfont icon-password"></span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 新密码 -->
        <el-form-item label="New Password" prop="newPassword">
          <el-input
            type="password"
            size="large"
            placeholder="Enter new password"
            v-model.trim="formData.newPassword"
            show-password
            autocomplete="new-password"
          >
            <template #prefix>
              <span class="iconfont icon-password"></span>
            </template>
          </el-input>
        </el-form-item>

        <!-- 确认新密码 -->
        <el-form-item label="Confirm Password" prop="rePassword">
          <el-input
            type="password"
            size="large"
            placeholder="Re-enter new password"
            v-model.trim="formData.rePassword"
            show-password
            autocomplete="new-password"
          >
            <template #prefix>
              <span class="iconfont icon-password"></span>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance, nextTick } from "vue";
import { forceRelogin } from "@/utils/Auth";
const { proxy } = getCurrentInstance();

const api = {
  updatePassword: "updatePassword",
};

const formData = ref({
  oldPassword: "",
  newPassword: "",
  rePassword: "",
});
const formDataRef = ref();

// 校验：确认密码一致
const checkRePassword = (rule, value, callback) => {
  if (value !== formData.value.newPassword) {
    callback(new Error(rule.message));
  } else {
    callback();
  }
};

// 校验：新密码不能与原密码相同
const checkNewNotSameAsOld = (rule, value, callback) => {
  if (!value) return callback(); // required 由 required 规则管
  if (value === formData.value.oldPassword) {
    callback(new Error(rule.message));
  } else {
    callback();
  }
};

const rules = {
  oldPassword: [
    { required: true, message: "Please enter your current password" },
  ],
  newPassword: [
    { required: true, message: "Please enter your new password" },
    {
      validator: proxy.Verify.password,
      message:
        "Password must be 8–18 characters and can include letters, numbers, and special characters",
    },
    {
      validator: checkNewNotSameAsOld,
      message: "New password cannot be the same as current password",
    },
  ],
  rePassword: [
    { required: true, message: "Please re-enter your new password" },
    { validator: checkRePassword, message: "The passwords do not match" },
  ],
};

const dialogConfig = ref({
  show: false,
  title: "Change Password",
  buttons: [
    {
      type: "primary",
      text: "Save",
      click: () => submitForm(),
    },
  ],
});

const show = () => {
  dialogConfig.value.show = true;
  nextTick(() => {
    formDataRef.value?.resetFields();
    formData.value = { oldPassword: "", newPassword: "", rePassword: "" };
  });
};
defineExpose({ show });

const submitForm = async () => {
  formDataRef.value.validate(async (valid) => {
    if (!valid) return;

    const result = await proxy.Request({
      url: api.updatePassword,
      params: {
        oldPassword: formData.value.oldPassword,
        newPassword: formData.value.newPassword,
      },
      showLoading: false,
    });

    if (!result) return;

    dialogConfig.value.show = false;
    setTimeout(() => {
      forceRelogin("Password changed. Please log in again.", {
        toastType: "success",
      });
    }, 0);
  });
};
</script>

<style lang="scss"></style>
