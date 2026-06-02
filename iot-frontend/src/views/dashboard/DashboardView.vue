<template>
  <DashboardLayout :ws-connected="wsConnected">
    <div class="dashboard-view">
      <!-- 第一行：指标卡片 -->
      <StatusCards :dashboard="dashboard" />

      <!-- 第二行：趋势图 -->
      <TrendChart
        :data="trend"
        :loading="trendLoading"
        @period-change="onPeriodChange"
      />

      <!-- 第三行：站点排名 + 故障统计 -->
      <div class="bottom-row">
        <div class="bottom-left">
          <StationRank
            :data="stationRanks"
            :loading="ranksLoading"
            @type-change="onRankTypeChange"
          />
        </div>
        <div class="bottom-right">
          <FaultPieChart
            :data="faultStats"
            :loading="faultLoading"
          />
        </div>
      </div>
    </div>
  </DashboardLayout>
</template>

<script setup lang="ts">
import DashboardLayout from '@/layouts/DashboardLayout.vue'
import StatusCards from './components/StatusCards.vue'
import TrendChart from './components/TrendChart.vue'
import StationRank from './components/StationRank.vue'
import FaultPieChart from './components/FaultPieChart.vue'
import { useDashboard } from '@/composables/useDashboard'

// 使用大屏数据 composable（内部自动管理 WebSocket 连接）
const {
  dashboard,
  trend,
  trendLoading,
  stationRanks,
  ranksLoading,
  faultStats,
  faultLoading,
  wsConnected,
  loadTrend,
  loadStationRank
} = useDashboard()

/** 切换趋势周期 */
function onPeriodChange(period: 'day' | 'week' | 'month') {
  loadTrend(period)
}

/** 切换站点排名维度 */
function onRankTypeChange(type: 'order' | 'energy' | 'revenue') {
  loadStationRank(type, 10)
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
