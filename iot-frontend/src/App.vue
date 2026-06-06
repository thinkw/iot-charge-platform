<template>
  <component :is="layoutComponent">
    <router-view />
  </component>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import type { Component } from 'vue'

const route = useRoute()

/**
 * 布局组件映射
 * 根据路由 meta.layout 动态选择布局容器：
 * - auth: 登录页/404 居中布局
 * - admin: 侧边栏管理后台布局
 * - dashboard: 大屏全宽布局
 * 默认使用 admin 布局
 */
const layoutMap: Record<string, Component> = {
  auth: defineAsyncComponent(() => import('@/layouts/AuthLayout.vue')),
  admin: defineAsyncComponent(() => import('@/layouts/AdminLayout.vue'))
}

const layoutComponent = computed(() => {
  const layout = (route.meta.layout as string) || 'admin'
  return layoutMap[layout] || layoutMap.admin
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background-color: #f5f7fa;
}
</style>
