<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProjects, createProject, updateProject, deleteProject } from '../../api/project'

const router = useRouter()
const projects = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref(null)
const form = ref({ name: '', description: '' })

const rules = {
  name: [
    { required: true, message: '请输入项目名', trigger: 'blur' },
    { max: 100, message: '项目名最长 100 个字符', trigger: 'blur' },
  ],
  description: [{ max: 500, message: '描述最长 500 个字符', trigger: 'blur' }],
}

const roleText = (r) => ({ 0: '所有者', 1: '成员', 2: '只读' })[r] || '未知'
const roleType = (r) => ({ 0: 'primary', 1: 'success', 2: 'info' })[r] || 'info'

async function load() {
  loading.value = true
  try {
    projects.value = await listProjects()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = null
  form.value = { name: '', description: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  if (row.myRole !== 0) {
    ElMessage.warning('仅项目所有者可编辑项目')
    return
  }
  editingId.value = row.id
  form.value = { name: row.name, description: row.description || '' }
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (editingId.value) {
      await updateProject(editingId.value, form.value)
      ElMessage.success('项目已更新')
    } else {
      await createProject(form.value)
      ElMessage.success('项目已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  if (row.myRole !== 0) {
    ElMessage.warning('仅项目所有者可删除项目')
    return
  }
  await ElMessageBox.confirm(`确定删除项目「${row.name}」吗？项目下的接口、用例将一并删除。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteProject(row.id)
  ElMessage.success('已删除')
  load()
}

function goDetail(row) {
  router.push(`/projects/${row.id}`)
}
</script>

<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的项目</span>
          <el-button type="primary" @click="openCreate">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>新建项目
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="projects" @row-click="goDetail" style="cursor: pointer">
        <el-table-column prop="name" label="项目名" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="我的角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="roleType(row.myRole)">{{ roleText(row.myRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="所有者" width="120">
          <template #default="{ row }">{{ row.ownerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.myRole === 0">
              <el-button size="small" @click.stop="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有项目，点击右上角「新建项目」开始" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑项目' : '新建项目'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="项目名" prop="name">
          <el-input v-model="form.name" placeholder="例如：订单服务" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="项目描述（可选）" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>