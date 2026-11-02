<template>
  <section id="data" class="task-data-panel" :class="{ 'is-flat': hideHeading }" :aria-labelledby="hideHeading ? undefined : 'task-data-title'">
    <h2 v-if="!hideHeading" id="task-data-title">导入与导出</h2>
    <p v-if="error" class="data-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="data-notice" role="status">{{ notice }}</p>
    <div class="data-block">
      <h3>导入任务</h3>
      <p>支持 Excel、CSV、ICS，单文件最多 10 MB、10,000 行、20 个 Sheet。预览阶段不会写入任务。</p>
      <div class="data-file">
        <input ref="fileInput" class="data-file-native" type="file" accept=".xlsx,.xls,.csv,.ics" aria-label="选择要导入的文件" @change="previewImport">
        <button type="button" class="data-file-pick" @click="fileInput?.click()">选择文件</button>
        <span class="data-file-name">{{ fileName || '未选择文件（xlsx / xls / csv / ics）' }}</span>
      </div>
      <progress v-if="uploadProgress && !preview" max="100" :value="uploadProgress"></progress>
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

// hideHeading：嵌进弹窗时由弹窗标题承担，面板自己不重复一行标题
defineProps({ hideHeading: { type: Boolean, default: false } })

const store = useTasksStore()
const fileInput = ref(null)
const fileName = ref('')
const error = ref('')
const notice = ref('')
const preview = ref(null)
const uploadProgress = ref(0)
function detail(value) { return value?.response?.data?.detail || value?.message || '操作失败' }
function dueLabel(value) { return value ? new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '无日期' }
async function previewImport(event) {
  const file = event.target.files?.[0]
  if (!file) return
  fileName.value = file.name
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
.task-data-panel{display:grid;gap:16px}.task-data-panel.is-flat .data-block:first-of-type{padding-top:0;border-top:0}.task-data-panel>h2,.data-block h3{margin:0}.data-block{display:grid;gap:10px;padding-top:16px;border-top:1px solid var(--line)}.data-block p{margin:0;color:var(--muted)}.data-file{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.data-file-native{position:absolute;width:1px;height:1px;opacity:0;pointer-events:none}.data-file-name{color:var(--muted);font-size:12px}progress{width:100%;height:4px;accent-color:var(--accent)}.summary,.export-actions{display:flex;gap:10px;flex-wrap:wrap}.summary b{padding:8px 12px;background:var(--panel2)}button{min-height:38px;border:1px solid var(--line2);background:var(--card);color:var(--ink);padding:7px 12px;cursor:pointer}.preview-table{max-height:420px;overflow:auto}.preview-table table{width:100%;min-width:720px;border-collapse:collapse}.preview-table th,.preview-table td{text-align:left;padding:9px;border-bottom:1px solid var(--line)}.data-error,.data-notice{padding:10px 12px;border-left:3px solid var(--down);background:color-mix(in srgb,var(--down) 8%,transparent)}.data-notice{border-color:var(--accent);background:var(--accent-soft)}
</style>
