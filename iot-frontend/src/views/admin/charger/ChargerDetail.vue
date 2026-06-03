<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <template #header>
        <el-button @click="$router.back()" text>← 返回</el-button>
        <span style="margin-left:12px">充电桩详情</span>
      </template>
      <el-descriptions v-if="charger" :column="2" border>
        <el-descriptions-item label="SN">{{ charger.sn }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ charger.name }}</el-descriptions-item>
        <el-descriptions-item label="所属站点ID">{{ charger.stationId }}</el-descriptions-item>
        <el-descriptions-item label="额定功率">{{ charger.power }} kW</el-descriptions-item>
        <el-descriptions-item label="当前电压">{{ charger.currentVoltage ?? '-' }} V</el-descriptions-item>
        <el-descriptions-item label="当前电流">{{ charger.currentCurrent ?? '-' }} A</el-descriptions-item>
        <el-descriptions-item label="当前功率">{{ charger.currentPower ?? '-' }} kW</el-descriptions-item>
        <el-descriptions-item label="温度">{{ charger.temperature ?? '-' }} ℃</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(charger.status)" size="small">{{ statusText(charger.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最后上线">{{ charger.lastOnlineTime ?? '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getChargerDetail } from '@/api/admin/charger'

const route = useRoute(); const charger = ref<any>(null); const loading = ref(false)
const statusMap: Record<number, string> = { 0: '离线', 1: '空闲', 2: '充电中', 3: '故障', 4: '锁定' }
const tagMap: Record<number, string> = { 0: 'info', 1: 'success', 2: '', 3: 'danger', 4: 'warning' }
function statusText(s: number) { return statusMap[s] || '未知' }
function statusTag(s: number): any { return tagMap[s] || 'info' }

onMounted(async () => {
  loading.value = true
  try { charger.value = await getChargerDetail(Number(route.params.id)) } finally { loading.value = false }
})
</script>

<style scoped>.page-container { padding: 0; }</style>
