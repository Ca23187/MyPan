<template>
  <div>
    <!-- 修改头像弹出框 -->
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
        ref="formDataRef"
        label-width="80px"
        @submit.prevent
      >
        <!--显示昵称-->
        <el-form-item label="Nickname">
          {{ formData.nickname }}
        </el-form-item>

        <!--显示头像-->
        <el-form-item label="Avatar">
          <AvatarUpload v-model="formData.avatar"></AvatarUpload>
        </el-form-item>
      </el-form>
    </Dialog>
  </div>
</template>




<script setup>
// 引入头像上传组件
import AvatarUpload from "@/components/AvatarUpload.vue";
import { ref, reactive, getCurrentInstance } from "vue";
import { useRouter, useRoute } from "vue-router";

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const api = {
  updateUserAvatar: "updateUserAvatar",
};

const formData = ref({});
const formDataRef = ref();

const show = (data) => {
  formData.value = {
    ...data,
    avatar: { userId: data.userId, qqAvatar: data.avatar },
  };
  dialogConfig.value.show = true;
};
// 子组件暴露自己的属性
// 父组件需要调用子组件的方法父组件需要调用子组件的方法，
// 或者访问子组件的变量
defineExpose({ show });

// 定义弹出框的属性
const dialogConfig = ref({
  show: false,
  title: "Change Avatar",
  buttons: [
    {
      type: "primary",
      text: "Save",
      click: (e) => {
        submitForm();
      },
    },
  ],
});

// 1、在子组件中调用defineEmits并定义要发射给父组件的方法
// 2、使用defineEmits会返回一个方法，使用一个变量emit(变量名随意)去接收
// 3、在子组件要触发的方法中，调用emit并传入发射给 父组件的方法（updateAvatar）
const emit = defineEmits(["updateAvatar"]);
const pickFile = (v) => {
  if (!v) return null;
  if (v instanceof File) return v;
  if (v?.raw instanceof File) return v.raw;
  if (v?.file instanceof File) return v.file;
  return null;
};
const submitForm = async () => {
  const file = pickFile(formData.value.avatar);
  if (!file) {
    proxy.Message?.error?.("Please select a new avatar file"); // 或 Message.error
    return;
  }

  const result = await proxy.Request({
    url: api.updateUserAvatar,
    dataType: "file",
    params: { avatar: file },
  });

  if (!result) return;
  dialogConfig.value.show = false;
  emit("updateAvatar");
};
</script>

<style lang="scss">
</style>