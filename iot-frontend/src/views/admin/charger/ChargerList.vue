<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="SN"><el-input v-model="query.sn" placeholder="模糊搜索" clearable /></el-form-item>
        <el-form-item label="充电站">
          <el-select v-model="query.stationId" placeholder="全部" clearable style="width:160px">
            <el-option v-for="s in stationOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:130px">
            <el-option label="空闲" :value="1" /><el-option label="充电中" :value="2" />
            <el-option label="故障" :value="3" /><el-option label="离线" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="toolbar"><el-button type="primary" @click="openDialog()">新增充电桩</el-button></div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="sn" label="SN" width="140" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="stationId" label="站点ID" width="80" />
        <el-table-column prop="power" label="功率(kW)" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 0 ? 'success' : 'warning'" @click="toggleStatus(row)">
              {{ row.status === 0 ? '启用' : '禁用' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchList" style="margin-top:16px; justify-content:flex-end" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑充电桩' : '新增充电桩'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="设备SN" prop="sn">
          <el-input v-model="form.sn" placeholder="如 CHARGER-101" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="所属充电站" prop="stationId">
          <el-select v-model="form.stationId" placeholder="选择充电站" style="width:100%">
            <el-option v-for="s in stationOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="额定功率(kW)"><el-input v-model="form.power" placeholder="如 7.00" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getChargerList, createCharger, updateCharger, deleteCharger, updateChargerStatus } from '@/api/admin/charger'
import { getStationList } from '@/api/admin/station'

const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stationOptions = ref<any[]>([])
const query = reactive<any>({ page: 1, size: 20, sn: '', stationId: '', status: undefined })

const dialogVisible = ref(false); const isEdit = ref(false)
const editId = ref<number | null>(null); const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{ sn: string; name: string; stationId: number | undefined; power: string }>({ sn: '', name: '', stationId: undefined, power: '7.00' })
const formRules: FormRules = {
  sn: [{ required: true, message: '请输入设备SN', trigger: 'blur' }],
  stationId: [{ required: true, message: '请输入所属充电站ID', trigger: 'blur' }]
}

const statusMap: Record<number, string> = { 0: '离线', 1: '空闲', 2: '充电中', 3: '故障', 4: '锁定' }
const statusTagMap: Record<number, string> = { 0: 'info', 1: 'success', 2: '', 3: 'danger', 4: 'warning' }
function statusText(s: number) { return statusMap[s] || '未知' }
function statusTag(s: number): any { return statusTagMap[s] || 'info' }

async function fetchList() {
  loading.value = true
  try {
    const p: any = { page: query.page, size: query.size }
    if (query.sn) p.sn = query.sn
    if (query.stationId) p.stationId = query.stationId
    if (query.status !== undefined && query.status !== null && query.status !== '') p.status = query.status
    const res: any = await getChargerList(p)
    list.value = res.records || []; total.value = res.total || 0
  } finally { loading.value = false }
}
function search() { query.page = 1; fetchList() }
function reset() { query.sn = ''; query.stationId = ''; query.status = undefined; search() }

function openDialog(row?: any) {
  isEdit.value = !!row
  if (row) {
    editId.value = row.id; form.sn = row.sn; form.name = row.name || ''
    form.stationId = row.stationId; form.power = row.power ? String(row.power) : '7.00'
  } else {
    editId.value = null; form.sn = ''; form.name = ''; form.stationId = undefined; form.power = '7.00'
  }
  dialogVisible.value = true
}
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data: any = { sn: form.sn, name: form.name, stationId: form.stationId, power: Number(form.power) }
    if (isEdit.value) {
      await updateCharger(editId.value!, data); ElMessage.success('修改成功')
    } else {
      data.status = 1; await createCharger(data); ElMessage.success('新增成功')
    }
    dialogVisible.value = false; fetchList()
  } finally { submitting.value = false }
}

async function toggleStatus(row: any) {
  const newStatus = row.status === 0 ? 1 : 0
  try {
    await updateChargerStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    fetchList()
  } catch { /* 错误已在 request 层处理 */ }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除此充电桩吗？', '删除确认', { type: 'warning' })
    await deleteCharger(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 用户取消或错误已在 request 层处理 */ }
}

onMounted(() => { fetchList(); loadStations() })

async function loadStations() {
  try {
    const res: any = await getStationList({ page: 1, size: 100 })
    stationOptions.value = res.records || []
  } catch { /* ignore */ }
}
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.toolbar { margin-bottom: 16px; }
</style>
