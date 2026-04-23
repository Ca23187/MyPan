<template>
  <div class="uploader-panel">
    <!-- 顶部标题处 -->
    <div class="uploader-title">
      <span>Upload Tasks</span>
      <span class="tips">(Only tasks from this session are shown)</span>
    </div>

    <!-- 上传文件列表 -->
    <div class="file-list">
      <div v-for="(item, index) in fileList" :key="item.uid" class="file-item">
        <div class="upload-panel">
          <div class="file-name">{{ item.fileName }}</div>

          <div class="progress">
            <el-progress
              :percentage="item.uploadProgress"
              v-if="
                item.status == STATUS.uploading.value ||
                item.status == STATUS.instant_upload.value ||
                item.status == STATUS.upload_completed.value
              "
            />
          </div>

          <div class="upload-status">
            <span
              :class="[
                'iconfont',
                'icon-' + (STATUS[item.status]?.icon || 'close'),
              ]"
              :style="{ color: STATUS[item.status]?.color || '#F75000' }"
            ></span>

            <span
              class="status"
              :style="{ color: STATUS[item.status]?.color || '#F75000' }"
            >
              {{
                item.status == STATUS.fail.value
                  ? item.errorMsg
                  : STATUS[item.status]?.desc || item.status
              }}
            </span>

            <span
              class="upload-info"
              v-if="item.status == STATUS.uploading.value"
            >
              {{ proxy.Utils.size2Str(item.uploadSize) }}/{{
                proxy.Utils.size2Str(item.totalSize)
              }}
            </span>
          </div>
        </div>

        <div class="op">
          <!-- 解析中 -->
          <div class="md5-circle-wrap" v-if="item.status == STATUS.init.value">
            <el-progress
              type="circle"
              :width="50"
              :percentage="item.md5Progress"
              :show-text="false"
            />
            <div class="md5-circle-text">{{ item.md5Progress }}%</div>
          </div>

          <div class="op-btn">
            <!-- 上传中：暂停/继续/终止 -->
            <span v-if="item.status == STATUS.uploading.value">
              <Icon
                :width="28"
                class="btn-item"
                iconName="upload"
                v-if="item.pause"
                title="Resume"
                @click="resumeUpload(item.uid)"
              />
              <Icon
                :width="28"
                class="btn-item"
                iconName="pause"
                title="Pause"
                @click="pauseUpload(item.uid)"
                v-else
              />
              <Icon
                :width="28"
                class="del btn-item"
                iconName="del"
                title="Terminate"
                @click="terminateUpload(item.uid)"
              />
            </span>

            <!-- 非 init/uploading/完成/秒传：删除 -->
            <Icon
              :width="28"
              class="del btn-item"
              iconName="del"
              title="Remove"
              v-if="
                item.status != STATUS.init.value &&
                item.status != STATUS.uploading.value &&
                item.status != STATUS.upload_completed.value &&
                item.status != STATUS.instant_upload.value
              "
              @click="delUpload(item.uid)"
            />

            <!-- 完成/秒传：清理记录 -->
            <Icon
              :width="28"
              class="clean btn-item"
              iconName="clean"
              title="Clear upload record"
              v-if="
                item.status == STATUS.upload_completed.value ||
                item.status == STATUS.instant_upload.value
              "
              @click="delUpload(item.uid)"
            />
          </div>
        </div>
      </div>

      <div v-if="fileList.length == 0">
        <NoData msg="No upload tasks"></NoData>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, onUnmounted } from "vue";
import { useUploadManager } from "@/store/uploadManager";

const { proxy } = getCurrentInstance();

const uploadManager = useUploadManager();

// ✅ 让你的 template 继续用 fileList（不改模板）
const fileList = computed(() => uploadManager.fileList.value);

// 你模板里用到 STATUS（保持你原来的 STATUS 常量也行；这里用 manager 的）
const STATUS = uploadManager.STATUS;

// 按钮事件代理到 manager
const pauseUpload = (uid) => uploadManager.pauseUpload(uid);
const resumeUpload = (uid) => uploadManager.resumeUpload(uid);
const terminateUpload = (uid, index) => uploadManager.terminateUpload(uid);
const delUpload = (uid, index) => uploadManager.removeTask(uid);

// 全局中断事件：直接调 manager.abortAll
const onAbortUploads = () => uploadManager.abortAll();

onMounted(() => {
  window.addEventListener("APP_ABORT_UPLOADS", onAbortUploads);
});
onUnmounted(() => {
  window.removeEventListener("APP_ABORT_UPLOADS", onAbortUploads);
});
</script>

<style lang="scss" scoped>
/* ✅ 这里保持你原来的 scss，别放我之前新增的简化 CSS */
.uploader-panel {
  .uploader-title {
    border-bottom: 1px solid #ddd;
    line-height: 40px;
    padding: 0px 10px;
    font-size: 15px;
    .tips {
      font-size: 13px;
      color: rgb(169, 169, 169);
    }
  }
  .file-list {
    overflow: auto;
    padding: 10px 0px;
    min-height: calc(100vh / 2);
    max-height: calc(100vh - 120px);
    .file-item {
      position: relative;
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 3px 10px;
      background-color: #fff;
      border-bottom: 1px solid #ddd;
    }
    .file-item:nth-child(even) {
      background-color: #fcf8f4;
    }
    .upload-panel {
      flex: 1;
      .file-name {
        color: rgb(64, 62, 62);
      }
      .upload-status {
        display: flex;
        align-items: center;
        margin-top: 5px;
        .iconfont {
          margin-right: 3px;
        }
        .status {
          font-size: 13px;
        }
        .upload-info {
          margin-left: 5px;
          font-size: 12px;
          color: rgb(112, 111, 111);
        }
      }
      .progress {
        height: 10px;
      }
    }
    .op {
      width: 100px;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      .op-btn {
        .btn-item {
          cursor: pointer;
        }
        .del,
        .clean {
          margin-left: 5px;
        }
      }
    }
  }
}
.md5-circle-wrap {
  position: relative;
  width: 50px;
  height: 50px;
  display: inline-block;
}

.md5-circle-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  color: #303133;
}

</style>
