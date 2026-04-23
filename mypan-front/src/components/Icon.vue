<template>
  <!-- 图标 -->
  <span :style="{ width: width + 'px', height: width + 'px' }" class="icon">
    <img :src="getImage()" :style="{ 'object-fit': fit }" />
  </span>
</template>

<script setup>
import { ref, reactive, getCurrentInstance } from "vue";
const { proxy } = getCurrentInstance();
const props = defineProps({
  fileType: {
    type: Number,
  },
  iconName: {
    type: String,
  },
  cover: {
    type: String,
  },
  width: {
    type: Number,
    default: 32,
  },
  fit: {
    type: String,
    default: "cover",
  },
    // ✅ 新增：分享场景用
  shareId: { type: String, default: "" },
});

const fileTypeMap = {
  0: { desc: "Folder", icon: "folder" },
  1: { desc: "Video", icon: "video" },
  2: { desc: "Audio", icon: "music" },
  3: { desc: "Image", icon: "image" },
  4: { desc: "exe", icon: "pdf" },
  5: { desc: "doc", icon: "word" },
  6: { desc: "excel", icon: "excel" },
  7: { desc: "txt", icon: "txt" },
  8: { desc: "code", icon: "code" },
  9: { desc: "zip", icon: "zip" },
  10: { desc: "Others", icon: "others" },
};

const getImage = () => {
  if (props.cover) {
    // cover = "yyyymm/userId/fileName"
    const parts = props.cover.split('/');
    if (parts.length >= 3) {
      const [month, userId, ...rest] = parts;
      const imageName = rest.join('/'); // 防御：未来可能有子路径

      // 分享页
      if (props.shareId) {
        return (
          proxy.globalInfo.shareImageUrl +
          props.shareId +
          '/' +
          month +
          '/' +
          userId +
          '/' +
          imageName
        );
      }

      // 主站
      return (
        proxy.globalInfo.imageUrl +
        month +
        '/' +
        userId +
        '/' +
        imageName
      );
    }

    // 兜底（理论上不该发生）
    return proxy.globalInfo.imageUrl + props.cover;
  }

  // 图标逻辑不变
  let icon = "unknow_icon";
  if (props.iconName) {
    icon = props.iconName;
  } else {
    const iconMap = fileTypeMap[props.fileType];
    if (iconMap != undefined) {
      icon = iconMap["icon"];
    }
  }
  return new URL(`/src/assets/icon-image/${icon}.png`, import.meta.url).href;
};

</script>

<style lang="scss" scoped>
.icon {
  text-align: center;
  display: inline-block;
  border-radius: 3px;
  overflow: hidden;
  img {
    width: 100%;
    height: 100%;
  }
}
</style>
