import router from "@/router";
import Message from "@/utils/Message";
import { useUserStore } from "@/store/user";

let redirecting = false;
let toastShown = false;

export function gotoLogin(msg) {
  if (redirecting) return;
  redirecting = true;

  // 默认是“登录失效”的错误提示（给 request.js 用）
  if (!toastShown) {
    toastShown = true;
    Message.error(msg || "Session expired. Please log in again.");
    setTimeout(() => (toastShown = false), 1200);
  }

  window.dispatchEvent(new Event("APP_ABORT_UPLOADS"));

  const userStore = useUserStore();
  userStore.clearUserInfo();

  const redirectUrl = encodeURIComponent(
    router.currentRoute.value.fullPath || router.currentRoute.value.path
  );
  router.replace(`/login?redirectUrl=${redirectUrl}`);

  setTimeout(() => (redirecting = false), 800);
}

/**
 * 主动强制重新登录（比如改密成功）
 * toastType:
 *  - "success": 先弹成功提示，再跳登录（不弹 error）
 *  - "none": 不弹提示，直接跳
 *  - "error": 沿用 gotoLogin 的 error 提示（默认）
 */
export function forceRelogin(msg, { toastType = "error" } = {}) {
  if (redirecting) return;

  if (toastType === "success") {
    Message.success(msg || "Please log in again.");
    // 直接执行跳转逻辑，但不要触发 error toast
    toastShown = true;               // 临时锁住 toast
    gotoLogin(null);
    setTimeout(() => (toastShown = false), 0);
    return;
  }

  if (toastType === "none") {
    toastShown = true;
    gotoLogin(null);
    setTimeout(() => (toastShown = false), 0);
    return;
  }

  gotoLogin(msg);
}
