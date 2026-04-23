<template>
  <!-- 文件选择/文件移动 -->
  <div>
    <Dialog
      :show="dialogConfig.show"
      :title="dialogConfig.title"
      :buttons="dialogConfig.buttons"
      width="600px"
      :showCancel="true"
      @close="dialogConfig.show = false"
    >
      <!-- 目录导航 -->
      <div class="navigation-panel">
        <Navigation
          ref="navigationRef"
          @navChange="navChange"
          :watchPath="false"
        ></Navigation>
      </div>
      <div class="folder-list" v-if="folderList.length > 0">
        <div
          class="folder-item"
          v-for="item in folderList"
          @click="selectFolder(item)"
        >
          <Icon :fileType="0"></Icon>
          <span class="file-name">{{ item.fileName }}</span>
        </div>
      </div>
      <div v-else class="tips">
        Move to <span>{{ currentFolder.fileName }}</span>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance } from "vue";
import { useRouter, useRoute } from "vue-router";

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const api = {
  loadAllFolder: "/file/loadAllFolder",
};

// 定义弹出框的属性
const dialogConfig = ref({
  show: false,
  title: "Move to",
  buttons: [
    {
      type: "primary",
      text: "Move here",
      click: (e) => {
        folderSelect();
      },
    },
  ],
});

// 目录列表
const folderList = ref([]);
// 父级ID
const filePid = ref("0");
// 当前目录ID
const currentFileIds = ref([]);
// 当前文件夹
const currentFolder = ref({});

// 获取所有的目录
const loadAllFolder = async () => {
  const ids = currentFileIds.value;

  let result = await proxy.Request({
    url: api.loadAllFolder,
    method: 'get',
    params: {
      filePid: filePid.value,
      currentFileIds: Array.isArray(ids) ? ids.join(",") : ids || "",
    },
  });
  if (!result) return;
  folderList.value = result.data;
};


// 展示弹出框，对外方法：接收的是「选中的 fileId 列表」
const showFolderDialog = (selectedFileIds) => {
  dialogConfig.value.show = true;

  // 确保是数组
  currentFileIds.value = Array.isArray(selectedFileIds)
    ? selectedFileIds
    : [selectedFileIds];

  // 弹窗初始浏览位置（你可以按需求改：0=根目录，或 currentFolder）
  filePid.value = "0";

  loadAllFolder();
};


// 关闭弹出框
const close = () => {
  dialogConfig.value.show = false;
};
// 向外暴露这两个函数，使得父组件Main可以调用这两个函数
defineExpose({ showFolderDialog, close });

// 绑定导航栏
const navigationRef = ref();

// 调用Navigation子组件中的navChange,使得参数传递给该组件
const navChange = (data) => {
  const { curFolder } = data;
  currentFolder.value = curFolder;
  filePid.value = curFolder.fileId;
  loadAllFolder();
};

// 选择目录(目录导航)
const selectFolder = (data) => {
  navigationRef.value.openFolder(data);
};

// 确定选择要移动到的目录
// 将选定的文件目录参数传递给父组件 Main 中的 folderSelect 函数
const emit = defineEmits(["folderSelect"]);
const folderSelect = () => {
  emit("folderSelect", filePid.value);
};
</script>

<style lang="scss" scoped>
.navigation-panel {
  padding-left: 10px;
  background: #f1f1f1;
}
.folder-list {
  .folder-item {
    cursor: pointer;
    display: flex;
    align-items: center;
    padding: 10px;
    .file-name {
      display: inline-block;
      margin-left: 10px;
    }
    &:hover {
      background: #f8f8f8;
    }
  }
  max-height: calc(100vh - 200px);
  min-height: 200px;
}
.tips {
  text-align: center;
  line-height: 200px;
  span {
    color: #06a7ff;
  }
}
</style>
