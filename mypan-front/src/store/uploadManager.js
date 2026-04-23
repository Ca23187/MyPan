// src/store/uploadManager.js
import { ref, reactive } from "vue";
const listeners = new Set();
const notify = (evt) => listeners.forEach((fn) => fn(evt));

const onChange = (fn) => {
    listeners.add(fn);
    return () => listeners.delete(fn);
};

export const STATUS = {
    emptyfile: { value: "emptyfile", desc: "Empty file", color: "#F75000", icon: "close" },
    fail: { value: "fail", desc: "Upload failed", color: "#F75000", icon: "close" },
    checking: { value: "checking", desc: "Checking storage", color: "#e6a23c", icon: "clock" },
    init: { value: "init", desc: "Calculating checksum", color: "#e6a23c", icon: "clock" },
    uploading: { value: "uploading", desc: "Uploading", color: "#409eff", icon: "upload" },
    upload_completed: { value: "upload_completed", desc: "Upload completed", color: "#67c23a", icon: "ok" },
    instant_upload: { value: "instant_upload", desc: "Instant upload", color: "#67c23a", icon: "ok" },
};

const api = {
    uploadFile: "/file/uploadFile",
    InitUpload: "/file/initUpload",
    getUsedSpace: "/getUsedSpace",
    resumeUpload: "/file/resumeUpload",
    abortUpload: "/file/abortUpload",
};

// 分片大小：8MB
const chunkSize = 1024 * 1024 * 8;

// 全局任务列表（关键：永不随组件销毁）
const fileList = ref([]);

// 删除的文件 uid
const delList = ref([]);

// 本地“预占用”字节数
const reservedBytes = ref(0);

// 每个 uid 一个 worker
const md5WorkerMap = new Map();

// 防止同一个 uid 多次 uploadFile 并发
const uploadingSet = new Set();

// key: `${uid}:${chunkIndex}`
const reqAbortMap = new Map();

// 依赖注入（由 Framework 初始化一次）
let _request = null;
let _utils = null;
let _message = null;

const ensureInited = () => {
    if (!_request) throw new Error("uploadManager not initialized: missing request");
};

const genTaskUid = () => `${Date.now()}_${Math.random().toString(16).slice(2)}`;

const abortMd5 = (uid) => {
    const w = md5WorkerMap.get(uid);
    if (w) {
        try { w.terminate(); } catch { }
        md5WorkerMap.delete(uid);
    }
};

const abortUploading = (uid) => {
    const prefix = `${uid}:`;
    for (const [k, ctrl] of reqAbortMap.entries()) {
        if (k.startsWith(prefix)) {
            try { ctrl.abort(); } catch { }
            reqAbortMap.delete(k);
        }
    }
};

const getFileByUid = (uid) => fileList.value.find((it) => it.uid === uid);

const recomputeProgress = (f) => {
    const map = f.uploadedChunkBytes || {};
    let sum = 0;
    for (const k in map) sum += map[k] || 0;
    f.uploadSize = Math.min(sum, f.totalSize);
    f.uploadProgress = f.totalSize > 0 ? Math.floor((f.uploadSize / f.totalSize) * 100) : 0;
};

const isFinalStatus = (st) =>
    st === STATUS.instant_upload.value ||
    st === STATUS.upload_completed.value ||
    st === STATUS.fail.value;

const formatBytes = (n) => {
    if (n == null) return "";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let i = 0, x = Number(n);
    while (x >= 1024 && i < units.length - 1) { x /= 1024; i++; }
    return `${x.toFixed(i === 0 ? 0 : 2)} ${units[i]}`;
};

const getUserSpace = async () => {
    ensureInited();
    const res = await _request({
        url: api.getUsedSpace,
        method: "get",
        showLoading: false,
        showError: false,
        returnError: true,
    });
    if (!res || res.__error) return null;
    return res.data; // { usedSpace, totalSpace }
};

const releaseReserve = (f) => {
    if (!f) return;
    if (f._reserved && f._reserved > 0) {
        reservedBytes.value = Math.max(0, reservedBytes.value - f._reserved);
        f._reserved = 0;
    }
};

const failWithMsg = (fileItem, msg) => {
    fileItem.status = STATUS.fail.value;
    fileItem.errorMsg = msg || "上传失败";
    _message?.warning?.(fileItem.errorMsg);
    releaseReserve(fileItem);
};

const waitIfPaused = (f) => {
    if (!f.pause) return Promise.resolve();

    return new Promise((resolve) => {
        f._pauseResolver = resolve;
    });
};

/* ===================== 核心：addFile（无缝迁移） ===================== */

const addFile = async ({ file, filePid, request, utils, message } = {}) => {
    // ✅ 允许 Framework 每次注入；也允许你只 init() 一次
    if (request) _request = request;
    if (utils) _utils = utils;
    if (message) _message = message;

    ensureInited();

    const fileItem = reactive({
        file,
        uid: genTaskUid(),
        md5Progress: 0,
        md5: null,
        fileName: file?.name || "",
        status: STATUS.checking.value,
        uploadSize: 0,
        totalSize: file?.size || 0,
        uploadProgress: 0,
        pause: false,
        chunkIndex: 0,
        filePid,
        errorMsg: null,
        canceled: false,
        _reserved: 0,
        uploadedChunkBytes: {},
        uploadId: null,
        fileId: null,
        _lastLoadedMap: {},
        _pauseResolver: null,
    });
    fileList.value.unshift(fileItem);


    if (!file || fileItem.totalSize === 0) {
        fileItem.status = !file ? STATUS.fail.value : STATUS.emptyfile.value;
        fileItem.errorMsg = !file ? "No file selected" : null;
        return;
    }

    // ✅ quota 预检（在算 MD5 前）
    const space = await getUserSpace();
    if (space) {
        const remainingEffective = Math.max(
            0,
            Number(space.totalSpace) - Number(space.usedSpace) - reservedBytes.value
        );

        if (file.size > remainingEffective) {
            failWithMsg(
                fileItem,
                `Insufficient storage space (remaining ${formatBytes(
                    remainingEffective
                )}, file size ${formatBytes(file.size)})`
            );
            return;
        }

        reservedBytes.value += file.size;
        fileItem._reserved = file.size;
    } else {
        _message?.info?.(
            "Unable to retrieve storage information. Upload will continue and may fail due to insufficient space."
        );
    }

    // ✅ 开始算 MD5
    fileItem.status = STATUS.init.value;
    const md5FileUid = await computeMD5(fileItem);
    if (md5FileUid == null) {
        releaseReserve(fileItem);
        return;
    }

    // ✅ initUpload：沿用你原来的 Request 约定 params + dataType:"json"
    const initRes = await _request({
        url: api.InitUpload,
        method: "post",
        dataType: "json",
        showLoading: false,
        showError: false,
        returnError: true,
        params: {
            fileId: fileItem.fileId || "",
            fileName: fileItem.fileName,
            filePid: fileItem.filePid,
            fileMd5: fileItem.md5,
            fileSize: fileItem.totalSize,
            chunks: Math.ceil(fileItem.totalSize / chunkSize),
            chunkSize,
        },
    });

    if (!initRes || initRes.__error) {
        failWithMsg(
            fileItem,
            initRes?.msg || "Failed to initialize upload session"
        );
        notify({ type: "FAIL", uid: fileItem.uid, status: STATUS.fail.value });
        return;
    }

    fileItem.fileId = initRes.data.fileId;
    fileItem.uploadId = initRes.data.uploadId || null;

    // ✅ 秒传
    if (initRes.data.status === STATUS.instant_upload.value) {
        fileItem.status = STATUS.instant_upload.value;
        fileItem.uploadSize = fileItem.totalSize;
        fileItem.uploadProgress = 100;
        fileItem.pause = false;
        fileItem.canceled = false;

        abortUploading(fileItem.uid);
        abortMd5(fileItem.uid);

        releaseReserve(fileItem);
        notify({ type: "DONE", uid: fileItem.uid, status: fileItem.status });
        return;
    }

    // ✅ 普通上传：继续你原来的逻辑
    const uploaded = new Set(initRes.data.uploaded || []);
    const chunks = Math.ceil(fileItem.totalSize / chunkSize);

    for (const i of uploaded) {
        const size = Math.min(chunkSize, fileItem.totalSize - i * chunkSize);
        fileItem.uploadedChunkBytes[i] = size;
    }
    recomputeProgress(fileItem);

    const missing = [];
    for (let i = 0; i < chunks; i++) if (!uploaded.has(i)) missing.push(i);

    fileItem.status = STATUS.uploading.value;
    uploadFileWithQueue(md5FileUid, missing);
};

/* ===================== 暂停 / 恢复 / 删除 / 终止 ===================== */

const pauseUpload = (uid) => {
    const f = getFileByUid(uid);
    if (!f) return;
    f.pause = true;
    abortMd5(uid);
    abortUploading(uid);
};

const resumeUpload = async (uid) => {
    ensureInited();
    const f = getFileByUid(uid);
    if (!f) return;
    if (isFinalStatus(f.status)) return;

    f.pause = false;
    if (f._pauseResolver) {
        f._pauseResolver();
        f._pauseResolver = null;
    }
    if (!f.fileId) {
        failWithMsg(f, "File ID is missing. Please reselect the file for upload.");
        return;
    }

    const st = await _request({
        url: api.resumeUpload,
        method: "get",
        params: { fileId: f.fileId },
        showLoading: false,
    });

    const uploaded = new Set(st?.data?.uploaded || []);
    const chunks = Math.ceil(f.totalSize / chunkSize);

    for (const i of uploaded) {
        const size = Math.min(chunkSize, f.totalSize - i * chunkSize);
        f.uploadedChunkBytes[i] = size;
    }
    recomputeProgress(f);

    const missing = [];
    for (let i = 0; i < chunks; i++) if (!uploaded.has(i)) missing.push(i);

    uploadFileWithQueue(uid, missing);
};

const removeTask = (uid) => {
    const idx = fileList.value.findIndex((x) => x.uid === uid);
    if (idx === -1) return;

    const f = fileList.value[idx];
    if (f) {
        f.pause = true;
        f.canceled = true;
        releaseReserve(f);
    }
    abortMd5(uid);
    delList.value.push(uid);
    abortUploading(uid);

    fileList.value.splice(idx, 1);
};

const terminateUpload = async (uid) => {
    ensureInited();
    const f = getFileByUid(uid);
    if (!f) return;

    f.pause = true;
    f.canceled = true;
    abortMd5(uid);
    abortUploading(uid);
    releaseReserve(f);

    try {
        if (f.fileId) {
            await _request({
                url: api.abortUpload,
                method: "post",
                showLoading: false,
                showError: false,
                params: { fileId: f.fileId },
            });
        }
    } catch { }

    // remove from list
    removeTask(uid);
};

const abortAll = async () => {
    ensureInited();

    for (const [, ctrl] of reqAbortMap.entries()) {
        try { ctrl.abort(); } catch { }
    }
    reqAbortMap.clear();

    for (const [uid] of md5WorkerMap.entries()) abortMd5(uid);

    const ids = fileList.value.map((f) => f.fileId).filter(Boolean);

    fileList.value.forEach((f) => {
        f.pause = true;
        f.canceled = true;
        releaseReserve(f);
    });
    delList.value = [];

    for (const fileId of ids) {
        try {
            await _request({
                url: api.abortUpload,
                method: "post",
                dataType: "json",
                showLoading: false,
                showError: false,
                params: { fileId },
            });
        } catch { }
    }

    fileList.value = [];
};

/* ===================== 分片上传队列（无缝迁移） ===================== */

const DEFAULT_CONCURRENCY = 4;

const uploadFileWithQueue = async (uid, indices, concurrency = DEFAULT_CONCURRENCY) => {
    ensureInited();

    if (!Array.isArray(indices) || indices.length === 0) {
        const f = getFileByUid(uid);
        if (!f) return;
        if (isFinalStatus(f.status)) return;
        f.status = STATUS.upload_completed.value;
        f.uploadProgress = 100;
        releaseReserve(f);
        return;
    }

    if (uploadingSet.has(uid)) return;
    uploadingSet.add(uid);

    try {
        const f = getFileByUid(uid);
        if (!f) return;

        const file = f.file;
        const fileSize = f.totalSize;
        const chunks = Math.ceil(fileSize / chunkSize);

        let ptr = 0;
        let finished = false;

        const runOne = async (i) => {
            if (!f || f.canceled || delList.value.includes(uid)) return null;

            // ✅ 确保一旦真正开始发分片，就进入 uploading（否则 UI 进度条 v-if 不显示）
            if (f.status !== STATUS.uploading.value && !isFinalStatus(f.status)) {
                f.status = STATUS.uploading.value;
            }
            const ctrl = new AbortController();
            reqAbortMap.set(`${uid}:${i}`, ctrl);

            const start = i * chunkSize;
            const end = Math.min(start + chunkSize, fileSize);
            const chunkFile = file.slice(start, end);

            const params = {
                file: chunkFile,
                fileName: file.name,
                fileMd5: f.md5,
                chunkIndex: i,
                chunks,
                filePid: f.filePid,
            };
            if (f.fileId != null && f.fileId !== "") params.fileId = f.fileId;

            const res = await _request({
                url: api.uploadFile,
                showLoading: false,
                dataType: "file",
                params,
                showError: false,
                showLoading: false,
                errorCallback: (errorMsg) => {
                    if (errorMsg === "__CANCELED__") return;
                    f.status = STATUS.fail.value;
                    f.errorMsg = errorMsg || "Upload failed";
                    releaseReserve(f);
                },
                uploadProgressCallback: (event) => {
                    const loaded = Math.min(event.loaded, chunkFile.size);
                    const prev = f._lastLoadedMap[i] || 0;
                    const delta = loaded - prev;

                    if (delta > 0) {
                        f._lastLoadedMap[i] = loaded;
                        f.uploadSize += delta;
                        f.uploadProgress = Math.floor(
                            (f.uploadSize / f.totalSize) * 100
                        );
                    }
                }
            });

            reqAbortMap.delete(`${uid}:${i}`);

            if (!res) {
                if (!f.pause && !f.canceled) {
                    f.status = STATUS.fail.value;
                    f.errorMsg = f.errorMsg || "Upload failed";
                    releaseReserve(f);
                }
                return null;
            }

            if (res.data?.fileId != null) f.fileId = res.data.fileId;
            const st = res?.data?.status;
            if (st === STATUS.upload_completed.value || st === STATUS.instant_upload.value) {
                f.status = st;
            } else {
                // ✅ 上传中一律保持 uploading（否则按钮会消失）
                if (f.status !== STATUS.fail.value) f.status = STATUS.uploading.value;
            }

            if (res.data?.status === STATUS.upload_completed.value || res.data?.status === STATUS.instant_upload.value) {
                finished = true;
                f.uploadProgress = 100;
                releaseReserve(f);
                notify({ type: "DONE", uid, status: f.status });
                return res;
            }

            f.chunkIndex = Math.max(f.chunkIndex ?? -1, i);
            f.uploadedChunkBytes[i] = chunkFile.size;
            recomputeProgress(f);

            return res;
        };

        const workers = Array.from({ length: concurrency }).map(async () => {
            while (!finished) {
                if (!f || f.canceled || delList.value.includes(uid)) return;

                // ✅ 核心：pause 时冻结调度
                if (f.pause) {
                    await waitIfPaused(f);
                    continue;
                }

                const i = indices[ptr++];
                if (i == null) return;

                const expected = Math.min(chunkSize, fileSize - i * chunkSize);
                if ((f.uploadedChunkBytes?.[i] || 0) >= expected) {
                    f.chunkIndex = Math.max(f.chunkIndex ?? -1, i);
                    continue;
                }

                await runOne(i);

                if (f.status === STATUS.fail.value) {
                    finished = true;
                    return;
                }
            }

        });

        await Promise.all(workers);
    } finally {
        uploadingSet.delete(uid);
    }
};

/* ===================== MD5 worker（无缝迁移） ===================== */

const computeMD5 = (fileItem) => {
    const uid = fileItem.uid;
    const file = fileItem.file;

    return new Promise((resolve) => {
        const rf = getFileByUid(uid);
        if (!rf) return resolve(null);

        abortMd5(uid);

        const worker = new Worker(new URL("@/workers/md5.worker.js", import.meta.url), { type: "module" });
        md5WorkerMap.set(uid, worker);

        worker.onmessage = (e) => {
            const msg = e.data || {};
            const cur = getFileByUid(uid);
            if (!cur || cur.canceled || delList.value.includes(uid)) {
                abortMd5(uid);
                return resolve(null);
            }

            if (msg.type === "PROGRESS") {
                cur.md5Progress = msg.progress;
                return;
            }
            if (msg.type === "DONE") {
                cur.md5Progress = 100;
                cur.md5 = msg.md5;
                abortMd5(uid);
                return resolve(uid);
            }
            if (msg.type === "ERROR") {
                cur.md5Progress = -1;
                cur.status = STATUS.fail.value;
                cur.errorMsg = "Failed to calculate file checksum";
                releaseReserve(cur);
                abortMd5(uid);
                return resolve(uid);
            }
        };

        worker.postMessage({ type: "START", file, chunkSize });
    }).catch(() => null);
};

/* ===================== 初始化注入 ===================== */

const init = ({ request, utils, message }) => {
    _request = request;
    _utils = utils;
    _message = message;
};

export function useUploadManager() {
    return {
        STATUS,
        fileList,        // ref([])
        init,
        addFile,
        pauseUpload,
        resumeUpload,
        terminateUpload,
        removeTask,
        abortAll,
        onChange,
    };
}
