<template>
  <div class="status-cards">
    <!-- 设备在线率 -->
    <el-card shadow="hover" class="status-card card-blue">
      <div class="card-icon">
        <el-icon :size="40"><Connection /></el-icon>
      </div>
      <div class="card-info">
        <div class="card-label">设备在线率</div>
        <div class="card-value">
          <span class="value-main">{{ dashboard.onlineRate.toFixed(1) }}%</span>
          <span class="value-sub">{{ dashboard.onlineDeviceCount }} / {{ dashboard.totalDeviceCount }}</span>
        </div>
      </div>
    </el-card>

    <!-- 充电中数量 -->
    <el-card shadow="hover" class="status-card card-green">
      <div class="card-icon">
        <el-icon :size="40"><Odometer /></el-icon>
      </div>
      <div class="card-info">
        <div class="card-label">充电中</div>
        <div class="card-value">
          <span class="value-main">{{ dashboard.chargingCount }}</span>
          <span class="value-sub">台</span>
        </div>
      </div>
    </el-card>

    <!-- 今日订单数 -->
    <el-card shadow="hover" class="status-card card-orange">
      <div class="card-icon">
        <el-icon :size="40"><Document /></el-icon>
      </div>
      <div class="card-info">
        <div class="card-label">今日订单</div>
        <div class="card-value">
          <span class="value-main">{{ dashboard.todayOrderCount }}</span>
          <span class="value-sub">笔</span>
        </div>
      </div>
    </el-card>

    <!-- 今日营收 -->
    <el-card shadow="hover" class="status-card card-purple">
      <div class="card-icon">
        <el-icon :size="40"><Money /></el-icon>
      </div>
      <div class="card-info">
        <div class="card-label">今日营收</div>
        <div class="card-value">
          <span class="value-main">¥{{ formatMoney(dashboard.todayRevenue) }}</span>
        </div>
      </div>
    </el-card>

    <!-- 未处理告警 -->
    <el-card shadow="hover" class="status-card" :class="dashboard.unhandledAlarmCount > 0 ? 'card-red' : 'card-gray'">
      <div class="card-icon">
        <el-icon :size="40"><WarningFilled /></el-icon>
      </div>
      <div class="card-info">
        <div class="card-label">未处理告警</div>
        <div class="card-value">
          <span class="value-main" :class="{ 'text-danger': dashboard.unhandledAlarmCount > 0 }">
            {{ dashboard.unhandledAlarmCount }}
          </span>
          <span class="value-sub">条</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { Connection, Odometer, Document, Money, WarningFilled } from '@element-plus/icons-vue'
import type { DashboardData } from '@/api/dashboard'

/** 接收父组件传入的仪表盘数据 */
defineProps<{
  dashboard: DashboardData
}>()

/** 格式化金额显示 */
function formatMoney(value: number): string {
  if (value >= 10000) {
    return (value / 10000).toFixed(2) + '万'
  }
  return value.toFixed(2)
}
</script>

<style scoped>
.status-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.status-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  border-radius: 8px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.status-card:hover {
  transform: translateY(-2px);
}

.card-icon {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

/* 各卡片图标区配色 */
.card-blue .card-icon {
  background: linear-gradient(135deg, #e6f0ff, #d0e4ff);
  color: #409eff;
}

.card-green .card-icon {
  background: linear-gradient(135deg, #e8f8e8, #c8f0c8);
  color: #67c23a;
}

.card-orange .card-icon {
  background: linear-gradient(135deg, #fff4e6, #ffe8cc);
  color: #e6a23c;
}

.card-purple .card-icon {
  background: linear-gradient(135deg, #f3e8ff, #e8d4ff);
  color: #a855f7;
}

.card-red .card-icon {
  background: linear-gradient(135deg, #ffe8e8, #ffd4d4);
  color: #f56c6c;
}

.card-gray .card-icon {
  background: linear-gradient(135deg, #f0f0f0, #e0e0e0);
  color: #909399;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.card-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.value-main {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  font-variant-numeric: tabular-nums;
}

.value-sub {
  font-size: 13px;
  color: #909399;
}

.text-danger {
  color: #f56c6c !important;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@media (max-width: 1400px) {
  .status-cards {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .status-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
