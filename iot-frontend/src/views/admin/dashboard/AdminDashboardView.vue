<template>
  <div class="dashboard-view">
    <StatusCards :dashboard="dashboard" />
    <TrendChart :data="trend" :loading="trendLoading" @period-change="onPeriodChange" />
    <div class="bottom-row">
      <div class="bottom-left">
        <StationRank :data="stationRanks" :loading="ranksLoading" @type-change="onRankTypeChange" />
      </div>
      <div class="bottom-right">
        <FaultPieChart :data="faultStats" :loading="faultLoading" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import StatusCards from '@/views/dashboard/components/StatusCards.vue'
import TrendChart from '@/views/dashboard/components/TrendChart.vue'
import StationRank from '@/views/dashboard/components/StationRank.vue'
import FaultPieChart from '@/views/dashboard/components/FaultPieChart.vue'
import { useDashboard } from '@/composables/useDashboard'

const {
  dashboard, trend, trendLoading, stationRanks, ranksLoading, faultStats, faultLoading,
  wsConnected, loadTrend, loadStationRank, loadFaultStats
} = useDashboard()

/**
 * 趋势图 period 切换事件处理
 * 子组件 TrendChart 切换日/周/月时触发
 */
function onPeriodChange(period: 'day' | 'week' | 'month') {
  loadTrend(period)
}

/**
 * 站点排名 type 切换事件处理
 * 子组件 StationRank 切换排序维度时触发
 */
function onRankTypeChange(type: string) {
  loadStationRank(type, 10)
}
</script>

<style scoped>
.dashboard-view { padding: 0; }
.bottom-row { display: flex; gap: 20px; margin-top: 20px; }
.bottom-left { flex: 1; }
.bottom-right { width: 400px; }
</style>
