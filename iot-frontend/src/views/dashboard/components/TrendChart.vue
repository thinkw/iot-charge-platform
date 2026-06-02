<template>
  <el-card shadow="hover" class="trend-chart-card">
    <template #header>
      <div class="chart-header">
        <span class="chart-title">运营趋势</span>
        <el-radio-group v-model="period" size="small" @change="onPeriodChange">
          <el-radio-button value="day">日</el-radio-button>
          <el-radio-button value="week">周</el-radio-button>
          <el-radio-button value="month">月</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <div v-loading="loading" class="chart-body">
      <div ref="chartRef" class="chart-container"></div>
      <div v-if="!hasData" class="chart-empty">
        <el-empty description="暂无趋势数据" :image-size="80" />
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'
import type { TrendData } from '@/api/dashboard'

const props = defineProps<{
  data: TrendData | null
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'period-change', period: 'day' | 'week' | 'month'): void
}>()

const period = ref<'day' | 'week' | 'month'>('day')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

/** 是否有数据 */
const hasData = computed(() => {
  return props.data && props.data.orderTrend && props.data.orderTrend.length > 0
})

/** 切换周期 */
function onPeriodChange(val: 'day' | 'week' | 'month') {
  emit('period-change', val)
}

/** 初始化/更新图表 */
function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const data = props.data
  if (!data || !data.orderTrend || data.orderTrend.length === 0) {
    chart.clear()
    return
  }

  const dates = data.orderTrend.map((p) => p.date)
  const orders = data.orderTrend.map((p) => p.value)
  const energies = data.energyTrend.map((p) => p.decimalValue || 0)
  const revenues = data.revenueTrend.map((p) => p.decimalValue || 0)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['订单量', '充电量(kWh)', '营收(元)'],
      bottom: 0
    },
    grid: {
      left: 60,
      right: 60,
      top: 20,
      bottom: 40
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        rotate: dates.length > 10 ? 45 : 0
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '订单量(笔)',
        axisLabel: { formatter: '{value}' }
      },
      {
        type: 'value',
        name: '金额/电量',
        axisLabel: { formatter: '{value}' }
      }
    ],
    series: [
      {
        name: '订单量',
        type: 'line',
        smooth: true,
        data: orders,
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.02)' }
          ])
        }
      },
      {
        name: '充电量(kWh)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: energies,
        itemStyle: { color: '#67c23a' }
      },
      {
        name: '营收(元)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: revenues,
        itemStyle: { color: '#e6a23c' }
      }
    ]
  }, true)
}

// 数据变化时重绘
watch(() => props.data, () => {
  renderChart()
}, { deep: true })

// 窗口大小变化时 resize
function onResize() {
  chart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  renderChart()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.trend-chart-card {
  margin-bottom: 20px;
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

.chart-body {
  position: relative;
}

.chart-container {
  width: 100%;
  height: 350px;
}

.chart-empty {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 350px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
