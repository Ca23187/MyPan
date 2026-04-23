<template>
  <div>
    <div class="top">
      <el-button
        type="primary"
        :disabled="selectIdList.length == 0"
        @click="cancelShareBatch"
      >
        <span class="iconfont icon-cancel"></span>&nbsp;Stop Sharing
      </el-button>
    </div>

    <div class="file-list">
      <Table
        :columns="columns"
        :showPagination="true"
        :dataSource="tableData"
        :fetch="loadDataList"
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
                (row.fileType == 3 || row.fileType == 1 || (row.fileType == 2 && row.fileCover)) && row.status !== 0
              "
            >
              <!-- 如果文件类型是图片或者视频,且已经成功转码,则执行 Icon中的cover -->
              <Icon :cover="row.fileCover"></Icon>
            </template>
            <template v-else>
              <!-- 如果文件夹类型是文件,则文件类型是该文件类型 -->
              <Icon v-if="row.folderType == 0" :fileType="row.fileType"></Icon>
              <!-- 如果文件夹类型是目录,则文件类型就是目录0 -->
              <Icon v-if="row.folderType == 1" :fileType="0"></Icon>
            </template>

            <!-- 显示文件名称 -->
            <span
              class="file-name"
              v-if="!row.showRename"
              :title="row.fileName"
            >
              <span>{{ row.fileName }}</span>
            </span>

            <!-- 当鼠标放在当前行时显示 -->
            <span class="op">
              <template v-if="row.showOp && row.fileId">
                <span class="iconfont icon-link" @click="copy(row)">
                  Copy Link
                </span>

                <span class="iconfont icon-cancel danger" @click="cancelShare(row)">
                  Stop Sharing
                </span>
              </template>
            </span>
          </div>
        </template>
        <template #expiredAt="{ index, row }">
          {{ row.expireType == 3 ? "Never expires" : row.expiredAt }}
        </template>
      </Table>
    </div>
  </div>
</template>

<script setup>
// 引入实现复制的文件
import useClipboard from "vue-clipboard3";
const { toClipboard } = useClipboard();
import { ref, reactive, getCurrentInstance, watch } from "vue";
const { proxy } = getCurrentInstance();

const api = {
  loadDataList: "/share/loadShareList",
  cancelShare: "/share/cancelShare",
};

// 列表头信息
const columns = [
  {
    label: "Name",
    prop: "fileName",
    scopedSlots: "fileName",
  },
  {
    label: "Shared At",
    prop: "sharedAt",
    width: 180,
  },
  {
    label: "Expiration",
    prop: "expiredAt",
    scopedSlots: "expiredAt",
    width: 180,
  },
  {
    label: "Views",
    prop: "viewCount",
    width: 140,
  },
];

// 数据源
const tableData = ref({});
// 表格选项
const tableOptions = {
  extHeight: 20,
  selectType: "checkbox",
};

// 获得数据;
const loadDataList = async () => {
  let params = {
    // 页码
    pageNo: tableData.value.pageNo,
    // 分页大小
    pageSize: tableData.value.pageSize,
  };
  if (params.category !== "all") {
    delete params.filePid;
  }
  let result = await proxy.Request({
    url: api.loadDataList,
    method: 'get',
    params,
  });
  if (!result) {
    return;
  }
  tableData.value = result.data;
};

// 当鼠标放在当前行时,分享下载等图标出现
// 展示操作
const showOp = (row) => {
  // 关闭所有的显示
  tableData.value.list.forEach((element) => {
    element.showOp = false;
  });
  // 只开启当前显示
  row.showOp = true;
};

// 取消展示
const cancelShowOp = (row) => {
  row.showOp = false;
};

// 文档的当前路径
const shareUrl = ref(document.location.origin + "/share/");
// 复制
const copy = async (data) => {
  await toClipboard(
    `${shareUrl.value}${data.shareId}?code=${data.code}`
  );
  proxy.Message.success("Copied to clipboard");
};

// 行选中
// 多选 批量选中
const selectIdList = ref([]);
const rowSelected = (rows) => {
  selectIdList.value = [];
  rows.forEach((item) => {
    selectIdList.value.push(item.shareId);
  });
};

const cancelShareIdList = ref([]);
// 批量取消分享
const cancelShareBatch = () => {
  if (selectIdList.value.length == 0) {
    return;
  }

  cancelShareIdList.value = selectIdList.value;
  cancelShareDone();
};

// 单个取消分享
const cancelShare = (row) => {
  cancelShareIdList.value = [row.shareId];
  cancelShareDone();
};

// 取消分享http请求
const cancelShareDone = async () => {
  proxy.Confirm(`Are you sure you want to stop sharing?`, async () => {
    let result = await proxy.Request({
      url: api.cancelShare,
      params: {
        shareIds: cancelShareIdList.value.join(","),
      },
    });
    if (!result) {
      return;
    }
    proxy.Message.success("Stopped sharing successfully.");
    loadDataList();
  });
};
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
</style>
