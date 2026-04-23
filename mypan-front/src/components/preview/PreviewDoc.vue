<template>
  <div class="doc-content">
    <div v-if="loading" class="skeleton"></div>
    <div v-show="!loading" ref="docRef"></div>
    <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
  </div>
</template>

<script setup>
import * as docx from "docx-preview";
import {
  ref,
  getCurrentInstance,
  onMounted,
  onBeforeUnmount,
  watch,
  nextTick,
} from "vue";
const { proxy } = getCurrentInstance();

const props = defineProps({ url: String });
const docRef = ref(null);

const loading = ref(false);
const errorMsg = ref("");

let jobId = 0; // 版本号，防止并发覆盖
let destroyed = false;

async function renderDoc() {
  if (!props.url || !docRef.value) return;

  const myJob = ++jobId;
  loading.value = true;
  errorMsg.value = "";
  docRef.value.innerHTML = "";

  try {
    const res = await proxy.Request({
      url: props.url,
      method: "get",
      responseType: "blob",
      showLoading: false,
    });
    if (destroyed || myJob !== jobId) return;
    const blob = res instanceof Blob ? res : res?.data;

    if (!(blob instanceof Blob)) {
      throw new Error("Response is not a Blob");
    }

    // MIME 不一定总准确，但至少能挡住明显的 HTML/JSON 错误页
    if (
      blob.type &&
      (blob.type.includes("text/html") ||
        blob.type.includes("application/json"))
    ) {
      throw new Error("Failed to load document.");
    }

    await nextTick();
    let tries = 0;
    while (docRef.value && docRef.value.clientWidth === 0 && tries < 10) {
      await new Promise((r) => requestAnimationFrame(r));
      tries++;
    }
    await new Promise((r) => requestAnimationFrame(r));
    await new Promise((r) => requestAnimationFrame(r));

    if (destroyed || myJob !== jobId) return;

    const buffer = await blob.arrayBuffer();
    await docx.renderAsync(buffer, docRef.value);

    if (destroyed || myJob !== jobId) return;

    const pages = docRef.value.querySelectorAll("section.docx");
    pages.forEach((p, i) => p.setAttribute("data-page", `Page ${i + 1}`));
  } catch (e) {
    if (destroyed || myJob !== jobId) return;

    const msg = e?.msg;
    const code = e?.code;

    if (msg === "__CANCELED__") return;
    if (code === 901 || code === 401) return; // 已跳登录
    if (code === 907 || code === 403) {
      errorMsg.value = msg || "Access denied.";
      return;
    }

    errorMsg.value = msg || e?.message || "Preview failed";
  } finally {
    if (!destroyed && myJob === jobId) loading.value = false;
  }
}

onMounted(renderDoc);
watch(() => props.url, renderDoc);

onBeforeUnmount(() => {
  destroyed = true;
  jobId++;
  if (docRef.value) docRef.value.innerHTML = "";
});
</script>

<style lang="scss" scoped>
.doc-content {
  margin: 0 auto;
  background: #f5f6f8; // 让白纸更明显
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Arial,
    sans-serif;
  min-height: 60vh;
  scrollbar-gutter: stable both-edges;
  padding: 16px 0;

  :deep(.docx-wrapper) {
    background: transparent; // wrapper 不要抢背景
    padding: 0;
  }

  :deep(.docx-wrapper > section.docx) {
    position: relative;
  }

  :deep(.docx-wrapper > section.docx::after) {
    content: attr(data-page);
    position: absolute;
    bottom: 10px;
    right: 16px;
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }
}
</style>
