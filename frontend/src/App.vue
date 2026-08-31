<template>
  <div v-if="store.ready">
    <header class="app-header">
      <h1>真实时薪<small>REAL HOURLY RATE</small></h1>
      <div class="header-right">
        <el-tooltip :content="store.theme === 'light' ? '切换到夜间模式' : '切换到日间模式'">
          <el-button circle size="small" @click="store.toggleTheme()">{{ store.theme === 'light' ? '🌙' : '☀️' }}</el-button>
        </el-tooltip>
        <button class="sync-badge" :class="{ db: store.dbMode }" @click="onSyncClick" :title="store.dbMode ? '已连接数据库，点击查看' : '数据存储状态，点击重试连接'">
          <span class="dot"></span><span>{{ store.dbMode ? '数据库' : '本地' }}</span>
        </button>
        <div class="basis-pill">
          <button class="seg" :class="{ on: basis === 'pre' }" @click="setBasis('pre')">税前</button>
          <button class="seg" :class="{ on: basis === 'post' }" @click="setBasis('post')">税后</button>
        </div>
      </div>
    </header>

    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

    <BottomNav />

    <!-- 访问口令对话框 -->
    <el-dialog v-model="store.needCode" title="访问保护" width="320px" :close-on-click-modal="false"
               :show-close="false" class="code-dialog" append-to-body>
      <el-input v-model="codeInput" placeholder="请输入访问口令" show-password
                @keyup.enter="submitCode" autofocus />
      <template #footer>
        <el-button @click="store.cancelAccessCode()">取消</el-button>
        <el-button type="primary" @click="submitCode">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from './stores/app'
import BottomNav from './components/BottomNav.vue'

const store = useAppStore()
const codeInput = ref('')

const basis = computed(() => store.settings.basis)

function setBasis(v) {
  if (store.settings.basis === v) return
  store.settings.basis = v
  store.saveAll()
}

function onSyncClick() {
  if (!store.dbMode) { store.connectDb().then(() => {
    store.dbMode ? ElMessage.success('已连接数据库') : ElMessage.warning('数据库仍不可用')
  }); return }
  ElMessage.success('本地服务已连接，数据安全存储在数据库')
}

function submitCode() {
  if (!codeInput.value.trim()) { ElMessage.warning('请输入访问口令'); return }
  store.submitAccessCode(codeInput.value.trim())
  codeInput.value = ''
}

onMounted(() => store.init())
</script>
