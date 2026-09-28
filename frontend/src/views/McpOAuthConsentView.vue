<template>
  <section class="oauth-page">
    <div class="oauth-card">
      <div class="oauth-mark">MCP</div>
      <p class="eyebrow">OAUTH 2.1 · PKCE</p>
      <h1>授权外部应用</h1>
      <p v-if="loading" class="muted">正在核对授权请求…</p>
      <div v-else-if="error" class="error" role="alert">{{ error }}</div>
      <template v-else-if="preview">
        <div class="client"><b>{{ preview.clientName }}</b><span>{{ callbackOrigin }}</span></div>
        <p class="muted">该应用申请访问你的个人工作台。批准后可随时在“设置 → 外部 Agent / MCP”撤销。</p>
        <div class="scope-list">
          <label v-for="scope in preview.scopes" :key="scope"><span>✓</span><div><b>{{ scopeLabel(scope) }}</b><small>{{ scopeDescription(scope) }}</small></div></label>
        </div>
        <fieldset v-if="preview.books?.length">
          <legend>允许访问的账本</legend>
          <label v-for="book in preview.books" :key="book.id"><input v-model="selectedBookIds" type="checkbox" :value="book.id" />{{ book.name }}</label>
          <small>至少选择一个账本。后续新增账本不会自动加入本次授权。</small>
        </fieldset>
        <div class="security"><b>安全说明</b><span>本站只接受登记过的精确回调地址，必须使用 PKCE S256；外部应用不会获得你的登录密码。</span></div>
        <div class="actions"><Button variant="ghost" :disabled="busy" @click="decide(false)">拒绝</Button><Button :disabled="busy || (preview.books?.length && !selectedBookIds.length)" @click="decide(true)">{{ busy ? '处理中…' : '允许访问' }}</Button></div>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Button from '../components/ui/Button.vue'
import { apiDecideMcpOAuthAuthorization, apiPreviewMcpOAuthAuthorization } from '../../packages/api-client/src/index.js'

const route = useRoute()
const loading = ref(true), busy = ref(false), error = ref(''), preview = ref(null), selectedBookIds = ref([])
const request = computed(() => ({
  response_type: String(route.query.response_type || ''), client_id: String(route.query.client_id || ''),
  redirect_uri: String(route.query.redirect_uri || ''), scope: String(route.query.scope || ''),
  state: route.query.state == null ? null : String(route.query.state), code_challenge: String(route.query.code_challenge || ''),
  code_challenge_method: String(route.query.code_challenge_method || ''), resource: String(route.query.resource || '')
}))
const commandRequest = computed(() => ({ responseType: request.value.response_type, clientId: request.value.client_id,
  redirectUri: request.value.redirect_uri, scope: request.value.scope, state: request.value.state,
  codeChallenge: request.value.code_challenge, codeChallengeMethod: request.value.code_challenge_method,
  resource: request.value.resource }))
const callbackOrigin = computed(() => { try { return new URL(preview.value?.redirectUri || '').origin } catch { return preview.value?.redirectUri || '' } })
const labels = { 'mcp:ledger:read': '查询账本', 'mcp:ledger:prepare': '准备账本操作', 'mcp:ledger:commit': '提交低风险账本操作', 'mcp:worktime:read': '查询工时', 'mcp:worktime:prepare': '准备工时操作', 'mcp:worktime:commit': '提交低风险工时操作' }
function scopeLabel(value) { return labels[value] || value }
function scopeDescription(value) { return value.endsWith(':read') ? '只读取当前数据' : value.endsWith(':prepare') ? '生成预览和站内确认，不直接写入' : '仅提交已经在本站批准的 R2 操作' }
async function load() { loading.value = true; try { preview.value = await apiPreviewMcpOAuthAuthorization(request.value); selectedBookIds.value = [...(preview.value.selectedBookIds || [])] } catch (e) { error.value = e?.response?.data?.detail || '授权请求无效或已过期' } finally { loading.value = false } }
async function decide(approved) { if (busy.value) return; busy.value = true; try { const result = await apiDecideMcpOAuthAuthorization({ request: commandRequest.value, approved, bookIds: selectedBookIds.value }); window.location.assign(result.redirectUrl) } catch (e) { error.value = e?.response?.data?.detail || '授权处理失败'; busy.value = false } }
onMounted(load)
</script>

<style scoped>
.oauth-page{min-height:calc(100vh - 84px);display:grid;place-items:center;padding:28px 16px}.oauth-card{width:min(620px,100%);background:var(--surface,#fff);border:1px solid var(--line,#d8d8d8);border-radius:24px;padding:30px;box-shadow:0 24px 80px #00000012}.oauth-mark{width:48px;height:48px;border-radius:15px;display:grid;place-items:center;background:#111;color:#fff;font-weight:900}.eyebrow{font-size:12px;letter-spacing:.16em;color:var(--muted,#777);margin:20px 0 6px}.oauth-card h1{font-size:28px;margin:0 0 18px}.muted{color:var(--muted,#666);line-height:1.7}.client{display:flex;justify-content:space-between;gap:16px;padding:16px;border-radius:14px;background:var(--soft,#f5f5f5)}.client span{font-size:12px;color:var(--muted,#666);overflow-wrap:anywhere}.scope-list{display:grid;gap:10px;margin:20px 0}.scope-list label{display:flex;gap:12px;padding:12px;border:1px solid var(--line,#ddd);border-radius:12px}.scope-list label>span{color:#199d55}.scope-list b,.scope-list small{display:block}.scope-list small,fieldset small{color:var(--muted,#666);margin-top:4px}fieldset{border:1px solid var(--line,#ddd);border-radius:14px;padding:14px;display:grid;gap:10px}fieldset label{display:flex;gap:8px}.security{display:grid;gap:4px;margin:18px 0;padding:14px;border-left:4px solid #f2b84b;background:#f2b84b18}.security span{font-size:13px;line-height:1.6}.actions{display:flex;justify-content:flex-end;gap:10px}.error{padding:16px;border-radius:12px;background:#d83b3b18;color:#b42318}@media(max-width:640px){.oauth-card{padding:22px;border-radius:18px}.client{display:grid}.actions>*{flex:1}}
</style>
