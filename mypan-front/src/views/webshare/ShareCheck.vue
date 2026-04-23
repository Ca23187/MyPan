<template>
  <div class="share">
    <div class="body-content">
      <div class="logo">
        <span class="iconfont icon-pan"></span>
        <span class="name">MyPan</span>
      </div>

      <!-- ✅ 失效态 -->
      <div v-if="pageState === 'invalid'" class="invalid-panel">
        <div class="title">This share link is invalid</div>
        <div class="desc">{{ invalidText }}</div>
        <div class="actions">
          <el-button type="primary" @click="router.push('/')"
            >Go home</el-button
          >
        </div>
      </div>

      <!-- ✅ 正常提取码页 -->
      <div v-else class="code-panel">
        <div class="file-info" v-if="shareInfo && shareInfo.userId">
          <div class="avatar">
            <Avatar
              :userId="shareInfo.userId"
              :avatar="shareInfo.avatar"
              :timestamp="getAvatarTs(shareInfo.userId)"
              :width="50"
            />
          </div>
          <div class="share-info">
            <div class="user-info">
              <span class="nick-name">{{ shareInfo.nickname }}</span>
              <span class="share-time"
                > Shared on {{ shareInfo.sharedOn }}</span
              >
            </div>
            <div class="file-name">File: {{ shareInfo.fileName }}</div>
          </div>
        </div>

        <div class="code-body">
          <div class="tips">Enter access code:</div>
          <div class="input-area">
            <el-form
              :model="formData"
              :rules="rules"
              ref="formDataRef"
              @submit.prevent
            >
              <el-form-item prop="code">
                <el-input
                  class="input"
                  v-model="formData.code"
                  maxlength="5"
                  @keyup.enter="checkShare"
                />
                <el-button type="primary" @click="checkShare"
                  >View files</el-button
                >
              </el-form-item>
            </el-form>
          </div>

          <!-- ✅ 提取码错误用页内提示，不 toast -->
          <div v-if="codeErrorText" class="code-error">{{ codeErrorText }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance, nextTick, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const api = {
  getShareInfo: "/showShare/getShareInfo",
  checkShareCode: "/showShare/checkShareCode",
};

const shareId = route.params.shareId;

const pageState = ref("loading"); // loading | needCode | invalid | ok
const invalidText = ref("");
const codeErrorText = ref("");

const shareInfo = ref({});
const formData = ref({ code: "" });
const formDataRef = ref();

const rules = {
  code: [
    { required: true, message: "Please enter the access code" },
    { min: 5, message: "The access code must be 5 characters" },
    { max: 5, message: "The access code must be 5 characters" },
  ],
};

const SHARE_INVALID_TEXT_BY_CODE = {
  902: "The share link does not exist or has been removed.",
  903: "This file has been deleted and the share is no longer available.",
  904: "The owner account is restricted. This share is unavailable.",
  905: "This share link has expired.",
};

const mapShareInvalidTextByCode = (code, msg) =>
  SHARE_INVALID_TEXT_BY_CODE[code] ||
  (msg
    ? String(msg)
    : "This share link is unavailable. Please ask the owner to share again.");

const setInvalid = (code, msg) => {
  invalidText.value = mapShareInvalidTextByCode(code, msg);
  pageState.value = "invalid";
};

// ✅ 获取分享信息：失败就进入 invalid 态（不弹 toast）
const getShareInfo = async () => {
  pageState.value = "loading";
  const result = await proxy.Request({
    url: api.getShareInfo,
    method: "get",
    params: { shareId },
    showError: false, // ✅ 吞 toast
    returnError: true, // ✅ 拿到 {__error,msg}
  });

  if (!result) {
    setInvalid(-1, "Network error");
    return;
  }

  if (result.__error) {
    setInvalid(result.code, result.msg);
    return;
  }

  shareInfo.value = result.data;
  pageState.value = "needCode";
};

const checkShare = async (useReplace = false) => {
  codeErrorText.value = "";
  formDataRef.value.validate(async (valid) => {
    if (!valid) return;

    const result = await proxy.Request({
      url: api.checkShareCode,
      method: "post",
      params: { shareId, code: formData.value.code },
      showError: false, // ✅ 吞 toast
      returnError: true, // ✅ 拿到 {__error,msg}
    });

    if (!result) return;

    if (result.__error) {
      // 提取码错误：页内提示
      if (
        String(result.msg || "")
          .toLowerCase()
          .includes("code is incorrect")
      ) {
        codeErrorText.value = "Incorrect access code.";
        return;
      }
      // 其他：分享失效页
      setInvalid(result.code, result.msg);
      return;
    }

    // ✅ 成功：进入分享内容页
    const nav = { path: `/share/${shareId}` };
    useReplace ? router.replace(nav) : router.push(nav);
  });
};

const getAvatarTs = (uid) => {
  if (!uid) return 0;
  return Number(localStorage.getItem(`avatar_ts_${uid}`)) || 0;
};

onMounted(async () => {
  await getShareInfo();

  const qCode = route.query.code;
  if (qCode && pageState.value !== "invalid") {
    formData.value.code = String(qCode);
    await nextTick();
    checkShare(true);
  }
});
</script>

<style lang="scss" scoped>
.share {
  height: calc(100vh);
  background: url("../../assets/share_bg.png");
  background-repeat: repeat-x;
  background-position: 0 bottom;
  background-color: #eef2f6;
  display: flex;
  justify-content: center;

  .body-content {
    margin-top: calc(100vh / 5);
    width: 500px;

    .logo {
      display: flex;
      align-items: center;
      justify-content: center;

      .icon-pan {
        font-size: 60px;
        color: #409eff;
      }
      .name {
        font-weight: bold;
        margin-left: 5px;
        font-size: 25px;
        color: #409eff;
      }
    }

    .code-panel {
      margin-top: 20px;
      background: #fff;
      border-radius: 5px;
      overflow: hidden;
      box-shadow: 0 0 7px 1px #5757574f;

      .file-info {
        padding: 10px 20px;
        background: #409eff;
        color: #fff;
        display: flex;
        align-items: center;

        .avatar {
          margin-right: 5px;
        }

        .share-info {
          .user-info {
            display: flex;
            align-items: center;

            .nick-name {
              font-size: 15px;
            }
            .share-time {
              margin-left: 20px;
              font-size: 12px;
            }
          }

          .file-name {
            margin-top: 10px;
            font-size: 12px;
          }
        }
      }

      .code-body {
        padding: 30px 20px 60px 20px;

        .tips {
          font-weight: bold;
        }

        .input-area {
          margin-top: 10px;

          /* ✅ Element Plus 表单项默认不是 flex，强制一行排列 */
          :deep(.el-form-item__content) {
            display: flex;
            align-items: center;
          }

          .input {
            flex: 1;
            margin-right: 10px;
          }
        }
      }
    }

    /* ✅ 失效态：沿用 code-panel 的卡片风格 */
    .invalid-panel {
      margin-top: 20px;
      background: #fff;
      border-radius: 5px;
      overflow: hidden;
      box-shadow: 0 0 7px 1px #5757574f;
      padding: 30px 20px 40px 20px;
      text-align: center;

      .title {
        font-size: 18px;
        font-weight: bold;
        margin-bottom: 8px;
      }
      .desc {
        font-size: 13px;
        color: #666;
        line-height: 1.6;
        margin-bottom: 18px;
      }
    }

    /* ✅ 提取码错误 */
    .code-error {
      margin-top: 10px;
      font-size: 16px;
      color: #f56c6c;
    }
  }
}
</style>
