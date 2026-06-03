<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="告警类型">
          <el-select v-model="query.alarmType" placeholder="全部" clearable style="width:140px">
            <el-option label="过温" value="OVER_TEMP" /><el-option label="过压" value="OVER_VOLT" />
            <el-option label="欠压" value="UNDER_VOLT" /><el-option label="短路" value="SHORT_CIRCUIT" />
            <el-option label="漏电" value="LEAKAGE" /><el-option label="离线" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.alarmLevel" placeholder="全部" clearable style="width:110px">
            <el-option label="一般" :value="1" /><el-option label="重要" :value="2" /><el-option label="紧急" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:110px">
            <el-option label="未处理" :value="0" /><el-option label="已处理" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="chargerId" label="充电桩ID" width="90" />
        <el-table-column prop="alarmType" label="告警类型" width="110" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.alarmLevel===3?'danger':row.alarmLevel===2?'warning':'info'" size="small">
              {{ row.alarmLevel===3?'紧急':row.alarmLevel===2?'重要':'一般' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="告警内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status===0?'danger':'success'" size="small">{{ row.status===0?'未处理':'已处理' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160"><template #default="{ row }">{{ row.createTime }}</template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="primary" @click="openHandle(row)">处理</el-button>
            <span v-else style="color:#909399">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchList" style="margin-top:16px; justify-content:flex-end" />
    </el-card>

    <el-dialog v-model="handleVisible" title="处理告警" width="440px">
      <el-input v-model="handleNote" type="textarea" :rows="4" placeholder="请输入处理备注" />
      <template #footer>
        <el-button @click="handleVisible=false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlarmList, handleAlarm } from '@/api/admin/alarm'

const list = ref<any[]>([]); const total = ref(0); const loading = ref(false)
const query = reactive<any>({ page: 1, size: 20, alarmType: '', alarmLevel: undefined, status: undefined })
const handleVisible = ref(false); const handleNote = ref(''); const alarmId = ref<number | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const p: any = { page: query.page, size: query.size }
    for (const k of ['alarmType', 'alarmLevel', 'status']) { if (query[k] !== undefined && query[k] !== null && query[k] !== '') p[k] = query[k] }
    const res: any = await getAlarmList(p)
    list.value = res.records || []; total.value = res.total || 0
  } finally { loading.value = false }
}
function search() { query.page = 1; fetchList() }
function reset() { query.alarmType = ''; query.alarmLevel = undefined; query.status = undefined; search() }

function openHandle(row: any) { alarmId.value = row.id; handleNote.value = ''; handleVisible.value = true }
async function submitHandle() {
  try {
    await handleAlarm(alarmId.value!, handleNote.value)
    ElMessage.success('告警已处理')
    handleVisible.value = false
    fetchList()
  } catch { /* 错误已在 request 层处理 */ }
}

onMounted(fetchList)
</script>
<style scoped>.page-container { display: flex; flex-direction: column; gap: 16px; }</style>
