<template>
  <div class="page-container">
    <el-card class="search-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item label="用户ID"><el-input v-model="query.userId" placeholder="可选" clearable /></el-form-item>
        <el-form-item label="模块"><el-input v-model="query.module" placeholder="可选" clearable /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="operation" label="操作" min-width="150" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'成功':'失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时(ms)" width="90" />
        <el-table-column label="时间" width="160"><template #default="{ row }">{{ row.createTime }}</template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchList" style="margin-top:16px; justify-content:flex-end" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getOperationLogList } from '@/api/admin/operationLog'

const list = ref<any[]>([]); const total = ref(0); const loading = ref(false)
const query = reactive<any>({ page: 1, size: 20, userId: '', module: '' })

async function fetchList() {
  loading.value = true
  try {
    const p: any = { page: query.page, size: query.size }
    if (query.userId) p.userId = query.userId
    if (query.module) p.module = query.module
    const res: any = await getOperationLogList(p)
    list.value = res.records || []; total.value = res.total || 0
  } finally { loading.value = false }
}
function search() { query.page = 1; fetchList() }
function reset() { query.userId = ''; query.module = ''; search() }

onMounted(fetchList)
</script>
<style scoped>.page-container { display: flex; flex-direction: column; gap: 16px; }</style>
