<template>
  <div class="auth-layout">
    <slot />
  </div>
</template>

<script setup lang="ts">
/** 认证页布局 — 居中容器 + 渐变背景，用于登录页和错误页 */
</script>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f5ff 50%, #f5f7fa 100%);
}
</style>
