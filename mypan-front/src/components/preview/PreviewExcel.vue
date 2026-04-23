<template>
  <div class="excel-preview">
    <div class="toolbar">
      <div class="left">
        <span class="title">Spreadsheet Preview</span>
        <span v-if="sheetNames.length" class="meta">
          {{ sheetNames.length }} sheet(s)
        </span>
      </div>

      <div class="right" v-if="sheetNames.length > 1">
        <el-select
          v-model="activeSheet"
          size="small"
          style="width: 220px"
          :disabled="loading"
        >
          <el-option
            v-for="name in sheetNames"
            :key="name"
            :label="name"
            :value="name"
          />
        </el-select>
      </div>
    </div>

    <div class="status" v-if="loading">Loading spreadsheet...</div>
    <div class="status error" v-else-if="errorMsg">{{ errorMsg }}</div>

    <div class="table-wrap" v-else>
      <table class="sheet" v-if="visibleRows.length">
        <thead>
          <tr>
            <th class="corner"></th>
            <th v-for="(col, cIdx) in colHeaders" :key="cIdx">
              {{ col }}
            </th>
          </tr>
        </thead>

        <tbody>
          <tr v-for="(row, rIdx) in visibleRows" :key="rIdx">
            <th class="row-header">{{ rIdx + 1 }}</th>
            <td v-for="(cell, cIdx) in row" :key="cIdx" :title="cell">
              {{ cell }}
            </td>
          </tr>
        </tbody>
      </table>

      <div v-else class="empty">This sheet is empty.</div>

      <div v-if="truncated" class="hint">
        Large file detected. Showing the first {{ maxRows }} rows and
        {{ maxCols }} columns.
      </div>
    </div>
  </div>
</template>

<script setup>
import * as XLSX from "xlsx";
import { ref, computed, watch, onBeforeUnmount, getCurrentInstance } from "vue";

const { proxy } = getCurrentInstance();

const props = defineProps({
  url: { type: String, default: "" },
  fileName: { type: String, default: "" },
});

function isCsv() {
  const n = (props.fileName || "").toLowerCase();
  return n.endsWith(".csv");
}

const loading = ref(false);
const errorMsg = ref("");

const sheetNames = ref([]);
const activeSheet = ref("");
const sheetData = ref([]); // 2D array (strings)

const truncated = ref(false);
const maxRows = 300;
const maxCols = 80;

let cachedWorkbook = null;
let jobId = 0;
let destroyed = false;

const colHeaders = computed(() => {
  const cols = Math.min(maxCols, sheetData.value?.[0]?.length || 0);
  const headers = [];
  for (let i = 0; i < cols; i++) headers.push(toExcelColName(i));
  return headers;
});

const visibleRows = computed(() => {
  const rows = sheetData.value || [];
  const r = Math.min(maxRows, rows.length);
  const out = [];
  for (let i = 0; i < r; i++) {
    const row = rows[i] || [];
    const c = Math.min(maxCols, row.length);
    const normalized = [];
    for (let j = 0; j < c; j++) normalized.push(row[j] ?? "");
    out.push(normalized);
  }
  return out;
});

function toExcelColName(index) {
  let n = index + 1;
  let s = "";
  while (n > 0) {
    const r = (n - 1) % 26;
    s = String.fromCharCode(65 + r) + s;
    n = Math.floor((n - 1) / 26);
  }
  return s;
}

function clearState() {
  cachedWorkbook = null;
  sheetNames.value = [];
  activeSheet.value = "";
  sheetData.value = [];
  truncated.value = false;
  errorMsg.value = "";
}

function renderSheet(sheetName) {
  if (!cachedWorkbook || !sheetName) return;

  const ws = cachedWorkbook.Sheets?.[sheetName];
  if (!ws) {
    sheetData.value = [];
    truncated.value = false;
    return;
  }

  const rows = XLSX.utils.sheet_to_json(ws, {
    header: 1,
    raw: false,
    defval: "",
  });

  const rowCount = rows.length;
  const colCount = rows.reduce(
    (m, r) => Math.max(m, Array.isArray(r) ? r.length : 0),
    0
  );
  truncated.value = rowCount > maxRows || colCount > maxCols;

  sheetData.value = rows.map((r) =>
    (Array.isArray(r) ? r : []).map((v) => (v == null ? "" : String(v)))
  );
}

async function loadExcel() {
  if (!props.url) {
    clearState();
    return;
  }

  const myJob = ++jobId;
  loading.value = true;
  errorMsg.value = "";
  sheetData.value = [];
  truncated.value = false;
  sheetNames.value = [];
  activeSheet.value = "";
  cachedWorkbook = null;

  try {
    const res = await proxy.Request({
      url: props.url,
      method: "get",
      responseType: "arraybuffer",
      showLoading: false,
    });

    if (destroyed || myJob !== jobId) return;

    const buffer = res instanceof ArrayBuffer ? res : res?.data;
    if (!(buffer instanceof ArrayBuffer)) {
      throw new Error("Failed to load spreadsheet (invalid response).");
    }

    let workbook;

    if (isCsv()) {
      // 1) 先按 UTF-8 解码；如果出现大量 � 再用 GBK（最省事的启发式）
      const u8 = new Uint8Array(buffer);

      const decode = (enc) => {
        try {
          return new TextDecoder(enc, { fatal: false }).decode(u8);
        } catch {
          return null;
        }
      };

      let csvText = decode("utf-8") || "";
      const comma = (csvText.match(/,/g) || []).length;
      const semi = (csvText.match(/;/g) || []).length;
      if (semi > comma * 2) {
        csvText = csvText.replace(/;/g, ",");
      }
      const badCharCount = (csvText.match(/\uFFFD/g) || []).length; // �
      if (badCharCount > 20) {
        const gbk = decode("gbk");
        if (gbk) csvText = gbk;
      }

      // 2) 让 xlsx 从 string 读 CSV
      workbook = XLSX.read(csvText, { type: "string" });
    } else {
      // xlsx/xls 正常走 array
      workbook = XLSX.read(new Uint8Array(buffer), { type: "array" });
    }

    const names = workbook.SheetNames || [];
    if (!names.length) throw new Error("This spreadsheet has no sheets.");

    cachedWorkbook = workbook;
    sheetNames.value = names;

    // Set default sheet (this will trigger the watch and render)
    activeSheet.value = names[0];
  } catch (e) {
    if (destroyed || myJob !== jobId) return;

    const msg = e?.msg;
    const code = e?.code;

    if (msg === "__CANCELED__") return; // 取消不提示
    if (code === 901 || code === 401) return; // 登录失效已处理（会跳登录），这里不提示
    if (code === 907 || code === 403) {
      errorMsg.value = msg || "Access denied.";
      return;
    }

    errorMsg.value =
      msg || e?.message || "Preview failed. Please download to view.";
  } finally {
    if (!destroyed && myJob === jobId) loading.value = false;
  }
}

// 关键：sheet 切换时基于 cachedWorkbook 渲染
watch(activeSheet, (name) => {
  if (!name || !cachedWorkbook) return;
  renderSheet(name);
});

// url 变化重新加载
watch(
  () => props.url,
  () => {
    loadExcel();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  destroyed = true;
  jobId++;
  clearState();
});
</script>

<style lang="scss" scoped>
.excel-preview {
  width: 100%;
  height: 100%;
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

.status {
  padding: 16px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}

.status.error {
  color: #d93025;
}

.table-wrap {
  width: 100%;
  height: calc(100% - 48px);
  overflow: auto;
  padding: 12px;
  box-sizing: border-box;
}

.sheet {
  width: max-content;
  min-width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 12px;
  background: #fff;
}

.sheet th,
.sheet td {
  border: 1px solid rgba(0, 0, 0, 0.08);
  padding: 6px 10px;
  height: 28px;
  white-space: nowrap;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sheet thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fafafa;
  font-weight: 700;
}

.sheet .corner {
  left: 0;
  z-index: 3;
  position: sticky;
  background: #fafafa;
  min-width: 48px;
}

.sheet .row-header {
  position: sticky;
  left: 0;
  z-index: 1;
  background: #fafafa;
  font-weight: 700;
  min-width: 48px;
  text-align: right;
  padding-right: 10px;
}

.sheet tbody tr:nth-child(even) td {
  background: rgba(0, 0, 0, 0.015);
}

.sheet tbody tr:hover td {
  background: rgba(64, 158, 255, 0.1);
}

.hint {
  margin-top: 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.empty {
  padding: 16px;
  color: rgba(0, 0, 0, 0.6);
  font-size: 13px;
}
</style>
