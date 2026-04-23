<template>
  <div>
    <!-- 顶部查询 -->
    <div class="top-panel">
      <el-form :model="searchFormData" label-width="80px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="Nickname">
              <el-input
                clearable
                placeholder="Supports fuzzy search"
                v-model.trim="searchFormData.nicknameFuzzy"
                @keyup.enter="loadDataList"
                :suffix-icon="Search"
                @clear="handleSearchClear"
              />
            </el-form-item>
          </el-col>

          <el-col :span="8">
            <el-form-item label="Status">
              <el-select
                clearable
                placeholder="Select status"
                v-model="searchFormData.status"
                style="width: 100%"
              >
                <el-option :value="null" label="All" />
                <el-option :value="1" label="Enabled" />
                <el-option :value="0" label="Disabled" />
              </el-select>
            </el-form-item>
          </el-col>

          <!-- 关键：用空 label 的 form-item，让按钮和上面两项对齐 -->
          <!-- 查询 / 重置 -->
          <el-col :span="8">
            <el-form-item label=" ">
              <div class="btn-group">
                <el-button type="primary" @click="loadDataList">Search</el-button>
                <el-button @click="resetSearch">Reset</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- 列表 -->
    <div class="file-list">
      <Table
        ref="dataTableRef"
        :columns="columns"
        :dataSource="tableData"
        :fetch="loadDataList"
        :initFetch="true"
        :options="tableOptions"
      >
        <template #avatar="{ row }">
          <div class="avatar">
            <Avatar :userId="row.userId" :avatar="row.qqAvatar" :timestamp="getAvatarTs(row.userId)" :width="46" />
          </div>
        </template>

        <template #email="{ row }">
          <div class="email-cell">
            {{ row.email }}
          </div>
        </template>

        <template #nickname="{ row }">
          <div class="nickname-cell">
            <div class="nickname-main">
              {{ row.nickname }}
              <el-tooltip v-if="row.admin" content="Admin account" placement="top">
                <el-icon class="admin-icon">
                  <UserFilled />
                </el-icon>
              </el-tooltip>
            </div>
          </div>
        </template>

        <template #space="{ row }">
          <div class="space-cell">
            <div class="space-used">
              {{ proxy.Utils.size2Str(row.usedSpace) }} /
            </div>
            <div class="space-total">
              {{ proxy.Utils.size2Str(row.totalSpace) }}
            </div>
          </div>
        </template>

        <template #createdAt="{ row }">
          <div class="time-cell">
            <div class="date">
              {{ row.createdAt?.slice(0, 10) }}
            </div>
            <div class="clock">
              {{ row.createdAt?.slice(11) }}
            </div>
          </div>
        </template>
        <template #lastLoginAt="{ row }">
          <div class="time-cell">
            <div class="date">
              {{ row.lastLoginAt?.slice(0, 10) }}
            </div>
            <div class="clock">
              {{ row.lastLoginAt?.slice(11) }}
            </div>
          </div>
        </template>
        <template #status="{ row }">
          <span v-if="row.status == 1" style="color: #529b2e">Enabled</span>
          <span v-else style="color: #f56c62">Disabled</span>
        </template>

        <template #op="{ row }">
          <div class="op-actions">
            <template v-if="!hideSpaceAction(row)">
              <el-tooltip content="Adjust this user's total quota" placement="top">
                <el-button
                  class="op-btn"
                  type="primary"
                  plain
                  @click="updateSpace(row)"
                >
                  Adjust quota
                </el-button>
              </el-tooltip>
            </template>

            <!-- 禁用/启用：对自己 + 管理员隐藏 -->
            <template v-if="!hideStatusAction(row)">
              <el-tooltip
                :content="row.status == 0 ? 'Enable this user' : 'Disable this user'"
                placement="top"
              >
                <el-button
                  class="op-btn"
                  :type="row.status == 0 ? 'success' : 'warning'"
                  plain
                  @click="updateUserStatus(row)"
                >
                  {{ row.status == 0 ? "Enable" : "Disable" }}
                </el-button>
              </el-tooltip>
            </template>

            <!-- 清库：对管理员隐藏；允许自己清自己 -->
            <template v-if="!hideClearFilesAction(row)">
              <el-tooltip content="Danger: permanently delete all files" placement="top">
                <el-button
                  class="op-btn"
                  type="danger"
                  plain
                  @click="openClearFilesDialog(row)"
                >
                  Clear files
                </el-button>
              </el-tooltip>
            </template>
          </div>
        </template>
      </Table>
    </div>

    <!-- 分配空间 Dialog：增减 totalSpace（MB，可正可负） -->
    <Dialog
      :show="dialogConfig.show"
      :title="dialogConfig.title"
      :buttons="dialogConfig.buttons"
      width="420px"
      :showCancel="false"
      @close="dialogConfig.show = false"
    >
      <el-form
        :model="formData"
        :rules="spaceRules"
        ref="formDataRef"
        label-width="90px"
        @submit.prevent
      >
        <el-form-item label="Nickname">{{ formData.nickname }}</el-form-item>

        <el-form-item label="Quota change" prop="newSpace">
          <div class="space-input">
            <el-input-number
              v-model="formData.newSpace"
              :precision="0"
              :step="100"
              controls-position="right"
              placeholder="Negative values allowed"
            />
            <span class="unit">MB</span>
          </div>
          <div class="tip">Negative values supported (e.g., -100 reduces quota by 100 MB).</div>
        </el-form-item>
      </el-form>
    </Dialog>

    <!-- 清除文件 Dialog -->
    <Dialog
      :show="clearDialog.show"
      :title="clearDialog.title"
      :buttons="clearDialog.buttons"
      width="520px"
      :showCancel="true"
      @close="closeClearDialog"
    >
      <div class="danger-box">
        <div class="danger-title">Danger: Permanently delete all files for this user</div>
        <div class="danger-text">
          User: <b>{{ clearDialog.target?.nickname }}</b
          >（{{ clearDialog.target?.email }}）
        </div>
        <div class="danger-text">
          This will permanently delete all files for this user (cannot be undone).
        </div>

        <el-form label-width="90px" @submit.prevent>
          <el-form-item label="Confirmation">
            <el-input
              v-model.trim="clearDialog.confirmText"
              placeholder='Type "DELETE" to confirm'
              clearable
            />
          </el-form-item>
        </el-form>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance, nextTick } from "vue";
import { UserFilled } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";
import { Search } from "@element-plus/icons-vue";

const { proxy } = getCurrentInstance();
const emit = defineEmits(["reload"]);

const userStore = useUserStore();

const api = {
  loadDataList: "/admin/loadUserList", // GET
  updateUserStatus: "/admin/updateUserStatus", // POST/REQUEST-MAPPING
  addUserTotalSpace: "/admin/addUserTotalSpace", // POST
  clearUserFiles: "/admin/clearUserFiles", // POST
};

// 列表列
const columns = [
  { label: "Avatar", prop: "avatar", width: 70, scopedSlots: "avatar" },
  { label: "Nickname", prop: "nickname", scopedSlots: "nickname" },
  { label: "Email", prop: "email" },
  { label: "Storage", prop: "space", scopedSlots: "space" },
  { label: "Joined", prop: "createdAt", scopedSlots: "createdAt" },
  {
    label: "Last login",
    prop: "lastLoginAt",
    scopedSlots: "lastLoginAt",
  },
  {
    label: "Status",
    prop: "status",
    scopedSlots: "status",
    align: "center",
  },
  { label: "Actions", prop: "op", width: 280, scopedSlots: "op", align: "center"},
];

const searchFormData = ref({});
const tableData = ref({});
const tableOptions = { extHeight: 20 };

const resetSearch = () => {
  // 清空查询条件
  searchFormData.value = {
    nicknameFuzzy: "",
    status: null,
  };

  // 回到第一页
  tableData.value.pageNo = 1;

  // 重新加载
  loadDataList();
};

const loadDataList = async () => {
  const params = {
    pageNo: tableData.value.pageNo,
    pageSize: tableData.value.pageSize,
    ...searchFormData.value,
  };
  Object.assign(params, searchFormData.value);

  const result = await proxy.Request({
    url: api.loadDataList,
    method: "get",
    params,
  });
  if (!result) return;
  tableData.value = result.data;
};

// ====== 按钮显示规则 ======
const hideStatusAction = (row) => {
  const isSelf = row.userId === userStore.userId;
  const isAdmin = !!row.admin;
  return isSelf || isAdmin;
};

// ====== 分配空间按钮显示规则 ======
const hideSpaceAction = (row) => {
  const isAdmin = !!row.admin;
  const isSelf = row.userId === userStore.userId;
  // 管理员且不是自己 → 隐藏
  return isAdmin && !isSelf;
};

const hideClearFilesAction = (row) => {
  const isAdmin = !!row.admin;
  if (isAdmin && row.userId !== userStore.userId) return true;
  return false;
};

// ====== 修改状态 ======
const updateUserStatus = (row) => {
  proxy.Confirm(
    `Are you sure you want to ${row.status == 0 ? "enable" : "disable"} this user?`,
    async () => {
      const result = await proxy.Request({
        url: api.updateUserStatus,
        params: {
          userId: row.userId,
          status: row.status == 0 ? 1 : 0,
        },
      });
      if (!result) return;
      loadDataList();
    }
  );
};

// ====== 分配空间：增减 totalSpace ======
const dialogConfig = ref({
  show: false,
  title: "Adjust storage quota",
  buttons: [
    {
      type: "primary",
      text: "Confirm",
      click: () => submitSpaceForm(),
    },
  ],
});

const formData = ref({});
const formDataRef = ref();

const spaceRules = {
  newSpace: [
    { required: true, message: "Please enter a quota change (MB)." },
    {
      validator: (rule, value, callback) => {
        if (value === "" || value === null || value === undefined) {
          callback(new Error("Please enter a quota change (MB)."));
          return;
        }
        if (!Number.isInteger(value)) {
          callback(new Error("Please enter an integer (MB)."));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
};

const updateSpace = (row) => {
  dialogConfig.value.show = true;
  nextTick(() => {
    formDataRef.value?.resetFields?.();
    formData.value = { ...row, newSpace: 0 };
  });
};

const submitSpaceForm = () => {
  formDataRef.value.validate(async (valid) => {
    if (!valid) return;

    const result = await proxy.Request({
      url: api.addUserTotalSpace,
      method: "post",
      params: {
        userId: formData.value.userId,
        newSpace: formData.value.newSpace,
      },
    });
    if (!result) return;

    dialogConfig.value.show = false;
    proxy.Message.success("Updated successfully.");

    if (formData.value.userId === userStore.userId) {
      emit("reload");
    }
    loadDataList();
  });
};

const handleSearchClear = () => {
  resetSearch();
};

// ====== 清库 ======
const clearDialog = ref({
  show: false,
  title: "Permanently delete all files",
  target: null,
  confirmText: "",
  buttons: [
    {
      type: "danger",
      text: "Confirm deletion",
      click: async () => submitClearFiles(),
    },
  ],
});

const openClearFilesDialog = (row) => {
  clearDialog.value.target = row;
  clearDialog.value.confirmText = "";
  clearDialog.value.show = true;
};

const closeClearDialog = () => {
  clearDialog.value.show = false;
  clearDialog.value.target = null;
  clearDialog.value.confirmText = "";
};

const submitClearFiles = async () => {
  if (!clearDialog.value.target) return;

  if (clearDialog.value.confirmText !== "DELETE") {
    proxy.Message.warning('Type "DELETE" to confirm.');
    return;
  }

  proxy.Confirm(
    `Final confirmation: Permanently delete all files for "${nickname}"? This cannot be undone.`,
    async () => {
      const result = await proxy.Request({
        url: api.clearUserFiles,
        method: "post",
        params: {
          userId: clearDialog.value.target.userId,
        },
      });
      if (!result) return;

      proxy.Message.success("Deleted successfully.");
      const isSelf = clearDialog.value.target.userId === userStore.userId;
      closeClearDialog();

      if (isSelf) emit("reload");
      loadDataList();
    }
  );
};
const getAvatarTs = (uid) => {
  if (!uid) return 0;
  return Number(localStorage.getItem(`avatar_ts_${uid}`)) || 0;
};

</script>

<style lang="scss" scoped>
.top-panel {
  margin-top: 10px;
}

.avatar {
  width: 50px;
  height: 50px;
  border-radius: 20px;
  overflow: hidden;
  img {
    width: 100%;
    height: 100;
  }
}

.admin-icon {
  margin-left: 4px;
  color: #e6a23c;
  font-size: 14px;
  vertical-align: middle;
}

.space-input {
  display: flex;
  align-items: center;
  gap: 10px;
}
.unit {
  color: #909399;
}
.tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.danger-box {
  padding: 8px 4px;
}
.danger-title {
  font-weight: 700;
  margin-bottom: 10px;
  color: #f56c6c;
}
.danger-text {
  margin: 6px 0;
  line-height: 1.6;
}

/* 操作按钮：默认字号 + 有边框 + 不换行 */
.op-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end; // 👈 靠右
  gap: 6px;
  white-space: nowrap;
  width: 100%; // 👈 撑满单元格
}
:deep(.op-actions .el-button + .el-button) {
  margin-left: 0 !important;
}

/* 让 plain 按钮更像“轻量按钮”，但不是 link 小字 */
:deep(.op-btn.el-button) {
  padding: 6px 10px;
  border-width: 1px;
  border-style: solid;
  font-size: 14px;
  line-height: 1;
}

/* 可选：减少按下去的突兀感 */
:deep(.op-btn.el-button:active) {
  transform: none;
}

.space-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.space-used {
  font-size: 13px;
  color: #303133;
}

.space-total {
  font-size: 12px;
  color: #909399;
}

.time-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.time-cell .date {
  font-size: 13px;
  color: #303133;
}

.time-cell .clock {
  font-size: 12px;
  color: #909399;
}

.nickname-cell {
  display: flex;
  flex-direction: column;
}

.nickname-main {
  font-size: 15px; // 👈 比默认大一档
  font-weight: 500; // 👈 半粗
  color: #303133;
  line-height: 1.4;
}

.email-cell {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  word-break: break-all;
}
</style>
