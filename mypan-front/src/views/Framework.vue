<template>
  <div class="framework">
    <!-- 头部 -->
    <div class="header">
      <!-- 左上角logo -->
      <div class="logo">
        <span class="iconfont icon-pan"></span>
        <span class="name">MyPan</span>
      </div>

      <!-- 右侧消息弹框 -->
      <div class="right-panel">
        <el-popover
          :width="800"
          trigger="click"
          v-model:visible="showUploader"
          :offset="20"
          transition="none"
          :hide-after="0"
          :popper-style="{ padding: '0px' }"
          popper-class="uploader-popper"
        >
          <template #reference>
            <span class="iconfont icon-transfer"></span>
          </template>

          <!-- ✅ slot 里放空壳，避免 Uploader 在 slot 生命周期里被销毁 -->
          <template #default>
            <div style="height: 1px"></div>
          </template>
        </el-popover>

        <!-- ✅ Uploader 永久挂载，但显示在 popover 的 popper 里 -->
        <Teleport to=".uploader-popper">
          <Uploader v-show="showUploader" />
        </Teleport>

        <!-- 用户信息下拉菜单 -->
        <el-dropdown>
          <!-- 用户信息 -->
          <div class="user-info" v-if="userStore.userId">
            <Avatar
              :userId="userStore.userId"
              :timestamp="timestamp"
              :width="46"
            />
            <span class="nick-name">{{ userStore.nickname }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="updateAvatar">
                Change Avatar
              </el-dropdown-item>
              <el-dropdown-item @click="updatePassword">
                Change Password
              </el-dropdown-item>
              <el-dropdown-item @click="logout"> Log Out </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 主体 -->
    <div class="body">
      <!-- 最左侧菜单栏 一级目录-->
      <div class="left-sider">
        <div class="menu-list">
          <template v-for="item in menus">
            <div
              @click="jump(item)"
              :class="[
                'menu-item',
                item.menuCode == currentMenu.menuCode ? 'active' : '',
              ]"
            >
              <div :class="['iconfont', 'icon-' + item.icon]"></div>
              <div class="text">
                {{ item.name }}
              </div>
            </div>
          </template>
        </div>
        <div class="menu-sub-list">
          <div
            @click="jump(sub)"
            :class="['menu-item-sub', currentPath == sub.path ? 'active' : '']"
            v-for="sub in currentMenu.children"
          >
            <span
              :class="['iconfont', 'icon-' + sub.icon]"
              v-if="sub.icon"
            ></span>
            <span class="text">{{ sub.name }}</span>
          </div>
          <div class="tips" v-if="currentMenu && currentMenu.tips">
            {{ currentMenu.tips }}
          </div>
          <div class="space-info">
            <div>Storage</div>
            <div class="percent">
              <el-progress
                :percentage="
                  Math.floor(
                    (userSpaceInfo.usedSpace / userSpaceInfo.totalSpace) * 10000
                  ) / 100
                "
                color="#409eff"
              />
            </div>

            <div class="space-use">
              <div class="use">
                {{ proxy.Utils.size2Str(userSpaceInfo.usedSpace) }}/
                {{ proxy.Utils.size2Str(userSpaceInfo.totalSpace) }}
              </div>
              <div class="iconfont icon-refresh" @click="getUsedSpace"></div>
            </div>
          </div>
        </div>
      </div>
      <!-- 中间主题内容 -->
      <div class="body-content">
        <!-- v-slot="{ Component } 解构插槽 -->
        <!-- 让router-view的插槽能够访问子组件中的数据 -->
        <!-- 访问的数据就是Component -->
        <router-view v-slot="{ Component }">
          <!-- 调用Main子组件 将Main中的数据接收到Framework中 -->
          <component
            @addFile="addFile"
            @reload="getUsedSpace"
            ref="routerViewRef"
            :is="Component"
          />
        </router-view>
      </div>
    </div>
    <!-- 修改头像 -->
    <!-- @updateAvatar接收子组件 UpdateAvatar传来的数据 -->
    <UpdateAvatar
      ref="updateAvatarRef"
      @updateAvatar="reloadAvatar"
    ></UpdateAvatar>

    <!-- 修改密码 -->
    <UpdatePassword ref="updatePasswordRef"></UpdatePassword>
  </div>
</template>

<script setup>
import UpdateAvatar from "./UpdateAvatar.vue";
import UpdatePassword from "./UpdatePassword.vue";
import Uploader from "@/views/main/Uploader.vue";
import Avatar from "@/components/Avatar.vue";
import { useUserStore } from "@/store/user";

const userStore = useUserStore();

import {
  ref,
  reactive,
  getCurrentInstance,
  watch,
  nextTick,
  computed,
  onMounted,
  onUnmounted,
} from "vue";
import { useRouter, useRoute } from "vue-router";
const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

import { useUploadManager } from "@/store/uploadManager";

const uploadManager = useUploadManager();
uploadManager.init({
  request: proxy.Request,
  utils: proxy.Utils,
  message: proxy.Message,
});
const api = {
  getUsedSpace: "/getUsedSpace",
  getUserInfo: "/getUserInfo",
};

const initUserInfo = async () => {
  const result = await proxy.Request({
    url: "/getUserInfo",
    method: "get",
    showLoading: false,
  });
  if (result?.data) userStore.setProfile(result.data);
};

// 控制是否展示上传区域
const showUploader = ref(false);
// 文件上传处数据绑定
const uploaderRef = ref();
// 上传文件
const addFile = ({ file, filePid }) => {
  showUploader.value = true;
  uploadManager.addFile({ file, filePid });
};

// 上传文件后的刷新列表(调用Uploader子组件中的函数)
const routerViewRef = ref();

// 每个用户一个 key
const avatarKey = computed(() => {
  const uid = userStore.userId;
  return uid ? `avatar_ts_${uid}` : "";
});

// 先给一个默认值（避免 userId 还没来时 URL 没变化）
const timestamp = ref(Date.now());

// userId 一旦拿到，就用该用户自己的 ts 覆盖
watch(
  () => userStore.userId,
  (uid) => {
    if (!uid) return;
    const saved = Number(localStorage.getItem(`avatar_ts_${uid}`));
    if (saved) timestamp.value = saved;
  },
  { immediate: true }
);

// 菜单栏
const rawMenus = [
  {
    icon: "cloude",
    name: "Home",
    menuCode: "main",
    path: "/main/all",
    allShow: true,
    children: [
      {
        icon: "all",
        name: "All",
        category: "all",
        path: "/main/all",
      },
      {
        icon: "video",
        name: "Videos",
        category: "video",
        path: "/main/video",
      },
      {
        icon: "music",
        name: "Audio",
        category: "music",
        path: "/main/music",
      },
      {
        icon: "image",
        name: "Images",
        category: "image",
        path: "/main/image",
      },
      {
        icon: "doc",
        name: "Documents",
        category: "doc",
        path: "/main/doc",
      },
      {
        icon: "more",
        name: "Others",
        category: "others",
        path: "/main/others",
      },
      {
        icon: "search",
        name: "Search",
        category: "search",
        path: "/main/search",
      },
    ],
  },
  {
    path: "/myshare",
    icon: "share",
    name: "Shares",
    menuCode: "share",
    allShow: true,
    children: [
      {
        name: "Share History",
        path: "/myshare",
      },
    ],
  },
  {
    path: "/recycle",
    icon: "del",
    name: "Recycle Bin",
    menuCode: "recycle",
    tips: "Items deleted in the last 10 days are kept here.",
    allShow: true,
    children: [
      {
        name: "Deleted Files",
        path: "/recycle",
      },
    ],
  },
  {
    path: "/settings/fileList",
    icon: "settings",
    name: "Settings",
    menuCode: "settings",
    allShow: false,
    children: [
      { name: "User Files", path: "/settings/fileList" },
      { name: "User Management", path: "/settings/userList" },
      { path: "/settings/sysSetting", name: "System Settings" },
    ],
  },
];
const currentMenu = ref({});
const currentPath = ref();

const menus = computed(() => {
  // 注意：userInfo 是 store 里的响应式对象
  const isAdmin = !!userStore.isAdmin;

  return rawMenus.filter((m) => {
    if (m.menuCode === "settings") return isAdmin;
    return true;
  });
});

// 菜单栏选项跳转
const jump = (data) => {
  if (!data.path) return;
  if (data.menuCode && data.menuCode === currentMenu.value.menuCode) return;
  router.push(data.path);
};

// 设置当前菜单栏
const setMenu = (menuCode, path) => {
  const menu = menus.value.find((item) => item.menuCode === menuCode);
  currentMenu.value = menu || {};
  currentPath.value = path;
};

watch(
  () => route,
  (newVal, oldVal) => {
    if (newVal.meta.menuCode) {
      setMenu(newVal.meta.menuCode, newVal.path);
    }
  },
  { immediate: true, deep: true }
);

// 修改头像
const updateAvatarRef = ref();
// 利用defineExpose，父组件调用子组件的函数，
// 将用户信息利用show传递给子组件的show函数，使得子组件更新信息
const updateAvatar = () => {
  updateAvatarRef.value.show(userStore);
};
// 利用子组件的 emit("updateAvatar"); 传递回来的信息，重新加载最新头像
const reloadAvatar = () => {
  const uid = userStore.userId;
  const ts = Date.now();
  timestamp.value = ts;

  if (uid) {
    localStorage.setItem(`avatar_ts_${uid}`, String(ts));
  }
};

// 修改密码
const updatePasswordRef = ref();
const updatePassword = () => {
  updatePasswordRef.value.show();
};

// 退出登录
const logout = () => {
  proxy.Confirm(`Are you sure you want to log out?`, async () => {
    // ✅ 先中止所有上传/并发请求（你项目里已经监听这个事件了）
    window.dispatchEvent(new Event("APP_ABORT_UPLOADS"));

    // ✅ 再调后端注销（不需要 try/catch，失败也照样清前端并跳）
    await proxy.Request({
      url: "/auth/logout",
      showLoading: false,
      showError: false,     // ✅ 关键：登出过程不弹 “Request failed”
      returnError: true,    // 可选：拿到错误对象，但不弹窗
    });

    // 清前端状态 & 跳登录
    const uid = userStore.userId;
    userStore.clearUserInfo();
    if (uid) localStorage.removeItem(`avatar_ts_${uid}`);
    router.replace("/login"); // ✅ replace 更好，避免返回键回到已登录页
  });
};

// 使用空间
const userSpaceInfo = ref({ usedSpace: 0, totalSpace: 1 });
const getUsedSpace = async () => {
  let result = await proxy.Request({
    url: api.getUsedSpace,
    method: "get",
    showLoading: false,
  });
  if (!result) {
    return;
  }
  userSpaceInfo.value = result.data;
};
// 页面加载时拉一次用户信息
onMounted(() => {
  initUserInfo();
  getUsedSpace();
});

// ✅ 暴露给 router-view 的子组件（Recycle.vue / Main.vue 等）
defineExpose({
  getUsedSpace,
});

let off = null;

onMounted(() => {
  off = uploadManager.onChange((evt) => {
    if (evt.type === "DONE") {
      getUsedSpace();
      routerViewRef.value?.reload?.();
    }
  });
});

onUnmounted(() => {
  off?.();
});
</script>
<style lang="scss" scoped>
.header {
  box-shadow: 0 3px 10px 0 rgb(0 0 0 / 6%);
  height: 56px;
  padding-left: 24px;
  padding-right: 24px;
  position: relative;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .logo {
    display: flex;
    align-items: center;
    .icon-pan {
      font-size: 40px;
      color: #1296db;
    }
    .name {
      font-weight: bold;
      margin-left: 5px;
      font-size: 25px;
      color: #05a1f5;
    }
  }
  .right-panel {
    display: flex;
    align-items: center;
    .icon-transfer {
      cursor: pointer;
    }
    .user-info {
      margin-right: 10px;
      display: flex;
      align-items: center;
      cursor: pointer;
      // 头像
      .avatar {
        margin: 0px 5px 0px 15px;
      }
      // 昵称
      .nick-name {
        color: #05a1f5;
      }
    }
  }
}
.body {
  display: flex;
  .left-sider {
    border-right: 1px solid #f1f2f4;
    display: flex;
    .menu-list {
      height: calc(100vh - 56px);
      width: 80px;
      box-shadow: 0 3px 10px 0 rgb(0 0 0 / 6%);
      border-right: 1px solid #f1f2f4;
      .menu-item {
        text-align: center;
        font-size: 14px;
        font-weight: bold;
        padding: 20px 0px;
        cursor: pointer;
        &:hover {
          background: #f3f3f3;
        }
        .iconfont {
          font-weight: normal;
          font-size: 28px;
        }
      }
      .active {
        .iconfont {
          color: #06a7ff;
        }
        .text {
          color: #06a7ff;
        }
      }
    }
    .menu-sub-list {
      width: 200px;
      padding: 20px 10px 0px;
      position: relative;
      .menu-item-sub {
        display: flex;
        align-items: center;
        height: 40px; // 用 height 更稳定
        line-height: 40px;
        border-radius: 5px;
        cursor: pointer;
        padding: 0 40px; // 给一点左右内边距
        gap: 10px; // 统一间距
        &:hover {
          background: #f3f3f3;
        }

        .iconfont {
          flex: 0 0 20px; // 固定图标宽度，保证文字起点一致
          width: 20px;
          font-size: 14px;
          text-align: center;
          margin-right: 0; // 删掉原来的 margin
        }

        .text {
          font-size: 13px;
          flex: 1;
          text-align: left; // 左对齐
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .active {
        background: #eef9fe;
        .iconfont {
          color: #05a1f5;
        }
        .text {
          color: #05a1f5;
        }
      }

      .tips {
        margin-top: 10px;
        color: #888888;
        font-size: 13px;
      }

      .space-info {
        position: absolute;
        bottom: 10px;
        width: 100%;
        padding: 0px 5px;
        .percent {
          padding-right: 10px;
        }
        .space-use {
          margin-top: 5px;
          color: #7e7e7e;
          display: flex;
          justify-content: space-around;
          .use {
            flex: 1;
          }
          .iconfont {
            cursor: pointer;
            margin-right: 20px;
            color: #05a1f5;
          }
        }
      }
    }
  }
  .body-content {
    flex: 1;
    width: 0;
    padding-left: 20px;
  }
}
</style>
