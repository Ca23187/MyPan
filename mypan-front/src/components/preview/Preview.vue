<template>
  <PreviewImage
    ref="imageViewerRef"
    :imageList="[previewImageUrl]"
    v-if="fileInfo.fileCategory == 3"
  />

  <Window
    :show="windowShow"
    @close="closeWindow"
    :width="fileInfo.fileCategory == 1 ? 1300 : 900"
    :title="fileInfo.fileName"
    :align="fileInfo.fileCategory == 1 ? 'center' : 'top'"
    v-else
  >
    <!-- fileType: 1 video 2 audio 3 image 4 pdf 5 doc 6 excel 7 txt 8 code 9 zip 10 other -->
    <PreviewVideo :url="url" v-if="fileInfo.fileCategory == 1" />

    <PreviewExcel
      :url="url"
      :fileName="fileInfo.fileName"
      v-if="fileInfo.fileType == 6"
    />

    <PreviewDoc :url="url" v-if="fileInfo.fileType == 5" />
    <PreviewPdf :url="url" v-if="fileInfo.fileType == 4" />

    <PreviewTxt
      :url="url"
      v-if="fileInfo.fileType == 7 || fileInfo.fileType == 8"
    />
    <PreviewMusic
      v-if="fileInfo.fileCategory == 2"
      :url="url"
      :fileName="fileInfo.fileName"
      :fileId="fileInfo.fileId"
      :showPart="curShowPart"
      :shareId="fileInfo.shareId"
      :coverKey="fileInfo.fileCover || ''"
    />
    <PreviewDownload
      :createDownloadUrl="createDownloadUrl"
      :downloadUrl="downloadUrl"
      :fileInfo="fileInfo"
      v-if="fileInfo.fileCategory == 5 && fileInfo.fileType != 8"
    />

    <!-- Minimal fallback (no style changes) -->
    <div
      v-if="shouldShowFallback"
      style="padding: 16px; text-align: center; color: rgba(0, 0, 0, 0.65)"
    >
      Preview is not available for this file type. Please download to view.
    </div>
  </Window>
</template>

<script setup>
import PreviewImage from "@/components/preview/PreviewImage.vue";
import PreviewVideo from "@/components/preview/PreviewVideo.vue";
import PreviewDoc from "@/components/preview/PreviewDoc.vue";
import PreviewExcel from "@/components/preview/PreviewExcel.vue";
import PreviewPdf from "@/components/preview/PreviewPdf.vue";
import PreviewTxt from "@/components/preview/PreviewTxt.vue";
import PreviewMusic from "@/components/preview/PreviewMusic.vue";
import PreviewDownload from "@/components/preview/PreviewDownload.vue";

import Message from "@/utils/Message";
import { gotoLogin } from "@/utils/Auth";
import {
  ref,
  computed,
  nextTick,
  onBeforeUnmount,
  getCurrentInstance,
} from "vue";

const { proxy } = getCurrentInstance();

const curShowPart = ref(0);

// 给 proxy.Request 用：不要带 /api（否则 /api/api）
function toRequestPath(u) {
  if (!u) return "";
  const s = String(u);
  if (/^https?:\/\//i.test(s)) return s; // 极少数情况：你真传了绝对URL给 Request（不推荐）
  if (s.startsWith("/api/")) return s.slice(4); // "/api/file/xx" -> "/file/xx"
  return s.startsWith("/") ? s : `/${s}`;
}

const previewImageUrl = computed(() => {
  const u = imageUrl.value;
  if (!u) return "";

  // ✅ 预览专用：写死 mode=preview
  const withMode = u.includes("?") ? `${u}&mode=preview` : `${u}?mode=preview`;

  // ✅ 可选：防止浏览器强缓存绕过鉴权（推荐）
  const t = Date.now();
  return withMode.includes("?") ? `${withMode}&_t=${t}` : `${withMode}?_t=${t}`;
});

// 给浏览器直接 src 用：同源必须带 /api
function toSrcUrl(u) {
  if (!u) return "";
  const s = String(u);
  if (/^https?:\/\//i.test(s)) return s;
  if (s.startsWith("/api/")) return s;
  return s.startsWith("/") ? `/api${s}` : `/api/${s}`;
}

// probe：不要走 proxy.Request（你们拦截器会把 HEAD 的空 body 当失败）
async function probeResource(rawPathOrUrl) {
  const url = toSrcUrl(rawPathOrUrl);
  if (!url) return { ok: false, url };

  try {
    // HEAD
    const r = await fetch(url, { method: "HEAD", credentials: "include" });

    const expired = r.headers.get("x-auth-expired");
    if (String(expired) === "1") {
      gotoLogin("Session expired. Please log in again.");
      return { ok: false, url };
    }
    if (r.status === 403) {
      Message.error("Access denied.");
      return { ok: false, url };
    }

    // 有些后端不支持 HEAD，会返回 405/404（尤其是映射问题）
    if (r.status === 405 || r.status === 404) {
      // GET Range 0-0 兜底
      const g = await fetch(url, {
        method: "GET",
        headers: { Range: "bytes=0-0" },
        credentials: "include",
      });

      if (g.status === 401) {
        const expired2 = g.headers.get("x-auth-expired");
        if (String(expired2) === "1")
          gotoLogin("Session expired. Please log in again.");
        else Message.error("Unauthorized.");
        return { ok: false, url };
      }
      if (g.status === 403) {
        Message.error("Access denied.");
        return { ok: false, url };
      }
      if (!g.ok) {
        Message.error("Preview failed.");
        return { ok: false, url };
      }
      return { ok: true, url };
    }

    if (!r.ok) {
      Message.error("Preview failed.");
      return { ok: false, url };
    }

    return { ok: true, url };
  } catch {
    Message.error("Preview failed.");
    return { ok: false, url };
  }
}

// ===== state =====
const windowShow = ref(false);
const fileInfo = ref({});

const url = ref(null);
const createDownloadUrl = ref(null);
const downloadUrl = ref(null);

const imageViewerRef = ref(null);
const imageUrl = computed(() => {
  const cover = fileInfo.value?.fileCover;
  if (!cover) return "";
  const original = String(cover).replaceAll("_.", ".");
  const shareId = fileInfo.value?.shareId;
  if (shareId) {
    return proxy.globalInfo.shareImageUrl + shareId + "/" + original;
  }
  return proxy.globalInfo.imageUrl + original;
});

const FILE_URL_MAP = {
  0: {
    fileUrl: "/file/getFile",
    videoUrl: "/file/ts/getVideoInfo",
    createDownloadUrl: "/file/createDownloadUrl",
    downloadUrl: "/api/file/download",
  },
  1: {
    fileUrl: "/admin/getFile",
    videoUrl: "/admin/ts/getVideoInfo",
    createDownloadUrl: "/admin/createDownloadUrl",
    downloadUrl: "/api/admin/download",
  },
  2: {
    fileUrl: "/showShare/getFile",
    videoUrl: "/showShare/ts/getVideoInfo",
    createDownloadUrl: "/showShare/createDownloadUrl",
    downloadUrl: "/api/showShare/download",
  },
};

let seq = 0;
let destroyed = false;

function resetState() {
  url.value = null;
  createDownloadUrl.value = null;
  downloadUrl.value = null;
}

function closeWindow() {
  windowShow.value = false;
  resetState();
}

const shouldShowFallback = computed(() => {
  const c = Number(fileInfo.value?.fileCategory);
  const t = Number(fileInfo.value?.fileType);
  if (c === 3) return false;

  const matches =
    c === 1 ||
    c === 2 ||
    t === 4 ||
    t === 5 ||
    t === 6 ||
    t === 7 ||
    t === 8 ||
    (c === 5 && t !== 8);

  return !matches;
});

function buildUrls(data, showPart) {
  const cfg = FILE_URL_MAP[showPart] || FILE_URL_MAP[0];

  // fileApi: 需要拼 id
  let fileApi = cfg.fileUrl;
  if (Number(data.fileCategory) === 1) fileApi = cfg.videoUrl;

  if (showPart === 0) {
    fileApi = `${fileApi}/${data.fileId}`;
  } else if (showPart === 1) {
    fileApi = `${fileApi}/${data.userId}/${data.fileId}`;
  } else if (showPart === 2) {
    fileApi = `${fileApi}/${data.shareId}/${data.fileId}`;
  } else {
    fileApi = `${cfg.fileUrl}/${data.fileId}`;
  }

  // ✅ 关键：createDownloadUrl 不拼 id（你后端是 ?fileIds=xxx）
  const createApi = cfg.createDownloadUrl;
  const downloadApi = cfg.downloadUrl;

  return { fileApi, createApi, downloadApi };
}

async function showPreview(data, showPart = 0) {
  curShowPart.value = showPart;
  const mySeq = ++seq;
  fileInfo.value = { ...(data || {}), __showPart: showPart };

  const c = Number(data?.fileCategory);
  const t = Number(data?.fileType);

  // ===== image =====
  if (c === 3) {
    windowShow.value = false;
    resetState();

    // 探测最终图片直链
    const ok = await probeResource(previewImageUrl.value);
    if (destroyed || mySeq !== seq) return;
    if (!ok.ok) return;

    await nextTick();
    if (destroyed || mySeq !== seq) return;
    imageViewerRef.value?.show?.(0);
    return;
  }

  // ===== others =====
  windowShow.value = true;
  resetState();

  const { fileApi, createApi, downloadApi } = buildUrls(data, showPart);
  if (destroyed || mySeq !== seq) return;

  // 这些类型常见是 iframe/audio 直连，先探活，避免白屏
  const needProbe =
    t === 4 || // pdf
    t === 5 || // doc
    t === 6; // excel

  if (needProbe) {
    const r = await probeResource(fileApi);
    if (destroyed || mySeq !== seq) return;
    if (!r.ok) {
      windowShow.value = false;
      return;
    }
  }

  // ✅ 给子组件：仍然用接口路径（不带 /api），避免 /api/api
  // 哪些组件是用 <iframe src> / <audio src> 这种“浏览器直连”的：必须带 /api
  const usesDirectSrc =
    c === 2 || // audio
    t === 4; // pdf（通常 iframe）

  if (usesDirectSrc) {
    url.value = toSrcUrl(fileApi); // => /api/file/...
  } else {
    url.value = toRequestPath(fileApi); // => /file/...
  }
  createDownloadUrl.value = toRequestPath(createApi);
  downloadUrl.value = downloadApi;
}

defineExpose({ showPreview });

onBeforeUnmount(() => {
  destroyed = true;
  seq++;
  resetState();
});
</script>

<style lang="scss" scoped>
</style>
