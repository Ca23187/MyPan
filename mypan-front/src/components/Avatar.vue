<template>
  <span class="avatar" :style="{ width: width + 'px', height: width + 'px' }">
    <img :src="finalSrc" :alt="'avatar'" @error="onImgError" />
  </span>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  userId: String,
  avatar: String,
  timestamp: { type: Number, default: 0 },
  width: { type: Number, default: 40 },
});

const DEFAULT_AVATAR = "/default_avatar.png";

const finalSrc = computed(() => {
  // 1) 有外部头像 URL 就直接用（不走 /getAvatar）
  if (props.avatar) return props.avatar;

  // 2) 没有 userId 用默认
  if (!props.userId) return DEFAULT_AVATAR;

  // 3) 本地头像：加版本号绕缓存
  const qs = props.timestamp > 0 ? `?t=${props.timestamp}` : "";
  return `/api/getAvatar/${props.userId}${qs}`;
});

function onImgError(e) {
  const img = e.target;
  img.onerror = null;          // 防止死循环
  img.src = DEFAULT_AVATAR;
}
</script>

<style lang="scss" scoped>
.avatar {
  display: flex;
  border-radius: 50%;
  overflow: hidden;
  /* 宽高由内联样式控制 */
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}
</style>
