<template>
  <div class="ledger-book-switcher">
    <label>
      当前账本
      <select v-model="selected" :disabled="ledger.loading || !ledger.books.length" @change="changeBook">
        <option v-for="book in ledger.books" :key="book.id" :value="book.id">{{ book.name }}{{ book.roleCode === 'OWNER' ? '' : ` · ${book.roleName || '成员'}` }}</option>
      </select>
    </label>
    <Button size="sm" variant="ghost" @click="open = true">新建账本</Button>
    <Sheet v-model:open="open" title="新建账本">
      <form class="book-form" @submit.prevent="create">
        <label>名称<input v-model="form.name" class="ui-input" required maxlength="120" placeholder="例如：家庭账本" /></label>
        <label>币种<input v-model="form.currency" class="ui-input" maxlength="3" required /></label>
        <label>初始化方式<select v-model="form.mode"><option value="BLANK">空白账本</option><option value="SYSTEM_TEMPLATE">系统基础模板</option><option value="COPY">复制已有账本</option></select></label>
        <label v-if="form.mode === 'COPY'">复制来源<select v-model="form.sourceBookId" required><option v-for="book in ledger.books" :key="book.id" :value="book.id">{{ book.name }}</option></select></label>
        <div class="book-form-actions"><Button type="button" variant="ghost" @click="open = false">取消</Button><Button type="submit" :disabled="saving">{{ saving ? '创建中…' : '创建账本' }}</Button></div>
      </form>
    </Sheet>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useLedgerStore } from '../../stores/ledger'
import Button from '../ui/Button.vue'
import Sheet from '../ui/Sheet.vue'

const ledger = useLedgerStore()
const selected = ref('')
const open = ref(false)
const saving = ref(false)
const form = reactive({ name: '', currency: 'CNY', mode: 'BLANK', sourceBookId: '' })
watch(() => ledger.currentBookId, value => { selected.value = value || '' }, { immediate: true })

async function changeBook() {
  try {
    await ledger.selectBook(selected.value)
    window.dispatchEvent(new CustomEvent('ledger-book-changed'))
  } catch (error) {
    selected.value = ledger.currentBookId
    ElMessage.error(error?.response?.data?.detail || error?.message || '切换账本失败')
  }
}
async function create() {
  saving.value = true
  try {
    await ledger.createBook({
      name: form.name,
      currency: form.currency.toUpperCase(),
      mode: form.mode,
      sourceBookId: form.mode === 'COPY' ? form.sourceBookId : undefined
    })
    Object.assign(form, { name: '', currency: 'CNY', mode: 'BLANK', sourceBookId: '' })
    open.value = false
    window.dispatchEvent(new CustomEvent('ledger-book-changed'))
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || error?.message || '新建账本失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.ledger-book-switcher { display:flex; align-items:flex-end; gap:10px; margin-bottom:14px; padding:10px 12px; border:1px solid var(--line); border-radius:4px; background:var(--card) }
.ledger-book-switcher label,.book-form label { display:flex; min-width:0; flex-direction:column; gap:5px; color:var(--muted); font-size:11px }
.ledger-book-switcher select { min-width:0; width:100%; height:32px; border:1px solid var(--line2); border-radius:3px; background:var(--paper); color:var(--ink); padding:0 8px }
.book-form { display:flex; flex-direction:column; gap:13px }.book-form select,.book-form input { box-sizing:border-box; width:100%; min-width:0 }.book-form-actions { display:flex; justify-content:flex-end; gap:8px; padding-top:4px }
@media(max-width:560px){.ledger-book-switcher{align-items:stretch;flex-direction:column}.ledger-book-switcher select{width:100%}}
</style>
