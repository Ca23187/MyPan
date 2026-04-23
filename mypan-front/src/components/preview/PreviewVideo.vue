<template>
  <div ref="playerEl" class="player"></div>
</template>

<script setup>
import DPlayer from "dplayer";
import Hls from "hls.js";
import { ref, watch, nextTick, onBeforeUnmount } from "vue";
import { gotoLogin } from "@/utils/Auth";
import Message from "@/utils/Message";

const props = defineProps({
  url: { type: String, default: "" }, // expect like "/xxx/yyy"
  enableScreenshot: { type: Boolean, default: false },
});

const playerEl = ref(null);

let dp = null;
let hls = null;
let jobId = 0;
let destroyed = false;

function destroyPlayer() {
  try {
    if (hls) {
      hls.stopLoad();
      hls.detachMedia();
      hls.destroy();
    }
  } catch {}
  hls = null;

  try {
    dp?.destroy();
  } catch {}
  dp = null;

  if (playerEl.value) playerEl.value.innerHTML = "";
}

function isHlsSource(u) {
  // 1) normal m3u8 url
  if (/\.m3u8(\?|#|$)/i.test(u || "")) return true;

  // 2) your backend playlist API (no extension but returns m3u8 text)
  if (/\/ts\/getVideoInfo\//i.test(u || "")) return true;

  return false;
}

function normalizeApiUrl(u) {
  if (!u) return "";
  // if already absolute, keep it
  if (/^https?:\/\//i.test(u)) return u;
  const path = String(u).startsWith("/") ? String(u) : `/${u}`;
  return `/api${path}`;
}

function isM3U8(u) {
  return /\.m3u8(\?|#|$)/i.test(u || "");
}

async function initPlayer() {
  const myJob = ++jobId;

  // Wait until element exists (important in modal)
  await nextTick();
  await new Promise((r) => requestAnimationFrame(r));

  if (destroyed || myJob !== jobId) return;

  if (!playerEl.value) {
    return;
  }

  const src = normalizeApiUrl(props.url);

  if (!src) {
    return;
  }

  // Destroy old
  destroyPlayer();

  const useHlsJs = isHlsSource(src) && Hls.isSupported();

  try {
    dp = new DPlayer({
      element: playerEl.value,
      theme: "#b7daff",
      lang: "en", // ✅ 顺便先设英文（下面第2点解释）
      screenshot: !!props.enableScreenshot,
      video: useHlsJs
        ? {
            url: src,
            type: "customHls",
            customType: {
              customHls: (videoEl) => {
                if (hls) {
                  try {
                    hls.stopLoad();
                    hls.detachMedia();
                    hls.destroy();
                  } catch {}
                  hls = null;
                }

                hls = new Hls({
                  enableWorker: true,
                  xhrSetup: (xhr) => {
                    xhr.withCredentials = true;

                    xhr.addEventListener("loadend", () => {
                      const s = xhr.status;

                      if (s === 401) {
                        const expired = xhr.getResponseHeader("X-Auth-Expired");
                        if (expired === "1") {
                          gotoLogin("Session expired. Please log in again.");
                        } else {
                          Message.error("Unauthorized.");
                        }
                      } else if (s === 403) {
                        Message.error("Access denied.");
                      }
                    });
                  },
                  fetchSetup: (context, initParams) => {
                    return new Request(context.url, {
                      ...initParams,
                      credentials: "include",
                    });
                  },
                });

                hls.loadSource(src);
                hls.attachMedia(videoEl);

                // 保留 ERROR 仅做日志（不要拿它做登录判断）
                hls.on(Hls.Events.ERROR, (evt, data) => {
                  console.warn("[HLS ERROR]", data);
                });
              },
            },
          }
        : {
            url: src,
            type: "auto",
          },
    });

    // Basic error hooks
    dp.on("error", (e) => {
      console.warn("[PreviewVideo][DPlayer ERROR]", e);
    });

    // Force a play attempt? (optional)
    // dp.play();
  } catch (e) {
    console.error("[PreviewVideo] init failed:", e);
  }
}

watch(
  () => props.url,
  () => {
    initPlayer();
  },
  { immediate: true, flush: "post" }
);

onBeforeUnmount(() => {
  destroyed = true;
  jobId++;
  destroyPlayer();
});
</script>

<style lang="scss" scoped>
.player {
  width: 100%;
  :deep(.dplayer-video-wrap) {
    text-align: center;
    .dplayer-video {
      margin: 0 auto;
      max-height: calc(100vh - 41px);
    }
  }
}
</style>
