<template>
  <div class="dashboard-view">
    <!-- 第一行：指标卡片 -->
    <StatusCards :dashboard="dashboardStore.dashboard" />

    <!-- 第二行：趋势图 -->
    <TrendChart
      :data="dashboardStore.trend"
      :loading="dashboardStore.trendLoading"
      @period-change="onPeriodChange"
    />

    <!-- 第三行：站点排名 + 故障统计 -->
    <div class="bottom-row">
      <div class="bottom-left">
        <StationRank
          :data="dashboardStore.stationRanks"
          :loading="dashboardStore.ranksLoading"
          @type-change="onRankTypeChange"
        />
      </div>
      <div class="bottom-right">
        <FaultPieChart
          :data="dashboardStore.faultStats"
          :loading="dashboardStore.faultLoading"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import StatusCards from './components/StatusCards.vue'
import TrendChart from './components/TrendChart.vue'
import StationRank from './components/StationRank.vue'
import FaultPieChart from './components/FaultPieChart.vue'
import { useDashboardStore } from '@/stores/dashboardStore'

// 共享 store：AdminLayout 也读取 wsConnected 用于顶栏展示
const dashboardStore = useDashboardStore()

onMounted(() => {
  dashboardStore.init()
})

onUnmounted(() => {
  dashboardStore.destroy()
})

/** 切换趋势周期 */
function onPeriodChange(period: 'day' | 'week' | 'month') {
  dashboardStore.loadTrend(period)
}

/** 切换站点排名维度 */
function onRankTypeChange(type: 'order' | 'energy' | 'revenue') {
  dashboardStore.loadStationRank(type, 10)
}
</script>

<style scoped>
.dashboard-view {
  max-width: 1600px;
  margin: 0 auto;
}

.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 1200px) {
  .bottom-row {
    grid-template-columns: 1fr;
  }
}
</style>
