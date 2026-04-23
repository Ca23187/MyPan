<template>
  <div>
    <Dialog
      :show="dialogConfig.show"
      :title="dialogConfig.title"
      :buttons="dialogConfig.buttons"
      width="600px"
      :showCancel="showCancel"
      @close="dialogConfig.show = false"
    >
      <el-form
        :model="formData"
        :rules="rules"
        ref="formDataRef"
        label-width="100px"
        @submit.prevent
      >
        <el-form-item label="File"> {{ formData.fileName }} </el-form-item>

        <template v-if="showType == 0">
          <el-form-item label="Expiration" prop="expireType">
            <el-radio-group v-model="formData.expireType">
              <el-radio :label="0">1 day</el-radio>
              <el-radio :label="1">7 days</el-radio>
              <el-radio :label="2">30 days</el-radio>
              <el-radio :label="3">Never expires</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="Access code" prop="codeType">
            <el-radio-group v-model="formData.codeType">
              <el-radio :label="0">Custom</el-radio>
              <el-radio :label="1">Auto-generate</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item prop="code" v-if="formData.codeType == 0">
            <el-input
              clearable
              placeholder="Enter a 5-character access code"
              v-model.trim="formData.code"
              maxLength="5"
              :style="{ width: '130px' }"
            ></el-input>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="Share link">
            {{ fullShareUrl }}
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="copy">Copy link and access code</el-button>
          </el-form-item>
        </template>
      </el-form>
    </Dialog>
  </div>
</template>

<script setup>
// 引入实现复制的文件
import useClipboard from "vue-clipboard3";
const { toClipboard } = useClipboard();

import { ref, getCurrentInstance, nextTick, computed } from "vue";

const { proxy } = getCurrentInstance();

// 文档的当前路径
const shareUrl = ref(document.location.origin + "/share/");

const api = {
  shareFile: "/share/shareFile",
};

const fullShareUrl = computed(() => {
  if (!resultInfo.value.shareId) return "";
  return `${shareUrl.value}${resultInfo.value.shareId}?code=${resultInfo.value.code}`;
});

// 辨别是分享前还是分享后，分享前为0，分享后为1
// 是否展示分享表单   0：分享表单   1：分享结果
const showType = ref(0);

const formData = ref({});
const formDataRef = ref();
const rules = {
  expireType: [{ required: true, message: "Please select an expiration." }],
  codeType: [{ required: true, message: "Please select an access code type." }],
  code: [
    { required: true, message: "Please enter an access code." },
    { validator: proxy.Verify.shareCode, message: "Access code can contain only letters and numbers." },
    { min: 5, message: "Access code must be 5 characters." },
  ],
};

// 取消按钮
const showCancel = ref(true);

// 定义弹出框的属性
const dialogConfig = ref({
  show: false,
  title: "Share",
  buttons: [
    {
      type: "primary",
      text: "Confirm",
      click: (e) => {
        share();
      },
    },
  ],
});

// 结果数据
const resultInfo = ref({});

const share = async () => {
  if (Object.keys(resultInfo.value).length > 0) {
    dialogConfig.value.show = false;
    return;
  }
  formDataRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }
    let params = {};
    Object.assign(params, formData.value);
    let result = await proxy.Request({
      url: api.shareFile,
      params: params,
    });
    if (!result) {
      return;
    }
    showType.value = 1;
    resultInfo.value = result.data;
    dialogConfig.value.buttons[0].text = "Close";
    showCancel.value = false;
  });
};

// 向父组件 Main 暴露该函数，使得父组件能够调用该函数，并向子组件传递参数
const show = (data) => {
  // 触发时，下面的五个值要初始化

  showCancel.value = true;
  dialogConfig.value.show = true;
  dialogConfig.value.buttons[0].text = "Confirm";
  showType.value = 0;
  resultInfo.value = {};

  nextTick(() => {
    // 初始化
    formDataRef.value.resetFields();
    // 获取数据
    formData.value = Object.assign({}, data);
  });
};
defineExpose({ show });

// 复制
const copy = async () => {
  await toClipboard(fullShareUrl.value);
  proxy.Message.success("Copied to clipboard.");
};
</script>

<style lang="scss" scoped>
</style>
