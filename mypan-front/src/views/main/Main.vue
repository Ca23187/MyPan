<template>
  <div
    class="main-drop-zone"
    @dragover.prevent
    @dragenter.prevent="handleDragEnter"
    @dragleave.prevent="handleDragLeave"
    @drop.prevent="handleDropUpload"
  >
    <div v-if="dragging" class="drop-mask">Release to upload files</div>
    <div class="top">
      <!-- 头部按钮处 -->
      <div class="top-op">
        <!-- 左侧：通用操作 -->
        <div class="op-left">
          <el-upload
            :show-file-list="false"
            :with-credentials="true"
            :multiple="true"
            :http-request="addFile"
            :accept="fileAccept"
          >
            <el-button type="primary">
              <span class="iconfont icon-upload"></span>
              &nbsp;Upload
            </el-button>
          </el-upload>

          <el-button type="success" @click="newFolder" v-if="category == 'all'">
            <span class="iconfont icon-folder-add"></span>
            &nbsp;New Folder
          </el-button>

          <el-button
            type="primary"
            @click="downloadBatch"
            :disabled="selectFileIdList.length == 0"
          >
            <span class="iconfont icon-download"></span>
            &nbsp;Download Selected
          </el-button>

          <el-button
            @click="delFileBatch"
            type="danger"
            :disabled="selectFileIdList.length == 0"
          >
            <span class="iconfont icon-del"></span>
            &nbsp;Delete Selected
          </el-button>

          <el-button
            @click="moveFolderBatch"
            type="warning"
            :disabled="selectFileIdList.length == 0"
          >
            <span class="iconfont icon-move"></span>
            &nbsp;Move Selected
          </el-button>
        </div>
      </div>
    </div>

    <!-- 第二行：搜索区（下一行） -->
    <div class="top-search-row">
      <!-- 非 search 页 -->
      <div class="search-panel" v-if="!isSearch">
        <el-input
          clearable
          placeholder="Search by file name"
          v-model="fileNameFuzzy"
          style="width: 300px"
          @keyup.enter="doSearch"
          @clear="handleClear"
          :suffix-icon="Search"
        />

        <el-tooltip
          content="Search within this folder and its subfolders"
          placement="bottom"
          v-if="isHome"
        >
          <el-checkbox
            v-model="recursive"
            :disabled="String(currentFolder.fileId ?? 0) === '0'"
          >
            Search subfolders
          </el-checkbox>
        </el-tooltip>

        <el-select
          v-model="folderType"
          clearable
          placeholder="--File type--"
          style="width: 130px"
        >
          <el-option label="All" value="" />
          <el-option label="Files only" :value="0" />
          <el-option label="Folders only" :value="1" />
        </el-select>

        <div class="btn-group">
          <el-button type="primary" @click="doSearch">Search</el-button>
          <el-button @click="resetFilters">Reset</el-button>
          <!-- ✅ 回到搜索结果 -->
          <el-button v-if="showBackToSearch" @click="backToSearch">
            Back to results
          </el-button>
        </div>
      </div>

      <!-- search 页 -->
      <div class="search-bar" v-else>
        <el-input
          clearable
          placeholder="Search across all files"
          v-model="searchKeyword"
          style="width: 300px"
          @keyup.enter="doSearch"
          @clear="handleClear"
          :suffix-icon="Search"
        />

        <el-select
          v-model="folderType"
          clearable
          placeholder="--File type--"
          style="width: 130px"
        >
          <el-option label="All" value="" />
          <el-option label="Files only" :value="0" />
          <el-option label="Folders only" :value="1" />
        </el-select>

        <div class="btn-group">
          <el-button type="primary" @click="doSearch">Search</el-button>
          <el-button @click="resetFilters">Reset</el-button>
        </div>
      </div>
    </div>
    <!-- Navigation 继续保留（方案1） -->
    <Navigation ref="navigationRef" @navChange="navChange"></Navigation>

    <!-- 文件列表 -->
    <div class="file-list" v-if="tableData.list && tableData.list.length > 0">
      <Table
        ref="dataTableRef"
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
              v-if="
                (row.fileType == 3 ||
                  row.fileType == 1 ||
                  (row.fileType == 2 && row.fileCover)) &&
                row.status == 2
              "
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
            <span
              class="file-name clickable"
              v-if="!row.showEdit"
              :title="row.fileName"
            >
              <span @click="preview(row)">{{ row.fileName }}</span>
              <span v-if="row.status == 0" class="transfer-status"
                >Transcoding</span
              >
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
                <span class="iconfont icon-share1" @click="share(row)">
                  Share
                </span>
                <span class="iconfont icon-download" @click="download(row)">
                  Download
                </span>
                <span class="iconfont icon-edit" @click="editFileName(index)">
                  Rename
                </span>
                <span class="iconfont icon-move" @click="moveFolder(row)">
                  Move
                </span>
                <span class="iconfont icon-del danger" @click="delFile(row)">
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
    <!-- ✅ search 页：未点击搜索时，不显示“上传文件”那套 -->
    <div class="no-data" v-else-if="isSearch && !searchTriggered">
      <div class="no-data-inner">
        <Icon iconName="no_data" :width="100" fit="fill"></Icon>
        <div class="tips">Enter a keyword and click “Search”.</div>
      </div>
    </div>

    <!-- ✅ search 页：已点击搜索但无结果 -->
    <div class="no-data" v-else-if="isSearch && searchTriggered">
      <div class="no-data-inner">
        <Icon iconName="no_data" :width="120" fit="fill"></Icon>
        <div class="tips">No matching files or folders found.</div>
        <div class="sub-tips">
          Try a different keyword, or clear filters and try again.
        </div>
      </div>
    </div>

    <!-- ✅ 其他页面：维持原来的空目录提示 -->
    <div class="no-data" v-else>
      <div class="no-data-inner">
        <Icon iconName="no_data" :width="120" fit="fill"></Icon>
        <div class="tips">This folder is empty. Upload your first file.</div>
        <div class="sub-tips">
          Supports drag & drop for single or multiple files. Folder upload is
          not supported.
        </div>
        <div class="op-list">
          <el-upload
            :show-file-list="false"
            :with-credentials="true"
            :multiple="true"
            :http-request="addFile"
            :accept="fileAccept"
          >
            <div class="op-item">
              <Icon iconName="file" :width="60"></Icon>
              <div>Upload File</div>
            </div>
          </el-upload>
          <div class="op-item" v-if="category == 'all'" @click="newFolder">
            <Icon iconName="folder" :width="60"></Icon>
            <div>New Folder</div>
          </div>
        </div>
      </div>
    </div>

    <FolderSelect
      ref="folderSelectRef"
      @folderSelect="moveFolderDone"
    ></FolderSelect>

    <!-- 预览 -->
    <Preview ref="previewRef"></Preview>

    <!-- 分享 -->
    <ShareFile ref="shareRef"></ShareFile>
  </div>
</template>

<script setup>
import CategoryInfo from "@/js/CategoryInfo.js";
import ShareFile from "./ShareFile.vue";
import { Search } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/user";
import {
  ref,
  reactive,
  getCurrentInstance,
  nextTick,
  computed,
  watch,
  onMounted,
  onUnmounted,
} from "vue";
import { useRouter, useRoute } from "vue-router";
import { gotoLogin } from "@/utils/Auth";

// ✅ 记住从 search 进入 all 前的搜索条件（用于返回）
const lastSearchSnapshot = ref(null);

// ✅ 当前 all 页是否是“从 search 点文件夹跳转而来”
const fromSearchToAll = ref(false);

// ✅ all 页显示“回到搜索结果”按钮的条件：必须在 all 且来自 search
const showBackToSearch = computed(() => isHome.value && fromSearchToAll.value);

const uploaderRef = ref(null);

// 维护一个“文件索引”：fileId -> row（让更新是 O(1)）
const fileIndex = new Map();
const rebuildFileIndex = () => {
  fileIndex.clear();
  (tableData.value.list || []).forEach((row) => {
    if (row?.fileId != null) {
      fileIndex.set(String(row.fileId), row);
    }
  });
};

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

// 是否已在 search 页点击过“搜索”（避免一进来就查）
const searchTriggered = ref(false);

// 实现上传文件的请求
// 将Main子组件页面的数据传递给Framwork父组件
const emit = defineEmits(["addFile"]);
const addFile = (fileData) => {
  emit("addFile", { file: fileData.file, filePid: currentFolder.value.fileId });
};

// 添加文件回调
const reload = () => {
  showLoading.value = false;
  loadDataList();
};

const api = {
  loadDataList: "/file/loadDataList",
  rename: "/file/rename",
  newFolder: "/file/newFolder",
  getFolderInfo: "/file/getFolderInfo",
  delFile: "/file/delFile",
  changeFileFolder: "/file/changeFileFolder",
  createDownloadUrl: "/file/createDownloadUrl",
  download: "/api/file/download",
  getFolderBreadcrumb: "/file/getFolderBreadcrumb",
};

// 实现文件选择
const fileAccept = computed(() => {
  const categoryItem = CategoryInfo[category.value];
  return categoryItem ? categoryItem.accept : "*";
});

// 列表头信息
const columns = [
  {
    label: "Name",
    prop: "fileName",
    scopedSlots: "fileName",
  },
  {
    label: "Last Modified",
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
const tableData = ref({});
// 表格选项
const tableOptions = {
  extHeight: 240,
  selectType: "checkbox",
}
// 转码轮询相关
const pollingTimer = ref(null);

// 文件名
const fileNameFuzzy = ref("");

const showLoading = ref(true);
// 分类
const category = ref();
// 当前文件夹
const currentFolder = ref({ fileId: 0 });

// 主页(all)的目录递归开关
const recursive = ref(false);

// 下拉：null=全部 / 0=仅文件 / 1=仅文件夹
const folderType = ref(""); // 默认不选

// search 页专用关键字（与你主页的 fileNameFuzzy 分开，避免互相污染）
const searchKeyword = ref("");

const isHome = computed(() => category.value === "all");
const isSearch = computed(() => category.value === "search");
const isCategoryPage = computed(() => !isHome.value && !isSearch.value);

// 获得数据;
const loadDataList = async () => {
  const cat = CategoryInfo[category.value] || CategoryInfo.all;

  let params = {
    pageNo: tableData.value.pageNo,
    pageSize: tableData.value.pageSize,
  };

  // folderType：只在 0/1 时传
  if (folderType.value === 0 || folderType.value === 1) {
    params.folderType = folderType.value;
  }

  if (isHome.value) {
    // ✅ 主页：目录浏览（当前层/递归）
    const pid = String(currentFolder.value.fileId ?? 0);
    params.filePid = pid;
    params.searchScope = recursive.value && pid !== "0" ? 1 : 0;
    params.fileNameFuzzy = fileNameFuzzy.value;
  } else if (isSearch.value) {
    if (!searchTriggered.value) {
      tableData.value = {
        list: [],
        pageNo: 1,
        pageSize: tableData.value.pageSize || 15,
        totalCount: 0,
      };
      editing.value = false;
      stopTranscodePolling();
      return;
    }
    if (!searchKeyword.value.trim()) {
      tableData.value = {
        list: [],
        pageNo: 1,
        pageSize: tableData.value.pageSize || 15,
        totalCount: 0,
      };
      editing.value = false;
      stopTranscodePolling();
      return;
    }

    params.searchScope = 2;
    params.fileNameFuzzy = searchKeyword.value;
  } else {
    // ✅ 类别页：全盘 + fileCategory
    params.searchScope = 2;
    if (cat.fileCategory) params.fileCategory = cat.fileCategory;
    params.fileNameFuzzy = fileNameFuzzy.value; // 类别页也允许按名搜（可选）
  }

  let result = await proxy.Request({
    url: api.loadDataList,
    method: "get",
    showLoading: showLoading,
    params,
  });
  if (!result) return;

  tableData.value = result.data;
  rebuildFileIndex();
  startTranscodePolling();
  editing.value = false;
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

// 编辑行(新建文件夹时编辑行)
// 当前编辑行状态
const editing = ref(false);
// 新建文件夹行内填充的内容绑定
const editNameRef = ref();

const doSearch = () => {
  // ✅ search 页：关键词不能为空
  if (isSearch.value && !searchKeyword.value.trim()) {
    proxy.Message.warning("Please enter a search keyword.");
    return;
  }

  showLoading.value = true;
  tableData.value.pageNo = 1;

  if (isSearch.value) {
    searchTriggered.value = true;
  }

  loadDataList();
};

const resetFilters = () => {
  showLoading.value = true;
  tableData.value.pageNo = 1;

  folderType.value = null;

  if (isSearch.value) {
    searchKeyword.value = "";
    searchTriggered.value = false; // ✅ 重置后也不要自动查
  } else {
    fileNameFuzzy.value = "";
    if (isHome.value) recursive.value = false;
  }

  loadDataList();
};

// 新建文件夹
const newFolder = () => {
  // 如果当前编辑行存在,则再次点击新建文件夹按钮时不起作用
  if (editing.value) {
    return;
  }
  // 让其他行都不允许编辑
  tableData.value.list.forEach((element) => {
    element.showEdit = false;
  });
  editing.value = true;
  tableData.value.list.unshift({
    showEdit: true,
    fileType: 0,
    fileId: "",
    filePid: currentFolder.value.fileId,
  });
  nextTick(() => {
    editNameRef.value.focus();
  });
};

// 取消新建文件夹操作
const cancelNameEdit = (index) => {
  const fileData = tableData.value.list[index];
  // 如果存在这个文件的话,说明此处是重命名操作,那么可以直接将编辑行关闭
  if (fileData.fileId) {
    fileData.showEdit = false;
  } else {
    // 如果不存在的话,那么直接将此行删除
    tableData.value.list.splice(index, 1);
  }
  // 当前编辑行状态为:未编辑
  editing.value = false;
};

// 确定新建文件夹操作
const saveNameEdit = async (index) => {
  const { fileId, filePid, fileNameReal } = tableData.value.list[index];
  if (fileNameReal == "" || fileNameReal.indexOf("/") != -1) {
    proxy.Message.warning('File name cannot be empty and cannot contain "/".');
    return;
  }
  // 重命名
  let url = api.rename;
  if (fileId == "") {
    // 当文件ID不存在时,新建目录
    url = api.newFolder;
  }
  let result = await proxy.Request({
    url: url,
    params: {
      fileId,
      filePid: filePid,
      fileName: fileNameReal,
    },
  });
  if (!result) {
    return;
  }
  tableData.value.list[index] = result.data;
  editing.value = false;
};

// 重命名 编辑文件名
const editFileName = (index) => {
  // 如果现在有新建文件夹的编辑行,那么先将其删除,并且将序号减一
  if (tableData.value.list[0].fileId == "") {
    tableData.value.list.splice(0, 1);
    index = index - 1;
  }
  tableData.value.list.forEach((element) => {
    element.showEdit = false;
  });
  let cureentData = tableData.value.list[index];
  cureentData.showEdit = true;

  //编辑文件
  if (cureentData.folderType == 0) {
    cureentData.fileNameReal = cureentData.fileName.substring(
      0,
      cureentData.fileName.indexOf(".")
    );
    cureentData.fileSuffix = cureentData.fileName.substring(
      cureentData.fileName.indexOf(".")
    );
  } else {
    cureentData.fileNameReal = cureentData.fileName;
    cureentData.fileSuffix = "";
  }

  // 当前编辑行状态为true
  editing.value = true;
  nextTick(() => {
    editNameRef.value.focus();
  });
};

// 行选中
// 多选 批量选中
const selectFileIdList = ref([]);
const rowSelected = (rows) => {
  selectFileIdList.value = [];
  rows.forEach((item) => {
    selectFileIdList.value.push(item.fileId);
  });
};

// 删除单个文件
const delFile = (row) => {
  proxy.Confirm(
    `Are you sure you want to delete "${row.fileName}"? You can restore it from Recycle Bin within 10 days.`,
    async () => {
      let result = await proxy.Request({
        url: api.delFile,
        params: {
          fileIds: row.fileId,
        },
      });
      if (!result) {
        return;
      }
      // 重新获取数据
      loadDataList();
    }
  );
};

// 批量删除文件
const delFileBatch = () => {
  if (selectFileIdList.value.length == 0) {
    return;
  }
  proxy.Confirm(
    `Are you sure you want to delete these items? You can restore them from Recycle Bin within 10 days.`,
    async () => {
      let result = await proxy.Request({
        url: api.delFile,
        params: {
          fileIds: selectFileIdList.value.join(","),
        },
      });
      if (!result) {
        return;
      }
      // 重新获取数据
      loadDataList();
    }
  );
};

// 移动目录
const folderSelectRef = ref();
// 当前要移动的文件（单个文件）
const currentMoveFile = ref({});

// 单个移动
const moveFolder = (data) => {
  currentMoveFile.value = data;

  // 只传当前要移动的文件/文件夹的 fileId 列表
  folderSelectRef.value.showFolderDialog([data.fileId]);
};

// 批量移动
const moveFolderBatch = () => {
  currentMoveFile.value = {};

  // 批量：selectFileIdList 里本来就是一堆 fileId
  folderSelectRef.value.showFolderDialog(selectFileIdList.value);
};

// 移动文件操作
const moveFolderDone = async (folderId) => {
  // 如果要移动到当前目录，提醒无需移动
  if (
    currentMoveFile.value.filePid == folderId ||
    currentFolder.value.fileId == folderId
  ) {
    proxy.Message.warning("This item is already in the current folder.");
    return;
  }
  let filedIdsArray = [];
  // 如果是单个文件移动
  if (currentMoveFile.value.fileId) {
    filedIdsArray.push(currentMoveFile.value.fileId);
  } else {
    // 如果是多个文件移动
    // concat 连接多个数组
    // selectFileIdList 是指批量选择时选择的文件ID
    filedIdsArray = filedIdsArray.concat(selectFileIdList.value);
  }
  let result = await proxy.Request({
    url: api.changeFileFolder,
    params: {
      fileIds: filedIdsArray.join(","),
      filePid: folderId,
    },
  });
  if (!result) {
    return;
  }
  // 调用子组件暴露的close方法，实现当前弹出框页面的关闭
  folderSelectRef.value.close();
  // 更新当前文件列表
  loadDataList();
};

// 绑定导航栏
const navigationRef = ref();

// 预览
const previewRef = ref();
const preview = (data) => {
  // 如果是目录(文件夹)
  if (data.folderType == 1) {
    if (category.value === "search") {
      openFolderFromSearch(data);
      return;
    }
    navigationRef.value.openFolder(data);
    return;
  }

  if (data.status != 2) {
    proxy.Message.warning("This file is not ready for preview yet.");
    return;
  }
  previewRef.value.showPreview(data, 0);
};

// 目录
const navChange = (data) => {
  const { curFolder, categoryId } = data;

  // ✅ 用户主动导航离开“从搜索进入的 all”语境，就隐藏返回按钮
  if (categoryId !== "all") {
    fromSearchToAll.value = false;
  }

  currentFolder.value = curFolder;
  showLoading.value = true;
  category.value = categoryId;
  loadDataList();
};

const downloadByNavigate = (downloadToken) => {
  const url = api.download + "/" + downloadToken;
  // 用 location 最稳，不容易被浏览器拦截
  window.location.href = url;

  // 如果你更想新窗口/新标签（可能被拦截弹窗）：
  // window.open(url, "_blank");
};

const download = async (row) => {
  const result = await proxy.Request({
    url: api.createDownloadUrl,
    params: { fileIds: row.fileId },
  });
  if (!result) return;

  downloadByNavigate(result.data);
};

const downloadBatch = async () => {
  if (selectFileIdList.value.length === 0) {
    proxy.Message.warning("Please select files or folders first.");
    return;
  }

  const result = await proxy.Request({
    url: api.createDownloadUrl,
    params: { fileIds: selectFileIdList.value.join(",") },
  });
  if (!result) return;

  downloadByNavigate(result.data);
};

// 分享文件
// 利用ShareFile组件暴露出的show函数，实现将Main组件中的函数传递给ShareFile组件
const shareRef = ref();
const share = (row) => {
  shareRef.value.show(row);
};

watch(
  () => route.params.category,
  (newCategory) => {
    if (!newCategory) return;

    // ✅ 如果用户离开 all，也清掉标记
    if (newCategory !== "all") {
      fromSearchToAll.value = false;
    }

    category.value = newCategory;

    // 切页重置一些状态，避免串页
    folderType.value = null;
    recursive.value = false;
    fileNameFuzzy.value = "";
    searchKeyword.value = "";

    // ✅ 关键：进入 search 页不立刻查
    if (newCategory === "search") {
      stopTranscodePolling();

      searchTriggered.value = false;
      showLoading.value = false;

      // 可选：清空列表（避免残留上一次搜索结果）
      tableData.value = {
        list: [],
        pageNo: 1,
        pageSize: tableData.value.pageSize || 15,
        totalCount: 0,
      };
      return;
    }

    // currentFolder 保持不变（主页目录依赖）
    showLoading.value = true;
    loadDataList();
  },
  { immediate: true }
);

const openFolderFromSearch = async (folderRow) => {
  // ✅ 进入 all 之前先保存 search 快照（关键词 + folderType）
  lastSearchSnapshot.value = {
    keyword: searchKeyword.value,
    folderType: folderType.value,
  };
  fromSearchToAll.value = true;

  // 1) 切回 all
  if (route.params.category !== "all") {
    await router.push("/main/all");
  }

  // 2) 拉取面包屑信息（依赖你后端实现）
  const res = await proxy.Request({
    url: api.getFolderBreadcrumb,
    method: "get",
    params: { fileId: folderRow.fileId },
    showLoading: true,
  });
  if (!res) return;

  navigationRef.value?.setBreadcrumb?.(res.data);

  const curFolder = res.data?.[res.data.length - 1] || folderRow;

  currentFolder.value = curFolder;
  category.value = "all";

  // ✅ 进入 all 后，保持 all 的搜索输入为空（避免污染）
  recursive.value = false;
  fileNameFuzzy.value = "";
  searchKeyword.value = "";
  tableData.value.pageNo = 1;
  loadDataList();
};

const backToSearch = async () => {
  if (!lastSearchSnapshot.value) {
    // 兜底：没快照就直接回 search
    await router.push("/main/search");
    return;
  }

  await router.push("/main/search");

  // ✅ 恢复搜索条件并自动出结果
  searchKeyword.value = lastSearchSnapshot.value.keyword || "";
  folderType.value = lastSearchSnapshot.value.folderType ?? null;

  searchTriggered.value = true;
  showLoading.value = true;
  tableData.value.pageNo = 1;

  loadDataList();
};

const handleClear = () => {
  resetFilters();
};

const dragging = ref(false);
const dragCounter = ref(0);

const handleDragEnter = () => {
  dragCounter.value++;
  dragging.value = true;
};

const handleDragLeave = () => {
  dragCounter.value--;

  if (dragCounter.value <= 0) {
    dragCounter.value = 0;
    dragging.value = false;
  }
};

const handleDropUpload = (e) => {
  dragging.value = false;
  dragCounter.value = 0;

  const files = Array.from(e.dataTransfer?.files || []);
  if (files.length === 0) return;

  files.forEach((file) => {
    addFile({ file });
  });
};

const hasTranscodingFile = () => {
  return (tableData.value?.list || []).some((row) => Number(row.status) === 0);
};

const stopTranscodePolling = () => {
  if (pollingTimer.value) {
    clearInterval(pollingTimer.value);
    pollingTimer.value = null;
  }
};

const startTranscodePolling = () => {
  if (!hasTranscodingFile()) {
    stopTranscodePolling();
    return;
  }

  if (pollingTimer.value) return;

  pollingTimer.value = setInterval(() => {
    if (!hasTranscodingFile()) {
      stopTranscodePolling();
      return;
    }

    showLoading.value = false;
    loadDataList();
  }, 5000);
};

onMounted(() => {
  window.addEventListener("dragover", preventDefault);
  window.addEventListener("drop", preventDefault);
});

onUnmounted(() => {
  window.removeEventListener("dragover", preventDefault);
  window.removeEventListener("drop", preventDefault);
  stopTranscodePolling();
});

const preventDefault = (e) => {
  e.preventDefault();
};

defineExpose({ reload });
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";

/* ===============================
   顶部工具栏（主页专有）
   =============================== */
.top-op {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;

  /* 小屏换行 */
  flex-wrap: wrap;
  row-gap: 10px;
  column-gap: 12px;
}

/* 左侧按钮区 */
.op-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  row-gap: 10px;
  column-gap: 10px;
}

/* 去掉 element 默认按钮间距（toolbar 区） */
.op-left :deep(.el-button),
.op-left :deep(.el-button + .el-button) {
  margin-left: 0 !important;
}

/* 右侧搜索区（如果你把搜索放右侧） */
.top-search-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex: 1;
  min-width: 260px; /* 防止太窄挤爆 */
}

/* 搜索容器 */
.search-panel,
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: nowrap;
}

/* 按钮组（如果你有一组按钮靠一起） */
.btn-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-group :deep(.el-button + .el-button) {
  margin-left: 0 !important;
}

/* 空状态副文案 */
.sub-tips {
  margin-top: 8px;
  font-size: 14px;
  color: #999;
}

.main-drop-zone {
  min-height: 100%;
}

.drop-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(64, 158, 255, 0.12);
  border: 3px dashed #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
  pointer-events: none;
}
</style>
