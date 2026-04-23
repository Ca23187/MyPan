import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from "@/utils/request"; // 你封装的 proxy.Request 对应的文件路径
import { useUserStore } from "@/store/user";

const router = createRouter({
    history: createWebHistory(
        import.meta.env.BASE_URL),
    routes: [{
            path: '/login',
            name: 'Login',
            component: () =>
                import ("@/views/Login.vue")
        },
        {
            path: '/',
            name: 'Framework',
            component: () =>
                import ("@/views/Framework.vue"),
            children: [{
                    path: '/',
                    redirect: "/main/all"
                },
                {
                    path: '/main/:category',
                    name: 'Home',
                    meta: {
                        needLogin: true,
                        menuCode: "main"
                    },
                    component: () =>
                        import ("@/views/main/Main.vue")
                },
                {
                    path: '/myshare',
                    name: 'My Shares',
                    meta: {
                        needLogin: true,
                        menuCode: "share"
                    },
                    component: () =>
                        import ("@/views/share/Share.vue")
                },
                {
                    path: '/recycle',
                    name: 'Recycle Bin',
                    meta: {
                        needLogin: true,
                        menuCode: "recycle"
                    },
                    component: () =>
                        import ("@/views/recycle/Recycle.vue")
                },
                {
                    path: '/settings/sysSetting',
                    name: 'System Settings',
                    meta: {
                        needLogin: true,
                        needAdmin: true,
                        menuCode: "settings"
                    },
                    component: () =>
                        import ("@/views/admin/SysSettings.vue")
                },
                {
                    path: '/settings/userList',
                    name: 'Users Management',
                    meta: {
                        needLogin: true,
                        needAdmin: true,
                        menuCode: "settings"
                    },
                    component: () =>
                        import ("@/views/admin/UserList.vue")
                },
                {
                    path: '/settings/fileList',
                    name: 'User Files',
                    meta: {
                        needLogin: true,
                        needAdmin: true,
                        menuCode: "settings"
                    },
                    component: () =>
                        import ("@/views/admin/FileList.vue")
                },
            ]
        },
        {
            path: '/shareCheck/:shareId',
            name: 'Share Verification',
            component: () =>
                import ("@/views/webshare/ShareCheck.vue")
        },
        {
            path: '/share/:shareId',
            name: 'Share',
            component: () =>
                import ("@/views/webshare/Share.vue")
        },
    ]
})

let mePromise = null;

async function ensureMe() {
  // 已经有用户信息就不再请求
  const userStore = useUserStore();
  if (userStore.userId) return userStore;

  if (!mePromise) {
    mePromise = request({
      url: "/getUserInfo",
      method: "get",
      showLoading: false,
      showError: false,
    }).finally(() => {
      mePromise = null;
    });
  }

  const res = await mePromise;
  if (res && res.data && res.code === 200) {
    userStore.setProfile(res.data);
    return res.data;
  }

  userStore.clearUserInfo();
  return null;
}

router.beforeEach(async (to, from, next) => {
  // ✅ 不需要登录的页面直接放行
  const userStore = useUserStore();
  if (!to.meta.needLogin) return next();

  // ✅ 先确保已登录 & 拿到用户信息
  let me = null;
  try {
    me = await ensureMe();
  } catch (e) {
    me = null;
  }

  if (!me) {
    ElMessage.error("Please log in first.");
    return next(`/login?redirectUrl=${encodeURIComponent(to.fullPath)}`);
  }

  // ✅ 管理员权限校验：只要不是管理员，任何 needAdmin 路由都进不去
  if (to.meta.needAdmin) {
    // 这里字段名按你的实际来：isAdmin / admin / role 等
    const isAdmin = !!userStore.isAdmin; // 或 !!me.isAdmin

    if (!isAdmin) {
      ElMessage.error("Access denied.");
      // 你也可以跳回首页或 403 页
      return next({ path: "/main/all", replace: true });
    }
  }

  return next();
});


export default router