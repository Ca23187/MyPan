<template>
  <!-- 头像上传 -->
  <div class="avatar-upload">
    <div class="avatar-show">
      <template v-if="localFile">
        <img :src="localFile" />
      </template>
      <template v-else>
        <img
          :src="modelValue.qqAvatar"
          v-if="modelValue && modelValue.qqAvatar"
        />

        <img :src="avatarSrc" v-else-if="modelValue && modelValue.userId" />

        <img v-else src="/default_avatar.png" />
      </template>
    </div>
    <div class="select-btn">
      <el-upload
        name="file"
        :show-file-list="false"
        accept=".png,.PNG,.jpg,.JPG,.jpeg,.JPEG,.gif,.GIF,.bmp,.BMP,.webp"
        :multiple="false"
        :http-request="uploadImage"
      >
        <el-button type="primary">Select</el-button>
      </el-upload>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, computed, watch } from "vue";
import { useRouter, useRoute } from "vue-router";

// defineProps是一个函数 定义后props可直接在模板中使用，或者在setup其他地方使用
const props = defineProps({
  modelValue: {
    type: Object,
    default: null,
  },
});

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

// 读取“该用户的头像版本号”（跟你 Framework 那套一致）
const avatarTs = ref(0);

watch(
  () => props.modelValue?.userId,
  (uid) => {
    if (!uid) return;
    const saved = Number(localStorage.getItem(`avatar_ts_${uid}`)) || 0;
    avatarTs.value = saved;
  },
  { immediate: true }
);

const avatarSrc = computed(() => {
  const uid = props.modelValue?.userId;
  if (!uid) return "";
  const ts = avatarTs.value > 0 ? avatarTs.value : 0;
  return ts > 0 ? `/api/getAvatar/${uid}?t=${ts}` : `/api/getAvatar/${uid}`;
});


// 本地图片
const localFile = ref(null);
// 子组件向父组件传值
const emit = defineEmits();
// 上传图片
const uploadImage = async (file) => {
  file = file.file;
  let img = new FileReader();
  img.readAsDataURL(file);
  img.onload = ({ target }) => {
    localFile.value = target.result;
  };
  emit("update:modelValue", file);
};
</script>

<style lang="scss">
.avatar-upload {
  display: flex;
  justify-content: center;
  align-items: end;
  .avatar-show {
    background: rgb(245, 245, 245);
    width: 150px;
    height: 150px;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    position: relative;
    .iconfont {
      font-size: 50px;
      color: #ddd;
    }
    img {
      width: 100%;
      height: 100%;
    }
    .op {
      position: absolute;
      color: #0e8aef;
      top: 80px;
    }
  }
  .select-btn {
    margin-left: 10px;
    vertical-align: bottom;
  }
}
</style>