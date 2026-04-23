<template>
  <div class="pdf">
    <div class="toolbar">
      <div class="left">
        <span class="title">PDF Preview</span>
        <span class="meta" v-if="state.numPages">
          {{ state.pageNum }} / {{ state.numPages }}
        </span>
      </div>

      <div class="right">
        <el-button size="small" :disabled="state.pageNum <= 1" @click="prevPage">
          Prev
        </el-button>
        <el-button
          size="small"
          :disabled="state.numPages ? state.pageNum >= state.numPages : false"
          @click="nextPage"
        >
          Next
        </el-button>
      </div>
    </div>

    <div class="viewer">
      <!-- Always render, don't gate with v-if/v-else -->
      <vue-pdf-embed
        ref="pdfRef"
        class="vue-pdf-embed"
        :source="state.url"
        :page="state.pageNum"
        width="850"
        @loaded="onLoaded"
        @rendered="onRendered"
        @error="onError"
        @loading-failed="onError"
      />

      <!-- Loading overlay (won't block rendering) -->
      <div class="overlay" v-if="loading">
        Loading PDF...
      </div>

      <div class="overlay error" v-if="errorMsg">
        {{ errorMsg }}
      </div>
    </div>
  </div>
</template>

<script setup>
import VuePdfEmbed from "vue-pdf-embed";
import { ref, watch, onBeforeUnmount } from "vue";

const props = defineProps({
  url: { type: String, default: "" },
});

const pdfRef = ref(null);

const loading = ref(false);
const errorMsg = ref("");

const state = ref({
  url: "",
  pageNum: 1,
  numPages: 0,
});

let destroyed = false;
let jobId = 0;
let loadingTimer = null;

function clearTimer() {
  if (loadingTimer) {
    clearTimeout(loadingTimer);
    loadingTimer = null;
  }
}

function initPdf() {
  const myJob = ++jobId;
  errorMsg.value = "";
  loading.value = true;
  state.value.numPages = 0;
  state.value.pageNum = 1;
  state.value.url = props.url || "";

  clearTimer();
  // Fallback: even if events don't fire, stop showing loading after a short time
  loadingTimer = setTimeout(() => {
    if (!destroyed && myJob === jobId && loading.value && !errorMsg.value) {
      loading.value = false;
    }
  }, 1500);
}

function onLoaded(doc) {
  loading.value = false;
  errorMsg.value = "";

  const pages = doc?.numPages;
  if (typeof pages === "number" && pages > 0) {
    state.value.numPages = pages;
    if (state.value.pageNum > pages) state.value.pageNum = pages;
  }
}

function onRendered() {
  // Some versions emit rendered but not loaded
  loading.value = false;
}

function onError() {
  loading.value = false;
  state.value.numPages = 0;
  errorMsg.value = "Failed to load PDF. Please download to view.";
}

function prevPage() {
  state.value.pageNum = Math.max(1, state.value.pageNum - 1);
}

function nextPage() {
  if (state.value.numPages) {
    state.value.pageNum = Math.min(state.value.numPages, state.value.pageNum + 1);
  } else {
    state.value.pageNum += 1;
  }
}

watch(
  () => props.url,
  () => initPdf(),
  { immediate: true }
);

onBeforeUnmount(() => {
  destroyed = true;
  clearTimer();
});
</script>

<style lang="scss" scoped>
.pdf {
  width: 100%;
  box-sizing: border-box;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.title {
  font-weight: 700;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
}

.meta {
  margin-left: 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.viewer {
  position: relative;
  padding: 12px;
}

.overlay {
  position: absolute;
  inset: 12px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 14px;
  pointer-events: none;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
  background: rgba(255, 255, 255, 0.65);
  border-radius: 8px;
}

.overlay.error {
  color: #d93025;
}
</style>
