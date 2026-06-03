<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="充电站ID"><el-input v-model="query.stationId" placeholder="全部" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:110px">
            <el-option label="启用" :value="1" /><el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="toolbar"><el-button type="primary" @click="openDialog()">新增规则</el-button></div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="规则名称" min-width="160" />
        <el-table-column prop="stationId" label="站点ID" width="80" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.ruleType === 1 ? '基础电价' : row.ruleType === 2 ? '峰谷电价' : '阶梯电价' }}</template>
        </el-table-column>
        <el-table-column label="时段" width="180">
          <template #default="{ row }">{{ row.startTime && row.endTime ? `${row.startTime}~${row.endTime}` : '全天' }}</template>
        </el-table-column>
        <el-table-column prop="electricityPrice" label="电价(元/kWh)" width="110" />
        <el-table-column prop="servicePrice" label="服务费(元/kWh)" width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" :type="row.status===1?'warning':'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchList" style="margin-top:16px; justify-content:flex-end" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑计费规则' : '新增计费规则'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-form-item label="规则名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="充电站ID" prop="stationId"><el-input v-model="form.stationId" placeholder="0表示全局规则" /></el-form-item>
        <el-form-item label="规则类型" prop="ruleType">
          <el-radio-group v-model="form.ruleType">
            <el-radio :value="1">基础电价</el-radio>
            <el-radio :value="2">峰谷电价</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.ruleType === 2" label="开始时间"><el-time-picker v-model="form.startTime" format="HH:mm:ss" value-format="HH:mm:ss" /></el-form-item>
        <el-form-item v-if="form.ruleType === 2" label="结束时间"><el-time-picker v-model="form.endTime" format="HH:mm:ss" value-format="HH:mm:ss" /></el-form-item>
        <el-form-item label="电价(元/kWh)" prop="electricityPrice"><el-input v-model="form.electricityPrice" placeholder="如 0.80" /></el-form-item>
        <el-form-item label="服务费(元/kWh)" prop="servicePrice"><el-input v-model="form.servicePrice" placeholder="如 0.50" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" :max="999" /></el-form-item>
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
import * as api from '@/api/admin/pricing'

const list = ref<any[]>([]); const total = ref(0); const loading = ref(false)
const query = reactive<any>({ page: 1, size: 20, stationId: '', status: undefined })
const dialogVisible = ref(false); const isEdit = ref(false); const editId = ref<number | null>(null); const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({ name: '', stationId: '0', ruleType: 1, startTime: '', endTime: '', electricityPrice: '0.80', servicePrice: '0.50', priority: 10 })
const formRules: FormRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  stationId: [{ required: true, message: '请输入充电站ID', trigger: 'blur' }],
  electricityPrice: [{ required: true, message: '请输入电价', trigger: 'blur' }],
  servicePrice: [{ required: true, message: '请输入服务费', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const p: any = { page: query.page, size: query.size }
    if (query.stationId) p.stationId = query.stationId
    if (query.status !== undefined && query.status !== null && query.status !== '') p.status = query.status
    const res: any = await api.getPricingList(p)
    list.value = res.records || []; total.value = res.total || 0
  } finally { loading.value = false }
}
function search() { query.page = 1; fetchList() }
function reset() { query.stationId = ''; query.status = undefined; search() }

function openDialog(row?: any) {
  isEdit.value = !!row
  if (row) {
    editId.value = row.id; form.name = row.name; form.stationId = String(row.stationId); form.ruleType = row.ruleType
    form.startTime = row.startTime; form.endTime = row.endTime || ''
    form.electricityPrice = String(row.electricityPrice); form.servicePrice = String(row.servicePrice)
    form.priority = row.priority ?? 10
  } else {
    editId.value = null; form.name = ''; form.stationId = '0'; form.ruleType = 1
    form.startTime = ''; form.endTime = ''; form.electricityPrice = '0.80'; form.servicePrice = '0.50'; form.priority = 10
  }
  dialogVisible.value = true
}
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data: any = { name: form.name, stationId: Number(form.stationId), ruleType: form.ruleType,
      electricityPrice: Number(form.electricityPrice), servicePrice: Number(form.servicePrice),
      priority: form.priority, status: 1 }
    if (form.ruleType === 2) { data.startTime = form.startTime; data.endTime = form.endTime }
    if (isEdit.value) {
      await api.updatePricing(editId.value!, data); ElMessage.success('修改成功')
    } else {
      await api.createPricing(data); ElMessage.success('新增成功')
    }
    dialogVisible.value = false; fetchList()
  } finally { submitting.value = false }
}

async function toggleStatus(row: any) { try { await api.updatePricingStatus(row.id, row.status === 1 ? 0 : 1); ElMessage.success('状态已更新'); fetchList() } catch { /* error handled by request layer */ } }
async function handleDelete(id: number) { try { await ElMessageBox.confirm('确定删除此规则吗？', '删除确认', { type: 'warning' }); await api.deletePricing(id); ElMessage.success('删除成功'); fetchList() } catch { /* user cancelled or error */ } }

onMounted(fetchList)
</script>
<style scoped>.page-container { display: flex; flex-direction: column; gap: 16px; } .toolbar { margin-bottom: 16px; }</style>
