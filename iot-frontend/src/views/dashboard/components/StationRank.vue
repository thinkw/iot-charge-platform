<template>
  <el-card shadow="hover" class="rank-card">
    <template #header>
      <div class="chart-header">
        <span class="chart-title">站点排名</span>
        <el-radio-group v-model="rankType" size="small" @change="onTypeChange">
          <el-radio-button value="order">订单量</el-radio-button>
          <el-radio-button value="energy">充电量</el-radio-button>
          <el-radio-button value="revenue">营收</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <div v-loading="loading">
      <el-table :data="data" stripe size="small" max-height="380" empty-text="暂无排名数据">
        <el-table-column type="index" label="排名" width="60" align="center">
          <template #default="{ $index }">
            <el-tag
              :type="$index < 3 ? ['danger', 'warning', 'success'][$index] : 'info'"
              size="small"
              effect="dark"
            >
              {{ $index + 1 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stationName" label="站点名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="数值" width="120" align="right">
          <template #default="{ row }">
            <span v-if="rankType === 'order'" class="rank-number">{{ row.orderCount }} 笔</span>
            <span v-else-if="rankType === 'energy'" class="rank-number">{{ formatNumber(row.totalEnergy) }} kWh</span>
            <span v-else class="rank-number">¥{{ formatNumber(row.totalRevenue) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { StationRank } from '@/api/dashboard'

defineProps<{
  data: StationRank[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'type-change', type: 'order' | 'energy' | 'revenue'): void
}>()

const rankType = ref<'order' | 'energy' | 'revenue'>('order')

function onTypeChange(val: 'order' | 'energy' | 'revenue') {
  emit('type-change', val)
}

function formatNumber(val: number): string {
  if (val >= 10000) {
    return (val / 10000).toFixed(1) + '万'
  }
  return val.toFixed(0)
}
</script>

<style scoped>
.rank-card {
  height: 100%;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.rank-number {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #303133;
}
</style>
