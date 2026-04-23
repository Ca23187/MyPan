<template>
  <div class="others">
    <div class="body-content">
      <div class="icon-wrap">
        <Icon :iconName="iconName" :width="84" />
      </div>

      <!-- Optional: show file name inside (default false to avoid duplication with Window title) -->
      <div v-if="showFileName" class="file-name" :title="fileInfo?.fileName">
        {{ fileInfo?.fileName || "Unnamed file" }}
      </div>

      <div class="meta" v-if="fileInfo?.fileSize">Size: {{ fileSizeText }}</div>

      <div class="tips">
        Preview is not available for this file type. Please download to view.
      </div>

      <div class="download-btn">
        <el-button
          type="primary"
          :loading="downloading"
          :disabled="!canDownload"
          @click="download"
        >
          {{ downloading ? "Preparing..." : "Download" }}
        </el-button>
      </div>

      <div class="error" v-if="errorMsg">
        {{ errorMsg }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, getCurrentInstance } from "vue";

const { proxy } = getCurrentInstance();

const props = defineProps({
  createDownloadUrl: { type: String, default: "" },
  downloadUrl: { type: String, default: "" },
  fileInfo: { type: Object, default: () => ({}) },

  // Prevent duplicate title when wrapped by Window
  showFileName: { type: Boolean, default: false },
});

const downloading = ref(false);
const errorMsg = ref("");

const iconName = computed(() =>
  Number(props.fileInfo?.fileType) === 9 ? "zip" : "others"
);

const fileSizeText = computed(() => {
  const size = props.fileInfo?.fileSize;
  if (!size) return "";
  try {
    return proxy?.Utils?.size2Str
      ? proxy.Utils.size2Str(size)
      : `${size} bytes`;
  } catch {
    return `${size} bytes`;
  }
});

const canDownload = computed(() => {
  return !!props.createDownloadUrl && !!props.downloadUrl && !downloading.value;
});

const download = async () => {
  errorMsg.value = "";

  if (!props.createDownloadUrl || !props.downloadUrl) {
    errorMsg.value = "Download is not available. Missing download URL.";
    return;
  }
  if (downloading.value) return;

  downloading.value = true;
  try {
    const res = await proxy.Request({
      url: props.createDownloadUrl,
      params: { fileIds: props.fileInfo.fileId },
    });

    // Support both { data: token } and raw token return styles
    const token = res?.data ?? res;
    if (!token) throw new Error("Failed to create a download link.");

    const base = String(props.downloadUrl).replace(/\/+$/, "");
    const path = encodeURIComponent(String(token));
    window.location.href = `${base}/${path}`;
  } catch (e) {
    const msg = e?.msg;
    const code = e?.code;
    if (msg === "__CANCELED__") return;
    if (code === 901 || code === 401) return;
    errorMsg.value = msg || e?.message || "Download failed. Please try again.";
  } finally {
    downloading.value = false;
  }
};
</script>

<style lang="scss" scoped>
/* No “card inside card”: keep layout clean and let Window own the container look */
.others {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 28px 16px;
  box-sizing: border-box;
}

.body-content {
  width: min(520px, 100%);
  text-align: center;
}

.icon-wrap {
  display: flex;
  justify-content: center;
  margin-bottom: 10px;
}

.file-name {
  font-weight: 700;
  font-size: 16px;
  color: rgba(0, 0, 0, 0.88);
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.tips {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(0, 0, 0, 0.6);
}

.download-btn {
  margin-top: 16px;
}

.error {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #d93025;
  word-break: break-word;
}
</style>
