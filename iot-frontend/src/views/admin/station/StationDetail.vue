<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <el-button @click="$router.back()" text>← 返回</el-button>
          <span>充电站详情</span>
        </div>
      </template>
      <el-descriptions v-if="station" :column="2" border>
        <el-descriptions-item label="站点名称">{{ station.name }}</el-descriptions-item>
        <el-descriptions-item label="ID">{{ station.id }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ station.address }}</el-descriptions-item>
        <el-descriptions-item label="经度">{{ station.longitude }}</el-descriptions-item>
        <el-descriptions-item label="纬度">{{ station.latitude }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ station.contact }}</el-descriptions-item>
        <el-descriptions-item label="营业时间">{{ station.businessHours }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="station.status === 1 ? 'success' : station.status === 2 ? 'warning' : 'info'" size="small">
            {{ station.status === 1 ? '营业中' : station.status === 2 ? '维护中' : '暂停营业' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ station.createTime }}</el-descriptions-item>
      </el-descriptions>

      <!-- 关联充电桩 -->
      <el-divider content-position="left" v-if="station">关联充电桩 ({{ chargers.length }})</el-divider>
      <el-table v-if="station" :data="chargers" v-loading="chargerLoading" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="sn" label="SN" width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="power" label="功率(kW)" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/admin/charger/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getStationDetail } from '@/api/admin/station'
import { getChargerList } from '@/api/admin/charger'

const route = useRoute()
const station = ref<any>(null)
const chargers = ref<any[]>([])
const loading = ref(false)
const chargerLoading = ref(false)

const statusMap: Record<number, string> = { 0: '离线', 1: '空闲', 2: '充电中', 3: '故障', 4: '锁定' }
const tagMap: Record<number, string> = { 0: 'info', 1: 'success', 2: '', 3: 'danger', 4: 'warning' }
function statusText(s: number) { return statusMap[s] || '未知' }
function statusTag(s: number): any { return tagMap[s] || 'info' }

onMounted(async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    station.value = await getStationDetail(id)
    // 同时加载关联充电桩
    chargerLoading.value = true
    try {
      const res: any = await getChargerList({ page: 1, size: 200, stationId: id })
      chargers.value = res.records || []
    } finally { chargerLoading.value = false }
  } finally { loading.value = false }
})
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; align-items: center; gap: 12px; }
</style>
