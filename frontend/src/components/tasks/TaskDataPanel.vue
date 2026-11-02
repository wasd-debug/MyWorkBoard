<template>
  <section id="data" class="task-data-panel" aria-labelledby="task-data-title">
    <h2 id="task-data-title">导入与导出</h2>
    <p v-if="error" class="data-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="data-notice" role="status">{{ notice }}</p>
    <div class="data-block">
      <h3>导入任务</h3>
      <p>支持 Excel、CSV、ICS，单文件最多 10 MB、10,000 行、20 个 Sheet。预览阶段不会写入任务。</p>
      <input type="file" accept=".xlsx,.xls,.csv,.ics" @change="previewImport">
      <progress v-if="uploadProgress" max="100" :value="uploadProgress"></progress>
      <div v-if="preview" class="preview">
        <div class="summary"><b>有效 {{ preview.valid }}</b><b>重复 {{ preview.duplicates }}</b><b>错误 {{ preview.errors }}</b></div>
        <div class="preview-table"><table><thead><tr><th>行</th><th>状态</th><th>标题</th><th>清单</th><th>到期</th><th>说明</th></tr></thead><tbody><tr v-for="row in preview.rows.slice(0, 200)" :key="`${row.sheet}-${row.rowNumber}`"><td>{{ row.sheet }} · {{ row.rowNumber }}</td><td>{{ row.state }}</td><td>{{ row.title }}</td><td>{{ row.listName }}</td><td>{{ dueLabel(row.dueAt) }}</td><td>{{ row.message }}</td></tr></tbody></table></div>
        <button :disabled="!preview.valid" @click="commitImport">确认导入 {{ preview.valid }} 条</button>
      </div>
    </div>
    <div class="data-block">
      <h3>导出任务</h3>
      <div class="export-actions"><button @click="download('xlsx')">导出 Excel</button><button @click="download('csv')">导出 CSV</button><button @click="download('ics')">导出 ICS</button></div>
      <p>CSV 与 Excel 会转义以 =、+、-、@ 开头的单元格，避免公式注入。</p>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { apiCommitTaskImport, apiDownloadTaskExport, apiPreviewTaskImport } from '../../../packages/api-client/src/index.js'
import { useTasksStore } from '../../stores/tasks.js'

const store = useTasksStore()
const error = ref('')
const notice = ref('')
const preview = ref(null)
const uploadProgress = ref(0)
function detail(value) { return value?.response?.data?.detail || value?.message || '操作失败' }
function dueLabel(value) { return value ? new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '无日期' }
async function previewImport(event) {
  const file = event.target.files?.[0]
  if (!file) return
  error.value = ''
  notice.value = ''
  try {
    uploadProgress.value = 1
    preview.value = await apiPreviewTaskImport(file, value => { uploadProgress.value = value })
    uploadProgress.value = 100
  } catch (value) {
    error.value = detail(value)
    uploadProgress.value = 0
  }
}
async function commitImport() {
  error.value = ''
  try {
    const result = await apiCommitTaskImport(preview.value.previewId, true)
    await store.refreshServer()
    notice.value = `已导入 ${result.created} 条，跳过 ${result.skipped} 条`
    preview.value = null
  } catch (value) { error.value = detail(value) }
}
async function download(format) {
  error.value = ''
  try {
    const blob = await apiDownloadTaskExport(format, true)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `tasks.${format}`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (value) { error.value = detail(value) }
}
</script>

<style scoped>
.task-data-panel{display:grid;gap:16px}.task-data-panel>h2,.data-block h3{margin:0}.data-block{display:grid;gap:10px;padding-top:16px;border-top:1px solid var(--line)}.data-block p{margin:0;color:var(--muted)}input[type=file]{max-width:100%}progress{width:100%}.summary,.export-actions{display:flex;gap:10px;flex-wrap:wrap}.summary b{padding:8px 12px;background:var(--panel2)}button{min-height:38px;border:1px solid var(--line2);background:var(--card);color:var(--ink);padding:7px 12px;cursor:pointer}.preview-table{max-height:420px;overflow:auto}.preview-table table{width:100%;min-width:720px;border-collapse:collapse}.preview-table th,.preview-table td{text-align:left;padding:9px;border-bottom:1px solid var(--line)}.data-error,.data-notice{padding:10px 12px;border-left:3px solid var(--down);background:color-mix(in srgb,var(--down) 8%,transparent)}.data-notice{border-color:var(--accent);background:var(--accent-soft)}
</style>
