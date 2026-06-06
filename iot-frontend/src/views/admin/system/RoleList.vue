<template>
  <div class="page-container">
    <el-card class="table-card">
      <div class="toolbar"><el-button type="primary" @click="openDialog()">新增角色</el-button></div>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="角色名称" min-width="140" />
        <el-table-column prop="code" label="角色编码" width="150" />
        <el-table-column prop="description" label="描述" min-width="180" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'启用':'禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" :type="row.status===1?'warning':'success'" @click="toggleStatus(row)">
              {{ row.status===1?'禁用':'启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchList" style="margin-top:16px; justify-content:flex-end" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="角色名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="如 ROLE_EDITOR" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="权限分配">
          <el-tree
            ref="treeRef"
            :data="permissionTree"
            show-checkbox
            node-key="id"
            :default-checked-keys="form.permissionIds"
            :props="{ label: 'name', children: 'children' }"
          />
        </el-form-item>
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
import type { ElTree } from 'element-plus'
import * as api from '@/api/admin/role'

const list = ref<any[]>([]); const total = ref(0); const loading = ref(false)
const query = reactive({ page: 1, size: 20 })
const dialogVisible = ref(false); const isEdit = ref(false); const editId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>(); const treeRef = ref<InstanceType<typeof ElTree>>()
const form = reactive({ name: '', code: '', description: '', permissionIds: [] as number[] })
const formRules: FormRules = { name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }], code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }] }
const permissionTree = ref<any[]>([])

async function fetchList() {
  loading.value = true
  try {
    const res: any = await api.getRoleList({ page: query.page, size: query.size })
    list.value = res.records || []; total.value = res.total || 0
  } finally { loading.value = false }
}

async function openDialog(row?: any) {
  await loadPermissionTree()
  isEdit.value = !!row
  if (row) {
    editId.value = row.id
    try {
      const detail: any = await api.getRoleDetail(row.id)
      form.name = detail.name; form.code = detail.code; form.description = detail.description || ''
      form.permissionIds = detail.permissionIds || []
    } catch { form.permissionIds = [] }
  } else {
    editId.value = null; form.name = ''; form.code = ''; form.description = ''; form.permissionIds = []
  }
  dialogVisible.value = true
}

async function loadPermissionTree() {
  try { permissionTree.value = (await api.getPermissionTree()) as unknown as any[] } catch { permissionTree.value = [] }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const checkedKeys = treeRef.value?.getCheckedKeys() as number[] || []
  const halfCheckedKeys = treeRef.value?.getHalfCheckedKeys() as number[] || []
  const allPermissionIds = [...checkedKeys, ...halfCheckedKeys]
  submitting.value = true
  try {
    const data = { name: form.name, code: form.code, description: form.description, permissionIds: allPermissionIds }
    if (isEdit.value) {
      await api.updateRole(editId.value!, data); ElMessage.success('修改成功')
    } else {
      await api.createRole(data); ElMessage.success('新增成功')
    }
    dialogVisible.value = false; fetchList()
  } finally { submitting.value = false }
}

async function toggleStatus(row: any) { try { await api.updateRoleStatus(row.id, row.status === 1 ? 0 : 1); ElMessage.success('状态已更新'); fetchList() } catch { /* error handled by request layer */ } }
async function handleDelete(id: number) { try { await ElMessageBox.confirm('确定删除此角色吗？', '删除确认', { type: 'warning' }); await api.deleteRole(id); ElMessage.success('删除成功'); fetchList() } catch { /* user cancelled or error */ } }

onMounted(fetchList)
</script>
<style scoped>.page-container { display: flex; flex-direction: column; gap: 16px; } .toolbar { margin-bottom: 16px; }</style>
