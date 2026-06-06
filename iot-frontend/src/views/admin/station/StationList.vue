<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="站点名称">
          <el-input v-model="query.name" placeholder="模糊搜索" clearable @clear="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
            <el-option label="营业中" :value="1" />
            <el-option label="暂停营业" :value="0" />
            <el-option label="维护中" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openDialog()">新增充电站</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="站点名称" min-width="180" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系电话" width="130" />
        <el-table-column prop="businessHours" label="营业时间" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'info'" size="small">
              {{ row.status === 1 ? '营业中' : row.status === 2 ? '维护中' : '暂停营业' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="changeStatus(row)">状态</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page" :page-size="query.size"
        :total="total" layout="total, prev, pager, next" @current-change="fetchList"
        style="margin-top:16px; justify-content:flex-end"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑充电站' : '新增充电站'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="站点名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入站点名称" />
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="经度">
          <el-input v-model="form.longitude" placeholder="如 116.461" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input v-model="form.latitude" placeholder="如 39.909" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contact">
          <el-input v-model="form.contact" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="营业时间" prop="businessHours">
          <el-input v-model="form.businessHours" placeholder="如 00:00-24:00" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 状态修改弹窗 -->
    <el-dialog v-model="statusVisible" title="修改营业状态" width="360px">
      <el-select v-model="targetStatus" style="width:100%">
        <el-option label="营业中" :value="1" />
        <el-option label="暂停营业" :value="0" />
        <el-option label="维护中" :value="2" />
      </el-select>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" @click="handleStatusChange">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getStationList, createStation, updateStation, deleteStation, updateStationStatus } from '@/api/admin/station'

const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({ page: 1, size: 20, name: '', status: undefined as number | undefined })

const dialogVisible = ref(false)
const statusVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const editId = ref<number | null>(null)
const statusId = ref<number | null>(null)
const targetStatus = ref(1)

const formRef = ref<FormInstance>()
const form = reactive({ name: '', address: '', longitude: '', latitude: '', contact: '', businessHours: '00:00-24:00' })
const formRules: FormRules = {
  name: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  contact: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  address: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: query.page, size: query.size }
    if (query.name) params.name = query.name
    if (query.status !== undefined && query.status !== null) params.status = query.status
    const res: any = await getStationList(params)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function search() { query.page = 1; fetchList() }
function reset() { query.name = ''; query.status = undefined; search() }

function openDialog(row?: any) {
  isEdit.value = !!row
  if (row) {
    editId.value = row.id
    form.name = row.name; form.address = row.address || ''
    form.longitude = row.longitude ? String(row.longitude) : ''
    form.latitude = row.latitude ? String(row.latitude) : ''
    form.contact = row.contact || ''; form.businessHours = row.businessHours || '00:00-24:00'
  } else {
    editId.value = null
    form.name = ''; form.address = ''; form.longitude = ''; form.latitude = ''; form.contact = ''; form.businessHours = '00:00-24:00'
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data = { ...form, status: 1 }
    if (isEdit.value) {
      await updateStation(editId.value!, data)
      ElMessage.success('修改成功')
    } else {
      await createStation(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally { submitting.value = false }
}

function changeStatus(row: any) {
  statusId.value = row.id
  // 默认为当前状态，由用户在弹窗中手动切换
  targetStatus.value = row.status
  statusVisible.value = true
}

async function handleStatusChange() {
  try {
    await updateStationStatus(statusId.value!, targetStatus.value)
    ElMessage.success('状态更新成功')
    statusVisible.value = false
    fetchList()
  } catch { /* 错误已在 request 层处理 */ }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除此充电站吗？关联充电桩的站点无法删除。', '删除确认', { type: 'warning' })
    await deleteStation(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 用户取消或错误已在 request 层处理 */ }
}

onMounted(fetchList)
</script>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card { padding: 0; }
.toolbar { margin-bottom: 16px; }
</style>
