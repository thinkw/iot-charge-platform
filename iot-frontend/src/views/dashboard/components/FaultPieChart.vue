<template>
  <el-card shadow="hover" class="fault-card">
    <template #header>
      <div class="chart-header">
        <span class="chart-title">设备故障统计</span>
        <el-tag :type="faultRateColor" size="small">
          故障率 {{ data?.faultRate?.toFixed(2) ?? 0 }}%
        </el-tag>
      </div>
    </template>
    <div v-loading="loading" class="fault-body">
      <!-- 饼图 -->
      <div v-if="hasData" ref="chartRef" class="pie-container"></div>
      <!-- 空数据 -->
      <div v-else class="fault-empty">
        <el-empty description="暂无故障数据" :image-size="80" />
      </div>
      <!-- 总计 -->
      <div v-if="hasData" class="fault-total">
        <span>故障总次数：</span>
        <strong>{{ data?.totalFaultCount ?? 0 }} 次</strong>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { FaultStatistics } from '@/api/dashboard'

const props = defineProps<{
  data: FaultStatistics | null
  loading: boolean
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

/** 是否有数据 */
const hasData = computed(() => {
  return props.data && props.data.faultCountByType && Object.keys(props.data.faultCountByType).length > 0
})

/** 故障率标签颜色 */
const faultRateColor = computed(() => {
  const rate = props.data?.faultRate ?? 0
  if (rate >= 10) return 'danger'
  if (rate >= 5) return 'warning'
  return 'success'
})

/** 告警类型中文映射 */
const alarmTypeLabels: Record<string, string> = {
  OVER_TEMP: '过温',
  OVER_VOLT: '过压',
  UNDER_VOLT: '欠压',
  SHORT_CIRCUIT: '短路',
  LEAKAGE: '漏电',
  OFFLINE: '离线',
  COMM_ERROR: '通信异常'
}

/** 渲染饼图 */
function renderChart() {
  if (!chartRef.value || !props.data?.faultCountByType) return

  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const entries = Object.entries(props.data.faultCountByType)
  if (entries.length === 0) {
    chart.clear()
    return
  }

  const pieData = entries.map(([type, count]) => ({
    name: alarmTypeLabels[type] || type,
    value: count
  }))

  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 次 ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { fontSize: 12 }
    },
    series: [
      {
        type: 'pie',
        radius: ['50%', '80%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          position: 'outside',
          formatter: '{b}\n{d}%'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          },
          scaleSize: 10
        },
        data: pieData,
        color: ['#f56c6c', '#e6a23c', '#f093fb', '#fa8c16', '#ff4d4f', '#909399', '#a0d911']
      }
    ]
  }, true)
}

watch(() => props.data, () => {
  // 等待 v-if="hasData" 创建 DOM 后再初始化 ECharts
  nextTick(() => renderChart())
}, { deep: true })

function onResize() {
  chart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  // 数据可能在 mounted 前已加载，同样需要等 DOM 就绪
  nextTick(() => renderChart())
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.fault-card {
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

.fault-body {
  position: relative;
}

.pie-container {
  width: 100%;
  height: 300px;
}

.fault-empty {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fault-total {
  text-align: center;
  padding-top: 8px;
  font-size: 14px;
  color: #606266;
  border-top: 1px solid #ebeef5;
}

.fault-total strong {
  color: #f56c6c;
  font-size: 18px;
}
</style>
