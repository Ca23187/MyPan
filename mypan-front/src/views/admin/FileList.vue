<template>
  <!-- 设置->用户文件 -->
  <div>
    <div class="top">
      <!-- 第一行：操作按钮 -->
      <div class="top-op">
        <div class="op-left">
          <el-button
            type="primary"
            :disabled="selectFileIdList.length === 0"
            @click="downloadBatch"
          >
            <span class="iconfont icon-download"></span>
            &nbsp;Download Selected
          </el-button>

          <el-button
            type="danger"
            :disabled="selectFileIdList.length === 0"
            @click="delFileBatch"
          >
            <span class="iconfont icon-del"></span>
            &nbsp;Delete Selected
          </el-button>
        </div>
      </div>

      <!-- 第二行：搜索 -->
      <div class="top-panel">
        <el-form :model="searchFormData" label-width="80px" @submit.prevent>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="File name">
                <el-input
                  clearable
                  placeholder="Supports fuzzy search"
                  v-model.trim="searchFormData.fileNameFuzzy"
                  @keyup.enter="loadDataList"
                  :suffix-icon="Search"
                  @clear="loadDataList"
                />
              </el-form-item>
            </el-col>

            <el-col :span="8">
              <el-form-item label="Uploader">
                <el-input
                  clearable
                  placeholder="Supports fuzzy search"
                  v-model.trim="searchFormData.nicknameFuzzy"
                  @keyup.enter="loadDataList"
                  :suffix-icon="Search"
                  @clear="loadDataList"
                />
              </el-form-item>
            </el-col>

            <el-col :span="8">
              <el-form-item label=" " class="op-form-item">
                <div class="op-row">
                  <el-button type="primary" @click="loadDataList"
                    >Search</el-button
                  >
                  <el-button @click="resetSearch">Reset</el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- 导航 -->
      <Navigation ref="navigationRef" @navChange="navChange" />
    </div>

    <!-- 文件列表 -->
    <div class="file-list" v-if="tableData.list && tableData.list.length > 0">
      <Table
        :columns="columns"
        :showPagination="true"
        :dataSource="tableData"
        :fetch="loadDataList"
        :initFetch="false"
        :options="tableOptions"
        @rowSelected="rowSelected"
      >
        <!-- 文件名 -->
        <template #fileName="{ index, row }">
          <!-- showOp(row) 当鼠标放在当前行时,分享下载等图标出现 -->
          <!-- cancelShowOp(row) 当鼠标离开当前行时,分享下载等图标消失 -->
          <div
            class="file-item"
            @mouseenter="showOp(row)"
            @mouseleave="cancelShowOp(row)"
          >
            <!-- 显示文件图标 -->
            <template
              v-if="(row.fileType == 3 || row.fileType == 1 || (row.fileType == 2 && row.fileCover)) && row.status == 2"
            >
              <!-- 如果文件类型是图片或者视频,且已经成功转码,则执行 Icon中的cover -->
              <Icon :cover="row.fileCover" :width="32"></Icon>
            </template>
            <template v-else>
              <!-- 如果文件夹类型是文件,则文件类型是该文件类型 -->
              <Icon v-if="row.folderType == 0" :fileType="row.fileType"></Icon>
              <!-- 如果文件夹类型是目录,则文件类型就是目录0 -->
              <Icon v-if="row.folderType == 1" :fileType="0"></Icon>
            </template>

            <!-- 显示文件名称 -->
            <!-- v-if="!row.showEdit" 如果该行文件没有编辑 -->
            <span class="file-name clickable" v-if="!row.showEdit" :title="row.fileName">
              <span @click="preview(row)">{{ row.fileName }}</span>
              <span v-if="row.status == 0" class="transfer-status">Transcoding…</span>
              <span v-if="row.status == 1" class="transfer-status transfer-fail"
                >Transcode failed</span
              >
            </span>

            <!-- 点击新建文件夹时显示行 -->
            <div class="edit-panel" v-if="row.showEdit">
              <el-input
                v-model.trim="row.fileNameReal"
                ref="editNameRef"
                :maxLength="190"
                @keyup.enter="saveNameEdit(index)"
              >
                <template #suffix>{{ row.fileSuffix }}</template>
              </el-input>

              <!-- 对号 确定 -->
              <span
                :class="[
                  'iconfont icon-right1',
                  row.fileNameReal ? '' : 'not-allow',
                ]"
                @click="saveNameEdit(index)"
              ></span>

              <!-- 叉号 取消 -->
              <span
                class="iconfont icon-error"
                @click="cancelNameEdit(index)"
              ></span>
            </div>

            <!-- 当鼠标放在当前行时显示 -->
            <span class="op">
              <template v-if="row.showOp && row.fileId && row.status == 2">
                <!-- 只有当是文件夹时才可下载 -->
                <span
                  class="iconfont icon-download"
                  v-if="row.folderType == 0"
                  @click="download(row)"
                >
                  Download
                </span>
                <span class="iconfont icon-del" @click="delFile(row)">
                  Delete
                </span>
              </template>
            </span>
          </div>
        </template>

        <!-- 文件大小 -->
        <template #fileSize="{ index, row }">
          <span v-if="row.fileSize">
            {{ proxy.Utils.size2Str(row.fileSize) }}</span
          >
        </template>
      </Table>
    </div>
    <Preview ref="previewRef"></Preview>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, nextTick, computed } from "vue";

const searchFormData = ref({
  fileNameFuzzy: "",
  nicknameFuzzy: "",
});

const resetSearch = () => {
  searchFormData.value.fileNameFuzzy = "";
  searchFormData.value.nicknameFuzzy = "";
  tableData.value.pageNo = 1;
  loadDataList();
};

import { Search } from "@element-plus/icons-vue";
import { onMounted } from "vue";

onMounted(() => {
  currentFolder.value = { fileId: 0 };
  loadDataList();
});

const { proxy } = getCurrentInstance();

const api = {
  loadDataList: "/admin/loadFileList",
  delFile: "/admin/delFile",
  createDownloadUrl: "/admin/createDownloadUrl",
  download: "/api/admin/download",
};

// 列表头信息
const columns = [
  {
    label: "File name",
    prop: "fileName",
    scopedSlots: "fileName",
  },
  {
    label: "Uploader",
    prop: "nickname",
    width: 200,
  },
  {
    label: "Last modified",
    prop: "lastModifiedAt",
    width: 180,
  },
  {
    label: "Size",
    prop: "fileSize",
    scopedSlots: "fileSize",
    width: 160,
  },
];

// 数据源
const tableData = ref({
  list: [],
  pageNo: 1,
  pageSize: 15,
  totalCount: 0,
});

// 表格选项
const tableOptions = {
  extHeight: 50,
  selectType: "checkbox",
};
const showLoading = ref(true);

// 当前文件夹
const currentFolder = ref({ fileId: 0 });
const navigationRef = ref(null);
const previewRef = ref(null);

// 获得数据;
const loadDataList = async () => {
  let params = {
    // 页码
    pageNo: tableData.value.pageNo,
    // 分页大小
    pageSize: tableData.value.pageSize,
    // 文件名（模糊）
    fileNameFuzzy: searchFormData.value.fileNameFuzzy,
    nicknameFuzzy: searchFormData.value.nicknameFuzzy,
    // 文件父id
    filePid: currentFolder.value.fileId,
  };
  let result = await proxy.Request({
    url: api.loadDataList,
    method: "get",
    showLoading: showLoading,
    params,
  });
  if (!result) {
    return;
  }
  tableData.value = result.data;
};

// 当鼠标放在当前行时,分享下载等图标出现
const showOp = (row) => {
  // 关闭所有的显示
  tableData.value.list.forEach((element) => {
    element.showOp = false;
  });
  // 只开启当前显示
  row.showOp = true;
};

const cancelShowOp = (row) => {
  row.showOp = false;
};

// 行选中
// 多选 批量选中
const selectFileIdList = ref([]);
const selectUserId = ref("");
const selectedUserIdByFileId = ref({});


const rowSelected = (rows) => {
  selectFileIdList.value = [];
  selectedUserIdByFileId.value = {}; // 每次重新选择就重建

  (rows || []).forEach((item) => {
    if (!item?.fileId) return;

    selectFileIdList.value.push(item.fileId); // ✅ 只存 fileId（下载继续可用）
    selectedUserIdByFileId.value[item.fileId] = item.userId; // ✅ 记录 fileId -> userId
  });

  selectUserId.value = rows?.[0]?.userId || "";
};


// const downloadByBlob = async (downloadToken, filename = "download.zip") => {
//   const blob = await proxy.Request({
//     url: api.download + "/" + downloadToken,
//     method: "get",
//     responseType: "blob",
//     showLoading: true,
//     showError: true,
//   });
//   if (!blob) return;

//   const url = window.URL.createObjectURL(blob);
//   const a = document.createElement("a");
//   a.href = url;
//   a.download = filename;
//   document.body.appendChild(a);
//   a.click();
//   a.remove();
//   window.URL.revokeObjectURL(url);
// };

// const downloadBatch = async () => {
//   if (selectFileIdList.value.length === 0) {
//     proxy.Message.warning("Please select files first.");
//     return;
//   }

//   const result = await proxy.Request({
//     url: `${api.createDownloadUrl}/${selectUserId.value}`,
//     method: "post",
//     params: { fileIds: selectFileIdList.value.join(",") },
//   });
//   if (!result) return;

//   await downloadByBlob(result.data, "download.zip");
// };
const downloadByNavigate = (downloadToken) => {
  const url = api.download + "/" + downloadToken;
  window.location.href = url;
};
const downloadBatch = async () => {
  if (selectFileIdList.value.length === 0) {
    proxy.Message.warning("Please select files first.");
    return;
  }

  const result = await proxy.Request({
    url: `${api.createDownloadUrl}/${selectUserId.value}`,
    method: "post",
    params: { fileIds: selectFileIdList.value.join(",") },
  });
  if (!result) return;

  downloadByNavigate(result.data);
};


// 删除单个文件
const delFile = (row) => {
  proxy.Confirm(
    `Are you sure you want to delete "${row.fileName}"?`,
    async () => {
      let result = await proxy.Request({
        url: api.delFile,
        params: {
          fileIdAndUserIds: row.userId + "_" + row.fileId,
        },
      });
      if (!result) {
        return;
      }
      proxy.Message.success("Deleted successfully.");
      // 重新获取数据
      loadDataList();
    }
  );
};

// 批量删除文件
const delFileBatch = () => {
  if (selectFileIdList.value.length === 0) return;

  proxy.Confirm(`Are you sure you want to delete the selected files?`, async () => {
    // ✅ 在这里拼 userId_fileId
    const tokens = selectFileIdList.value
      .map((fileId) => {
        const userId = selectedUserIdByFileId.value[fileId];
        return userId ? `${userId}_${fileId}` : null;
      })
      .filter(Boolean);

    if (tokens.length === 0) {
      proxy.Message.warning("Unable to batch delete because userId is missing for some selected files.");
      return;
    }

    const result = await proxy.Request({
      url: api.delFile,
      params: {
        fileIdAndUserIds: tokens.join(","),
      },
    });

    if (!result) return;

    proxy.Message.success("Deleted successfully.");
    loadDataList();
  });
};


// 预览
const preview = (data) => {
  // 如果是目录(文件夹)
  if (data.folderType == 1) {
    navigationRef.value.openFolder(data);
    return;
  }
  if (data.status != 2) {
    proxy.Message.warning("This file hasn't finished transcoding and can't be previewed.");
    return;
  }
  previewRef.value.showPreview(data, 1);
};

// 目录
const navChange = (data) => {
  const { curFolder } = data;
  currentFolder.value = curFolder;
  tableData.value.pageNo = 1;
  showLoading.value = true;
  loadDataList();
};

// 下载文件
// const download = async (row) => {
//   const result = await proxy.Request({
//     url: `${api.createDownloadUrl}/${row.userId}`,
//     method: "post",
//     params: { fileIds: row.fileId },
//   });
//   if (!result) return;

//   await downloadByBlob(result.data, row.fileName || "download");
// };
const download = async (row) => {
  const result = await proxy.Request({
    url: `${api.createDownloadUrl}/${row.userId}`,
    method: "post",
    params: { fileIds: row.fileId },
  });
  if (!result) return;

  downloadByNavigate(result.data);
};
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
.top-panel {
  margin-top: 10px;
}
.search-panel {
  margin-left: 0px !important;
}

.op-form-item :deep(.el-form-item__content) {
  display: flex;
}

.op-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.op-row :deep(.el-button) {
  padding: 6px 10px; // 让按钮更紧凑
}
</style>