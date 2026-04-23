import axios from "axios";
import { ElLoading } from "element-plus";
import Message from "../utils/Message";
import { gotoLogin } from "@/utils/Auth";

// form表单的内容类型
const contentTypeForm = "application/x-www-form-urlencoded;charset=UTF-8";
const contentTypeJson = "application/json";
const responseTypeJson = "json";

const shouldShowLoading = (cfg) => cfg?.showLoading !== false;

// ===== axios 实例 =====
const instance = axios.create({
  baseURL: "/api",
  timeout: 30 * 1000,
  withCredentials: true,
});

// ===== 全局 loading：用计数器避免并发错乱 =====
let loading = null;
let loadingCount = 0;

const openLoading = (options = {}) => {
  const {
    show = true,              // 是否显示 loading
    target = document.body,   // 默认 body
  } = options;

  if (!show) return;

  loadingCount++;
  if (loadingCount === 1) {
    loading = ElLoading.service({
      lock: target === document.body, // 只有 body 才 lock
      target,
      text: "Loading...",
      background: "rgba(0, 0, 0, 0.7)",
    });
  }
};


const closeLoading = () => {
  if (loadingCount <= 0) return;
  loadingCount--;
  if (loadingCount === 0 && loading) {
    loading.close();
    loading = null;
  }
};

// ===== 你后端的业务码 =====
const LOGIN_EXPIRED_CODES = [901]; // 登录超时 / 未登录 / token非法

// ===== 请求前拦截器 =====
instance.interceptors.request.use(
  (config) => {
    if (shouldShowLoading(config)) {
      openLoading({ target: config.loadingTarget || document.body });
    }
    return config;
  },
  (error) => {
    closeLoading();
    Message.error("Failed to send request.");
    return Promise.reject({ showError: true, msg: "Failed to send request." });
  }
);

// ===== 响应拦截器 =====
instance.interceptors.response.use(
  (response) => {
    const { errorCallback, showError = true, responseType } = response.config;
    if (shouldShowLoading(response.config)) closeLoading();

    const rd = response.data;

    if (responseType === "arraybuffer" || responseType === "blob") return rd;
    if (rd.code === 200) return rd;

    if (LOGIN_EXPIRED_CODES.includes(rd.code)) {
      // ✅ 把后端的提示带给用户（例如 “token非法/登录超时”）
      gotoLogin(rd.msg || rd.info || "Session expired. Please log in again.");
      return Promise.reject({ showError: false, code: rd.code, msg: rd.msg || rd.info || "Please log in again." });
    }

    if (rd.code === 907) {
      if (errorCallback) errorCallback(rd.info || rd.msg);
      return Promise.reject({ showError, code: rd.code, msg: rd.msg || "Access denied." });
    }

    if (errorCallback) errorCallback(rd.info || rd.msg);
    return Promise.reject({ showError, code: rd.code, msg: rd.info || rd.msg || "Request failed." });
  },
  (error) => {
    const cfg = error?.config || error?.response?.config;
    if (shouldShowLoading(cfg)) closeLoading();
    const canceled =
      error?.code === "ERR_CANCELED" ||
      error?.message === "canceled" ||
      axios.isCancel?.(error);

    if (canceled) {
      return Promise.reject({ showError: false, msg: "__CANCELED__" });
    }

    const status = error?.response?.status;

    // ✅ 只对 401 踢登录（403 是权限问题，不踢）
    if (status === 401) {
      // 如果后端也给了 X-Auth-Expired，更精准（axios 能拿到 header）
      const expired = error?.response?.headers?.["x-auth-expired"];
      if (String(expired) === "1" || expired === 1 || expired === true || expired === "true") {
        gotoLogin("Session expired. Please log in again.");
        return Promise.reject({ showError: false, code: status, msg: "Please log in again." });
      }

      // 没有标记就按未授权处理（不一定是登录失效）
      return Promise.reject({ showError: true, code: status, msg: "Unauthorized." });
    }

    // ✅ 403：不跳登录，给业务层按需处理（默认提示无权限）
    if (status === 403) {
      return Promise.reject({ showError: true, code: status, msg: "Access denied." });
    }

    const showError = cfg?.showError !== false;
    return Promise.reject({ showError, msg: "Network error." });
  }
);


// ===== request 封装（保持你原来的调用方式） =====
const request = (config) => {
  const {
    url,
    params = {},
    dataType = "form",
    method = "post",
    showLoading = true,
    responseType = responseTypeJson,
    signal,
    loadingTarget,
  } = config;

  const headers = {
    "X-Requested-With": "XMLHttpRequest",
    ...(config.headers || {}),
  };

  // ---------- GET ----------
  if (method.toLowerCase() === "get") {
    return instance
      .get(url, {
        params,
        responseType,
        headers,
        showLoading,
        errorCallback: config.errorCallback,
        showError: config.showError,
        signal,
        loadingTarget,
      })
      .then((res) => res)
      .catch((error) => {
        console.log(error);

        const m = error?.msg;

        // 只把“取消/网络异常”传给业务层（比如 Uploader.vue）
        if (config.errorCallback && (m === "__CANCELED__" || m === "Network error.")) {
          config.errorCallback(m);
        }

        if (error?.showError) {
          Message.error(m || "Network error.");
        }
        return config.returnError ? { __error: true, code: error?.code ?? -1, msg: m || "Network error." } : null;
      });
  }

  if (method.toLowerCase() === "head") {
    return instance
      .head(url, {
        params,
        headers,
        showLoading,
        errorCallback: config.errorCallback,
        showError: config.showError,
        signal,
        loadingTarget,
      })
      .then((res) => res)
      .catch((error) => {
        const m = error?.msg;
        if (error?.showError) Message.error(m || "Network error.");
        return config.returnError ? { __error: true, code: error?.code ?? -1, msg: m || "Network error." } : null;
      });
  }

  // ---------- POST ----------
  let data;

  if (dataType === "json") {
    headers["Content-Type"] = contentTypeJson;
    data = params;
  } else if (dataType === "form") {
    headers["Content-Type"] = contentTypeForm;
    const formParams = new URLSearchParams();
    for (let key in params) {
      formParams.append(key, params[key] == null ? "" : params[key]);
    }
    data = formParams;
  } else if (dataType === "file") {
    const formData = new FormData();
    for (let key in params) {
      formData.append(key, params[key]);
    }
    data = formData;
    // 不要手动写 Content-Type，让浏览器加 boundary
  }

  return instance
    .post(url, data, {
      onUploadProgress: (event) => {
        if (config.uploadProgressCallback) {
          config.uploadProgressCallback(event);
        }
      },
      responseType,
      headers,
      showLoading,
      errorCallback: config.errorCallback,
      showError: config.showError,
      signal,
      loadingTarget,
    })
    .catch((error) => {
      console.log(error);

      const m = error?.msg;

      if (config.errorCallback && (m === "__CANCELED__" || m === "Network error.")) {
        config.errorCallback(m);
      }

      if (error?.showError) {
        Message.error(m || "Network error.");
      }
      return config.returnError ? { __error: true, code: error?.code ?? -1, msg: m || "Network error." } : null;
    });
};
export default request;
