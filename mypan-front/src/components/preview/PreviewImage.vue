<template>
  <div class="image-viewer">
    <el-image-viewer
      v-if="previewImgIndex !== null"
      :initial-index="previewImgIndex"
      :url-list="imageList"
      hide-on-click-modal
      @close="closeImgViewer"
    />
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from "vue";

const props = defineProps({
  imageList: { type: Array, default: () => [] },
});

const previewImgIndex = ref(null);

// Keep previous body styles to avoid interfering with other overlays
let prevOverflow = "";
let prevPaddingRight = "";
let locked = false;

function lockScroll() {
  if (locked) return;
  locked = true;

  const body = document.body;
  prevOverflow = body.style.overflow;
  prevPaddingRight = body.style.paddingRight;

  // Compensate scrollbar width to avoid layout shift
  const scrollBarWidth = window.innerWidth - document.documentElement.clientWidth;

  body.style.overflow = "hidden";
  if (scrollBarWidth > 0) {
    body.style.paddingRight = `${scrollBarWidth}px`;
  }
}

function unlockScroll() {
  if (!locked) return;
  locked = false;

  const body = document.body;
  body.style.overflow = prevOverflow;
  body.style.paddingRight = prevPaddingRight;
}

const show = (index = 0) => {
  lockScroll();
  previewImgIndex.value = index;
};
defineExpose({ show });

const closeImgViewer = () => {
  previewImgIndex.value = null;
  unlockScroll();
};

// Safety: always restore scroll on unmount
onBeforeUnmount(() => {
  unlockScroll();
});
</script>

<style lang="scss" scoped>
.image-viewer {
  :deep(.el-image-viewer__mask) {
    opacity: 0.7;
  }
}
</style>
