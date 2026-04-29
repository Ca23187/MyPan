<template>
  <el-config-provider :locale="locale" :message="config">
    <router-view></router-view>
  </el-config-provider>
</template>

<script setup>
import { reactive } from "vue";
import en from "element-plus/es/locale/lang/en";
const locale = en;
const config = reactive({
  max: 1,
});
</script>

<style lang="scss" scoped>
</style>
