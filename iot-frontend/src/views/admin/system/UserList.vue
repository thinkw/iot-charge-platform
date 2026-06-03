<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="手机号"><el-input v-model="query.phone" placeholder="模糊搜索" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:110px">
            <el-option label="正常" :value="1" /><el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="plateNo" label="车牌号" width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'正常':'已禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160"><template #default="{ row }">{{ row.createTime }}</template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status===1?'danger':'success'" @click="toggleStatus(row)">
              {{ row.status===1?'禁用':'启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchList" style="margin-top:16px; justify-content:flex-end" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, updateUserStatus } from '@/api/admin/user'

const list = ref<any[]>([]); const total = ref(0); const loading = ref(false)
const query = reactive<any>({ page: 1, size: 20, phone: '', status: undefined })

async function fetchList() {
  loading.value = true
  try {
    const p: any = { page: query.page, size: query.size }
    if (query.phone) p.phone = query.phone
    if (query.status !== undefined && query.status !== null && query.status !== '') p.status = query.status
    const res: any = await getUserList(p)
    list.value = res.records || []; total.value = res.total || 0
  } finally { loading.value = false }
}
function search() { query.page = 1; fetchList() }
function reset() { query.phone = ''; query.status = undefined; search() }

async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    fetchList()
  } catch { /* 错误已在 request 层处理 */ }
}

onMounted(fetchList)
</script>
<style scoped>.page-container { display: flex; flex-direction: column; gap: 16px; }</style>
