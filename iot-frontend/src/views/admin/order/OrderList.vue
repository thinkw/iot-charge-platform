<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="用户ID"><el-input v-model="query.userId" placeholder="可选" clearable /></el-form-item>
        <el-form-item label="充电桩ID"><el-input v-model="query.chargerId" placeholder="可选" clearable /></el-form-item>
        <el-form-item label="订单状态">
          <el-select v-model="query.orderStatus" placeholder="全部" clearable style="width:120px">
            <el-option label="充电中" :value="1" /><el-option label="已完成" :value="2" />
            <el-option label="已取消" :value="3" /><el-option label="异常" :value="4" />
            <el-option label="待确认" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付状态">
          <el-select v-model="query.payStatus" placeholder="全部" clearable style="width:120px">
            <el-option label="未支付" :value="0" /><el-option label="已支付" :value="1" /><el-option label="已退款" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column prop="chargerName" label="充电桩" width="140" />
        <el-table-column prop="chargedEnergy" label="充电量(kWh)" width="110" />
        <el-table-column prop="totalAmount" label="金额(元)" width="100" />
        <el-table-column label="订单状态" width="90">
          <template #default="{ row }">{{ orderStatusText(row.orderStatus) }}</template>
        </el-table-column>
        <el-table-column label="支付状态" width="90">
          <template #default="{ row }">{{ payStatusText(row.payStatus) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="160"><template #default="{ row }">{{ row.startTime }}</template></el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.orderStatus === 1 || row.orderStatus === 4 || row.orderStatus === 5" size="small" type="warning" @click="openForceEnd(row)">
              强制结束
            </el-button>
            <el-button v-if="row.payStatus === 1" size="small" type="danger" @click="openRefund(row)">退款</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchList" style="margin-top:16px; justify-content:flex-end" />
    </el-card>

    <!-- 强制结束弹窗 -->
    <el-dialog v-model="endVisible" title="手动结束订单" width="420px">
      <el-input v-model="endReason" type="textarea" :rows="3" placeholder="请输入结束原因" />
      <template #footer>
        <el-button @click="endVisible=false">取消</el-button>
        <el-button type="primary" @click="handleForceEnd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 退款弹窗 -->
    <el-dialog v-model="refundVisible" title="管理员退款" width="420px">
      <el-input v-model="refundReason" type="textarea" :rows="3" placeholder="请输入退款原因" />
      <template #footer>
        <el-button @click="refundVisible=false">取消</el-button>
        <el-button type="primary" @click="handleRefund">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderList, forceEndOrder, adminRefund } from '@/api/admin/order'

const list = ref<any[]>([]); const total = ref(0); const loading = ref(false)
const query = reactive<any>({ page: 1, size: 20, userId: '', chargerId: '', orderStatus: undefined, payStatus: undefined })

const endVisible = ref(false); const endReason = ref(''); const endOrderNo = ref('')
const refundVisible = ref(false); const refundReason = ref(''); const refundOrderNo = ref('')

function orderStatusText(s: number) { const m: Record<number, string> = { 1: '充电中', 2: '已完成', 3: '已取消', 4: '异常', 5: '待确认' }; return m[s] || '未知' }
function payStatusText(s: number) { const m: Record<number, string> = { 0: '未支付', 1: '已支付', 2: '已退款' }; return m[s] || '未知' }

async function fetchList() {
  loading.value = true
  try {
    const p: any = { page: query.page, size: query.size }
    for (const k of ['userId', 'chargerId', 'orderStatus', 'payStatus']) { if (query[k] !== undefined && query[k] !== null && query[k] !== '') p[k] = query[k] }
    const res: any = await getOrderList(p)
    list.value = res.records || []; total.value = res.total || 0
  } finally { loading.value = false }
}
function search() { query.page = 1; fetchList() }
function reset() { query.userId = ''; query.chargerId = ''; query.orderStatus = undefined; query.payStatus = undefined; search() }

function openForceEnd(row: any) { endOrderNo.value = row.orderNo; endReason.value = ''; endVisible.value = true }
async function handleForceEnd() {
  await forceEndOrder(endOrderNo.value, endReason.value); ElMessage.success('已手动结束'); endVisible.value = false; fetchList()
}
function openRefund(row: any) { refundOrderNo.value = row.orderNo; refundReason.value = ''; refundVisible.value = true }
async function handleRefund() {
  await adminRefund(refundOrderNo.value, refundReason.value); ElMessage.success('退款成功'); refundVisible.value = false; fetchList()
}

onMounted(fetchList)
</script>

<style scoped>.page-container { display: flex; flex-direction: column; gap: 16px; }</style>
