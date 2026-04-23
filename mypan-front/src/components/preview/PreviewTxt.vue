<template>
  <div class="code">
    <div class="top-op">
      <div class="encode-select">
        <el-select
          placeholder="Select encoding"
          v-model="encode"
          @change="changeEncode"
          size="small"
        >
          <el-option value="utf-8" label="UTF-8"></el-option>
          <el-option value="gbk" label="GBK"></el-option>
        </el-select>

        <div class="tips">Seeing garbled text? Try another encoding.</div>
      </div>

      <div class="copy-btn">
        <el-button
          type="primary"
          size="small"
          @click="copy"
          :disabled="!txtContent"
        >
          Copy
        </el-button>
      </div>
    </div>

    <div class="hint" v-if="truncated">
      Large file detected. Showing a preview (content truncated).
    </div>

    <div class="status" v-if="loading">Loading...</div>
    <div class="status error" v-else-if="errorMsg">{{ errorMsg }}</div>

    <highlightjs v-else autodetect :code="txtContent" />
  </div>
</template>

<script setup>
import useClipboard from "vue-clipboard3";
import { ref, watch, onBeforeUnmount, getCurrentInstance } from "vue";

const { toClipboard } = useClipboard();
const { proxy } = getCurrentInstance();

const props = defineProps({
  url: { type: String, default: "" },
});

// UI state
const loading = ref(false);
const errorMsg = ref("");

// content
const txtContent = ref("");
const encode = ref("utf-8");
const truncated = ref(false);

// internal
let blobValue = null;
let jobId = 0;
let destroyed = false;
let reader = null;

// Limits (tune as you like)
const MAX_BYTES = 1 * 1024 * 1024; // 1MB
const MAX_LINES = 20000;

function cleanupReader() {
  try {
    reader?.abort?.();
  } catch {}
  reader = null;
}

function getBlobFromResponse(res) {
  // Compatible with axios-like wrapper and direct Blob return
  const b = res instanceof Blob ? res : res?.data;
  return b instanceof Blob ? b : null;
}

async function fetchBlob() {
  if (!props.url) return null;

  const res = await proxy.Request({
    url: props.url,
    method: "get",
    responseType: "blob",
    showLoading: false,
  });

  return getBlobFromResponse(res);
}

function truncateText(text) {
  // Prefer line-based truncation for code readability
  const lines = String(text).split(/\r?\n/);
  if (lines.length > MAX_LINES) {
    truncated.value = true;
    return lines.slice(0, MAX_LINES).join("\n");
  }
  return text;
}

function readTextFromBlob(blob, myJob) {
  return new Promise((resolve, reject) => {
    cleanupReader();
    reader = new FileReader();

    reader.onload = () => {
      if (destroyed || myJob !== jobId) return resolve("");

      let text = reader.result ?? "";
      text = String(text);

      // If blob is huge, we still might freeze highlight; better to truncate
      text = truncateText(text);

      resolve(text);
    };

    reader.onerror = () => {
      reject(new Error("Failed to read file content."));
    };

    // If blob is huge, slice before reading to keep UI responsive
    let toRead = blob;
    truncated.value = false;

    if (blob.size > MAX_BYTES) {
      truncated.value = true;
      toRead = blob.slice(0, MAX_BYTES);
    }

    try {
      reader.readAsText(toRead, encode.value);
    } catch (e) {
      reject(new Error("Unsupported encoding or failed to decode content."));
    }
  });
}

async function loadFile() {
  const myJob = ++jobId;
  loading.value = true;
  errorMsg.value = "";
  txtContent.value = "";
  truncated.value = false;

  try {
    const blob = await fetchBlob();
    if (destroyed || myJob !== jobId) return;

    if (!blob) throw new Error("Failed to load file.");

    blobValue = blob;

    const text = await readTextFromBlob(blob, myJob);
    if (destroyed || myJob !== jobId) return;

    txtContent.value = text;
  } catch (e) {
    if (destroyed || myJob !== jobId) return;

    // 你 request.js reject 的结构：{ msg, code, showError }
    const msg = e?.msg;
    const code = e?.code;

    // 1) 被取消/切换文件触发：不提示
    if (msg === "__CANCELED__") return;

    // 2) 登录失效：拦截器会 gotoLogin，这里别再提示
    if (code === 901 || code === 401) return;

    // 3) 403：给出更明确提示
    if (code === 907 || code === 403) {
      errorMsg.value = msg || "Access denied.";
      return;
    }

    // 4) 其他
    errorMsg.value =
      msg || e?.message || "Preview failed. Please download to view.";
  } finally {
    if (!destroyed && myJob === jobId) loading.value = false;
  }
}

// Encoding change: re-decode existing blob without refetching
async function changeEncode() {
  const myJob = ++jobId;
  loading.value = true;
  errorMsg.value = "";
  txtContent.value = "";

  try {
    if (!blobValue) {
      // No cache yet; fetch then decode
      const blob = await fetchBlob();
      if (destroyed || myJob !== jobId) return;
      if (!blob) throw new Error("Failed to load file.");
      blobValue = blob;
    }

    const text = await readTextFromBlob(blobValue, myJob);
    if (destroyed || myJob !== jobId) return;

    txtContent.value = text;
  } catch (e) {
    if (destroyed || myJob !== jobId) return;
    errorMsg.value =
      e?.message || "Failed to decode content with this encoding.";
  } finally {
    if (!destroyed && myJob === jobId) loading.value = false;
  }
}

async function copy() {
  try {
    await toClipboard(txtContent.value || "");
    proxy.Message.success("Copied");
  } catch {
    proxy.Message.error("Copy failed");
  }
}

watch(
  () => props.url,
  () => {
    blobValue = null;
    loadFile();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  destroyed = true;
  jobId++;
  cleanupReader();
  blobValue = null;
});
</script>

<style lang="scss" scoped>
.code {
  width: 100%;
}

.top-op {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.encode-select {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;

  .tips {
    color: #828282;
    font-size: 12px;
  }
}

.copy-btn {
  flex: 0 0 auto;
}

.hint {
  padding: 8px 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.status {
  padding: 12px 10px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}
.status.error {
  color: #d93025;
}

pre {
  margin: 0;
}
</style>
