<template>
  <section class="ledger-management">
    <LoadingOverlay :open="managementLoading || saving || Boolean(exportingBookId)" :label="exportingBookId ? '正在导出账本…' : saving ? '正在保存更改…' : '正在读取管理数据…'" />
    <div class="manager-heading">
      <div>
        <p class="manager-overline">账本设置 / {{ activeTab.label }}</p>
        <div class="manager-title-line">
          <h1>{{ activeTab.title }}</h1>
          <div v-if="tab === 'accounts'" class="account-totals">
            <span>净资产 <b class="num" :class="netAssets >= 0 ? 'down' : 'up'">¥{{ money(netAssets) }}</b></span>
            <span>总资产 <b class="num">¥{{ money(totalAssets) }}</b></span>
            <span>总负债 <b class="num">¥{{ money(totalLiabilities) }}</b></span>
          </div>
        </div>
        <p class="manager-description">{{ activeTab.description }}</p>
      </div>
      <div class="manager-heading-actions">
        <LedgerActionIcon
          v-if="tab === 'members' && memberSection === 'roles'"
          action="add"
          label="新增角色"
          class="manager-create"
          @click="openCreate('role')"
        />
        <LedgerActionIcon
          v-if="activeTab.action && (tab !== 'members' || memberSection === 'members')"
          action="add"
          :label="activeTab.action"
          class="manager-create"
          @click="openCreate()"
        />
      </div>
    </div>

    <nav v-if="managementTabKeys.has(tab)" class="manager-main-tabs" aria-label="管理分类">
      <button
        v-for="item in managementTabs"
        :key="item.key"
        type="button"
        :class="{ active: tab === item.key }"
        @click="selectManagementTab(item.key)"
      >
        {{ item.label }}
      </button>
    </nav>

    <nav v-if="tab === 'members'" class="manager-main-tabs" aria-label="成员与角色权限">
      <button
        v-for="item in memberSections"
        :key="item.key"
        type="button"
        :class="{ active: memberSection === item.key }"
        @click="selectMemberSection(item.key)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div class="manager-toolbar">
      <div v-if="tab === 'accounts'" class="segment-control">
        <button type="button" :class="{ active: accountScope === 'asset' }" @click="accountScope = 'asset'">资产账户</button>
        <button type="button" :class="{ active: accountScope === 'liability' }" @click="accountScope = 'liability'">负债账户</button>
      </div>
      <div v-else-if="tab === 'categories'" class="segment-control">
        <button type="button" :class="{ active: categoryKind === 'EXPENSE' }" @click="categoryKind = 'EXPENSE'">支出分类</button>
        <button type="button" :class="{ active: categoryKind === 'INCOME' }" @click="categoryKind = 'INCOME'">收入分类</button>
      </div>
      <div v-else class="toolbar-summary">
        <b>{{ visibleCount }}</b>
        <span>{{ visibleUnit }}</span>
      </div>

      <button
        v-if="hideableTab"
        type="button"
        class="visibility-toggle"
        :aria-pressed="showHidden"
        @click="showHidden = !showHidden"
      >
        <span class="check-box"><span v-if="showHidden">✓</span></span>
        显示已隐藏的{{ activeTab.unit }}
      </button>

      <LedgerActionIcon v-if="tab === 'recycle' || tab === 'audit'" action="refresh" label="刷新列表" @click="loadSecondary" />
    </div>

    <Card class="manager-table-card">
      <template v-if="tab === 'accounts'">
        <div class="manager-table account-table">
          <div class="table-header">
            <span>账户名称</span><span>余额</span><span>币种</span><span>状态</span><span>操作</span>
          </div>
          <Empty v-if="!accountGroups.length" description="当前没有账户" />
          <template v-for="group in accountGroups" :key="group.key">
            <button type="button" class="group-row table-row" @click="toggleGroup(group.key)">
              <span class="cell-primary">
                <component :is="collapsedGroups.has(group.key) ? ArrowRight : ArrowDown" />
                <b>{{ group.label }}</b>
              </span>
              <strong class="num">{{ money(group.total) }}</strong>
              <span>{{ ledger.currentBook?.currency || 'CNY' }}</span>
              <span>{{ group.items.length }} 个账户</span>
              <span />
            </button>
            <div
              v-for="item in collapsedGroups.has(group.key) ? [] : group.items"
              :key="item.id"
              class="table-row data-row"
              :class="{ hidden: item.hidden }"
            >
              <span class="cell-primary child-cell">
                <LedgerResourceIcon :icon="item.icon" type="account" />
                <button type="button" class="entity-link" @click="openResourceTransactions('account', item)">
                  <b>{{ item.name }}</b>
                </button>
              </span>
              <strong class="num">{{ money(item.balance) }}</strong>
              <span>{{ item.currency }}</span>
              <span><i class="status-dot" :class="{ muted: item.hidden }" />{{ item.hidden ? '已隐藏' : '正常' }}</span>
              <span class="row-actions">
                <LedgerActionIcon action="edit" label="编辑账户" @click="openEdit(item)" />
                <LedgerActionIcon :action="item.hidden ? 'show' : 'hide'" :label="item.hidden ? '显示账户' : '隐藏账户'" @click="toggleHidden('account', item)" />
                <LedgerActionIcon action="delete" label="删除账户" @click="askDelete('account', item)" />
              </span>
            </div>
          </template>
        </div>
      </template>

      <template v-else-if="tab === 'categories'">
        <div class="manager-table category-table">
          <div class="table-header">
            <span>分类名称</span><span>{{ categoryKind === 'EXPENSE' ? '支出' : '收入' }}</span><span>状态</span><span>操作</span>
          </div>
          <Empty v-if="!categoryGroups.length" description="当前没有分类" />
          <template v-for="group in categoryGroups" :key="group.id">
            <div class="table-row group-row">
              <span class="cell-primary">
                <button type="button" class="disclosure" :aria-label="collapsedGroups.has(group.id) ? '展开分类' : '收起分类'" @click="toggleGroup(group.id)">
                <component :is="collapsedGroups.has(group.id) ? ArrowRight : ArrowDown" />
                </button>
                <LedgerResourceIcon :icon="group.icon" type="category" :color="group.color" compact />
                <button type="button" class="entity-link" @click="openResourceTransactions('category', group)">
                  <b>{{ group.name }}</b>
                </button>
              </span>
              <strong class="num">{{ money(categoryAmount(group)) }}</strong>
              <span>{{ group.hidden ? '已隐藏' : `${group.children.length} 个二级分类` }}</span>
              <span class="row-actions">
                <LedgerActionIcon action="edit" label="编辑一级分类" @click="openEdit(group)" />
                <LedgerActionIcon :action="group.hidden ? 'show' : 'hide'" :label="group.hidden ? '显示分类' : '隐藏分类'" @click="toggleHidden('category', group)" />
                <LedgerActionIcon action="delete" label="删除一级分类" @click="askDelete('category', group)" />
              </span>
            </div>
            <div
              v-for="item in collapsedGroups.has(group.id) ? [] : group.children"
              :key="item.id"
              class="table-row data-row"
              :class="{ hidden: item.hidden }"
            >
              <span class="cell-primary child-cell">
                <LedgerResourceIcon :icon="item.icon" type="category" :color="item.color" compact />
                <button type="button" class="entity-link" @click="openResourceTransactions('category', item)">
                  <b>{{ item.name }}</b>
                </button>
              </span>
              <strong class="num">{{ money(categoryAmount(item)) }}</strong>
              <span>{{ item.hidden ? '已隐藏' : '可用' }}</span>
              <span class="row-actions">
                <LedgerActionIcon action="edit" label="编辑二级分类" @click="openEdit(item)" />
                <LedgerActionIcon :action="item.hidden ? 'show' : 'hide'" :label="item.hidden ? '显示分类' : '隐藏分类'" @click="toggleHidden('category', item)" />
                <LedgerActionIcon action="delete" label="删除二级分类" @click="askDelete('category', item)" />
              </span>
            </div>
          </template>
        </div>
      </template>

      <template v-else-if="tab === 'merchants' || tab === 'projects'">
        <div class="manager-table simple-table">
          <div class="table-header">
            <span>{{ tab === 'merchants' ? '商家名称' : '项目名称' }}</span><span>结余</span><span>备注</span><span>操作</span>
          </div>
          <Empty v-if="!filteredNamedItems.length" :description="`当前没有${activeTab.unit}`" />
          <div
            v-for="item in filteredNamedItems"
            :key="item.id"
            class="table-row data-row"
            :class="{ hidden: item.hidden }"
          >
            <span class="cell-primary">
              <LedgerResourceIcon :icon="item.icon" :type="namedType" :color="tab === 'projects' ? item.color : ''" />
              <button type="button" class="entity-link" @click="openResourceTransactions(namedType, item)">
                <b>{{ item.name }}</b>
              </button>
            </span>
            <strong class="num" :class="resourceFlow(item) >= 0 ? 'down' : 'up'">{{ signedMoney(resourceFlow(item)) }}</strong>
            <span class="truncate-note" :title="item.note">{{ item.note || '—' }}</span>
            <span class="row-actions">
              <LedgerActionIcon action="edit" :label="`编辑${activeTab.unit}`" @click="openEdit(item)" />
              <LedgerActionIcon :action="item.hidden ? 'show' : 'hide'" :label="item.hidden ? '显示条目' : '隐藏条目'" @click="toggleHidden(namedType, item)" />
              <LedgerActionIcon action="delete" :label="`删除${activeTab.unit}`" @click="askDelete(namedType, item)" />
            </span>
          </div>
        </div>
      </template>

      <template v-else-if="tab === 'members'">
        <div v-if="memberSection === 'members'" class="manager-table member-table">
          <div class="table-header">
            <span>成员</span><span>用户名</span><span>角色</span><span>结余</span><span>操作</span>
          </div>
          <Empty v-if="!ledger.members.length" description="当前没有成员" />
          <div v-for="item in ledger.members" :key="item.id" class="table-row data-row">
            <span class="cell-primary"><LedgerResourceIcon :icon="item.icon" type="member" /><b>{{ item.displayName }}</b></span>
            <span>{{ item.username }}</span>
            <span><em class="role-badge">{{ item.roleName }}</em></span>
            <strong class="num" :class="resourceFlow(item) >= 0 ? 'down' : 'up'">{{ signedMoney(resourceFlow(item)) }}</strong>
            <span class="row-actions">
              <LedgerActionIcon action="edit" :label="item.roleCode === 'OWNER' ? '编辑主人图标' : '编辑成员'" @click="openEdit(item, 'member')" />
              <LedgerActionIcon v-if="item.roleCode !== 'OWNER'" action="delete" label="移除成员" @click="askDelete('member', item)" />
            </span>
          </div>
        </div>
        <div v-else class="manager-table role-table">
          <div class="table-header">
            <span>角色</span><span>类型</span><span>权限</span><span>操作</span>
          </div>
          <Empty v-if="!ledger.roles.length" description="当前没有角色" />
          <div v-for="item in ledger.roles" :key="item.id" class="table-row data-row">
            <span class="cell-primary"><span class="resource-icon"><Lock /></span><b>{{ item.name }}</b></span>
            <span>{{ item.systemRole ? '系统角色' : '自定义角色' }}</span>
            <span class="permission-list">
              <em v-for="permission in item.permissions" :key="permission">{{ permissionLabel(permission) }}</em>
              <small v-if="!item.permissions.length">暂无权限</small>
            </span>
            <span class="row-actions">
              <LedgerActionIcon v-if="!item.systemRole" action="edit" label="编辑角色" @click="openEdit(item, 'role')" />
              <LedgerActionIcon v-if="!item.systemRole" action="delete" label="删除角色" @click="askDelete('role', item)" />
              <span v-else class="protected-label">系统保护</span>
            </span>
          </div>
        </div>
      </template>

      <template v-else-if="tab === 'recycle'">
        <div class="manager-table recycle-table">
          <div class="table-header"><span>已删除项目</span><span>类型</span><span>删除时间</span><span>操作</span></div>
          <Empty v-if="!recycle.items?.length" description="回收站为空" />
          <div v-for="item in recycle.items" :key="`${item.type}-${item.id}`" class="table-row data-row">
            <span class="cell-primary"><span class="resource-icon"><DeleteFilled /></span><b>{{ item.label }}</b></span>
            <span>{{ resourceTypeLabel(item.type) }}</span>
            <span>{{ formatTime(item.deletedAt) }}</span>
            <span class="row-actions">
              <LedgerActionIcon action="restore" label="恢复条目" @click="restore(item)" />
              <LedgerActionIcon v-if="canPurge" action="delete" label="永久删除条目" @click="askPurge(item)" />
            </span>
          </div>
          <div v-if="recycle.total" class="management-pagination">
            <label>每页<select v-model.number="recyclePageSize" @change="changeSecondaryPage('recycle', 1)"><option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条</option></select></label>
            <span>共 {{ recycle.total }} 条</span>
            <LedgerActionIcon action="previous" label="回收站上一页" :disabled="recycle.page <= 1" @click="changeSecondaryPage('recycle', recycle.page - 1)" />
            <b>{{ recycle.page }} / {{ recycle.totalPages }}</b>
            <LedgerActionIcon action="next" label="回收站下一页" :disabled="recycle.page >= recycle.totalPages" @click="changeSecondaryPage('recycle', recycle.page + 1)" />
          </div>
        </div>
      </template>

      <template v-else-if="tab === 'audit'">
        <div class="manager-table audit-table">
          <div class="table-header"><span>人员</span><span>操作</span><span>对象</span><span>时间</span></div>
          <Empty v-if="!audit.items?.length" description="暂无操作日志" />
          <div v-for="item in audit.items" :key="item.id" class="table-row data-row">
            <span class="cell-primary"><span class="member-avatar">{{ auditAvatar(item) }}</span><b>{{ item.actor?.nickname || item.actor?.username || '系统' }}</b></span>
            <span>{{ actionLabel(item.action) }}</span>
            <span class="audit-target"><small>{{ resourceTypeLabel(item.targetType) }}</small><b>{{ item.targetName || '已删除对象' }}</b></span>
            <span>{{ formatTime(item.createdAt) }}</span>
          </div>
          <div v-if="audit.total" class="management-pagination">
            <label>每页<select v-model.number="auditPageSize" @change="changeSecondaryPage('audit', 1)"><option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条</option></select></label>
            <span>共 {{ audit.total }} 条</span>
            <LedgerActionIcon action="previous" label="操作日志上一页" :disabled="audit.page <= 1" @click="changeSecondaryPage('audit', audit.page - 1)" />
            <b>{{ audit.page }} / {{ audit.totalPages }}</b>
            <LedgerActionIcon action="next" label="操作日志下一页" :disabled="audit.page >= audit.totalPages" @click="changeSecondaryPage('audit', audit.page + 1)" />
          </div>
        </div>
      </template>

      <template v-else>
        <div class="manager-table book-table">
          <div class="table-header"><span>账本</span><span>币种</span><span>成员</span><span>流水数量</span><span>我的角色</span><span>操作</span></div>
          <Empty v-if="!ledger.books.length" description="暂无可用账本" />
          <div v-for="item in ledger.books" :key="item.id" class="table-row data-row">
            <span class="cell-primary"><span class="resource-icon"><Management /></span><b>{{ item.name }}</b><em v-if="String(item.id) === String(ledger.currentBookId)" class="role-badge">当前</em></span>
            <span>{{ item.currency }}</span>
            <span>{{ item.memberCount }} 人</span>
            <strong class="num">{{ item.transactionCount || 0 }} 笔</strong>
            <span>{{ item.roleName }}</span>
            <span class="row-actions">
              <LedgerActionIcon v-if="String(item.id) !== String(ledger.currentBookId)" action="switch" label="切换到账本" @click="selectBook(item)" />
              <LedgerActionIcon action="download" label="导出账本流水" :disabled="exportingBookId === item.id" @click="exportBook(item)" />
              <LedgerActionIcon action="edit" label="编辑账本" @click="openEdit(item, 'book')" />
              <LedgerActionIcon v-if="item.roleCode === 'OWNER' && ledger.books.length > 1" action="delete" label="删除账本" @click="askDelete('book', item)" />
            </span>
          </div>
        </div>
      </template>
    </Card>

    <Dialog v-model:open="formOpen" :title="dialogTitle">
      <form class="manager-form" @submit.prevent="submitForm">
        <template v-if="formResource === 'account'">
          <label><span>账户名称</span><Input v-model="forms.account.name" placeholder="例如：招商银行卡" required /></label>
          <div class="form-grid">
            <label><span>账户类型</span><select v-model="forms.account.accountType"><option value="cash">现金</option><option value="bank">银行卡</option><option value="wallet">电子钱包</option><option value="card">信用卡</option><option value="other">其他账户</option></select></label>
            <label><span>币种</span><select v-model="forms.account.currency"><option value="CNY">CNY</option><option value="USD">USD</option><option value="HKD">HKD</option></select></label>
          </div>
          <label><span>期初余额</span><Input v-model.number="forms.account.openingBalance" type="number" step="0.01" /></label>
          <LedgerIconPicker v-model="forms.account.icon" type="account" label="账户图标" />
        </template>

        <template v-else-if="formResource === 'category'">
          <div class="form-grid">
            <label><span>收支类型</span><select v-model="forms.category.kind"><option value="EXPENSE">支出</option><option value="INCOME">收入</option></select></label>
            <label><span>分类层级</span><select v-model="forms.category.parentId"><option value="">一级分类</option><option v-for="parent in formParents" :key="parent.id" :value="parent.id">{{ parent.name }} 下的二级分类</option></select></label>
          </div>
          <label><span>分类名称</span><Input v-model="forms.category.name" placeholder="请输入分类名称" required /></label>
          <label><span>标识颜色</span><Input v-model="forms.category.color" type="color" /></label>
          <LedgerIconPicker v-model="forms.category.icon" type="category" label="分类图标" />
        </template>

        <template v-else-if="formResource === 'merchant' || formResource === 'project'">
          <label><span>{{ formResource === 'merchant' ? '商家名称' : '项目名称' }}</span><Input v-model="forms.named.name" placeholder="请输入名称" required /></label>
          <label v-if="formResource === 'project'"><span>标识颜色</span><Input v-model="forms.named.color" type="color" /></label>
          <label><span>备注</span><Textarea v-model="forms.named.note" placeholder="选填，便于后续识别" /></label>
          <LedgerIconPicker v-model="forms.named.icon" :type="formResource" :label="formResource === 'merchant' ? '商家图标' : '项目图标'" />
        </template>

        <template v-else-if="formResource === 'member'">
          <label v-if="!editingItem"><span>已注册用户名</span><Input v-model="forms.member.username" placeholder="请输入用户名" required /></label>
          <p v-if="!editingItem" class="form-help">只能添加已注册且账号可用的用户。用户名全站唯一，添加时需要联网核验；未找到或已加入当前账本时会提示原因。</p>
          <label><span>账本角色</span><select v-model="forms.member.roleId" :disabled="editingItem?.roleCode === 'OWNER'" required><option value="" disabled>请选择角色</option><option v-for="role in memberRoleOptions" :key="role.id" :value="role.id">{{ role.name }}</option></select></label>
          <LedgerIconPicker v-model="forms.member.icon" type="member" label="成员图标" />
        </template>

        <template v-else-if="formResource === 'role'">
          <label><span>角色名称</span><Input v-model="forms.role.name" placeholder="例如：记账成员" required /></label>
          <fieldset class="permission-picker">
            <legend>权限</legend>
            <label
              v-for="permission in editablePermissions"
              :key="permission.value"
              class="permission-option"
            >
              <input v-model="forms.role.permissions" type="checkbox" :value="permission.value">
              <span><b>{{ permission.label }}</b><small>{{ permission.description }}</small></span>
            </label>
            <label class="permission-option required">
              <input type="checkbox" checked disabled>
              <span><b>查看本人日志</b><small>所有角色默认拥有，不可关闭</small></span>
            </label>
          </fieldset>
          <p class="form-help">权限会应用到使用该角色的账本成员；账本主人和管理员的系统权限不会在此降级。</p>
        </template>

        <template v-else-if="formResource === 'book'">
          <label><span>账本名称</span><Input v-model="forms.book.name" maxlength="120" placeholder="例如：家庭账本" required /></label>
          <div class="form-grid">
            <label><span>币种</span><select v-model="forms.book.currency"><option value="CNY">CNY</option><option value="USD">USD</option><option value="HKD">HKD</option></select></label>
            <label v-if="!editingItem"><span>初始化方式</span><select v-model="forms.book.mode"><option value="BLANK">空白账本</option><option value="SYSTEM_TEMPLATE">系统基础模板</option><option value="COPY">复制已有账本</option></select></label>
          </div>
          <label v-if="!editingItem && forms.book.mode === 'COPY'"><span>复制来源</span><select v-model="forms.book.sourceBookId" required><option value="" disabled>请选择账本</option><option v-for="book in ledger.books" :key="book.id" :value="book.id">{{ book.name }}</option></select></label>
        </template>

        <div class="dialog-actions">
          <LedgerActionIcon action="close" label="取消" @click="formOpen = false" />
          <LedgerActionIcon action="confirm" :label="saving ? '保存中' : '保存'" type="submit" :disabled="saving" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:open="confirmOpen" :title="confirmState.title">
      <div class="confirm-content">
        <span class="confirm-icon"><Delete /></span>
        <div><b>{{ confirmState.message }}</b><p>{{ confirmState.description }}</p></div>
      </div>
      <template #footer>
        <LedgerActionIcon action="close" label="取消" @click="confirmOpen = false" />
        <LedgerActionIcon action="delete" :label="saving ? '处理中' : confirmState.confirmText" :disabled="saving" @click="confirmAction" />
      </template>
    </Dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowRight, Delete, DeleteFilled, Lock, Management, Plus, Refresh } from '@element-plus/icons-vue'
import {
  apiDownloadLedgerExport,
  apiCreateLedgerMember,
  apiDeleteLedgerBook,
  apiListLedgerAuditLogs,
  apiListLedgerBooks,
  apiListLedgerRecycle,
  apiPurgeLedgerRecycle,
  apiRestoreLedgerRecycle,
  apiUpdateLedgerBook
} from '../api'
import { useLedgerStore } from '../stores/ledger'
import { createClientId } from '../utils/clientId.js'
import LedgerIconPicker from '../components/ledger/LedgerIconPicker.vue'
import LedgerResourceIcon from '../components/ledger/LedgerResourceIcon.vue'
import LedgerActionIcon from '../components/ledger/LedgerActionIcon.vue'
import { totalLedgerAssets } from '../components/ledger/ledgerAccounting'
import LoadingOverlay from '../components/ledger/LoadingOverlay.vue'
import Card from '../components/ui/Card.vue'
import Dialog from '../components/ui/Dialog.vue'
import Empty from '../components/ui/Empty.vue'
import Input from '../components/ui/Input.vue'
import Textarea from '../components/ui/Textarea.vue'

const ledger = useLedgerStore()
const route = useRoute()
const router = useRouter()
const tabs = [
  { key: 'accounts', label: '账户', title: '账户管理', action: '新建账户', unit: '账户', description: '按账户类型查看余额，隐藏不再使用的账户。' },
  { key: 'categories', label: '分类', title: '收支分类管理', action: '新增分类', unit: '分类', description: '一级分类用于汇总，记账时选择对应的二级分类。' },
  { key: 'merchants', label: '商家', title: '商家管理', action: '新增商家', unit: '商家', description: '维护账本中可选择的商家及其备注。' },
  { key: 'members', label: '成员与角色权限', title: '成员与角色权限', action: '添加成员', unit: '成员', description: '添加账本成员、分配角色并维护自定义权限。' },
  { key: 'projects', label: '项目', title: '项目管理', action: '新增项目', unit: '项目', description: '使用项目归集同一目标下的多笔流水。' },
  { key: 'recycle', label: '回收站', title: '流水与资源回收站', action: '', unit: '项目', description: '恢复误删内容，或由管理员执行永久清理。' },
  { key: 'audit', label: '操作日志', title: '操作日志', action: '', unit: '记录', description: '查看成员对账本资源的新增、修改和删除记录。' },
  { key: 'books', label: '账本管理', title: '账本管理', action: '新建账本', unit: '账本', description: '集中创建、切换和维护账本，不在业务页面重复占用空间。' }
]
const tabKeys = new Set(tabs.map(item => item.key))
const managementTabKeys = new Set(['categories', 'merchants', 'projects', 'books'])
const managementTabs = tabs.filter(item => managementTabKeys.has(item.key))
const memberSectionKeys = new Set(['members', 'roles'])
const memberSections = [
  { key: 'members', label: '成员管理' },
  { key: 'roles', label: '角色与权限' }
]
const managementViewStorageKey = 'salary-sync:ledger-management-view'
const memberSectionStorageKey = 'salary-sync:ledger-member-section'
const storedManagementView = readStoredManagementView()
const tab = ref(tabKeys.has(String(route.query.view)) ? String(route.query.view) : storedManagementView)
const memberSection = ref(memberSectionKeys.has(String(route.query.section)) ? String(route.query.section) : readStoredMemberSection())
const showHidden = ref(false)
const accountScope = ref('asset')
const categoryKind = ref('EXPENSE')
const collapsedGroups = ref(new Set())
const recycle = ref({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 1 })
const audit = ref({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 1 })
const pageSizeOptions = [10, 20, 50, 100]
const recyclePageSize = ref(20)
const auditPageSize = ref(20)
const formOpen = ref(false)
const formResource = ref('account')
const confirmOpen = ref(false)
const editingItem = ref(null)
const exportingBookId = ref('')
const managementLoading = ref(true)
const saving = ref(false)
const confirmState = reactive({ mode: 'delete', type: '', item: null, title: '', message: '', description: '', confirmText: '删除' })
const forms = reactive({
  account: { name: '', icon: 'wallet', accountType: 'cash', currency: 'CNY', openingBalance: 0 },
  category: { name: '', icon: 'tag', kind: 'EXPENSE', parentId: '', color: '#0f5132' },
  named: { name: '', icon: 'shop', note: '', color: '#0f5132' },
  member: { username: '', roleId: '', icon: 'user' },
  role: { name: '', permissions: [] },
  book: { name: '', currency: 'CNY', mode: 'BLANK', sourceBookId: '' }
})
const liabilityTypes = new Set(['card', 'credit', 'credit_card', 'loan'])
const editablePermissions = [
  { value: 'RESOURCE_MANAGE', label: '资源管理', description: '管理账户、分类、商家、项目和预算' },
  { value: 'MEMBER_MANAGE', label: '成员管理', description: '添加、调整和移除账本成员' },
  { value: 'ROLE_MANAGE', label: '角色管理', description: '创建及维护自定义角色' },
  { value: 'TRANSACTION_ANY_WRITE', label: '管理全部流水', description: '新增、编辑和删除所有成员的流水' },
  { value: 'TRANSACTION_OWN_WRITE', label: '操作自己流水', description: '新增及管理本人创建的流水' },
  { value: 'RECYCLE_ALL', label: '管理全部回收站', description: '恢复和清理所有成员删除的数据' },
  { value: 'RECYCLE_SELF', label: '管理本人回收站', description: '恢复本人删除的数据' },
  { value: 'IMPORT_EXPORT', label: '导入导出', description: '使用账本导入与导出功能' }
]

const activeTab = computed(() => tabs.find(item => item.key === tab.value) || tabs[0])
const visibleUnit = computed(() => tab.value === 'members' && memberSection.value === 'roles' ? '角色' : activeTab.value.unit)
const hideableTab = computed(() => ['accounts', 'categories', 'merchants', 'projects'].includes(tab.value))
const namedType = computed(() => tab.value === 'merchants' ? 'merchant' : 'project')
const assignableRoles = computed(() => ledger.roles.filter(role => role.code !== 'OWNER'))
const memberRoleOptions = computed(() => editingItem.value?.roleCode === 'OWNER' ? ledger.roles : assignableRoles.value)
const totalAssets = computed(() => totalLedgerAssets(ledger.accounts.filter(item => !item.deleted), ledger.transactions))
const totalLiabilities = computed(() => Math.abs(ledger.accounts.filter(item => liabilityTypes.has(item.accountType)).reduce((sum, item) => sum + Number(item.balance || 0), 0)))
const netAssets = computed(() => totalAssets.value - totalLiabilities.value)
const formParents = computed(() => ledger.categories.filter(item => !item.parentId && item.kind === forms.category.kind && item.id !== editingItem.value?.id && !item.deleted))
const canPurge = computed(() => ['OWNER', 'ADMIN'].includes(ledger.currentBook?.roleCode))
const dialogTitle = computed(() => `${editingItem.value ? '编辑' : '新增'}${resourceTypeLabel(formResource.value)}`)

const accountGroups = computed(() => {
  const candidates = ledger.accounts.filter(item => (accountScope.value === 'liability') === liabilityTypes.has(item.accountType) && (showHidden.value || !item.hidden))
  const labels = { cash: '现金账户', bank: '储蓄账户', wallet: '虚拟账户', card: '信用账户', credit: '信用账户', credit_card: '信用账户', loan: '借贷账户', other: '其他账户' }
  const groups = new Map()
  for (const item of candidates) {
    const key = item.accountType || 'other'
    if (!groups.has(key)) groups.set(key, { key, label: labels[key] || '其他账户', items: [], total: 0 })
    groups.get(key).items.push(item)
    groups.get(key).total += Number(item.balance || 0)
  }
  return [...groups.values()]
})
const categoryGroups = computed(() => {
  const candidates = ledger.categories.filter(item => item.kind === categoryKind.value && (showHidden.value || !item.hidden))
  return candidates.filter(item => !item.parentId).map(parent => ({
    ...parent,
    children: candidates.filter(item => String(item.parentId || '') === String(parent.id))
  }))
})
const filteredNamedItems = computed(() => (tab.value === 'merchants' ? ledger.merchants : ledger.projects).filter(item => showHidden.value || !item.hidden))
const visibleCount = computed(() => ({
  accounts: accountGroups.value.reduce((sum, group) => sum + group.items.length, 0),
  categories: categoryGroups.value.reduce((sum, group) => sum + 1 + group.children.length, 0),
  merchants: filteredNamedItems.value.length,
  projects: filteredNamedItems.value.length,
  members: memberSection.value === 'roles' ? ledger.roles.length : ledger.members.length,
  recycle: recycle.value.total || 0,
  audit: audit.value.total || 0,
  books: ledger.books.length
})[tab.value] || 0)

onMounted(async () => {
  writeStoredManagementView(tab.value)
  if (!tabKeys.has(String(route.query.view))) await router.replace({ path: route.path, query: { ...route.query, view: tab.value } })
  if (tab.value === 'members' && !memberSectionKeys.has(String(route.query.section))) {
    await router.replace({ path: route.path, query: { ...route.query, view: 'members', section: memberSection.value } })
  }
  await ledger.init()
  await loadSecondary()
})
watch(() => route.query.view, value => {
  if (!tabKeys.has(String(value))) {
    router.replace({ path: route.path, query: { ...route.query, view: tab.value } })
    return
  }
  const next = String(value)
  writeStoredManagementView(next)
  if (next === tab.value) return
  tab.value = next
})
watch(tab, () => {
  writeStoredManagementView(tab.value)
  showHidden.value = false
  collapsedGroups.value = new Set()
  loadSecondary()
})
watch(() => [route.query.view, route.query.section], ([view, section]) => {
  if (view !== 'members') return
  if (!memberSectionKeys.has(String(section))) {
    router.replace({ path: route.path, query: { ...route.query, section: memberSection.value } })
    return
  }
  const next = String(section)
  writeStoredMemberSection(next)
  if (next !== memberSection.value) memberSection.value = next
})
watch(memberSection, value => writeStoredMemberSection(value))
watch(() => ledger.currentBookId, () => {
  recycle.value.page = 1
  audit.value.page = 1
  loadSecondary()
})

async function loadSecondary() {
  managementLoading.value = true
  try {
    if (!ledger.currentBookId) return
    if (tab.value === 'books' && ledger.online) ledger.books = await apiListLedgerBooks()
    if (tab.value === 'recycle') recycle.value = await apiListLedgerRecycle(ledger.currentBookId, { page: recycle.value.page || 1, pageSize: recyclePageSize.value })
    if (tab.value === 'audit') audit.value = await apiListLedgerAuditLogs(ledger.currentBookId, { page: audit.value.page || 1, pageSize: auditPageSize.value })
  } finally {
    managementLoading.value = false
  }
}

function readStoredManagementView() {
  try {
    const value = window.localStorage.getItem(managementViewStorageKey)
    return tabKeys.has(String(value)) ? String(value) : 'accounts'
  } catch {
    return 'accounts'
  }
}

function writeStoredManagementView(value) {
  try {
    window.localStorage.setItem(managementViewStorageKey, value)
  } catch {
    // URL 查询参数仍可保证刷新后保留当前视图。
  }
}

function readStoredMemberSection() {
  try {
    const value = window.localStorage.getItem(memberSectionStorageKey)
    return memberSectionKeys.has(String(value)) ? String(value) : 'members'
  } catch {
    return 'members'
  }
}

function writeStoredMemberSection(value) {
  try {
    window.localStorage.setItem(memberSectionStorageKey, value)
  } catch {
    // URL 查询参数仍可保证刷新后保留当前 Tab。
  }
}

function selectManagementTab(view) {
  if (view === tab.value) return
  router.push({ path: route.path, query: { ...route.query, view } })
}

function selectMemberSection(section) {
  if (section === memberSection.value) return
  router.push({ path: route.path, query: { ...route.query, view: 'members', section } })
}

async function changeSecondaryPage(type, page) {
  if (type === 'recycle') recycle.value.page = Math.max(1, page)
  else audit.value.page = Math.max(1, page)
  await loadSecondary()
}

function resourceForTab() {
  return ({ accounts: 'account', categories: 'category', merchants: 'merchant', members: 'member', projects: 'project', books: 'book' })[tab.value] || tab.value
}
function openCreate(resource = resourceForTab()) {
  editingItem.value = null
  formResource.value = resource
  resetForm()
  if (resource === 'category') forms.category.kind = categoryKind.value
  if (resource === 'member') forms.member.roleId = assignableRoles.value.find(role => role.code === 'MEMBER')?.id || assignableRoles.value[0]?.id || ''
  if (resource === 'project') forms.named.icon = 'folder'
  if (resource === 'merchant') forms.named.icon = 'shop'
  formOpen.value = true
}
function openEdit(item, resource = resourceForTab()) {
  editingItem.value = item
  formResource.value = resource
  if (resource === 'account') Object.assign(forms.account, { name: item.name, icon: item.icon || 'wallet', accountType: item.accountType, currency: item.currency || 'CNY', openingBalance: Number(item.openingBalance || 0) })
  if (resource === 'category') Object.assign(forms.category, { name: item.name, icon: item.icon || 'tag', kind: item.kind, parentId: item.parentId || '', color: item.color || '#0f5132' })
  if (resource === 'merchant' || resource === 'project') Object.assign(forms.named, { name: item.name, icon: item.icon || (resource === 'project' ? 'folder' : 'shop'), note: item.note || '', color: item.color || '#0f5132' })
  if (resource === 'member') Object.assign(forms.member, { username: item.username, roleId: item.roleId, icon: item.icon || 'user' })
  if (resource === 'role') Object.assign(forms.role, { name: item.name, permissions: item.permissions.filter(permission => permission !== 'AUDIT_SELF_READ') })
  if (resource === 'book') Object.assign(forms.book, { name: item.name, currency: item.currency || 'CNY', mode: 'BLANK', sourceBookId: '' })
  formOpen.value = true
}
async function submitForm() {
  saving.value = true
  try {
    const base = editingItem.value ? { id: editingItem.value.id, revision: editingItem.value.revision } : {}
    const resource = formResource.value
    if (resource === 'account') await ledger.put('account', { ...base, ...forms.account, hidden: editingItem.value?.hidden || false })
    if (resource === 'category') await ledger.put('category', { ...base, ...forms.category, hidden: editingItem.value?.hidden || false })
    if (resource === 'merchant' || resource === 'project') await ledger.put(resource, { ...base, ...forms.named, hidden: editingItem.value?.hidden || false })
    if (resource === 'member') {
      if (editingItem.value) await ledger.put('member', { ...base, ...forms.member })
      else {
        if (!ledger.online) throw new Error('添加成员需要联网，以核验已注册用户名')
        await apiCreateLedgerMember(ledger.currentBookId, { ...forms.member }, createClientId())
        await ledger.refreshResources()
      }
    }
    if (resource === 'role') await ledger.put('role', { ...base, name: forms.role.name, permissions: [...forms.role.permissions] })
    if (resource === 'book') {
      if (!ledger.online) throw new Error('账本管理需要联网')
      if (editingItem.value) await apiUpdateLedgerBook(editingItem.value.id, { name: forms.book.name, currency: forms.book.currency }, editingItem.value.revision)
      else await ledger.createBook({ name: forms.book.name, currency: forms.book.currency, mode: forms.book.mode, sourceBookId: forms.book.mode === 'COPY' ? forms.book.sourceBookId : undefined })
      await ledger.refreshServer()
    }
    formOpen.value = false
    ElMessage.success(editingItem.value ? '修改已保存' : '新增成功')
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
async function toggleHidden(type, item) {
  if (saving.value) return
  saving.value = true
  try {
    await ledger.put(type, resourcePayload(type, item, { hidden: !item.hidden }))
    ElMessage.success(item.hidden ? '已恢复显示' : '已隐藏')
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || error?.message || '操作失败')
  } finally {
    saving.value = false
  }
}
function resourcePayload(type, item, changes = {}) {
  const base = { id: item.id, revision: item.revision }
  if (type === 'account') return { ...base, name: item.name, icon: item.icon || 'wallet', accountType: item.accountType, currency: item.currency, openingBalance: item.openingBalance, hidden: item.hidden, ...changes }
  if (type === 'category') return { ...base, name: item.name, icon: item.icon || 'tag', kind: item.kind, parentId: item.parentId || '', color: item.color, hidden: item.hidden, ...changes }
  if (type === 'merchant' || type === 'project') return { ...base, name: item.name, icon: item.icon || (type === 'project' ? 'folder' : 'shop'), note: item.note || '', color: item.color, hidden: item.hidden, ...changes }
  if (type === 'member') return { ...base, username: item.username, roleId: item.roleId, icon: item.icon || 'user', ...changes }
  if (type === 'role') return { ...base, name: item.name, permissions: [...(item.permissions || [])], ...changes }
  return { ...base, ...changes }
}
function askDelete(type, item) {
  const isBook = type === 'book'
  Object.assign(confirmState, {
    mode: isBook ? 'delete-book' : 'delete',
    type,
    item,
    title: `删除${resourceTypeLabel(type)}`,
    message: `确定删除“${item.name || item.displayName}”吗？`,
    description: isBook ? '账本删除后将不再出现在账本列表中，请先确认已导出所需数据。' : '删除后会进入回收站，可在保留期内恢复。',
    confirmText: isBook ? '删除账本' : '移入回收站'
  })
  confirmOpen.value = true
}
function askPurge(item) {
  Object.assign(confirmState, { mode: 'purge', type: item.type, item, title: '永久删除', message: `永久删除“${item.label}”？`, description: '该操作不可恢复；被历史流水引用的资源只保留最小墓碑。', confirmText: '永久删除' })
  confirmOpen.value = true
}
async function confirmAction() {
  saving.value = true
  try {
    if (confirmState.mode === 'purge') {
      await apiPurgeLedgerRecycle(ledger.currentBookId, confirmState.item.type, confirmState.item.id)
      await loadSecondary()
      ElMessage.success('已永久删除')
    } else if (confirmState.mode === 'delete-book') {
      await apiDeleteLedgerBook(confirmState.item.id, confirmState.item.revision)
      await ledger.refreshServer()
      ElMessage.success('账本已删除')
    } else {
      await ledger.remove(confirmState.type, resourcePayload(confirmState.type, confirmState.item))
      ElMessage.success('已移入回收站')
    }
    confirmOpen.value = false
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || error?.message || '删除失败')
  } finally {
    saving.value = false
  }
}
async function restore(item) {
  if (saving.value) return
  saving.value = true
  try {
    await apiRestoreLedgerRecycle(ledger.currentBookId, item.type, item.id)
    if (recycle.value.items.length === 1 && recycle.value.page > 1) recycle.value.page--
    await ledger.refreshCurrentBook()
    await loadSecondary()
    ElMessage.success('已恢复')
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || error?.message || '恢复失败')
  } finally {
    saving.value = false
  }
}
async function selectBook(item) {
  if (saving.value) return
  saving.value = true
  try {
    await ledger.selectBook(item.id)
    ElMessage.success(`已切换到“${item.name}”`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || error?.message || '账本切换失败')
  } finally {
    saving.value = false
  }
}
async function exportBook(item) {
  if (exportingBookId.value) return
  exportingBookId.value = item.id
  try {
    const data = await apiDownloadLedgerExport(item.id, 'xlsx')
    const url = URL.createObjectURL(data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${safeFilename(item.name)}-全部流水.xlsx`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    ElMessage.success(`“${item.name}”流水已导出`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || error?.message || '账本导出失败')
  } finally {
    exportingBookId.value = ''
  }
}
function toggleGroup(key) {
  const next = new Set(collapsedGroups.value)
  next.has(key) ? next.delete(key) : next.add(key)
  collapsedGroups.value = next
}
function openResourceTransactions(type, item) {
  const id = String(item?.id || '')
  if (!id) return
  const query = { range: 'all', source: 'ledger-management' }
  if (type === 'account') query.accountId = id
  else if (type === 'merchant') query.merchantId = id
  else if (type === 'project') query.projectId = id
  else if (type === 'category') {
    if (item.parentId) query.secondaryCategoryId = id
    else query.primaryCategoryId = id
  } else return
  router.push({ path: '/ledger/transactions', query })
}
function resourceFlow(item) {
  const key = tab.value === 'merchants' ? 'merchantId' : tab.value === 'projects' ? 'projectId' : 'memberId'
  return ledger.transactions
    .filter(transaction => String(transaction[key] || '') === String(item.id) && !transaction.deleted)
    .reduce((sum, transaction) => sum + transactionDelta(transaction), 0)
}
function categoryAmount(item) {
  const ids = item.children?.length ? [item.id, ...item.children.map(child => child.id)] : [item.id]
  const normalizedIds = new Set(ids.map(id => String(id)))
  return ledger.transactions
    .filter(transaction => normalizedIds.has(String(transaction.categoryId || '')) && !transaction.deleted)
    .reduce((sum, transaction) => sum + Math.abs(transactionDelta(transaction)), 0)
}
function transactionDelta(transaction) {
  const amount = Number(transaction.amount || 0)
  if (['INCOME', 'BORROW_IN', 'COLLECT_DEBT'].includes(transaction.kind)) return amount
  if (['EXPENSE', 'LEND_OUT', 'REPAY_DEBT'].includes(transaction.kind)) return -amount
  return 0
}
function resetForm() {
  Object.assign(forms.account, { name: '', icon: 'wallet', accountType: 'cash', currency: 'CNY', openingBalance: 0 })
  Object.assign(forms.category, { name: '', icon: 'tag', kind: categoryKind.value, parentId: '', color: '#0f5132' })
  Object.assign(forms.named, { name: '', icon: 'shop', note: '', color: '#0f5132' })
  Object.assign(forms.member, { username: '', roleId: '', icon: 'user' })
  Object.assign(forms.role, { name: '', permissions: [] })
  Object.assign(forms.book, { name: '', currency: 'CNY', mode: 'BLANK', sourceBookId: '' })
}
function safeFilename(value) { return String(value || '账本').replace(/[\\/:*?"<>|]/g, '-').trim() || '账本' }
function money(value) { return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function signedMoney(value) { const amount = Number(value || 0); return `${amount > 0 ? '+' : amount < 0 ? '−' : ''}¥${money(Math.abs(amount))}` }
function auditAvatar(item) { return String(item.actor?.nickname || item.actor?.username || '系').slice(0, 1).toUpperCase() }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function actionLabel(value) { const action = String(value || '').split('.').pop(); return ({ add: '新增', create: '新增', update: '修改', delete: '删除', restore: '恢复', purge: '永久删除', anonymize: '匿名化' })[action] || value }
function resourceTypeLabel(type) { return ({ account: '账户', category: '分类', merchant: '商家', member: '成员', project: '项目', role: '角色', budget: '预算', transaction: '流水', book: '账本' })[type] || type }
function permissionLabel(permission) { return ({ BOOK_DELETE: '删除账本', RESOURCE_MANAGE: '资源管理', MEMBER_MANAGE: '成员管理', ROLE_MANAGE: '角色管理', TRANSACTION_ANY_WRITE: '管理全部流水', TRANSACTION_OWN_WRITE: '操作自己流水', AUDIT_ALL_READ: '查看全部日志', AUDIT_ALL_CLEAR: '清理全部日志', AUDIT_SELF_READ: '查看本人日志', RECYCLE_ALL: '管理全部回收站', RECYCLE_SELF: '管理本人回收站', IMPORT_EXPORT: '导入导出' })[permission] || permission }
</script>

<style scoped>
.ledger-management{min-width:0;max-width:100%}.manager-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:24px;margin:24px 0 18px;padding-bottom:20px;border-bottom:1px solid var(--line)}.manager-heading-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px;flex-wrap:wrap}.manager-overline{margin:0 0 4px;color:var(--muted);font-size:10px;letter-spacing:.16em}.manager-title-line{display:flex;align-items:baseline;gap:24px;flex-wrap:wrap}.manager-title-line h1{margin:0;color:var(--ink);font:700 30px/1.15 Georgia,"Songti SC",serif;letter-spacing:-.025em}.manager-description{margin:7px 0 0;color:var(--muted);font-size:12px}.manager-create svg,.manager-toolbar svg{width:15px;height:15px}.account-totals{display:flex;align-items:center;gap:22px;color:var(--muted);font-size:12px}.account-totals span{display:flex;align-items:baseline;gap:7px}.account-totals b{color:var(--ink);font-size:18px;font-weight:600}.account-totals b.up{color:var(--up)}.account-totals b.down{color:var(--down)}.manager-main-tabs{display:flex;align-items:center;gap:4px;max-width:100%;margin:-3px 0 16px;padding:3px;border:1px solid var(--line);border-radius:4px;background:var(--paper);overflow-x:auto}.manager-main-tabs button{flex:1 0 auto;min-height:36px;padding:7px 18px;border:0;border-radius:3px;background:transparent;color:var(--muted);font-size:12px;font-weight:600;white-space:nowrap}.manager-main-tabs button:hover{color:var(--ink)}.manager-main-tabs button.active{background:var(--card);color:var(--accent);box-shadow:0 1px 4px #00000012}.manager-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:44px;margin-bottom:12px}.segment-control{display:flex;align-items:center;gap:20px}.segment-control button{position:relative;padding:9px 0;border:0;background:transparent;color:var(--muted);font-size:13px;font-weight:600}.segment-control button::after{content:"";position:absolute;right:0;bottom:0;left:0;height:2px;background:transparent}.segment-control button.active{color:var(--accent)}.segment-control button.active::after{background:var(--accent)}.toolbar-summary{display:flex;align-items:baseline;gap:6px}.toolbar-summary b{font:700 22px Georgia,serif}.toolbar-summary span{color:var(--muted);font-size:11px}.visibility-toggle{display:inline-flex;align-items:center;gap:8px;margin-left:auto;padding:7px 0;border:0;background:transparent;color:var(--muted);font-size:12px}.visibility-toggle:hover{color:var(--ink)}.check-box{display:inline-flex;align-items:center;justify-content:center;width:17px;height:17px;border:1px solid var(--line2);border-radius:3px;background:var(--card);color:#fff;font-size:11px}.visibility-toggle[aria-pressed=true] .check-box{border-color:var(--accent);background:var(--accent)}.manager-table-card{padding:0;overflow:hidden}.manager-table{width:100%;min-width:0;animation:manager-view-in .2s ease both}@keyframes manager-view-in{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:translateY(0)}}.table-header,.table-row{display:grid;align-items:center;min-width:0}.table-header{min-height:50px;padding:0 20px;background:color-mix(in srgb,var(--paper) 78%,var(--card));color:var(--muted);font-size:10px;font-weight:700;letter-spacing:.1em;text-transform:uppercase;border-bottom:1px solid var(--line)}.table-row{min-height:64px;padding:10px 20px;border-bottom:1px solid var(--line);color:var(--ink2);font-size:12px}.table-row:last-child{border-bottom:0}.data-row{transition:background .12s}.data-row:hover{background:color-mix(in srgb,var(--accent-soft) 45%,transparent)}.data-row.hidden{opacity:.55}.account-table .table-header,.account-table .table-row{grid-template-columns:minmax(210px,1.5fr) minmax(110px,.8fr) minmax(72px,.45fr) minmax(110px,.65fr) minmax(210px,1fr);gap:16px}.category-table .table-header,.category-table .table-row,.simple-table .table-header,.simple-table .table-row,.role-table .table-header,.role-table .table-row,.recycle-table .table-header,.recycle-table .table-row{grid-template-columns:minmax(220px,1.5fr) minmax(100px,.65fr) minmax(170px,1fr) minmax(210px,1fr);gap:16px}.member-table .table-header,.member-table .table-row{grid-template-columns:minmax(180px,1.1fr) minmax(130px,.8fr) minmax(120px,.7fr) minmax(110px,.65fr) minmax(190px,1fr);gap:16px}.audit-table .table-header,.audit-table .table-row{grid-template-columns:minmax(170px,1fr) minmax(120px,.7fr) minmax(180px,1.1fr) minmax(180px,1fr);gap:16px}.book-table .table-header,.book-table .table-row{grid-template-columns:minmax(190px,1.4fr) minmax(70px,.4fr) minmax(78px,.5fr) minmax(88px,.55fr) minmax(100px,.65fr) minmax(260px,1.4fr);gap:14px}.group-row{background:color-mix(in srgb,var(--paper) 55%,var(--card));color:var(--ink)}button.group-row{width:100%;border-width:0 0 1px;border-style:solid;border-color:var(--line);text-align:left}.cell-primary{display:flex;align-items:center;min-width:0;gap:10px;color:var(--ink)}.cell-primary b{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.cell-primary>svg,.disclosure>svg{flex:0 0 14px;width:14px;height:14px;color:var(--muted)}.child-cell{padding-left:32px}.resource-icon{display:inline-flex;align-items:center;justify-content:center;flex:0 0 30px;width:30px;height:30px;border:1px solid var(--line);border-radius:50%;background:var(--card);color:var(--accent)}.resource-icon svg{width:15px;height:15px}.color-mark{flex:0 0 9px;width:9px;height:9px;border-radius:50%;box-shadow:0 0 0 3px color-mix(in srgb,currentColor 10%,transparent)}.disclosure{padding:0;border:0;background:transparent;text-align:left}.status-dot{display:inline-block;width:6px;height:6px;margin-right:7px;border-radius:50%;background:var(--accent)}.status-dot.muted{background:var(--muted)}.row-actions{display:flex;align-items:center;justify-content:flex-end;gap:12px;min-width:0}.row-actions button{padding:4px 0;border:0;background:transparent;color:var(--accent);font-size:11px;white-space:nowrap}.row-actions button:hover{text-decoration:underline}.row-actions button:disabled{cursor:wait;opacity:.5;text-decoration:none}.row-actions button.danger{color:var(--down)}.truncate-note{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.member-avatar{display:inline-flex;align-items:center;justify-content:center;flex:0 0 30px;width:30px;height:30px;border-radius:50%;background:var(--accent-soft);color:var(--accent);font-weight:700}.role-badge,.permission-list em{display:inline-flex;padding:3px 7px;border-radius:3px;background:var(--accent-soft);color:var(--accent);font-size:10px;font-style:normal}.permission-list{display:flex;min-width:0;flex-wrap:wrap;gap:4px}.permission-list small,.protected-label{color:var(--muted);font-size:10px}.num{font-variant-numeric:tabular-nums}.audit-target{display:flex;min-width:0;flex-direction:column;gap:3px}.audit-target small{color:var(--muted);font-size:10px}.audit-target b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--ink)}.management-pagination{display:flex;align-items:center;justify-content:flex-end;gap:14px;padding:14px 20px;border-top:1px solid var(--line);color:var(--muted);font-size:11px}.management-pagination label{display:inline-flex;align-items:center;gap:6px}.management-pagination select{height:28px;padding:0 20px 0 7px;border:1px solid var(--line2);border-radius:3px;background:var(--card);color:var(--ink2);font:inherit}.management-pagination button{height:28px;padding:0 9px;border:1px solid var(--line2);border-radius:3px;background:var(--card);color:var(--ink2);font:inherit}.management-pagination button:disabled{cursor:not-allowed;opacity:.4}.management-pagination b{min-width:40px;text-align:center;color:var(--ink2)}.manager-form{display:flex;flex-direction:column;gap:15px}.manager-form label{display:flex;flex-direction:column;gap:6px;color:var(--ink2);font-size:11px;font-weight:600}.manager-form select{height:38px}.manager-form input[type=color]{padding:4px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.form-help{margin:0;color:var(--muted);font-size:11px}.dialog-actions{display:flex;justify-content:flex-end;gap:8px;margin:4px -18px -18px;padding:14px 18px;border-top:1px solid var(--line)}.confirm-content{display:flex;align-items:flex-start;gap:12px}.confirm-icon{display:inline-flex;align-items:center;justify-content:center;flex:0 0 36px;width:36px;height:36px;border-radius:50%;background:color-mix(in srgb,var(--down) 10%,transparent);color:var(--down)}.confirm-icon svg{width:17px;height:17px}.confirm-content b{color:var(--ink);font-size:13px}.confirm-content p{margin:5px 0 0;color:var(--muted);font-size:11px;line-height:1.6}

@media(max-width:900px){.account-totals{width:100%;gap:16px}.account-totals span{flex-direction:column;gap:0}.account-totals b{font-size:15px}.table-header{display:none}.manager-table-card{overflow:visible;border:0;background:transparent}.table-row{grid-template-columns:minmax(0,1fr) auto!important;gap:5px 12px;min-height:0;margin-bottom:8px;padding:13px 14px;border:1px solid var(--line)!important;border-radius:4px;background:var(--card)}.table-row>span:not(.cell-primary):not(.row-actions),.table-row>strong{grid-column:2;text-align:right}.table-row>.cell-primary{grid-column:1;grid-row:1 / span 2}.table-row>.row-actions{grid-column:1 / -1;grid-row:auto;justify-content:flex-end;margin-top:7px;padding-top:8px;border-top:1px solid var(--line)}.group-row{margin-top:12px;background:color-mix(in srgb,var(--paper) 55%,var(--card))}.group-row>span:last-child{display:none}.child-cell{padding-left:18px}.permission-list{grid-column:1 / -1!important;text-align:left!important}.audit-table .table-row>.cell-primary{grid-row:1}.audit-table .table-row>span{grid-column:auto}.audit-table .table-row>span:last-child{grid-column:1 / -1;text-align:left}.recycle-table .table-row>.row-actions{grid-column:1 / -1}}
@media(prefers-reduced-motion:reduce){.manager-table{animation:none}}
@media(max-width:680px){.manager-heading{align-items:stretch;flex-direction:column;gap:14px;margin-top:18px}.manager-heading-actions{justify-content:stretch}.manager-heading-actions .ui-button{flex:1}.manager-title-line h1{font-size:26px}.manager-create{width:100%}.manager-toolbar{align-items:flex-start;flex-wrap:wrap}.visibility-toggle{order:2;margin-left:0}.segment-control{width:100%;justify-content:flex-start}.form-grid{grid-template-columns:1fr}.account-totals{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.account-totals span{min-width:0}.account-totals b{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.table-row{padding:12px}.row-actions{gap:14px}.role-table .table-row>span:nth-child(2){grid-column:2}.role-table .table-row>.permission-list{grid-column:1 / -1}.management-pagination{align-items:flex-start;flex-wrap:wrap;justify-content:flex-start;gap:8px;padding:12px}.management-pagination span{order:3;width:100%}.manager-description{max-width:92%}}
.permission-picker{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:0;padding:0;border:0}
.permission-picker legend{grid-column:1 / -1;margin-bottom:1px;color:var(--ink2);font-size:11px;font-weight:600}
.manager-form .permission-option{display:grid;grid-template-columns:16px minmax(0,1fr);gap:9px;padding:10px;border:1px solid var(--line);border-radius:4px;background:var(--paper);cursor:pointer}
.manager-form .permission-option:has(input:checked){border-color:color-mix(in srgb,var(--accent) 55%,var(--line));background:var(--accent-soft)}
.manager-form .permission-option.required{cursor:default;opacity:.72}
.permission-option input{width:15px;height:15px;margin:2px 0 0;accent-color:var(--accent)}
.permission-option span{display:flex;min-width:0;flex-direction:column;gap:2px}
.permission-option b{color:var(--ink);font-size:11px}
.permission-option small{color:var(--muted);font-size:9px;font-weight:400;line-height:1.4}
@media(max-width:680px){.permission-picker{grid-template-columns:1fr}}
.entity-link{display:block;min-width:0;max-width:100%;overflow:hidden;padding:3px 0;border:0;background:transparent;color:var(--ink);text-align:left;cursor:pointer}
.entity-link b{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.entity-link:hover,.entity-link:focus-visible{color:var(--accent);text-decoration:underline;outline:none}
.disclosure{display:inline-flex;align-items:center;justify-content:center;flex:0 0 18px;width:18px;height:24px;cursor:pointer}
.account-table .table-header,.account-table .table-row{
  grid-template-columns:minmax(150px,1.35fr) minmax(92px,.8fr) minmax(48px,.35fr) minmax(72px,.55fr) minmax(170px,auto);
  gap:clamp(6px,1vw,16px);
}
.manager-table .row-actions{gap:5px;flex-wrap:nowrap}
.manager-table .row-actions .ledger-action-icon{
  flex:0 0 32px;width:32px;height:32px;padding:0;
  border:1px solid var(--line2);background:var(--card);color:var(--ink2);
}
.manager-table .row-actions .ledger-action-icon:hover{border-color:var(--accent);background:var(--accent-soft);color:var(--accent);text-decoration:none}
.manager-table .row-actions .ledger-action-icon.destructive:hover{border-color:var(--down);color:var(--down)}
.manager-heading-actions .manager-create{flex:0 0 36px;width:36px;height:36px}
.book-table .table-header,.book-table .table-row{
  grid-template-columns:minmax(135px,1.25fr) minmax(42px,.32fr) minmax(52px,.36fr)
    minmax(72px,.5fr) minmax(68px,.58fr) minmax(145px,auto);
  gap:clamp(6px,.8vw,12px);
}
.book-table .table-row>span:not(.cell-primary):not(.row-actions){min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.book-table .table-header,.book-table .table-row{
  grid-template-columns:minmax(110px,1.25fr) 42px 52px minmax(68px,.5fr) minmax(64px,.58fr) 132px;
  gap:clamp(4px,.65vw,10px);
}
.book-table .row-actions{min-width:0;gap:4px}
.book-table .row-actions .ledger-action-icon{flex:0 0 29px;width:29px;height:29px}
@media(max-width:900px){
  .account-table .table-row{grid-template-columns:minmax(0,1fr) auto}
  .manager-table .row-actions{justify-content:flex-start;min-width:0;flex-wrap:wrap}
}
</style>
