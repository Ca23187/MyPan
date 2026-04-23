<template>
  <div class="music">
    <div class="body-content">
      <div class="header">
        <div class="cover">
          <img :src="coverUrl" alt="Cover" />
        </div>

        <div class="meta">
          <div class="title" :title="meta.title">{{ meta.title }}</div>

          <div class="sub">
            <span v-if="meta.artist">{{ meta.artist }}</span>
            <span v-if="meta.album"> • {{ meta.album }}</span>
            <span v-if="meta.year"> • {{ meta.year }}</span>
          </div>

          <div class="pill-row" v-if="summaryPills.length">
            <span
              class="pill"
              :class="{
                lossless: p === 'Lossless',
                hires: p === 'Hi-Res',
              }"
              v-for="(p, idx) in summaryPills"
              :key="idx"
            >
              {{ p }}
            </span>
          </div>

          <!-- ✅ 扩展信息：没那么重要的放下面 -->
          <div class="grid">
            <div class="item" v-if="meta.genre">
              <div class="k">Genre</div>
              <div class="v">{{ meta.genre }}</div>
            </div>

            <div class="item" v-if="meta.track">
              <div class="k">Track</div>
              <div class="v">{{ meta.track }}</div>
            </div>

            <div class="item" v-if="bitrateText">
              <div class="k">Bitrate</div>
              <div class="v">{{ bitrateText }}</div>
            </div>

            <div class="item" v-if="meta.sampleRateHz">
              <div class="k">Sample Rate</div>
              <div class="v">{{ sampleRateText }}</div>
            </div>

            <div class="item" v-if="meta.bitDepth">
              <div class="k">Bit Depth</div>
              <div class="v">{{ bitDepthText }}</div>
            </div>

            <div class="item" v-if="durationText">
              <div class="k">Duration</div>
              <div class="v">{{ durationText }}</div>
            </div>
          </div>

          <div class="status" v-if="loading">Reading metadata...</div>
          <div class="status error" v-else-if="errorMsg">{{ errorMsg }}</div>
        </div>
      </div>

      <div ref="playerRef" class="music-player"></div>
    </div>
  </div>
</template>

<script setup>
import APlayer from "APlayer";
import "APlayer/dist/APlayer.min.css";
import {
  ref,
  reactive,
  computed,
  watch,
  onBeforeUnmount,
  getCurrentInstance,
} from "vue";

const { proxy } = getCurrentInstance();

const props = defineProps({
  url: { type: String, default: "" }, // 播放用直链 /api/...
  fileName: { type: String, default: "" },

  // 元数据用
  fileId: { type: String, default: "" },
  showPart: { type: Number, default: 0 }, // 0 主站 / 2 分享
  shareId: { type: String, default: "" }, // showPart=2 必填
  coverKey: { type: String, default: "" }, // 缩略图 key
});

const playerRef = ref(null);
let ap = null;
let destroyed = false;

const loading = ref(false);
const errorMsg = ref("");

const meta = reactive({
  title: "",
  artist: "",
  album: "",
  year: "",
  genre: "",
  track: "",
  bitrateKbps: null,
  sampleRateHz: null,
  bitDepth: null,
  durationSec: null,
  coverKey: "", // 可选：后端如果给，就能显示封面；不给就走默认封面
  isLossless: false,
});

// 默认封面
const defaultCover = new URL("@/assets/music_cover.png", import.meta.url).href;

const coverUrl = computed(() => {
  if (!props.coverKey) return defaultCover;

  const previewKey = toPreviewKeyFromThumbKey(props.coverKey);

  if (props.showPart === 2) {
    if (!props.shareId) return defaultCover;
    // 预览封面：建议 mode=preview + 加 _t 防缓存绕过鉴权
    return (
      proxy.globalInfo.shareImageUrl +
      `${props.shareId}/${previewKey}?mode=preview&_t=${Date.now()}`
    );
  }

  return (
    proxy.globalInfo.imageUrl + `${previewKey}?mode=preview&_t=${Date.now()}`
  );
});

const durationText = computed(() => {
  const d = Number(meta.durationSec || 0);
  if (!Number.isFinite(d) || d <= 0) return "";
  const mm = String(Math.floor(d / 60)).padStart(2, "0");
  const ss = String(d % 60).padStart(2, "0");
  return `${mm}:${ss}`;
});

const bitrateText = computed(() => {
  const br = meta.bitrateKbps;
  if (br === null || br === undefined) return "";

  const n = Math.abs(Number(br));
  if (!Number.isFinite(n) || n <= 0) return "";
  return `${Math.round(n)} kbps`;
});

const sampleRateText = computed(() => {
  const sr = Number(meta.sampleRateHz || 0);
  if (!Number.isFinite(sr) || sr <= 0) return "";
  // 44100 -> 44.1 kHz, 48000 -> 48 kHz
  const khz = sr / 1000;
  const s = Number.isInteger(khz) ? `${khz}` : `${khz.toFixed(1)}`;
  return `${s} kHz`;
});

const bitDepthText = computed(() => {
  const bd = Number(meta.bitDepth || 0);
  if (!Number.isFinite(bd) || bd <= 0) return "";
  return `${bd}-bit`;
});

const summaryPills = computed(() => {
  const pills = [];
  if (meta.isLossless) pills.push("Lossless");
  if (isHiRes.value) pills.push("Hi-Res");
  return pills;
});

function resetMeta() {
  meta.title = props.fileName || "Audio";
  meta.artist = "";
  meta.album = "";
  meta.year = "";
  meta.genre = "";
  meta.track = "";
  meta.bitrateKbps = null;
  meta.sampleRateHz = null;
  meta.bitDepth = null;
  meta.durationSec = null;
  meta.isLossless = false;
  errorMsg.value = "";
}

function destroyPlayer() {
  try {
    ap?.destroy();
  } catch {}
  ap = null;
  if (playerRef.value) playerRef.value.innerHTML = "";
}

async function fetchMeta() {
  if (!props.fileId) return;

  let api = "";
  if (props.showPart === 2) {
    if (!props.shareId) return;
    api = `/showShare/audioMeta/${props.shareId}/${props.fileId}`;
  } else {
    api = `/file/audioMeta/${props.fileId}`;
  }

  loading.value = true;
  try {
    const r = await proxy.Request({
      url: api,
      method: "get",
      showLoading: false,
    });
    if (!r) return;

    const m = r.data || {};

    meta.title = m.title || props.fileName || "Audio";
    meta.artist = m.artist || "";
    meta.album = m.album || "";
    meta.year = m.year || "";
    meta.genre = m.genre || "";
    meta.track = m.track || "";

    meta.bitrateKbps = m.bitrateKbps ?? null;
    meta.sampleRateHz = m.sampleRateHz ?? null;
    meta.bitDepth = m.bitDepth ?? null;
    meta.durationSec = m.durationSec ?? null;
    meta.isLossless = m.isLossless ?? false;
  } catch (e) {
    // 元数据失败不致命：照样能播
    errorMsg.value = "Failed to read metadata.";
  } finally {
    if (!destroyed) loading.value = false;
  }
}

function initPlayer() {
  destroyPlayer();
  if (!playerRef.value || !props.url) return;

  ap = new APlayer({
    container: playerRef.value,
    autoplay: false,
    preload: "metadata",
    audio: [
      {
        url: props.url,
        name: meta.title || props.fileName || "Audio",
        artist: meta.artist || "",
        cover: coverUrl.value,
      },
    ],
  });

  // 兜底取 audioEl
  const audioEl =
    ap?.audio || ap?.audios?.[0] || playerRef.value?.querySelector("audio");
  if (!audioEl) {
    errorMsg.value = "Audio player init failed (no <audio> element).";
    return;
  }

  let seekTimer = null;
  let lastWantedTime = null;
  let suppress = false;

  audioEl.addEventListener("seeking", () => {
    if (suppress) return;

    // 用户想去的时间
    lastWantedTime = audioEl.currentTime;

    // 立刻暂停，避免浏览器一边 seek 一边疯狂拉
    audioEl.pause();

    clearTimeout(seekTimer);
    seekTimer = setTimeout(() => {
      if (destroyed) return;

      suppress = true; // 防止我们自己设置 currentTime 又触发 seeking -> 递归
      try {
        audioEl.currentTime = lastWantedTime;
        // 恢复播放（如果你希望用户拖完自动播）
        audioEl.play().catch(() => {});
      } finally {
        // 给浏览器一点时间稳定
        setTimeout(() => (suppress = false), 0);
      }
    }, 200); // 150~300ms 都行
  });

  audioEl.addEventListener(
    "loadedmetadata",
    () => {
      if (destroyed) return;
      // ✅ 如果后端没给 durationSec，就从浏览器补一个（更稳）
      if (!meta.durationSec) {
        const d = Number(audioEl.duration || 0);
        if (Number.isFinite(d) && d > 0) meta.durationSec = Math.floor(d);
      }
    },
    { once: true }
  );

  audioEl.addEventListener("error", () => {
    if (destroyed) return;
    errorMsg.value = "Failed to load audio.";
  });

  try {
    audioEl.load();
  } catch {}
}

watch(
  () => [
    props.url,
    props.fileId,
    props.showPart,
    props.shareId,
    props.fileName,
    props.coverKey,
  ],
  async () => {
    resetMeta();
    await fetchMeta();
    if (destroyed) return;
    initPlayer();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  destroyed = true;
  destroyPlayer();
});

function toPreviewKeyFromThumbKey(thumbKey) {
  if (!thumbKey) return "";
  const s = String(thumbKey);

  // 只替换最后一次 '_.', 避免路径里有 '_.'
  const idx = s.lastIndexOf("_.");
  if (idx < 0) return s;
  return s.slice(0, idx) + "." + s.slice(idx + 2); // "_.":2 chars
}
const isHiRes = computed(() => {
  const bd = Number(meta.bitDepth || 0);
  const sr = Number(meta.sampleRateHz || 0);
  return bd >= 24 || sr >= 96000;
});
</script>

<style lang="scss" scoped>
.music {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.body-content {
  width: min(920px, 92%);
  padding: 18px 10px;
  box-sizing: border-box;
}

.header {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.cover {
  width: 180px;
  height: 180px;
  border-radius: 14px;
  overflow: hidden;
  flex: 0 0 auto;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.meta {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 18px;
  font-weight: 800;
  color: rgba(0, 0, 0, 0.88);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.6);
  line-height: 1.6;
}

.pill-row {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.05);
  color: rgba(0, 0, 0, 0.7);
}

.grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
}

.item .k {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.5);
}

.item .v {
  margin-top: 2px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.78);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  margin-top: 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}
.status.error {
  color: #d93025;
}

.music-player {
  margin-top: 16px;
}

.pill.lossless {
  background: rgba(255, 193, 7, 0.18); // 黄
  color: rgba(160, 110, 0, 0.95);
  font-weight: 700;
}

.pill.hires {
  background: rgba(212, 175, 55, 0.2); // 金
  color: rgba(120, 85, 0, 0.95);
  font-weight: 800;
}
</style>
