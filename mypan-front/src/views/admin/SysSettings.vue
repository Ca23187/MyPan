<template>
  <!-- 设置->系统设置 -->
  <div class="sys-setting-panel">
    <el-form
      :model="formData"
      :rules="rules"
      ref="formDataRef"
      label-width="200px"
      label-position="left"
      @submit.prevent
    >
      <el-form-item label="Registration email subject" prop="registerEmailTitle">
        <el-input
          clearable
          placeholder="Your verification code"
          v-model.trim="formData.registerEmailTitle"
        ></el-input>
      </el-form-item>

      <el-form-item label="Registration email body" prop="registerEmailContent">
        <el-input
          clearable
          placeholder="Enter the email body. Use %s as a placeholder for the verification code."
          v-model.trim="formData.registerEmailContent"
        ></el-input>
      </el-form-item>

      <el-form-item label="Initial storage quota (MB)" prop="userInitTotalSpace">
        <el-input
          clearable
          placeholder="Enter initial quota"
          v-model.trim="formData.userInitTotalSpace"
        ></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveSettings">Save</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";
const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const api = {
  getSysSettings: "/admin/getSysSettings",
  saveSettings: "/admin/saveSysSettings",
};

const formData = ref({});
const formDataRef = ref();
const rules = {
  registerEmailTitle: [
    { required: true, message: "Please enter the registration email subject." },
  ],
  registerEmailContent: [
    { required: true, message: "Please enter the registration email body." },
  ],
  userInitTotalSpace: [
    { required: true, message: "Please enter the initial storage quota." },
    { validator: proxy.Verify.number, message: "Storage quota must be a number." },
  ],
};

const getSysSettings = async () => {
  let result = await proxy.Request({
    url: api.getSysSettings,
    method: 'get'
  });
  if (!result) {
    return;
  }
  formData.value = result.data;
};
getSysSettings();

const saveSettings = () => {
  formDataRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }
    let params = {};
    Object.assign(params, formData.value);
    let result = await proxy.Request({
      url: api.saveSettings,
      params: params,
    });
    if (!result) {
      return;
    }
    proxy.Message.success("Saved successfully.");
  });
};
</script>

<style lang="scss" scoped>
.sys-setting-panel {
  margin-top: 20px;
  width: 800px;
}
</style>
