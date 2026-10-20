<template>
  <section
    class="clone-task-shell"
    :class="{
      'sidebar-hidden': sidebarHidden,
      'detail-open': Boolean(selectedTask),
    }"
  >
    <aside class="clone-task-sidebar" aria-label="任务导航">
      <div class="clone-sidebar-head">
        <strong>任务</strong
        ><button type="button" aria-label="折叠任务侧栏" @click="toggleSidebar">
          <PanelLeftClose :size="17" />
        </button>
      </div>
      <nav class="clone-primary-nav">
        <RouterLink
          v-for="item in primaryViews"
          :key="item.key"
          :to="item.to"
          :class="{ active: isActive(item.to) }"
          ><component :is="item.icon" :size="16" /><span>{{ item.label }}</span
          ><small>{{ itemCount(item.key) }}</small></RouterLink
        >
      </nav>
      <section class="clone-sidebar-section">
        <header><button type="button" @click="toggleSection('lists')"><ChevronDown :size="14" :class="{ rotated: !sections.lists }" />清单</button><button class="sidebar-create" type="button" aria-label="新建清单" @click="createList"><Plus :size="14" /></button></header>
        <div v-show="sections.lists" class="clone-sidebar-items" @dragover.prevent="organizationDrag && (organizationDropTarget = 'list-root')" @drop.prevent="dropOrganizationRoot('list')">
        <section v-for="group in sortedListGroups" :key="group.publicId" class="clone-list-group clone-sortable-group" :class="{ 'drag-over': organizationDropTarget === `group:${group.publicId}` }" @dragover.prevent="organizationDropTarget = `group:${group.publicId}`" @drop.prevent.stop="dropGroup(group)" @contextmenu.prevent="openOrganizationMenu('group', group, $event)">
          <header class="clone-list-group-heading" draggable="true" @dragstart="startOrganizationDrag('group', group)"><button type="button" class="clone-group-toggle" :aria-label="`${groupCollapsed[group.publicId] ? '展开' : '折叠'}${group.name}`" @click.stop="toggleListGroupCollapsed(group)"><ChevronDown :size="13" :class="{ rotated: groupCollapsed[group.publicId] }" /></button><span>{{ group.name }}</span><small>{{ listsInGroup(group.publicId).length }}</small><button type="button" :aria-label="`新建清单到${group.name}`" @click.stop="createList(group.publicId)"><Plus :size="13" /></button><button type="button" :aria-label="`${group.name}的更多操作`" @click.stop="openOrganizationMenu('group', group, $event)"><Ellipsis :size="14" /></button></header>
          <div v-show="!groupCollapsed[group.publicId]" class="clone-group-lists"><RouterLink v-for="list in listsInGroup(group.publicId)" :key="list.publicId" :to="list.systemKey === 'INBOX' ? '/tasks/inbox' : `/tasks/list/${list.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId }" :draggable="list.systemKey !== 'INBOX'" @dragstart="startOrganizationDrag('list', list)" @dragover.prevent="organizationDropTarget = list.publicId" @drop.prevent.stop="dropOrganization('list', list)" @contextmenu.prevent="openOrganizationMenu('list', list, $event)"><ListTodo :size="15" :style="{ color: list.color || undefined }" /><span>{{ list.name }}</span><small>{{ listOpenCount(list.publicId) }}</small><button v-if="list.systemKey !== 'INBOX'" class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink></div>
        </section>
        <div v-if="ungroupedLists.length" class="clone-ungrouped-heading">未分组</div>
        <RouterLink
          v-for="list in ungroupedLists"
          :key="list.publicId"
          :to="
            list.systemKey === 'INBOX'
              ? '/tasks/inbox'
              : `/tasks/list/${list.publicId}`
          "
          class="clone-nav-item clone-sortable-item"
          :class="{ active: route.params.publicId === list.publicId }"
          :draggable="list.systemKey !== 'INBOX'"
          @dragstart="startOrganizationDrag('list', list)"
          @dragover.prevent="organizationDropTarget = list.publicId"
          @drop.prevent.stop="dropOrganization('list', list)"
          @contextmenu.prevent="openOrganizationMenu('list', list, $event)"
          ><ListTodo :size="15" :style="{ color: list.color || undefined }" /><span>{{ list.name }}</span><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink
        >
        <RouterLink to="/tasks/shortcut/archived" class="clone-nav-item" :class="{ active: route.params.shortcut === 'archived' }"><Archive :size="15" /><span>归档</span><small>{{ archivedTaskCount }}</small></RouterLink>
        <span v-if="!activeLists.length && !archivedLists.length" class="clone-empty-item">暂无清单</span>
        </div>
      </section>
      <section class="clone-sidebar-section">
        <header><button type="button" @click="toggleSection('filters')"><ChevronDown :size="14" :class="{ rotated: !sections.filters }" />筛选器</button><button class="sidebar-create" type="button" aria-label="管理筛选器" @click="router.push('/tasks/search')">···</button></header>
        <div v-show="sections.filters" class="clone-sidebar-items">
        <RouterLink
          v-for="item in shortcutViews"
          :key="item.to"
          :to="item.to"
          class="clone-nav-item"
          ><component :is="item.icon" :size="15" /><span>{{ item.label }}</span></RouterLink
        >
        <RouterLink
          v-for="filter in sortedFilters"
          :key="filter.publicId"
          :to="`/tasks/filter/${filter.publicId}`"
          class="clone-nav-item clone-sortable-item"
          :class="{ 'drag-over': organizationDropTarget === filter.publicId }"
          draggable="true"
          @dragstart="startOrganizationDrag('filter', filter)"
          @dragover.prevent="organizationDropTarget = filter.publicId"
          @drop.prevent.stop="dropOrganization('filter', filter)"
          @contextmenu.prevent="openOrganizationMenu('filter', filter, $event)"
          ><SlidersHorizontal :size="15" /><span>{{
            filter.name
          }}</span><button class="clone-item-menu-trigger" type="button" :aria-label="`${filter.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('filter', filter, $event)"><Ellipsis :size="14" /></button></RouterLink
        >
        <span v-if="!filters.length" class="clone-empty-item"
          >暂无自定义筛选器</span
        >
        </div>
      </section>
      <section class="clone-sidebar-section">
        <header><button type="button" @click="toggleSection('tags')"><ChevronDown :size="14" :class="{ rotated: !sections.tags }" />标签</button><button class="sidebar-create" type="button" aria-label="新建标签" @click="createTag"><Plus :size="14" /></button></header>
        <div v-show="sections.tags" class="clone-sidebar-items">
        <div v-for="tag in rootTags" :key="tag.publicId" class="clone-tag-group">
          <RouterLink :to="`/tasks/tag/${tag.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === tag.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', tag)" @dragover.prevent="organizationDropTarget = tag.publicId" @drop.prevent.stop="dropOrganization('tag', tag)" @contextmenu.prevent="openOrganizationMenu('tag', tag, $event)"><Tag :size="15" :style="{ color: tag.color || undefined }" /><span>#{{ tag.name }}</span><small>{{ tagOpenCount(tag.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`#${tag.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', tag, $event)"><Ellipsis :size="14" /></button></RouterLink>
          <RouterLink v-for="child in childTags.filter(item => item.parentId === tag.publicId)" :key="child.publicId" :to="`/tasks/tag/${child.publicId}`" class="clone-nav-item clone-child-tag clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === child.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', child)" @dragover.prevent="organizationDropTarget = child.publicId" @drop.prevent.stop="dropOrganization('tag', child)" @contextmenu.prevent="openOrganizationMenu('tag', child, $event)"><Tag :size="14" :style="{ color: child.color || undefined }" /><span>#{{ child.name }}</span><small>{{ tagOpenCount(child.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`#${child.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', child, $event)"><Ellipsis :size="14" /></button></RouterLink>
        </div>
        <span v-if="!store.tags.length" class="clone-empty-item">暂无标签</span>
        </div>
      </section>
      <div class="clone-sidebar-footer">
        <RouterLink to="/tasks/completed"
          ><CheckCircle2 :size="15" />已完成</RouterLink
        ><RouterLink to="/tasks/trash"><Trash2 :size="15" />垃圾桶</RouterLink>
        <div class="clone-week-nav" aria-label="一周日期导航"><RouterLink v-for="(date, index) in weekDates" :key="dateKey(date)" :to="{ path: '/tasks/today', query: { date: dateKey(date) } }" :class="{ selected: dateKey(date) === dateKey(selectedDate) }" :aria-label="`${date.getMonth() + 1}月${date.getDate()}日`"><small>{{ weekDayLabels[index] }}</small><span>{{ date.getDate() }}</span></RouterLink></div>
      </div>
    </aside>

    <main class="clone-task-main">
      <header class="clone-task-header">
        <div class="clone-title-block">
          <button
            type="button"
            class="clone-icon-button"
            aria-label="展开或折叠任务侧栏"
            @click="toggleSidebar"
          >
            <PanelLeft :size="18" />
          </button>
          <div>
            <p>任务</p>
            <h1>{{ title }}</h1>
            <span
              >{{ openTasks.length }} 项待完成 ·
              {{ completedTasks.length }} 项已完成</span
            >
          </div>
        </div>
        <div class="clone-header-actions">
          <span class="clone-sync-state" aria-live="polite">{{
            syncLabel
          }}</span
          ><button
            type="button"
            class="clone-icon-button"
            aria-label="搜索任务"
            @click="router.push('/tasks/search')"
          >
            <Search :size="17" /></button
          ><button
            type="button"
            class="clone-icon-button"
            aria-label="更多视图操作"
            @click="moreOpen = !moreOpen"
          >
            <Ellipsis :size="18" /></button
          ><button
            type="button"
            class="clone-primary-button"
            @click="openCreate"
          >
            <Plus :size="16" />新建任务
          </button>
        </div>
      </header>
      <div v-if="moreOpen" class="clone-floating-menu clone-view-menu" role="menu" aria-label="视图设置" @click.stop>
        <strong>分组</strong>
        <button v-for="option in groupOptions" :key="option.value" type="button" role="menuitemradio" :aria-checked="groupBy === option.value" @click="groupBy = option.value">
          <Check v-if="groupBy === option.value" :size="14" /><span v-else class="clone-menu-check-spacer"></span>{{ option.label }}
        </button>
        <strong>排序</strong>
        <button v-for="option in sortOptions" :key="option.value" type="button" role="menuitemradio" :aria-checked="sort === option.value" @click="sort = option.value">
          <Check v-if="sort === option.value" :size="14" /><span v-else class="clone-menu-check-spacer"></span>{{ option.label }}
        </button>
        <div class="clone-view-menu-order"><span>顺序</span><button type="button" :aria-pressed="sortDirection === 'asc'" @click="sortDirection = sortDirection === 'asc' ? 'desc' : 'asc'">{{ sortDirection === 'asc' ? '从旧到新' : '从新到旧' }}</button></div>
        <button type="button" :disabled="sort === 'time' && sortDirection === 'asc' && groupBy === 'time'" @click="sort = 'time'; sortDirection = 'asc'; groupBy = 'time'">恢复默认时间顺序</button>
        <hr />
        <button type="button" role="menuitemcheckbox" :aria-checked="hideCompleted" @click="hideCompleted = !hideCompleted"><Check v-if="hideCompleted" :size="14" /><span v-else class="clone-menu-check-spacer"></span>隐藏已完成</button>
        <button type="button" role="menuitemcheckbox" :aria-checked="showDescription" @click="showDescription = !showDescription"><Check v-if="showDescription" :size="14" /><span v-else class="clone-menu-check-spacer"></span>显示详细</button>
        <button type="button" role="menuitemcheckbox" :aria-checked="showChecklist" @click="showChecklist = !showChecklist"><Check v-if="showChecklist" :size="14" /><span v-else class="clone-menu-check-spacer"></span>显示检查事项</button>
        <button type="button" @click="router.push('/tasks/board'); moreOpen = false"><SquareKanban :size="14" />打开看板</button>
      </div>
      <div class="clone-quick-add">
        <Plus :size="17" />
        <form @submit.prevent="quickAdd">
          <input
            v-model="quickTitle"
            aria-label="快速添加任务标题"
            :placeholder="`添加任务至“${title === '今天' ? '收件箱' : title}”`"
          /><button type="submit" :disabled="!quickTitle.trim()">添加</button
          ><kbd>N</kbd>
        </form>
      </div>
      <div v-if="store.error" class="clone-error" role="alert">
        {{ store.error }}
      </div>
      <div
        v-if="store.pending || !store.online"
        class="clone-status"
        role="status"
      >
        <WifiOff v-if="!store.online" :size="15" />{{
          store.online
            ? `${store.pending} 项变更等待同步`
            : "当前离线，变更会在联网后同步"
        }}
      </div>
      <div class="clone-list-toolbar">
        <button
          type="button"
          :class="{ active: batchMode }"
          @click="
            batchMode = !batchMode;
            selectedIds.clear();
          "
        >
        {{ batchMode ? "退出多选" : "多选" }}</button
        ><button v-if="batchMode" type="button" @click="toggleAllSelected">{{ selectedIds.size === visibleTasks.length ? "取消全选" : "全选" }}</button><span>{{ visibleTasks.length }} 项任务</span>
      </div>
      <div v-if="!visibleTasks.length" class="clone-empty-state">
        <Inbox :size="28" /><strong>{{ title === "今天" ? "今天没有安排" : "这里还没有任务" }}</strong>
        <span v-if="title !== '今天'">使用上方输入框快速创建第一项任务</span>
      </div>
      <div v-else class="clone-task-groups">
        <section
          v-for="group in groups"
          :key="group.key"
          class="clone-task-group"
        >
          <button
            class="clone-group-heading"
            type="button"
            @click="collapsed[group.key] = !collapsed[group.key]"
          >
            <ChevronDown
              :size="16"
              :class="{ rotated: collapsed[group.key] }"
            /><strong>{{ group.label }}</strong
            ><span>{{ group.tasks.length }}</span>
          </button>
          <div v-show="!collapsed[group.key]" class="clone-task-rows">
            <article
              v-for="task in group.tasks"
              :key="task.publicId"
              class="clone-task-row"
              :class="{
                selected: selectedTask?.publicId === task.publicId,
                checked: task.status === 'COMPLETED',
                multi: selectedIds.has(task.publicId),
                dragging: dragged?.publicId === task.publicId,
                'drag-over': dragOverTask?.publicId === task.publicId,
              }"
              :data-priority="task.priority"
              :style="{ '--task-group-color': groupColor(task) }"
              :draggable="!batchMode && sort === 'manual'"
              @dragstart="startTaskDrag(task, $event)"
              @dragover.prevent="dragOverTask = task"
              @drop="dropTask(task, group.tasks.indexOf(task))"
              @dragend="endTaskDrag"
              @contextmenu.prevent="openContext(task, $event)"
            >
              <button
                v-if="batchMode"
                type="button"
                class="clone-select-box"
                :aria-label="`选择${task.title}`"
                @click="toggleSelected(task)"
              >
                <Check
                  v-if="selectedIds.has(task.publicId)"
                  :size="13"
                /></button
              ><button
                v-else
                type="button"
                class="clone-check"
                :aria-label="
                  task.status === 'COMPLETED'
                    ? `重新打开${task.title}`
                    : `完成${task.title}`
                "
                @click="toggleComplete(task)"
              >
                <Check v-if="task.status === 'COMPLETED'" :size="14" /></button
              ><GripVertical class="clone-drag-handle" :size="15" /><button
                type="button"
                class="clone-task-content"
                @click="batchMode ? toggleSelected(task) : openDetail(task)"
              >
                <strong>{{ task.title }}</strong
                ><small v-if="showDescription && task.description">{{
                  task.description
                }}</small
                ><span
                  ><span>{{ listName(task.listId) }}</span
                  ><span v-for="tagId in task.tagIds || []" :key="tagId"
                    >#{{ tagName(tagId) }}</span
                  ><span v-if="task.dueAt"
                    ><CalendarClock :size="13" />{{
                      formatDate(task.dueAt)
                    }}</span
                  ><span v-if="showChecklist && task.checklist?.length"
                    >{{ task.checklist.filter(item => item.completed).length }}/{{ task.checklist.length }}<CheckSquare :size="13" />检查项</span
                  ><span v-if="task.totalSubtasks"
                    >{{ task.completedSubtasks || 0 }}/{{ task.totalSubtasks }}<ListTodo :size="13" />子任务</span
                  ><span v-if="task.rrule" :title="recurrenceLabel(task.rrule)"><Repeat2 :size="13" />{{ recurrenceLabel(task.rrule) }}</span
                  ><span v-if="task.focusMinutes" :title="`累计专注 ${task.focusMinutes} 分钟`"><Timer :size="13" />{{ task.focusMinutes }} 分钟</span
                  ></span
                ></button
              ><em
                v-if="task.priority !== 'NONE'"
                :data-priority="task.priority"
                >{{ priorityLabel(task.priority) }}</em
              ><button
                v-if="!batchMode"
                type="button"
                class="clone-row-more"
                :aria-label="`更多操作：${task.title}`"
                @click.stop="openContext(task, $event)"
              >
                <Ellipsis :size="17" />
              </button>
            </article>
          </div>
        </section>
        <section v-if="todayCountdowns.length || todayHabits.length" class="clone-efficiency-groups">
          <section v-if="todayCountdowns.length" class="clone-task-group clone-efficiency-group">
            <button class="clone-group-heading" type="button" @click="collapsed.countdowns = !collapsed.countdowns">
              <ChevronDown :size="16" :class="{ rotated: collapsed.countdowns }" /><strong>倒数纪念日</strong><span>{{ todayCountdowns.length }}</span>
            </button>
            <div v-show="!collapsed.countdowns" class="clone-efficiency-rows">
              <article v-for="item in todayCountdowns" :key="item.publicId" class="clone-efficiency-row countdown-row">
                <span class="clone-efficiency-icon">{{ item.icon || '◷' }}</span><strong>{{ item.name || item.title }}</strong><span class="clone-efficiency-meta">{{ countdownLabel(item) }}</span>
              </article>
            </div>
          </section>
          <section v-if="todayHabits.length" class="clone-task-group clone-efficiency-group">
            <button class="clone-group-heading" type="button" @click="collapsed.habits = !collapsed.habits">
              <ChevronDown :size="16" :class="{ rotated: collapsed.habits }" /><strong>习惯</strong><span>{{ todayHabits.length }}</span>
            </button>
            <div v-show="!collapsed.habits" class="clone-efficiency-rows">
              <article v-for="item in todayHabits" :key="item.publicId" class="clone-efficiency-row">
                <button type="button" class="clone-efficiency-check" :class="{ checked: habitDone(item) }" :aria-label="`${habitDone(item) ? '取消' : '完成'}习惯${item.name}`" @click="toggleHabitToday(item)">{{ habitDone(item) ? '✓' : '' }}</button><strong>{{ item.name }}</strong><span class="clone-efficiency-meta">{{ habitDone(item) ? '已完成' : '今天' }}</span>
              </article>
            </div>
          </section>
        </section>
      </div>
      <div v-if="batchMode && selectedIds.size" class="clone-batch-bar">
        <strong>已选 {{ selectedIds.size }} 项</strong
        ><button type="button" @click="batchComplete">完成</button
        ><button type="button" class="danger" @click="batchDelete">删除</button
        ><button type="button" @click="selectedIds.clear()">取消</button>
      </div>
    </main>

    <aside
      class="clone-detail-pane"
      :class="{ 'is-empty': !selectedTask && !editorOpen }"
      aria-label="任务详情"
    >
      <header v-if="selectedTask || editorOpen" class="clone-detail-toolbar">
        <div class="clone-detail-toolbar-main">
          <button type="button" class="clone-detail-tool clone-detail-list-tool" aria-label="任务清单" @click="editorOpen ? null : openEdit(selectedTask)">
            <ListTodo :size="15" /><span>{{ listName(editorOpen ? draft.listId : selectedTask?.listId) }}</span>
          </button>
          <button type="button" class="clone-detail-tool" aria-label="设置日期" @click.stop="datePopoverOpen = !datePopoverOpen; repeatPopoverOpen = false; priorityPopoverOpen = false">
            <CalendarClock :size="15" /><span>{{ editorOpen && draft.dueAt ? formatDate(draft.dueAt) : selectedTask?.dueAt ? formatDate(selectedTask.dueAt) : '设置日期' }}</span>
          </button>
          <button type="button" class="clone-detail-tool" aria-label="设置重复" @click.stop="repeatPopoverOpen = !repeatPopoverOpen; datePopoverOpen = false; priorityPopoverOpen = false">
            <span class="clone-detail-repeat-icon">↻</span><span>{{ recurrenceLabel(editorOpen ? draft.rrule : selectedTask?.rrule) }}</span>
          </button>
          <div v-if="datePopoverOpen" class="clone-toolbar-popover clone-date-toolbar-popover" @click.stop>
            <strong>日期和时间</strong>
            <input v-if="editorOpen" v-model="draft.dueAt" type="datetime-local" aria-label="截止日期时间" />
            <span v-else class="clone-popover-current">{{ selectedTask?.dueAt ? formatDate(selectedTask.dueAt) : '未设置日期' }}</span>
            <div class="clone-popover-actions">
              <button type="button" @click="editorOpen ? draft.dueAt = toLocal(new Date()) : setTaskDue(0); datePopoverOpen = false">今天</button>
              <button type="button" @click="editorOpen ? draft.dueAt = toLocal(addDays(new Date(), 1)) : setTaskDue(1); datePopoverOpen = false">明天</button>
              <button type="button" @click="editorOpen ? draft.dueAt = '' : clearTaskDue(); datePopoverOpen = false">清除</button>
            </div>
          </div>
          <div v-if="repeatPopoverOpen" class="clone-toolbar-popover clone-repeat-toolbar-popover" @click.stop>
            <strong>重复</strong>
            <button v-for="option in recurrenceOptions" :key="option.value" type="button" :class="{ active: (editorOpen ? draft.rrule : selectedTask?.rrule) === option.value }" @click="editorOpen ? (draft.rrule = option.value) : setTaskRepeat(option.value); repeatPopoverOpen = false">{{ option.label }}</button>
          </div>
        </div>
        <div class="clone-detail-toolbar-actions">
          <button type="button" class="clone-detail-priority-tool" :data-priority="editorOpen ? draft.priority : selectedTask?.priority" aria-label="设置优先级" @click.stop="priorityPopoverOpen = !priorityPopoverOpen; datePopoverOpen = false; repeatPopoverOpen = false">⚑</button>
          <div v-if="priorityPopoverOpen" class="clone-toolbar-popover clone-priority-toolbar-popover" @click.stop>
            <strong>优先级</strong>
            <button v-for="option in priorityOptions" :key="option.value" type="button" :class="{ active: (editorOpen ? draft.priority : selectedTask?.priority) === option.value }" @click="editorOpen ? (draft.priority = option.value) : setTaskPriority(option.value); priorityPopoverOpen = false">{{ option.label }}</button>
          </div>
          <button type="button" class="clone-detail-tool" aria-label="更多详情操作" @click.stop="openContext(editing || selectedTask, $event)"><Ellipsis :size="17" /></button>
          <button type="button" class="clone-detail-tool clone-detail-close" aria-label="关闭任务详情" @click="closeDetail"><X :size="17" /></button>
        </div>
      </header>
      <div v-if="!selectedTask && !editorOpen" class="clone-detail-empty">
        <span class="clone-detail-empty-icon">✦</span>
        <strong>选择一个任务</strong>
        <p>任务详情、检查项和活动记录会显示在这里</p>
      </div>
      <form
        v-else-if="editorOpen"
        class="clone-detail-form"
        @submit.prevent="saveDetail"
      >
        <label>标题<input v-model="draft.title" required autofocus /></label
        ><label
          >描述<TaskRichTextEditor
            v-model="draft.description"
            label="任务描述"
            placeholder="补充任务描述"
        /></label>
          <div class="clone-form-grid clone-detail-section">
          <label
            >清单<select v-model="draft.listId">
              <option
                v-for="list in store.lists.filter((item) => !item.archived)"
                :key="list.publicId"
                :value="list.publicId"
              >
                {{ list.name }}
              </option>
            </select></label
          ><label
            >父任务<select v-model="draft.parentId">
              <option value="">无</option>
              <option
                v-for="task in parentOptions"
                :key="task.publicId"
                :value="task.publicId"
              >
                {{ task.title }}
              </option>
            </select></label
          >
        </div>
          <div class="clone-form-grid clone-detail-section">
          <label
            >优先级<select v-model="draft.priority">
              <option value="NONE">无优先级</option>
              <option value="LOW">低</option>
              <option value="MEDIUM">中</option>
              <option value="HIGH">高</option>
            </select></label
          ><label
            >持续分钟数<input
              v-model.number="draft.durationMinutes"
              type="number"
              min="0"
              step="5"
          /></label>
        </div>
        <div class="clone-form-grid">
          <label class="clone-popover-field"
            >开始时间<input
              v-model="draft.startAt"
              type="datetime-local" /></label
          ><label class="clone-popover-field"
            >截止时间
            <button type="button" class="clone-field-trigger" @click.stop="datePopoverOpen = !datePopoverOpen">
              <CalendarClock :size="15" /><span>{{ draft.dueAt ? formatDate(draft.dueAt) : '设置日期和时间' }}</span>
            </button>
            <div v-if="datePopoverOpen" class="clone-field-popover" @click.stop>
              <strong>日期和时间</strong>
              <input v-model="draft.dueAt" type="datetime-local" aria-label="截止日期时间" />
              <div class="clone-popover-actions">
                <button type="button" @click="draft.dueAt = toLocal(new Date()); datePopoverOpen = false">今天</button>
                <button type="button" @click="draft.dueAt = toLocal(addDays(new Date(), 1)); datePopoverOpen = false">明天</button>
                <button type="button" @click="draft.dueAt = ''; datePopoverOpen = false">清除</button>
              </div>
            </div>
          </label>
        </div>
        <label class="clone-inline-check"
          ><input v-model="draft.allDay" type="checkbox" />全天任务</label
        >
        <div class="clone-form-grid">
          <label class="clone-popover-field"
            >重复
            <button type="button" class="clone-field-trigger" @click.stop="repeatPopoverOpen = !repeatPopoverOpen">
              <span>{{ recurrenceLabel(draft.rrule) }}</span><ChevronDown :size="14" />
            </button>
            <div v-if="repeatPopoverOpen" class="clone-field-popover clone-repeat-popover" @click.stop>
              <strong>重复</strong>
              <button v-for="option in recurrenceOptions" :key="option.value" type="button" :class="{ active: draft.rrule === option.value }" @click="draft.rrule = option.value; repeatPopoverOpen = false">{{ option.label }}</button>
            </div>
          </label><label v-if="draft.rrule"
            >循环基准<select v-model="draft.recurrenceAnchor">
              <option value="DUE_DATE">按到期日期</option>
              <option value="COMPLETION_DATE">按完成日期</option>
            </select></label
          >
        </div>
        <label v-if="draft.rrule === 'CUSTOM'" class="clone-detail-section"
          >RRULE<input
            v-model="draft.customRrule"
            placeholder="FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR"
        /></label>
        <fieldset class="clone-reminder-editor clone-detail-section">
          <legend>提醒</legend>
          <label class="clone-inline-check"
            ><input
              v-model="draft.reminderEnabled"
              type="checkbox"
            />启用站内提醒</label
          >
          <div v-if="draft.reminderEnabled" class="clone-form-grid">
            <label
              >方式<select v-model="draft.reminderKind">
                <option value="RELATIVE">相对截止时间</option>
                <option value="ABSOLUTE">指定时间</option>
              </select></label
            ><label v-if="draft.reminderKind === 'RELATIVE'"
              >提前<select v-model.number="draft.reminderOffset">
                <option :value="0">准时</option>
                <option :value="5">5 分钟</option>
                <option :value="15">15 分钟</option>
                <option :value="30">30 分钟</option>
                <option :value="60">1 小时</option>
                <option :value="1440">1 天</option>
              </select></label
            ><label v-else
              >提醒时间<input v-model="draft.remindLocal" type="datetime-local"
            /></label>
          </div>
        </fieldset>
        <fieldset v-if="store.tags.length" class="clone-tags">
          <legend>标签</legend>
          <label v-for="tag in store.tags" :key="tag.publicId"
            ><input
              v-model="draft.tagIds"
              type="checkbox"
              :value="tag.publicId"
            />{{ tag.name }}</label
          >
        </fieldset>
        <section class="clone-checklist-editor" aria-label="检查项">
          <header><strong>检查项</strong><button type="button" aria-label="添加检查项" @click="addChecklistItem"><Plus :size="15" /></button></header>
          <div v-for="(item, index) in draft.checklistItems" :key="item.id || index" class="clone-checklist-row">
            <input v-model="item.completed" type="checkbox" :aria-label="`完成检查项 ${index + 1}`" />
            <input v-model="item.title" :class="{ completed: item.completed }" :aria-label="`检查项 ${index + 1}`" placeholder="添加检查项" />
            <button type="button" aria-label="删除检查项" @click="removeChecklistItem(index)"><Trash2 :size="14" /></button>
          </div>
          <button v-if="!draft.checklistItems.length" type="button" class="clone-checklist-add" @click="addChecklistItem"><Plus :size="14" />添加检查项</button>
        </section>
        <section v-if="editing" class="clone-collaboration-section">
          <header class="clone-section-heading">
            <h3>评论 <span>{{ comments.length }}</span></h3>
          </header>
          <p v-if="commentError" class="clone-error" role="alert">{{ commentError }}</p>
          <p v-if="!store.online && !commentError" class="clone-muted">离线时无法加载评论，联网后可发表评论</p>
          <article v-for="comment in comments" :key="comment.publicId" class="clone-comment">
            <header>
              <strong>{{ comment.authorName || '当前用户' }}</strong>
              <time>{{ formatDate(comment.createdAt) }}</time>
            </header>
            <TaskRichTextEditor
              v-if="editingCommentId !== comment.publicId"
              :model-value="comment.bodyMarkdown || ''"
              label="评论内容"
              preview-only
            />
            <textarea v-else v-model="commentEditDraft" aria-label="编辑评论" placeholder="编辑评论内容" />
            <div class="clone-comment-actions">
              <button v-if="editingCommentId !== comment.publicId" type="button" @click="startCommentEdit(comment)">编辑</button>
              <button v-if="editingCommentId !== comment.publicId" type="button" @click="removeComment(comment)">删除</button>
              <button v-if="editingCommentId === comment.publicId" type="button" @click="saveComment(comment)">保存</button>
              <button v-if="editingCommentId === comment.publicId" type="button" @click="cancelCommentEdit">取消</button>
            </div>
          </article>
          <div class="clone-comment-compose">
            <TaskRichTextEditor v-model="commentDraft" label="添加评论" placeholder="写下评论，支持 Markdown" />
            <button type="button" class="clone-primary-button" :disabled="!commentDraft.trim() || !store.online" @click="addComment">发表评论</button>
          </div>
        </section>
        <section v-if="editing" class="clone-collaboration-section">
          <header class="clone-section-heading">
            <h3>附件 <span>{{ attachments.length }}</span></h3>
            <label class="clone-attachment-upload">
              <Paperclip :size="14" />上传附件
              <input type="file" hidden @change="uploadAttachment" />
            </label>
          </header>
          <progress v-if="attachmentProgress" max="100" :value="attachmentProgress" />
          <p v-if="attachmentError" class="clone-error" role="alert">{{ attachmentError }}</p>
          <p v-if="!store.online && !attachmentError" class="clone-muted">离线时无法加载附件，联网后可上传或下载</p>
          <button v-if="pendingAttachment && !store.online" type="button" class="clone-retry" @click="retryAttachment">联网后重试：{{ pendingAttachment.name }}</button>
          <p v-if="!attachments.length" class="clone-muted">暂无附件</p>
          <ul v-else class="clone-attachment-list">
            <li v-for="file in attachments" :key="file.publicId">
              <Paperclip :size="14" />
              <span>{{ file.fileName }} <small>{{ formatBytes(file.sizeBytes) }}</small></span>
              <button type="button" :aria-label="`下载附件${file.fileName}`" @click="downloadAttachment(file)"><Download :size="14" /></button>
              <button type="button" :aria-label="`删除附件${file.fileName}`" @click="removeAttachment(file)"><Trash2 :size="14" /></button>
            </li>
          </ul>
        </section>
        <section v-if="editing" class="clone-activity-section">
          <h3>活动记录</h3>
          <p v-if="activityLoading" class="clone-muted">加载中…</p>
          <p v-else-if="!activities.length" class="clone-muted">{{ store.online ? "暂无活动记录" : "离线时无法加载活动记录" }}</p>
          <ol v-else>
            <li v-for="item in activities" :key="item.publicId">
              <strong>{{ item.summary }}</strong><time>{{ formatDate(item.createdAt) }}</time>
            </li>
          </ol>
        </section>
        <div class="clone-detail-actions">
          <button
            type="button"
            class="danger"
            @click="deleteTask(editing || selectedTask)"
          >
            <Trash2 :size="15" />删除</button
          ><button type="submit" class="clone-primary-button">保存</button>
        </div>
      </form>
      <div v-else class="clone-detail-read">
        <div class="clone-detail-title">
          <button
            type="button"
            class="clone-check"
            @click="toggleComplete(selectedTask)"
          >
            <Check v-if="selectedTask.status === 'COMPLETED'" :size="14" />
          </button>
          <h2 v-if="!inlineTitleEditing"><button type="button" aria-label="编辑任务标题" @click="beginInlineTitleEdit">{{ selectedTask.title }}<Pencil :size="13" /></button></h2>
          <input v-else v-model="inlineTitleDraft" class="clone-inline-title-input" aria-label="任务标题" maxlength="500" autofocus @keydown.enter.prevent="$event.target.blur()" @keydown.esc.prevent="cancelInlineTitleEdit" @blur="saveInlineTitle" />
        </div>
        <div v-if="!inlineDescriptionEditing" class="clone-detail-description-block">
          <TaskRichTextEditor v-if="selectedTask.description" :model-value="selectedTask.description" label="任务描述" preview-only />
          <p v-else class="clone-detail-description placeholder">输入内容或使用 / 快速插入</p>
          <button type="button" class="clone-edit-description" aria-label="编辑任务描述" @click="beginInlineDescriptionEdit"><Pencil :size="13" /></button>
        </div>
        <div v-else class="clone-inline-description-editor">
          <TaskRichTextEditor v-model="inlineDescriptionDraft" label="任务描述" placeholder="补充任务描述，输入 / 查看快捷菜单" />
          <div><button type="button" @click="cancelInlineDescriptionEdit">取消</button><button type="button" class="clone-primary-button" :disabled="inlineDescriptionSaving" @click="saveInlineDescription">保存描述</button></div>
        </div>
        <section v-if="selectedTask.checklist?.length" class="clone-checklist-read" aria-label="检查项">
          <header><strong>检查项</strong><span>{{ selectedTask.checklist.filter((item) => item.completed).length }}/{{ selectedTask.checklist.length }}</span></header>
          <label v-for="item in selectedTask.checklist" :key="item.publicId || item.title" class="clone-checklist-row">
            <input type="checkbox" :checked="item.completed" @change="toggleChecklistItem(selectedTask, item)" />
            <span :class="{ completed: item.completed }">{{ item.title }}</span>
          </label>
        </section>
        <div class="clone-detail-tags-row" v-if="selectedTask.tagIds?.length">
          <Tag :size="14" /><span v-for="tagId in selectedTask.tagIds" :key="tagId" class="clone-detail-tag">#{{ tagName(tagId) }}</span><button type="button" aria-label="添加标签" @click="openEdit(selectedTask)"><Plus :size="14" /></button>
        </div>
        <section class="clone-collaboration-section">
          <header class="clone-section-heading">
            <h3>评论 <span>{{ comments.length }}</span></h3>
          </header>
          <p v-if="commentError" class="clone-error" role="alert">{{ commentError }}</p>
          <p v-if="!store.online && !commentError" class="clone-muted">离线时无法加载评论，联网后可发表评论</p>
          <article v-for="comment in comments" :key="comment.publicId" class="clone-comment">
            <header>
              <strong>{{ comment.authorName || "当前用户" }}</strong>
              <time>{{ formatDate(comment.createdAt) }}</time>
            </header>
            <TaskRichTextEditor
              :model-value="comment.bodyMarkdown || ''"
              label="评论内容"
              preview-only
            />
          </article>
          <p v-if="!comments.length && !commentError" class="clone-muted">暂无评论</p>
        </section>
        <section class="clone-collaboration-section">
          <header class="clone-section-heading">
            <h3>附件 <span>{{ attachments.length }}</span></h3>
          </header>
          <p v-if="attachmentError" class="clone-error" role="alert">{{ attachmentError }}</p>
          <p v-if="!store.online && !attachmentError" class="clone-muted">离线时无法加载附件，联网后可上传或下载</p>
          <p v-if="!attachments.length && !attachmentError" class="clone-muted">暂无附件</p>
          <ul v-else class="clone-attachment-list">
            <li v-for="file in attachments" :key="file.publicId">
              <Paperclip :size="14" />
              <span>{{ file.fileName }} <small>{{ formatBytes(file.sizeBytes) }}</small></span>
              <button type="button" :aria-label="`下载附件${file.fileName}`" @click="downloadAttachment(file)"><Download :size="14" /></button>
            </li>
          </ul>
        </section>
        <section class="clone-activity-section">
          <h3>活动记录</h3>
          <p v-if="activityLoading" class="clone-muted">加载中…</p>
          <p v-else-if="!activities.length" class="clone-muted">{{ store.online ? "暂无活动记录" : "离线时无法加载活动记录" }}</p>
          <ol v-else>
            <li v-for="item in activities" :key="item.publicId">
              <strong>{{ item.summary }}</strong><time>{{ formatDate(item.createdAt) }}</time>
            </li>
          </ol>
        </section>
        <section class="clone-detail-properties" aria-label="任务属性">
          <button type="button" @click="openEdit(selectedTask)"><span>开始时间</span><strong>{{ selectedTask.startAt ? formatDate(selectedTask.startAt) : '未设置' }}</strong></button>
          <button type="button" @click="openEdit(selectedTask)"><span>持续时间</span><strong>{{ selectedTask.durationMinutes ? `${selectedTask.durationMinutes} 分钟` : '未设置' }}</strong></button>
          <button type="button" @click="openEdit(selectedTask)"><span>父任务</span><strong>{{ selectedTask.parentId ? taskName(selectedTask.parentId) : '无' }}</strong></button>
          <button type="button" @click="openEdit(selectedTask)"><span>全天</span><strong>{{ selectedTask.allDay ? '是' : '否' }}</strong></button>
        </section>
        <button type="button" class="clone-detail-edit-fields" @click="openEdit(selectedTask)"><Pencil :size="14" />编辑更多字段</button>
      </div>
    </aside>

    <div v-if="context.open" class="clone-context-menu clone-task-row-menu" :style="{ left: `${context.x}px`, top: `${context.y}px` }" role="menu" @click.stop>
      <strong>{{ context.task?.title }}</strong>
      <button type="button" @click="openDetail(context.task); closeContext()"><Pencil :size="14" />打开详情</button>
      <button type="button" @click="toggleComplete(context.task); closeContext()"><CheckCircle2 :size="14" />{{ context.task?.status === "COMPLETED" ? "重新打开" : "标记完成" }}</button>
      <button type="button" @click="setContextDue(0)"><CalendarClock :size="14" />安排今天</button>
      <button type="button" @click="setContextDue(1)"><CalendarClock :size="14" />安排明天</button>
      <button type="button" @click="setContextDue(null)"><X :size="14" />清除日期</button>
      <button type="button" @click="contextSubmenu = contextSubmenu === 'priority' ? '' : 'priority'"><Flag :size="14" />设置优先级<ChevronDown :size="13" /></button>
      <div v-if="contextSubmenu === 'priority'" class="clone-context-submenu"><button v-for="option in priorityOptions" :key="option.value" type="button" @click="setContextPriority(option.value)">{{ option.label }}</button></div>
      <button type="button" @click="contextSubmenu = contextSubmenu === 'list' ? '' : 'list'"><ListTodo :size="14" />移至清单<ChevronDown :size="13" /></button>
      <div v-if="contextSubmenu === 'list'" class="clone-context-submenu"><button v-for="list in activeLists" :key="list.publicId" type="button" :disabled="list.publicId === context.task?.listId" @click="moveContextTask(list)">{{ list.name }}</button></div>
      <button type="button" @click="contextSubmenu = contextSubmenu === 'tags' ? '' : 'tags'"><Tag :size="14" />设置标签<ChevronDown :size="13" /></button>
      <div v-if="contextSubmenu === 'tags'" class="clone-context-submenu"><label v-for="tag in sortedTags" :key="tag.publicId"><input type="checkbox" :checked="context.task?.tagIds?.includes(tag.publicId)" @change="toggleContextTag(tag)" />#{{ tag.name }}</label><span v-if="!sortedTags.length">暂无标签</span></div>
      <button type="button" @click="createSubtask(context.task); closeContext()"><Plus :size="14" />添加子任务</button>
      <button type="button" @click="startContextFocus"><Timer :size="14" />开始专注</button>
      <button type="button" @click="copyContextTaskLink"><Copy :size="14" />复制任务链接</button>
      <div class="clone-context-divider" aria-hidden="true"></div>
      <button type="button" @click="duplicateTask(context.task); closeContext()"><Copy :size="14" />创建副本</button>
      <button type="button" class="danger" @click="deleteTask(context.task); closeContext()"><Trash2 :size="14" />删除任务</button>
    </div>
    <div v-if="organizationMenuOpen" class="clone-context-menu clone-organization-menu" role="menu" :style="{ left: `${organizationMenu.x}px`, top: `${organizationMenu.y}px` }" @click.stop>
      <template v-if="organizationMenu.kind === 'list'"><button type="button" @click="editOrganization('list')"><Pencil :size="14" />编辑清单</button><button type="button" @click="createListGroupForItem"><Plus :size="14" />新建分组</button><button v-if="sortedListGroups.length" type="button" @click="toggleListGroupAssignment"><ListTodo :size="14" />{{ organizationMenu.item.groupId ? '移出分组' : '加入分组' }}</button><button type="button" @click="archiveList">{{ organizationMenu.item.archived ? '取消归档' : '归档清单' }}</button><button type="button" class="danger" @click="deleteOrganization('list')"><Trash2 :size="14" />删除清单</button></template>
      <template v-else-if="organizationMenu.kind === 'tag'"><button type="button" @click="editOrganization('tag')"><Pencil :size="14" />编辑标签</button><button type="button" @click="createChildTag"><Plus :size="14" />新建子标签</button><button type="button" class="danger" @click="deleteOrganization('tag')"><Trash2 :size="14" />删除标签</button></template>
      <template v-else-if="organizationMenu.kind === 'filter'"><button type="button" @click="editOrganization('filter')"><Pencil :size="14" />编辑筛选器</button><button type="button" class="danger" @click="deleteOrganization('filter')"><Trash2 :size="14" />删除筛选器</button></template>
      <template v-else><button type="button" @click="editListGroup"><Pencil :size="14" />编辑分组</button><button type="button" class="danger" @click="deleteListGroup"><Trash2 :size="14" />删除分组</button></template>
    </div>
    <Dialog v-model:open="organizationDialogOpen" :title="organizationDialogTitle"><form class="clone-organization-form" @submit.prevent="saveOrganization"><label>名称<input v-model.trim="organizationDialog.name" required maxlength="120" autofocus /></label><label v-if="['list','tag'].includes(organizationDialog.kind)">颜色<input v-model="organizationDialog.color" type="color" /></label><label v-if="organizationDialog.kind === 'tag'">父标签<select v-model="organizationDialog.parentId"><option value="">无</option><option v-for="tag in rootTags.filter(item => item.publicId !== organizationDialog.item?.publicId)" :key="tag.publicId" :value="tag.publicId">{{ tag.name }}</option></select></label><label v-if="organizationDialog.kind === 'list' && sortedListGroups.length"><span>分组</span><select v-model="organizationDialog.groupId"><option value="">未分组</option><option v-for="group in sortedListGroups" :key="group.publicId" :value="group.publicId">{{ group.name }}</option></select></label><footer><button type="button" @click="organizationDialogOpen = false">取消</button><button type="submit" class="clone-primary-button">保存</button></footer></form></Dialog>
  </section>
</template>

<script setup>
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter, RouterLink } from "vue-router";
import {
  Archive,
  CalendarClock,
  Check,
  CheckCircle2,
  CheckSquare,
  ChevronDown,
  ClipboardList,
  Copy,
  Ellipsis,
  Download,
  Flag,
  GripVertical,
  Inbox,
  ListFilter,
  ListTodo,
  PanelLeft,
  PanelLeftClose,
  Paperclip,
  Pencil,
  Plus,
  Search,
  SquareKanban,
  Repeat2,
  SlidersHorizontal,
  Tag,
  Timer,
  Trash2,
  WifiOff,
  X,
  UserRound,
} from "lucide-vue-next";
import TaskRichTextEditor from "../components/tasks/TaskRichTextEditor.vue";
import Dialog from "../components/ui/Dialog.vue";
import { moveItem } from "./taskSidebar.js";
import { groupTaskItems, sortTaskItems } from "./taskList.js";
import { useTasksStore } from "../stores/tasks.js";
import { useTaskFocusStore } from "../stores/taskFocus.js";
import { message } from "../services/message.js";
import {
  apiCreateTaskReminder,
  apiCreateTaskComment,
  apiDeleteTaskAttachment,
  apiDeleteTaskComment,
  apiDeleteTaskReminder,
  apiDownloadTaskAttachment,
  apiListTaskActivity,
  apiListTaskAttachments,
  apiListTaskComments,
  apiListTaskFilters,
  apiListTaskReminders,
  apiCreateTaskFilter,
  apiUpdateTaskFilter,
  apiDeleteTaskFilter,
  apiCreateTaskListGroup,
  apiUpdateTaskTag,
  apiDeleteTaskTag,
  apiUpdateTaskComment,
  apiUpdateTaskReminder,
  apiUploadTaskAttachment,
} from "../../packages/api-client/src/index.js";

const route = useRoute();
const router = useRouter();
const store = useTasksStore();
const focusStore = useTaskFocusStore();
const filters = ref([]);
const quickTitle = ref("");
const sidebarHidden = ref(false);
const moreOpen = ref(false);
const datePopoverOpen = ref(false);
const repeatPopoverOpen = ref(false);
const priorityPopoverOpen = ref(false);
const batchMode = ref(false);
const selectedIds = reactive(new Set());
const collapsed = reactive({
  overdue: false,
  today: false,
  later: false,
  none: false,
  open: false,
  countdowns: false,
  habits: false,
});
const sections = reactive({ lists: true, filters: true, tags: true });
const groupCollapsed = reactive({});
const organizationDrag = ref(null);
const organizationDropTarget = ref("");
const organizationMenuOpen = ref(false);
const organizationDialogOpen = ref(false);
const organizationMenu = reactive({ open: false, kind: "", item: null, x: 0, y: 0 });
const organizationDialog = reactive({ open: false, kind: "", item: null, name: "", color: "#2563eb", parentId: "", groupId: "" });
const selectedTask = ref(null);
const editorOpen = ref(false);
const editing = ref(null);
const inlineTitleEditing = ref(false);
const inlineTitleDraft = ref("");
const inlineTitleSaving = ref(false);
const inlineDescriptionEditing = ref(false);
const inlineDescriptionDraft = ref("");
const inlineDescriptionSaving = ref(false);
const dragged = ref(null);
const dragOverTask = ref(null);
const groupBy = ref("time");
const sort = ref("time");
const sortDirection = ref("asc");
const hideCompleted = ref(true);
const showDescription = ref(true);
const showChecklist = ref(true);
const groupOptions = [
  { value: "time", label: "时间" },
  { value: "list", label: "清单" },
];
const sortOptions = [
  { value: "time", label: "时间" },
  { value: "priority", label: "优先级" },
  { value: "title", label: "标题" },
  { value: "manual", label: "手动" },
];
const viewPreferencesScope = ref("");
function viewPreferencesKey() {
  return `task_view_preferences:${store.accountScope || "anonymous"}`;
}
function persistViewPreferences() {
  if (!viewPreferencesScope.value || viewPreferencesScope.value !== store.accountScope) return;
  try {
    localStorage.setItem(viewPreferencesKey(), JSON.stringify({
      groupBy: groupBy.value,
      sort: sort.value,
      sortDirection: sortDirection.value,
      hideCompleted: hideCompleted.value,
      showDescription: showDescription.value,
      showChecklist: showChecklist.value,
    }));
  } catch {}
}
function loadViewPreferences() {
  const scope = store.accountScope || "anonymous";
  viewPreferencesScope.value = scope;
  try {
    const saved = JSON.parse(localStorage.getItem(viewPreferencesKey()) || "{}");
    if (groupOptions.some((item) => item.value === saved.groupBy)) groupBy.value = saved.groupBy;
    if (sortOptions.some((item) => item.value === saved.sort)) sort.value = saved.sort;
    if (["asc", "desc"].includes(saved.sortDirection)) sortDirection.value = saved.sortDirection;
    if (typeof saved.hideCompleted === "boolean") hideCompleted.value = saved.hideCompleted;
    if (typeof saved.showDescription === "boolean") showDescription.value = saved.showDescription;
    if (typeof saved.showChecklist === "boolean") showChecklist.value = saved.showChecklist;
  } catch {}
}
watch([groupBy, sort, sortDirection, hideCompleted, showDescription, showChecklist], persistViewPreferences);
watch(() => store.accountScope, (scope) => {
  if (scope !== viewPreferencesScope.value) loadViewPreferences();
});
const context = reactive({ open: false, x: 0, y: 0, task: null });
const contextSubmenu = ref("");
const activities = ref([]);
const activityLoading = ref(false);
const comments = ref([]);
const attachments = ref([]);
const commentDraft = ref("");
const commentEditDraft = ref("");
const editingCommentId = ref("");
const commentError = ref("");
const attachmentError = ref("");
const attachmentProgress = ref(0);
const pendingAttachment = ref(null);
const draft = reactive({
  title: "",
  description: "",
  listId: "",
  parentId: "",
  tagIds: [],
  priority: "NONE",
  durationMinutes: 0,
  allDay: false,
  startAt: "",
  dueAt: "",
  rrule: "",
  customRrule: "",
  recurrenceAnchor: "DUE_DATE",
  reminderEnabled: false,
  reminderKind: "RELATIVE",
  reminderOffset: 30,
  remindLocal: "",
  reminder: null,
  checklistText: "",
  checklistItems: [],
});
const primaryViews = [
  { key: "all", label: "所有", to: "/tasks/all", icon: ListTodo },
  { key: "today", label: "今天", to: "/tasks/today", icon: CalendarClock },
  {
    key: "tomorrow",
    label: "明天",
    to: "/tasks/tomorrow",
    icon: CalendarClock,
  },
  { key: "next7", label: "最近 7 天", to: "/tasks/next7", icon: CalendarClock },
  { key: "assigned", label: "指派给我", to: "/tasks/shortcut/assigned", icon: UserRound },
  { key: "inbox", label: "收件箱", to: "/tasks/inbox", icon: Inbox },
  { key: "summary", label: "摘要", to: "/tasks/shortcut/summary", icon: ClipboardList },
];
const sortedListGroups = computed(() => [...store.listGroups].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const activeLists = computed(() => store.lists.filter(item => !item.archived));
const archivedLists = computed(() => store.lists.filter(item => item.archived).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const archivedTaskCount = computed(() => store.tasks.filter(task => !task.deleted && archivedLists.value.some(list => list.publicId === task.listId)).length);
const ungroupedLists = computed(() => activeLists.value.filter(item => !item.groupId).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const rootTags = computed(() => store.tags.filter(item => !item.parentId).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const childTags = computed(() => store.tags.filter(item => item.parentId).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const sortedTags = computed(() => [...store.tags].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const sortedFilters = computed(() => [...filters.value].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
function openCount(predicate) { return store.tasks.filter(task => !task.deleted && task.status !== "COMPLETED" && predicate(task)).length; }
function listOpenCount(listId) { return openCount(task => task.listId === listId); }
function tagOpenCount(tagId) { return openCount(task => (task.tagIds || []).includes(tagId)); }
function toggleListGroupCollapsed(group) { groupCollapsed[group.publicId] = !groupCollapsed[group.publicId]; persistListGroupState(); }
function persistListGroupState() { try { localStorage.setItem(`task_sidebar_groups:${store.accountScope || "anonymous"}`, JSON.stringify(groupCollapsed)); } catch {} }
function loadListGroupState() { try { Object.assign(groupCollapsed, JSON.parse(localStorage.getItem(`task_sidebar_groups:${store.accountScope || "anonymous"}`) || "{}")); } catch {} }
const organizationDialogTitle = computed(() => `${organizationDialog.item ? "编辑" : "新建"}${({ list: "清单", tag: "标签", filter: "筛选器", group: "清单分组" })[organizationDialog.kind] || "项目"}`);
const shortcutViews = [
  { label: "本周", to: "/tasks/shortcut/this-week", icon: CalendarClock },
  { label: "下周", to: "/tasks/shortcut/next-week", icon: CalendarClock },
  { label: "无日期任务", to: "/tasks/shortcut/no-date", icon: ListFilter },
  { label: "无标签", to: "/tasks/shortcut/no-tag", icon: ListFilter },
];
const recurrenceOptions = [
  { value: "", label: "不重复" },
  { value: "FREQ=DAILY", label: "每天" },
  { value: "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", label: "每个工作日" },
  { value: "FREQ=WEEKLY", label: "每周" },
  { value: "FREQ=MONTHLY", label: "每月" },
  { value: "FREQ=YEARLY", label: "每年" },
  { value: "CUSTOM", label: "自定义" },
];
const priorityOptions = [
  { value: "NONE", label: "无优先级" },
  { value: "LOW", label: "低优先级" },
  { value: "MEDIUM", label: "中优先级" },
  { value: "HIGH", label: "高优先级" },
];
const title = computed(() =>
  route.name === "tasks-today"
    ? "今天"
    : route.name === "tasks-tomorrow"
      ? "明天"
      : route.name === "tasks-next7"
        ? "最近 7 天"
        : route.name === "tasks-inbox"
          ? "收件箱"
          : route.name === "tasks-list"
            ? listName(route.params.publicId)
            : route.name === "tasks-tag"
              ? `#${tagName(route.params.publicId)}`
              : route.name === "tasks-filter"
                ? filters.value.find(
                    (item) => item.publicId === route.params.publicId,
                  )?.name || "智能清单"
                : route.name === "tasks-shortcut"
                  ? {
                      "no-date": "无日期任务",
                      "this-week": "本周",
                      "next-week": "下周",
                      "no-tag": "无标签",
                      assigned: "指派给我",
                      summary: "摘要",
                      archived: "归档",
                    }[route.params.shortcut] || "任务"
                  : "所有",
);
const selectedDate = computed(() => {
  const match = String(route.query.date || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : startOfDay(new Date());
});
const weekDayLabels = ["一", "二", "三", "四", "五", "六", "日"];
const weekDates = computed(() => {
  const monday = new Date(selectedDate.value);
  const weekday = monday.getDay() || 7;
  monday.setDate(monday.getDate() - weekday + 1);
  return Array.from({ length: 7 }, (_, index) => addDays(monday, index));
});
function dateKey(value) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}
const parentOptions = computed(() =>
  store.tasks.filter(
    (task) =>
      !task.deleted &&
      task.status !== "COMPLETED" &&
      task.publicId !== editing.value?.publicId &&
      task.listId === draft.listId,
  ),
);
const syncLabel = computed(() =>
  !store.online
    ? "离线"
    : store.syncing
      ? "同步中"
      : store.pending
        ? "待同步"
        : "已同步",
);
const sourceTasks = computed(() =>
  store.tasks.filter((task) =>
    route.name === "tasks-trash"
      ? task.deleted
      : !task.deleted && (route.name === "tasks-completed"
        ? task.status === "COMPLETED"
        : route.params.shortcut === "archived"
          ? archivedLists.value.some((list) => list.publicId === task.listId)
          : !hideCompleted.value || task.status !== "COMPLETED"),
  ),
);
const visibleTasks = computed(() => {
  let values = [...sourceTasks.value];
  const today = startOfDay(new Date());
  const tomorrow = addDays(today, 1);
  const end = addDays(today, 8);
  if (route.name === "tasks-completed")
    values = values.filter((task) => task.status === "COMPLETED");
  else if (route.name === "tasks-list")
    values = values.filter((task) => task.listId === route.params.publicId);
  else if (route.name === "tasks-tag")
    values = values.filter((task) =>
      task.tagIds?.includes(route.params.publicId),
    );
  else if (route.name === "tasks-inbox")
    values = values.filter((task) => task.listId === store.inbox?.publicId);
  else if (route.name === "tasks-today")
    values = values.filter((task) => inRange(task.dueAt, today, tomorrow));
  else if (route.name === "tasks-tomorrow")
    values = values.filter((task) =>
      inRange(task.dueAt, tomorrow, addDays(tomorrow, 1)),
    );
  else if (route.name === "tasks-next7")
    values = values.filter((task) => inRange(task.dueAt, today, end));
  else if (route.name === "tasks-shortcut") {
    const shortcut = route.params.shortcut;
    const archivedIds = new Set(archivedLists.value.map(list => list.publicId));
    values = values.filter(task => shortcut === "assigned"
      ? false
      : shortcut === "summary"
        ? task.status !== "COMPLETED" && task.dueAt && new Date(task.dueAt) < tomorrow
        : shortcut === "archived"
          ? archivedIds.has(task.listId)
          : shortcut === "no-tag"
            ? !(task.tagIds || []).length && task.status !== "COMPLETED"
            : shortcut === "no-date"
              ? !task.dueAt && task.status !== "COMPLETED"
              : shortcut === "this-week"
                ? inRange(task.dueAt, today, addDays(today, 7)) && task.status !== "COMPLETED"
                : inRange(task.dueAt, addDays(today, 7), addDays(today, 14)) && task.status !== "COMPLETED");
  }
  return sortTaskItems(values, { sortBy: sort.value, direction: sortDirection.value });
});
const openTasks = computed(() =>
  visibleTasks.value.filter((task) => task.status !== "COMPLETED"),
);
const completedTasks = computed(() =>
  visibleTasks.value.filter((task) => task.status === "COMPLETED"),
);
const groups = computed(() => {
  if (groupBy.value === "list") return groupTaskItems(visibleTasks.value, "list", store.lists);
  if (route.name === "tasks-shortcut" && route.params.shortcut === "archived")
    return [{ key: "open", label: "归档任务", tasks: visibleTasks.value }];
  const result = groupTaskItems(visibleTasks.value, "time", store.lists);
  if (["tasks-today", "tasks-tomorrow", "tasks-next7"].includes(route.name)) {
    const currentDay = result.find((group) => group.key === "today");
    if (currentDay) currentDay.label = title.value;
  }
  return result;
});
const todayCountdowns = computed(() => route.name === "tasks-today" && !route.query.date ? store.countdowns.filter((item) => !item.archived && !item.deleted) : []);
const todayHabits = computed(() => route.name === "tasks-today" && !route.query.date ? store.habits.filter((item) => !item.archived && !item.deleted) : []);
function groupColor(task) {
  const list = store.lists.find((item) => item.publicId === task.listId);
  return list?.color || (task.priority === "HIGH" ? "#ef4444" : task.priority === "MEDIUM" ? "#f59e0b" : "var(--accent)");
}
function countdownLabel(item) {
  const target = item.targetDate || item.date || item.dueDate;
  if (!target) return "倒数日";
  const days = Math.ceil((new Date(target).setHours(0, 0, 0, 0) - new Date().setHours(0, 0, 0, 0)) / 86400000);
  return days >= 0 ? `还有 ${days} 天` : `已过 ${Math.abs(days)} 天`;
}
function startOfDay(date) {
  date.setHours(0, 0, 0, 0);
  return date;
}
function addDays(date, days) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}
function inRange(value, start, end) {
  return value && new Date(value) >= start && new Date(value) < end;
}
function formatDate(value) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
function listName(id) {
  return store.lists.find((item) => item.publicId === id)?.name || "收件箱";
}
function tagName(id) {
  return store.tags.find((item) => item.publicId === id)?.name || "标签";
}
function taskName(id) {
  return store.tasks.find((item) => item.publicId === id)?.title || "任务";
}
function priorityLabel(value) {
  return { NONE: "无", LOW: "低", MEDIUM: "中", HIGH: "高" }[value] || value;
}
function recurrenceLabel(value) {
  return recurrenceOptions.find((option) => option.value === value)?.label || (value ? "自定义" : "不重复");
}
function itemCount(key) {
  const tasks = store.tasks.filter(item => !item.deleted && item.status !== "COMPLETED");
  const today = startOfDay(new Date());
  const tomorrow = addDays(today, 1);
  if (key === "all") return tasks.length;
  if (key === "assigned") return 0;
  if (key === "summary") return tasks.filter(item => item.dueAt && new Date(item.dueAt) < tomorrow).length;
  if (key === "inbox") return tasks.filter(item => item.listId === store.inbox?.publicId).length;
  if (key === "today") return tasks.filter(item => inRange(item.dueAt, today, tomorrow)).length;
  if (key === "tomorrow") return tasks.filter(item => inRange(item.dueAt, tomorrow, addDays(tomorrow, 1))).length;
  if (key === "next7") return tasks.filter(item => inRange(item.dueAt, today, addDays(today, 8))).length;
  return "";
}
function isActive(to) {
  return route.path === to || route.path.startsWith(`${to}/`);
}
function toggleSidebar() {
  sidebarHidden.value = !sidebarHidden.value;
}
function toggleSection(key) {
  sections[key] = !sections[key];
  persistSidebarSections();
}
function closeContext() {
  context.open = false;
  context.task = null;
  contextSubmenu.value = "";
  organizationMenuOpen.value = false;
}
async function setContextDue(offset) {
  const task = context.task;
  closeContext();
  if (!task) return;
  const dueAt = offset === null ? "" : addDays(startOfDay(new Date()), offset).toISOString();
  await store.update(task, { dueAt });
}
async function setTaskDue(offset) {
  if (!selectedTask.value) return;
  const dueAt = addDays(startOfDay(new Date()), offset).toISOString();
  selectedTask.value = await store.update(selectedTask.value, { dueAt });
}
async function clearTaskDue() {
  if (!selectedTask.value) return;
  selectedTask.value = await store.update(selectedTask.value, { dueAt: "" });
}
async function setTaskRepeat(value) {
  if (!selectedTask.value) return;
  selectedTask.value = await store.update(selectedTask.value, { rrule: value, recurrenceAnchor: value ? "DUE_DATE" : "" });
}
async function setTaskPriority(value) {
  if (!selectedTask.value) return;
  selectedTask.value = await store.update(selectedTask.value, { priority: value });
}
function addChecklistItem() {
  draft.checklistItems.push({ id: `new-${Date.now()}-${draft.checklistItems.length}`, title: "", completed: false });
}
function removeChecklistItem(index) {
  draft.checklistItems.splice(index, 1);
}
async function toggleChecklistItem(task, item) {
  const checklist = (task.checklist || []).map((entry) => entry === item || entry.publicId === item.publicId ? { ...entry, completed: !entry.completed } : entry);
  const updated = await store.update(task, { checklist });
  if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
}
function openContext(task, event) {
  if (!task) return;
  context.task = task;
  contextSubmenu.value = "";
  context.x = Math.max(8, Math.min(event.clientX, window.innerWidth - 290));
  const menuHeight = Math.min(window.innerHeight * 0.8, 680);
  context.y = Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8));
  context.open = true;
}
async function setContextPriority(priority) {
  const task = context.task;
  closeContext();
  if (task) await store.update(task, { priority });
}
async function moveContextTask(list) {
  const task = context.task;
  closeContext();
  if (task && list.publicId !== task.listId) await store.update(task, { listId: list.publicId });
}
async function toggleContextTag(tag) {
  const task = context.task;
  if (!task) return;
  const current = task.tagIds || [];
  const tagIds = current.includes(tag.publicId)
    ? current.filter((id) => id !== tag.publicId)
    : [...current, tag.publicId];
  try {
    const updated = await store.update(task, { tagIds });
    context.task = updated;
    message.success(current.includes(tag.publicId) ? `已移除标签 ${tag.name}` : `已添加标签 ${tag.name}`);
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "标签更新失败");
  }
}
async function startContextFocus() {
  const task = context.task;
  closeContext();
  if (!task) return;
  try {
    await focusStore.start({ taskId: task.publicId });
    await router.push("/tasks/focus");
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "无法开始专注");
  }
}
async function copyContextTaskLink() {
  const task = context.task;
  if (!task) return;
  const url = new URL("/tasks/all", window.location.origin);
  url.searchParams.set("task", task.publicId);
  try {
    await navigator.clipboard.writeText(url.href);
    closeContext();
    message.success("任务链接已复制");
  } catch {
    message.warning("复制失败，请检查浏览器剪贴板权限");
  }
}
function openDetail(task) {
  if (!task) return;
  selectedTask.value = task;
  editorOpen.value = false;
  inlineTitleEditing.value = false;
  inlineDescriptionEditing.value = false;
  void loadActivity(task);
  void loadCollaboration(task);
}
function beginInlineTitleEdit() {
  inlineTitleDraft.value = selectedTask.value?.title || "";
  inlineTitleEditing.value = true;
}
function cancelInlineTitleEdit() {
  inlineTitleEditing.value = false;
  inlineTitleDraft.value = selectedTask.value?.title || "";
}
async function saveInlineTitle() {
  if (!inlineTitleEditing.value || inlineTitleSaving.value) return;
  const title = inlineTitleDraft.value.trim();
  if (!title || title === selectedTask.value?.title) return cancelInlineTitleEdit();
  inlineTitleSaving.value = true;
  try {
    selectedTask.value = await store.update(selectedTask.value, { title });
    inlineTitleEditing.value = false;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "标题保存失败";
  } finally {
    inlineTitleSaving.value = false;
  }
}
function beginInlineDescriptionEdit() {
  inlineDescriptionDraft.value = selectedTask.value?.description || "";
  inlineDescriptionEditing.value = true;
}
function cancelInlineDescriptionEdit() {
  inlineDescriptionEditing.value = false;
  inlineDescriptionDraft.value = selectedTask.value?.description || "";
}
async function saveInlineDescription() {
  if (!selectedTask.value || inlineDescriptionSaving.value) return;
  inlineDescriptionSaving.value = true;
  try {
    selectedTask.value = await store.update(selectedTask.value, { description: inlineDescriptionDraft.value });
    inlineDescriptionEditing.value = false;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "描述保存失败";
  } finally {
    inlineDescriptionSaving.value = false;
  }
}
async function openEdit(task) {
  if (!task) return;
  editing.value = task;
  selectedTask.value = task;
  Object.assign(draft, {
    title: task.title,
    description: task.description || "",
    listId: task.listId || store.inbox?.publicId || "",
    parentId: task.parentId || "",
    tagIds: [...(task.tagIds || [])],
    priority: task.priority || "NONE",
    durationMinutes: task.durationMinutes || 0,
    allDay: Boolean(task.allDay),
    startAt: toLocal(task.startAt),
    dueAt: toLocal(task.dueAt),
    rrule: [
      "",
      "FREQ=DAILY",
      "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
      "FREQ=WEEKLY",
      "FREQ=MONTHLY",
      "FREQ=YEARLY",
    ].includes(task.rrule || "")
      ? task.rrule || ""
      : "CUSTOM",
    customRrule: [
      "",
      "FREQ=DAILY",
      "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
      "FREQ=WEEKLY",
      "FREQ=MONTHLY",
      "FREQ=YEARLY",
    ].includes(task.rrule || "")
      ? ""
      : task.rrule || "",
    recurrenceAnchor: task.recurrenceAnchor || "DUE_DATE",
    reminderEnabled: false,
    reminderKind: "RELATIVE",
    reminderOffset: 30,
    remindLocal: "",
    reminder: null,
    checklistText: (task.checklist || []).map((item) => item.title).join("\n"),
    checklistItems: (task.checklist || []).map((item, index) => ({ id: item.publicId || `item-${index}`, title: item.title || "", completed: Boolean(item.completed) })),
  });
  editorOpen.value = true;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  void loadActivity(task);
  void loadCollaboration(task);
  if (store.online) {
    try {
      const reminders = await apiListTaskReminders(task.publicId);
      const reminder = reminders?.[0];
      if (reminder)
        Object.assign(draft, {
          reminderEnabled: true,
          reminderKind: reminder.kind,
          reminderOffset: reminder.offsetMinutes ?? 30,
          remindLocal: reminder.remindAt ? toLocal(reminder.remindAt) : "",
          reminder,
        });
    } catch (error) {
      store.error =
        error?.response?.data?.detail || error?.message || "提醒加载失败";
    }
  }
}
function openCreate(parentId = "") {
  editing.value = null;
  selectedTask.value = {};
  resetCollaboration();
  Object.assign(draft, {
    title: "",
    description: "",
    listId: store.inbox?.publicId || "",
    parentId,
    tagIds: [],
    priority: "NONE",
    durationMinutes: 0,
    allDay: false,
    startAt: "",
    dueAt: "",
    rrule: "",
    customRrule: "",
    recurrenceAnchor: "DUE_DATE",
    reminderEnabled: false,
    reminderKind: "RELATIVE",
    reminderOffset: 30,
    remindLocal: "",
    reminder: null,
    checklistText: "",
    checklistItems: [],
  });
  editorOpen.value = true;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
}
function createSubtask(task) {
  if (!task) return;
  openCreate(task.publicId);
  draft.listId = task.listId || store.inbox?.publicId || "";
}
function closeDetail() {
  selectedTask.value = null;
  editing.value = null;
  editorOpen.value = false;
  inlineTitleEditing.value = false;
  inlineDescriptionEditing.value = false;
  resetCollaboration();
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
}
function toLocal(value) {
  if (!value) return "";
  const date = new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
async function quickAdd() {
  if (!quickTitle.value.trim()) return;
  await store.create({
    title: quickTitle.value.trim(),
    description: "",
    listId:
      route.name === "tasks-list"
        ? route.params.publicId
        : store.inbox?.publicId,
    priority: "NONE",
    startAt: "",
    dueAt: route.name === "tasks-today" ? new Date().toISOString() : "",
    durationMinutes: 0,
    allDay: false,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    rrule: "",
    recurrenceAnchor: "",
    tagIds: [],
    checklist: [],
  });
  quickTitle.value = "";
}
function resetCollaboration() {
  activities.value = [];
  activityLoading.value = false;
  comments.value = [];
  attachments.value = [];
  commentDraft.value = "";
  commentEditDraft.value = "";
  editingCommentId.value = "";
  commentError.value = "";
  attachmentError.value = "";
  attachmentProgress.value = 0;
  pendingAttachment.value = null;
}
async function loadActivity(task) {
  if (!task?.publicId || !store.online) {
    activities.value = [];
    return;
  }
  activityLoading.value = true;
  try {
    activities.value = (await apiListTaskActivity(task.publicId, { size: 20 }))?.items || [];
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "活动记录加载失败";
  } finally {
    activityLoading.value = false;
  }
}
async function loadCollaboration(task) {
  comments.value = [];
  attachments.value = [];
  commentError.value = "";
  attachmentError.value = "";
  if (!task?.publicId || !store.online) return;
  const [commentResult, attachmentResult] = await Promise.allSettled([
    apiListTaskComments(task.publicId, { size: 50 }),
    apiListTaskAttachments(task.publicId),
  ]);
  if (commentResult.status === "fulfilled") comments.value = commentResult.value?.items || [];
  else commentError.value = commentResult.reason?.response?.data?.detail || commentResult.reason?.message || "评论加载失败";
  if (attachmentResult.status === "fulfilled") attachments.value = attachmentResult.value || [];
  else attachmentError.value = attachmentResult.reason?.response?.data?.detail || attachmentResult.reason?.message || "附件加载失败";
}
async function addComment() {
  if (!editing.value || !commentDraft.value.trim()) return;
  if (!store.online) {
    commentError.value = "评论需要联网保存";
    return;
  }
  commentError.value = "";
  try {
    await apiCreateTaskComment(editing.value.publicId, { bodyMarkdown: commentDraft.value.trim() });
    commentDraft.value = "";
    await Promise.all([loadCollaboration(editing.value), loadActivity(editing.value)]);
  } catch (error) {
    commentError.value = error?.response?.data?.detail || error?.message || "发表评论失败";
  }
}
function startCommentEdit(comment) {
  editingCommentId.value = comment.publicId;
  commentEditDraft.value = comment.bodyMarkdown || "";
}
function cancelCommentEdit() {
  editingCommentId.value = "";
  commentEditDraft.value = "";
}
async function saveComment(comment) {
  if (!editing.value || !commentEditDraft.value.trim()) return;
  if (!store.online) {
    commentError.value = "评论需要联网保存";
    return;
  }
  commentError.value = "";
  try {
    await apiUpdateTaskComment(editing.value.publicId, comment.publicId, { bodyMarkdown: commentEditDraft.value.trim() }, comment.revision);
    cancelCommentEdit();
    await Promise.all([loadCollaboration(editing.value), loadActivity(editing.value)]);
  } catch (error) {
    commentError.value = error?.response?.data?.detail || error?.message || "保存评论失败";
  }
}
async function removeComment(comment) {
  if (!editing.value || !window.confirm("删除这条评论？")) return;
  if (!store.online) {
    commentError.value = "评论需要联网删除";
    return;
  }
  try {
    await apiDeleteTaskComment(editing.value.publicId, comment.publicId, comment.revision);
    await Promise.all([loadCollaboration(editing.value), loadActivity(editing.value)]);
  } catch (error) {
    commentError.value = error?.response?.data?.detail || error?.message || "删除评论失败";
  }
}
async function uploadAttachment(event) {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file || !editing.value) return;
  pendingAttachment.value = file;
  await uploadAttachmentFile(file);
}
async function uploadAttachmentFile(file) {
  if (!editing.value || !file) return;
  if (!store.online) {
    attachmentError.value = "当前离线，附件未上传；联网后可重试";
    return;
  }
  attachmentError.value = "";
  attachmentProgress.value = 1;
  try {
    await apiUploadTaskAttachment(editing.value.publicId, file, (value) => { attachmentProgress.value = value; });
    pendingAttachment.value = null;
    await Promise.all([loadCollaboration(editing.value), loadActivity(editing.value)]);
  } catch (error) {
    attachmentError.value = error?.response?.data?.detail || error?.message || "上传附件失败";
  } finally {
    attachmentProgress.value = 0;
  }
}
async function retryAttachment() {
  await uploadAttachmentFile(pendingAttachment.value);
}
async function downloadAttachment(file) {
  const task = editing.value || selectedTask.value;
  if (!task?.publicId) return;
  try {
    const blob = await apiDownloadTaskAttachment(task.publicId, file.publicId);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = file.fileName;
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    attachmentError.value = error?.response?.data?.detail || error?.message || "下载附件失败";
  }
}
async function removeAttachment(file) {
  if (!editing.value || !window.confirm(`删除附件“${file.fileName}”？`)) return;
  if (!store.online) {
    attachmentError.value = "附件需要联网删除";
    return;
  }
  try {
    await apiDeleteTaskAttachment(editing.value.publicId, file.publicId, file.revision);
    await Promise.all([loadCollaboration(editing.value), loadActivity(editing.value)]);
  } catch (error) {
    attachmentError.value = error?.response?.data?.detail || error?.message || "删除附件失败";
  }
}
function formatBytes(size = 0) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
async function saveDetail() {
  if ((draft.reminderEnabled || draft.reminder) && !store.online) {
    store.error = "提醒需要联网保存";
    return;
  }
  const rrule =
    draft.rrule === "CUSTOM" ? draft.customRrule.trim() : draft.rrule;
  const payload = {
    title: draft.title.trim(),
    description: draft.description,
    listId: draft.listId,
    parentId: draft.parentId || "",
    tagIds: draft.tagIds,
    priority: draft.priority,
    startAt: draft.startAt ? new Date(draft.startAt).toISOString() : "",
    dueAt: draft.dueAt ? new Date(draft.dueAt).toISOString() : "",
    durationMinutes: Number(draft.durationMinutes) || 0,
    allDay: draft.allDay,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    rrule,
    recurrenceAnchor: rrule ? draft.recurrenceAnchor : "",
    checklist: draft.checklistItems
      .map((item, index) => ({
        title: item.title.trim(),
        completed: Boolean(item.completed),
        sortOrder: index,
      }))
      .filter((item) => item.title),
  };
  try {
    const saved = editing.value
      ? await store.update(editing.value, payload)
      : await store.create(payload);
    if (draft.reminderEnabled || draft.reminder) {
      await store.syncNow();
      const serverTask =
        store.tasks.find((item) => item.publicId === saved.publicId) || saved;
      const reminder = {
        kind: draft.reminderKind,
        offsetMinutes:
          draft.reminderKind === "RELATIVE" ? draft.reminderOffset : null,
        remindAt:
          draft.reminderKind === "ABSOLUTE" && draft.remindLocal
            ? new Date(draft.remindLocal).toISOString()
            : null,
        channel: "IN_APP",
        dailyUntilDone: false,
      };
      if (draft.reminderEnabled) {
        if (draft.reminder)
          await apiUpdateTaskReminder(
            serverTask.publicId,
            draft.reminder.publicId,
            reminder,
            draft.reminder.revision,
          );
        else await apiCreateTaskReminder(serverTask.publicId, reminder);
      } else if (draft.reminder) {
        await apiDeleteTaskReminder(
          serverTask.publicId,
          draft.reminder.publicId,
          draft.reminder.revision,
        );
      }
    }
    selectedTask.value =
      store.tasks.find((item) => item.publicId === saved.publicId) || saved;
    editorOpen.value = false;
  } catch (error) {
    store.error =
      error?.response?.data?.detail || error?.message || "任务保存失败";
  }
}
async function toggleComplete(task) {
  if (!task) return;
  await (task.status === "COMPLETED"
    ? store.reopen(task)
    : store.complete(task));
  selectedTask.value =
    store.tasks.find((item) => item.publicId === task.publicId) || null;
}
async function duplicateTask(task) {
  if (!task) return;
  await store.create({
    ...task,
    title: `${task.title} 副本`,
    status: "OPEN",
    completedAt: null,
    publicId: undefined,
  });
}
async function deleteTask(task) {
  if (!task || !window.confirm(`删除任务“${task.title}”？`)) return;
  await store.remove(task);
  if (selectedTask.value?.publicId === task.publicId) closeDetail();
}
function toggleSelected(task) {
  selectedIds.has(task.publicId)
    ? selectedIds.delete(task.publicId)
    : selectedIds.add(task.publicId);
}
function toggleAllSelected() {
  if (selectedIds.size === visibleTasks.value.length) selectedIds.clear();
  else visibleTasks.value.forEach((task) => selectedIds.add(task.publicId));
}
function habitDone(habit) {
  const today = new Date().toISOString().slice(0, 10);
  return store.habitCheckins.some((item) => item.habitId === habit.publicId && item.date === today && item.status === "DONE");
}
async function toggleHabitToday(habit) {
  const done = habitDone(habit);
  await store.checkinHabit(habit, { date: new Date().toISOString().slice(0, 10), count: done ? 0 : 1, status: done ? "SKIP" : "DONE" });
}
async function batchComplete() {
  await store.batchTasks(
    [...selectedIds]
      .map((id) => store.tasks.find((task) => task.publicId === id))
      .filter(Boolean),
    "COMPLETE",
  );
  selectedIds.clear();
  batchMode.value = false;
}
async function batchDelete() {
  if (!window.confirm(`删除选中的 ${selectedIds.size} 项任务？`)) return;
  await store.batchTasks(
    [...selectedIds]
      .map((id) => store.tasks.find((task) => task.publicId === id))
      .filter(Boolean),
    "DELETE",
  );
  selectedIds.clear();
  batchMode.value = false;
}
function startTaskDrag(task, event) {
  if (batchMode.value || sort.value !== "manual") return;
  dragged.value = task;
  dragOverTask.value = null;
  event.dataTransfer?.setData("text/plain", task.publicId);
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
}
async function dropTask(target, index = 0) {
  if (!dragged.value || dragged.value.publicId === target.publicId || batchMode.value) return endTaskDrag();
  try {
    await store.sortTask(dragged.value, index);
  } finally {
    endTaskDrag();
  }
}
function endTaskDrag() {
  dragged.value = null;
  dragOverTask.value = null;
}
async function createList(groupId = "") {
  openOrganizationDialog("list", null, { groupId });
}
async function createTag() {
  openOrganizationDialog("tag");
}
function listsInGroup(groupId) { return activeLists.value.filter(item => item.groupId === groupId).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)); }
function persistSidebarSections() { try { localStorage.setItem(`task_sidebar_sections:${store.accountScope || "anonymous"}`, JSON.stringify(sections)); } catch {} }
function loadSidebarSections() { try { Object.assign(sections, JSON.parse(localStorage.getItem(`task_sidebar_sections:${store.accountScope || "anonymous"}`) || "{}")); } catch {} }
function openOrganizationDialog(kind, item = null, overrides = {}) {
  const defaults = { list: { color: "#2563eb", groupId: "" }, tag: { color: "#2f746f", parentId: "" }, filter: {}, group: {} }[kind] || {};
  Object.assign(organizationDialog, { open: true, kind, item, name: item?.name || "", color: item?.color || defaults.color || "#2563eb", parentId: item?.parentId || defaults.parentId || "", groupId: item?.groupId || overrides.groupId || "" });
  organizationDialogOpen.value = true;
  organizationMenuOpen.value = false;
}
function openOrganizationMenu(kind, item, event) {
  const width = 210, height = 230;
  Object.assign(organizationMenu, { open: true, kind, item, x: Math.max(8, Math.min(event.clientX, window.innerWidth - width - 8)), y: Math.max(8, Math.min(event.clientY, window.innerHeight - height - 8)) });
  organizationMenuOpen.value = true;
}
function editOrganization(kind) { openOrganizationDialog(kind, organizationMenu.item); }
function createListGroupForItem() { const list = organizationMenu.item; openOrganizationDialog("group", null, { groupId: list.publicId }); }
function editListGroup() { openOrganizationDialog("group", organizationMenu.item); organizationMenuOpen.value = false; }
function createChildTag() { const tag = organizationMenu.item; openOrganizationDialog("tag", null, { parentId: tag.publicId }); }
async function saveOrganization() {
  const item = organizationDialog.item, kind = organizationDialog.kind;
  const name = organizationDialog.name.trim();
  if (!name) return;
  try {
    if (kind === "list") {
      const payload = { name, color: organizationDialog.color, icon: item?.icon || "", archived: item?.archived || false, sortOrder: item?.sortOrder ?? store.lists.length };
      const saved = item ? await store.updateList(item, payload) : await store.createList(payload);
      const target = store.lists.find(value => value.publicId === (item?.publicId || saved?.id || saved?.publicId));
      if (target && (organizationDialog.groupId || target.groupId)) await store.assignListGroup(target, organizationDialog.groupId);
    } else if (kind === "tag") {
      const payload = { name, color: organizationDialog.color, parentId: organizationDialog.parentId || null, sortOrder: item?.sortOrder ?? store.tags.length };
      if (item) await apiUpdateTaskTag(item.publicId, payload, item.revision); else await store.createTag(payload);
      await store.refreshServer();
    } else if (kind === "filter") {
      const payload = { ...item, name, sortOrder: item?.sortOrder ?? filters.value.length };
      if (item) await apiUpdateTaskFilter(item.publicId, payload, item.revision);
      else await apiCreateTaskFilter({ name, sortOrder: filters.value.length, status: "OPEN", dueRange: "ANY", listIds: [], tagIds: [], priorities: [], includeSubtasks: true, matchAllTags: false, pinned: false });
      await loadFilters();
    } else if (kind === "group") {
      if (item) await store.updateListGroup(item, { name, sortOrder: item.sortOrder });
      else {
        const group = await apiCreateTaskListGroup({ name, sortOrder: store.listGroups.length });
        await store.refreshServer();
        const list = store.lists.find(value => value.publicId === organizationDialog.groupId);
        const createdGroup = store.listGroups.find(value => value.publicId === group?.publicId || value.name === name);
        if (list && createdGroup) await store.assignListGroup(list, createdGroup.publicId);
      }
    }
    organizationDialogOpen.value = false;
    await store.refreshServer();
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "保存失败"; }
}
async function archiveList() {
  const item = organizationMenu.item;
  organizationMenuOpen.value = false;
  try { await store.updateList(item, { archived: !item.archived }); await store.refreshServer(); } catch (error) { store.error = error?.message || "归档清单失败"; }
}
async function deleteOrganization(kind) {
  const item = organizationMenu.item;
  organizationMenuOpen.value = false;
  if (!window.confirm(`删除${({ list: "清单", tag: "标签", filter: "筛选器" })[kind]}“${item.name}”？`)) return;
  try {
    if (kind === "list") await store.deleteList(item);
    else if (kind === "tag") { await apiDeleteTaskTag(item.publicId, item.revision); await store.refreshServer(); }
    else { await apiDeleteTaskFilter(item.publicId, item.revision); await loadFilters(); }
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "删除失败"; }
}
async function toggleListGroupAssignment() {
  const item = organizationMenu.item; organizationMenuOpen.value = false;
  try {
    if (item.groupId) await store.assignListGroup(item, "");
    else {
      let group = sortedListGroups.value[0];
      if (!group) { const name = window.prompt("新建清单分组"); if (!name?.trim()) return; await apiCreateTaskListGroup({ name: name.trim(), sortOrder: 0 }); await store.refreshServer(); group = store.listGroups[0]; }
      if (group) await store.assignListGroup(item, group.publicId);
    }
  } catch (error) { store.error = error?.message || "更新清单分组失败"; }
}
async function deleteListGroup() {
  const item = organizationMenu.item; organizationMenuOpen.value = false;
  if (!window.confirm(`删除分组“${item.name}”？分组中的清单将保留。`)) return;
  try { await store.deleteListGroup(item); } catch (error) { store.error = error?.message || "删除分组失败"; }
}
function startOrganizationDrag(kind, item) { organizationDrag.value = { kind, item }; organizationDropTarget.value = ""; }
async function dropOrganization(kind, target) {
  const source = organizationDrag.value;
  if (!source || source.kind !== kind || source.item.publicId === target.publicId) return endOrganizationDrag();
  try {
    if (kind === "list" && source.item.groupId !== target.groupId) await store.assignListGroup(source.item, target.groupId || "");
    const collection = kind === "list" ? activeLists.value.filter(value => (value.groupId || "") === (target.groupId || "")) : kind === "tag" ? store.tags.filter(value => (value.parentId || "") === (target.parentId || "")) : filters.value;
    const ordered = moveItem(collection, source.item.publicId, target.publicId);
    if (kind === "list") for (const value of ordered) await store.updateList(store.lists.find(item => item.publicId === value.publicId), { sortOrder: value.sortOrder });
    else if (kind === "tag") for (const value of ordered) await apiUpdateTaskTag(value.publicId, { name: value.name, color: value.color || "", parentId: value.parentId || "", sortOrder: value.sortOrder }, value.revision);
    else for (const value of ordered) await apiUpdateTaskFilter(value.publicId, { ...value, sortOrder: value.sortOrder }, value.revision);
    await store.refreshServer(); await loadFilters();
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "排序保存失败"; }
  endOrganizationDrag();
}
async function dropOrganizationRoot(kind) {
  if (kind === "list" && organizationDrag.value?.kind === "list" && organizationDrag.value.item.groupId) {
    try { await store.assignListGroup(organizationDrag.value.item, ""); await store.refreshServer(); } catch (error) { store.error = error?.message || "移出分组失败"; }
  }
  endOrganizationDrag();
}
async function dropListGroup(group) {
  const source = organizationDrag.value;
  if (!source || source.kind !== "list") return endOrganizationDrag();
  try { await store.assignListGroup(source.item, group.publicId); await store.refreshServer(); } catch (error) { store.error = error?.message || "移动清单失败"; }
  endOrganizationDrag();
}
async function dropGroup(target) {
  const source = organizationDrag.value;
  if (!source || source.item.publicId === target.publicId) return endOrganizationDrag();
  try {
    if (source.kind === "list") {
      await store.assignListGroup(source.item, target.publicId);
    } else if (source.kind === "group") {
      const ordered = moveItem(sortedListGroups.value, source.item.publicId, target.publicId);
      for (const value of ordered) await apiUpdateTaskListGroup(value.publicId, { name: value.name, sortOrder: value.sortOrder }, value.revision);
      await store.refreshServer();
    }
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "移动失败"; }
  endOrganizationDrag();
}
function endOrganizationDrag() { organizationDrag.value = null; organizationDropTarget.value = ""; }
async function loadFilters() {
  if (!store.online) return;
  try {
    filters.value = await apiListTaskFilters();
  } catch {
    filters.value = [];
  }
}
function onKeydown(event) {
  if (event.key === "Escape") {
    moreOpen.value = false;
    closeContext();
    organizationMenuOpen.value = false;
    if (editorOpen.value) closeDetail();
  }
  if (
    event.key.toLowerCase() === "n" &&
    !["INPUT", "TEXTAREA", "SELECT"].includes(event.target?.tagName)
  ) {
    event.preventDefault();
    document.querySelector(".clone-quick-add input")?.focus();
  }
}
watch(
  () => route.fullPath,
  () => {
    closeContext();
    if (route.query.task) {
      const task = store.tasks.find(
        (item) => item.publicId === route.query.task,
      );
      if (task) openDetail(task);
    }
  },
);
watch(
  () => store.tasks,
  () => {
    if (selectedTask.value?.publicId)
      selectedTask.value =
        store.tasks.find(
          (item) => item.publicId === selectedTask.value.publicId,
        ) || null;
  },
  { deep: true },
);
onMounted(async () => {
  await store.init();
  loadViewPreferences();
  loadSidebarSections();
  loadListGroupState();
  await loadFilters();
  document.addEventListener("click", closeContext);
  window.addEventListener("keydown", onKeydown);
});
onBeforeUnmount(() => {
  document.removeEventListener("click", closeContext);
  window.removeEventListener("keydown", onKeydown);
});

const SidebarSection = {
  props: { title: String, open: Boolean },
  emits: ["toggle"],
  template:
    '<section class="clone-sidebar-section"><header><button type="button" @click="$emit(\'toggle\')"><ChevronDown :size="14" :class="{ rotated: !open }" />{{ title }}</button><slot name="actions" /></header><div v-show="open" class="clone-sidebar-items"><slot /></div></section>',
  components: { ChevronDown },
};
</script>

<style scoped>
.clone-task-shell {
  display: grid;
  grid-template-columns: 240px minmax(420px, 1fr) 342px;
  height: 100vh;
  margin: 0;
  background: var(--card);
  color: var(--ink);
  overflow: hidden;
}
.clone-task-sidebar {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 14px;
  padding: 14px 10px;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--card) 96%, var(--paper));
  overflow: auto;
}
.clone-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
}
.clone-sidebar-head { display: none; }
.clone-sidebar-head button,
.clone-icon-button,
.clone-row-more {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.clone-sidebar-head button:hover,
.clone-icon-button:hover,
.clone-row-more:hover {
  background: var(--paper);
  color: var(--ink);
}
.clone-primary-nav,
.clone-sidebar-items {
  display: grid;
  gap: 3px;
}
.clone-primary-nav a,
.clone-sidebar-footer a,
.clone-nav-item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 36px;
  padding: 7px 10px;
  border-radius: 8px;
  color: var(--ink2);
  font-size: 13px;
  text-decoration: none;
}
.clone-primary-nav a:hover,
.clone-primary-nav a.active,
.clone-nav-item:hover,
.clone-nav-item.active,
.clone-sidebar-footer a:hover {
  background: color-mix(in srgb, var(--ink) 7%, transparent);
  color: var(--ink);
}
.clone-primary-nav a.active {
  font-weight: 700;
}
.clone-primary-nav span,
.clone-nav-item span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clone-primary-nav small {
  color: var(--muted);
  font-size: 11px;
}
.clone-sidebar-section {
  display: grid;
  gap: 7px;
}
.clone-list-group { display: grid; gap: 2px; }
.clone-list-group-heading { display: flex; align-items: center; gap: 4px; min-height: 28px; padding: 0 8px 0 10px; color: var(--muted); font-size: 11px; font-weight: 700; }
.clone-list-group-heading span { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-list-group-heading button { display: grid; width: 22px; height: 22px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-list-group-heading button:hover, .clone-item-menu-trigger:hover { background: var(--paper); color: var(--ink); }
.clone-sortable-item { position: relative; }
.clone-sortable-item.drag-over { outline: 1px solid var(--accent); background: color-mix(in srgb, var(--accent) 9%, var(--card)); }
.clone-item-menu-trigger { display: grid; flex: none; width: 24px; height: 24px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); opacity: 0; cursor: pointer; }
.clone-sortable-item:hover .clone-item-menu-trigger, .clone-sortable-item:focus-within .clone-item-menu-trigger { opacity: 1; }
.clone-child-tag { margin-left: 16px; }
.clone-tag-group { display: grid; gap: 2px; }
.clone-organization-form { display: grid; gap: 14px; }
.clone-organization-form label { display: grid; gap: 6px; color: var(--muted); font-size: 13px; }
.clone-organization-form input:not([type="color"]), .clone-organization-form select { min-height: 38px; padding: 7px 9px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink); }
.clone-organization-form input[type="color"] { width: 46px; height: 34px; padding: 2px; border: 1px solid var(--line); background: var(--card); }
.clone-organization-form footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 6px; }
.clone-organization-form footer > button:first-child { min-height: 34px; padding: 0 12px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); }
.clone-sidebar-section > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
}
.clone-sidebar-section > header button {
  display: flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.clone-sidebar-section > header svg {
  transition: transform 0.15s;
}
.rotated {
  transform: rotate(-90deg);
}
.sidebar-create {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.clone-empty-item {
  padding: 8px 10px;
  color: var(--muted);
  font-size: 12px;
}
.clone-sidebar-footer {
  display: grid;
  gap: 3px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.clone-week-nav { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; padding: 8px 2px 0; border-top: 1px solid var(--line); }
.clone-week-nav a { display: grid; min-width: 0; min-height: 42px; place-content: center; gap: 2px; border-radius: 6px; color: var(--muted); text-align: center; text-decoration: none; }
.clone-week-nav small { font-size: 9px; }
.clone-week-nav span { font-size: 11px; font-variant-numeric: tabular-nums; }
.clone-week-nav a:hover, .clone-week-nav a.selected { background: color-mix(in srgb, var(--accent) 10%, var(--card)); color: var(--accent); }
.clone-task-main {
  position: relative;
  min-width: 0;
  overflow: auto;
  padding: 20px 28px 100px;
}
.clone-task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 54px;
}
.clone-title-block,
.clone-header-actions {
  display: flex;
  align-items: center;
  gap: 9px;
}
.clone-title-block p {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
}
.clone-title-block p,
.clone-title-block > div > span { display: none; }
.clone-title-block h1 {
  margin: 0;
  font-size: 25px;
  line-height: 1.2;
}
.clone-title-block span {
  color: var(--muted);
  font-size: 12px;
}
.clone-header-actions {
  justify-content: flex-end;
}
.clone-sync-state { display: none; }
.clone-sync-state {
  color: var(--muted);
  font-size: 11px;
}
.clone-primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: 7px;
  background: var(--accent);
  color: var(--accent-contrast, #fff);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.clone-header-actions .clone-primary-button { display: none; }
.clone-primary-button.full {
  width: 100%;
  margin-top: 22px;
}
.clone-quick-add {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 22px 0 18px;
  padding: 0 16px;
  min-height: 56px;
  border: 0;
  border-radius: 10px;
  background: color-mix(in srgb, var(--paper) 78%, var(--card));
  color: var(--muted);
}
.clone-quick-add form {
  display: flex;
  align-items: center;
  flex: 1;
  gap: 8px;
}
.clone-quick-add input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
}
.clone-quick-add form button {
  border: 0;
  border-radius: 6px;
  background: var(--accent);
  color: var(--accent-contrast, #fff);
  padding: 6px 10px;
  cursor: pointer;
}
.clone-quick-add form button:disabled {
  opacity: 0.45;
}
.clone-quick-add kbd {
  padding: 2px 6px;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--muted);
  font-size: 11px;
}
.clone-quick-add form button,
.clone-quick-add kbd { display: none; }
.clone-status,
.clone-error {
  display: flex;
  gap: 7px;
  align-items: center;
  margin: 9px 0;
  padding: 9px 11px;
  border-radius: 7px;
  background: color-mix(in srgb, var(--accent) 8%, var(--card));
  color: var(--muted);
  font-size: 12px;
}
.clone-error {
  background: color-mix(in srgb, var(--down) 8%, var(--card));
  color: var(--down);
}
.clone-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 0;
  color: var(--muted);
  font-size: 12px;
}
.clone-list-toolbar { display: flex; }
.clone-list-toolbar button {
  border: 0;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.clone-list-toolbar button.active {
  color: var(--accent);
  font-weight: 700;
}
.clone-task-groups {
  display: grid;
  gap: 18px;
}
.clone-group-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 32px;
  border: 0;
  background: transparent;
  color: var(--ink2);
  text-align: left;
  cursor: pointer;
}
.clone-group-heading span {
  color: var(--muted);
  font-size: 12px;
}
.clone-group-heading svg {
  transition: transform 0.15s;
}
.clone-task-rows {
  border-top: 1px solid var(--line);
}
.clone-task-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  border-bottom: 1px solid var(--line);
  padding: 0 8px;
  border-left: 3px solid var(--task-group-color, transparent);
  transition: background 0.12s ease, box-shadow 0.12s ease, opacity 0.12s ease, transform 0.12s ease;
}
.clone-task-row.dragging { opacity: .42; }
.clone-task-row.drag-over { box-shadow: inset 0 3px 0 var(--accent), inset 3px 0 0 var(--task-group-color, var(--accent)); }
.clone-drag-handle { display: none; }
.clone-check,
.clone-select-box { width: 20px; height: 20px; border-radius: 5px; }
.clone-task-row:hover,
.clone-task-row.selected {
  background: color-mix(in srgb, var(--accent) 5%, var(--card));
  box-shadow: inset 3px 0 0 var(--accent);
}
.clone-task-row.multi {
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}
.clone-task-row.checked .clone-task-content strong {
  color: var(--muted);
  text-decoration: line-through;
}
.clone-drag-handle {
  flex: none;
  color: color-mix(in srgb, var(--muted) 65%, transparent);
}
.clone-check,
.clone-select-box {
  display: grid;
  flex: none;
  width: 21px;
  height: 21px;
  place-items: center;
  border: 1.5px solid var(--line2);
  border-radius: 50%;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}
.clone-select-box {
  border-radius: 5px;
}
.clone-task-content {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 2px;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}
.clone-task-content strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clone-task-content small {
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clone-task-content > span {
  display: flex;
  gap: 7px;
  overflow: hidden;
  color: var(--muted);
  font-size: 10px;
  white-space: nowrap;
}
.clone-task-content > span span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.clone-task-row em {
  font-style: normal;
  font-size: 11px;
}
.clone-task-row em[data-priority="HIGH"] {
  color: var(--down);
}
.clone-task-row em[data-priority="MEDIUM"] {
  color: var(--warn);
}
.clone-task-row[data-priority="HIGH"] .clone-check { border-color: var(--down); color: var(--down); }
.clone-task-row[data-priority="MEDIUM"] .clone-check { border-color: var(--warn); color: var(--warn); }
.clone-task-row[data-priority="LOW"] .clone-check { border-color: var(--accent); }
.clone-row-more {
  flex: none;
}
.clone-empty-state {
  display: grid;
  justify-items: center;
  gap: 9px;
  min-height: 260px;
  align-content: center;
  color: var(--muted);
}
.clone-efficiency-groups { display: grid; gap: 18px; margin-top: 18px; }
.clone-efficiency-rows { border-top: 1px solid var(--line); }
.clone-efficiency-row { display: flex; align-items: center; gap: 9px; min-height: 42px; padding: 0 8px; border-bottom: 1px solid var(--line); border-left: 3px solid var(--efficiency-color, #22c55e); }
.clone-efficiency-row strong { min-width: 0; flex: 1; overflow: hidden; font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.clone-efficiency-icon { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; background: var(--accent-soft); font-size: 13px; }
.clone-efficiency-meta { color: var(--muted); font-size: 11px; }
.clone-efficiency-check { display: grid; width: 21px; height: 21px; place-items: center; border: 1.5px solid var(--line2); border-radius: 50%; background: transparent; color: var(--accent); cursor: pointer; }
.clone-efficiency-check.checked { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast, #fff); }
.countdown-row { --efficiency-color: #f59e0b; }
.clone-detail-empty {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 10px;
  height: calc(100% - 56px);
  padding: 24px;
  color: var(--muted);
  text-align: center;
}
.clone-detail-empty-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ink2);
  font-size: 23px;
}
.clone-detail-empty strong { color: var(--ink2); font-size: 14px; }
.clone-detail-empty p { max-width: 190px; margin: 0; font-size: 12px; line-height: 1.6; }
.clone-empty-state strong {
  color: var(--ink2);
  font-size: 15px;
}
.clone-floating-menu,
.clone-context-menu {
  position: fixed;
  z-index: 90;
  display: grid;
  min-width: 190px;
  padding: 7px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--card);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.18);
}
.clone-floating-menu {
  top: 118px;
  right: 24px;
}
.clone-view-menu { width: 252px; max-height: min(74vh, 620px); overflow: auto; padding: 8px; }
.clone-view-menu > strong { padding: 8px 9px 4px; color: var(--muted); font-size: 11px; }
.clone-view-menu > button { min-height: 32px; }
.clone-view-menu > button:disabled { color: var(--muted); opacity: .5; cursor: default; }
.clone-view-menu hr { width: 100%; margin: 5px 0; border: 0; border-top: 1px solid var(--line); }
.clone-view-menu .clone-menu-check-spacer { flex: 0 0 14px; width: 14px; height: 14px; }
.clone-view-menu-order { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 4px 9px; color: var(--muted); font-size: 12px; }
.clone-view-menu-order button { min-height: 28px; padding: 3px 8px; border: 1px solid var(--line); border-radius: 5px; background: var(--card); color: var(--ink2); font: inherit; cursor: pointer; }
.clone-view-menu-order button:hover { border-color: var(--accent); color: var(--accent); }
.clone-floating-menu button,
.clone-context-menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}
.clone-floating-menu button:hover,
.clone-context-menu button:hover {
  background: var(--accent-soft);
  color: var(--accent);
}
.clone-context-menu strong {
  overflow: hidden;
  padding: 7px 9px;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clone-task-row-menu { width: min(290px, calc(100vw - 16px)); max-height: min(80vh, 680px); overflow-y: auto; overscroll-behavior: contain; }
.clone-task-row-menu > button { width: 100%; }
.clone-task-row-menu > button > svg:last-child { margin-left: auto; }
.clone-task-row-menu button:disabled { color: var(--muted); opacity: .55; cursor: default; }
.clone-context-submenu { display: grid; gap: 2px; max-height: 180px; overflow: auto; margin: 0 4px 4px 22px; padding: 4px; border-left: 1px solid var(--line); }
.clone-context-submenu button { min-height: 29px; padding: 5px 7px; font-size: 12px; }
.clone-context-submenu label { display: flex; align-items: center; gap: 7px; min-height: 28px; padding: 4px 7px; border-radius: 5px; color: var(--ink2); font-size: 12px; cursor: pointer; }
.clone-context-submenu label:hover { background: var(--accent-soft); }
.clone-context-submenu input { flex: 0 0 auto; width: 14px; min-height: 14px; margin: 0; accent-color: var(--accent); }
.clone-context-submenu > span { padding: 6px 7px; color: var(--muted); font-size: 12px; }
.clone-context-divider { height: 1px; margin: 5px 3px; background: var(--line); }
.clone-context-menu .danger,
.clone-detail-actions .danger {
  color: var(--down);
}
.clone-batch-bar {
  position: fixed;
  z-index: 70;
  bottom: 24px;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 8px;
  transform: translateX(-50%);
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--card);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.16);
}
.clone-batch-bar button {
  border: 0;
  border-radius: 6px;
  padding: 7px 9px;
  background: var(--accent);
  color: var(--accent-contrast, #fff);
  cursor: pointer;
}
.clone-batch-bar button.danger {
  background: transparent;
  color: var(--down);
}
.clone-detail-pane {
  min-width: 0;
  overflow: auto;
  border-left: 1px solid var(--line);
  background: color-mix(in srgb, var(--card) 98%, var(--paper));
}
.clone-detail-pane > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}
.clone-detail-toolbar {
  position: relative;
  min-height: 52px !important;
  padding: 0 12px !important;
  color: var(--ink2) !important;
}
.clone-detail-toolbar-main,
.clone-detail-toolbar-actions { display: flex; align-items: center; gap: 3px; min-width: 0; }
.clone-detail-toolbar-main { flex: 1; overflow: visible; }
.clone-detail-toolbar-actions { margin-left: auto; }
.clone-detail-tool,
.clone-detail-priority-tool {
  display: inline-flex; align-items: center; gap: 5px; min-height: 32px; max-width: 132px;
  padding: 5px 7px; border: 0; border-radius: 5px; background: transparent; color: var(--muted);
  font: inherit; font-size: 11px; cursor: pointer; white-space: nowrap;
}
.clone-detail-tool span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-detail-tool:hover { background: var(--accent-soft); color: var(--accent); }
.clone-detail-list-tool { width: 28px; padding-inline: 6px; color: var(--ink2); font-weight: 600; }
.clone-detail-list-tool span { display: none; }
.clone-detail-toolbar-main > .clone-detail-tool:nth-child(2) { max-width: 95px; }
.clone-detail-toolbar-main > .clone-detail-tool:nth-child(3) { max-width: 72px; }
.clone-detail-priority-tool { color: var(--muted); font-size: 20px; line-height: 1; }
.clone-detail-priority-tool[data-priority="HIGH"] { color: var(--down); }
.clone-detail-priority-tool[data-priority="MEDIUM"] { color: var(--warn); }
.clone-detail-repeat-icon { font-size: 18px !important; line-height: 1; }
.clone-detail-close { margin-left: 2px; }
.clone-toolbar-popover {
  position: absolute; z-index: 110; top: 46px; left: 12px; display: grid; gap: 7px; min-width: 170px;
  padding: 10px; border: 1px solid var(--line); border-radius: 7px; background: var(--card);
  box-shadow: 0 14px 30px rgba(15,23,42,.18);
}
.clone-toolbar-popover strong { color: var(--ink2); font-size: 12px; }
.clone-toolbar-popover button { min-height: 29px; padding: 5px 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); text-align: left; cursor: pointer; }
.clone-toolbar-popover button:hover, .clone-toolbar-popover button.active { background: var(--accent-soft); color: var(--accent); }
.clone-toolbar-popover input { width: 100%; box-sizing: border-box; }
.clone-repeat-toolbar-popover { left: 150px; }
.clone-popover-current { color: var(--muted); font-size: 12px; }
.clone-detail-pane.is-empty > header { display: none; }
.clone-detail-pane > header button {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.clone-detail-toolbar .clone-detail-tool,
.clone-detail-toolbar .clone-detail-priority-tool { display: inline-flex; width: auto; }
.clone-detail-toolbar .clone-detail-list-tool { width: 28px; }
.clone-detail-form,
.clone-detail-read {
  display: grid;
  gap: 16px;
  padding: 20px;
}
.clone-detail-section { padding-top: 12px; border-top: 1px solid var(--line); }
.clone-popover-field { position: relative; }
.clone-field-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--card);
  color: var(--ink);
  text-align: left;
  font: inherit;
}
.clone-field-trigger:hover { border-color: var(--accent); }
.clone-field-popover {
  position: absolute;
  z-index: 95;
  top: calc(100% + 6px);
  right: 0;
  left: 0;
  display: grid;
  gap: 7px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--card);
  box-shadow: 0 12px 28px rgba(15, 23, 42, .16);
}
.clone-field-popover strong { color: var(--ink2); font-size: 12px; }
.clone-field-popover input { width: 100%; }
.clone-popover-actions { display: flex; gap: 5px; }
.clone-popover-actions button,
.clone-repeat-popover > button {
  min-height: 30px;
  padding: 5px 8px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink2);
  text-align: left;
  cursor: pointer;
}
.clone-popover-actions button:hover,
.clone-repeat-popover > button:hover,
.clone-repeat-popover > button.active { background: var(--accent-soft); color: var(--accent); }
.clone-detail-form label {
  display: grid;
  gap: 7px;
  color: var(--muted);
  font-size: 12px;
}
.clone-detail-form input,
.clone-detail-form select,
.clone-detail-form textarea {
  min-width: 0;
  min-height: 36px;
  padding: 8px;
  border: 1px solid var(--line);
  background: var(--card);
  color: var(--ink);
  font: inherit;
}
.clone-detail-form textarea {
  min-height: 90px;
  resize: vertical;
}
.clone-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.clone-inline-check {
  display: flex !important;
  grid-template-columns: none;
  align-items: center;
  gap: 8px !important;
}
.clone-inline-check input {
  min-height: 16px;
  width: 16px;
}
.clone-reminder-editor,
.clone-tags {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--line);
}
.clone-reminder-editor legend,
.clone-tags legend {
  padding: 0 4px;
  color: var(--muted);
  font-size: 12px;
}
.clone-tags {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.clone-tags legend {
  grid-column: 1 / -1;
}
.clone-tags label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.clone-tags input {
  min-height: 16px;
  width: 16px;
}
.clone-detail-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.clone-detail-actions button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  padding: 7px;
  cursor: pointer;
}
.clone-detail-title {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.clone-detail-title h2 {
  min-width: 0;
  flex: 1;
  margin: 1px 0;
  font-size: 19px;
  line-height: 1.4;
}
.clone-detail-title h2 button { display: flex; align-items: flex-start; gap: 7px; width: 100%; padding: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-weight: 650; line-height: inherit; text-align: left; overflow-wrap: anywhere; cursor: text; }
.clone-detail-title h2 button svg { flex: 0 0 auto; margin-top: 5px; color: var(--muted); opacity: 0; }
.clone-detail-title h2 button:hover svg, .clone-detail-title h2 button:focus-visible svg { opacity: 1; }
.clone-inline-title-input { min-width: 0; flex: 1; padding: 2px 5px; border: 1px solid var(--accent); border-radius: 4px; background: var(--card); color: var(--ink); font: inherit; font-size: 19px; }
.clone-detail-description-block { position: relative; min-width: 0; padding: 2px 24px 2px 0; }
.clone-detail-description-block :deep(.task-rich-editor) { border: 0; border-radius: 0; background: transparent; }
.clone-detail-description-block :deep(.task-rich-preview) { min-height: 0; padding: 0; overflow: visible; color: var(--ink2); font-size: 13px; line-height: 1.7; }
.clone-detail-description { display: block; width: 100%; padding: 0; border: 0; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; line-height: 1.7; text-align: left; white-space: pre-wrap; cursor: text; }
.clone-detail-description.placeholder { color: var(--muted); }
.clone-edit-description { position: absolute; top: 0; right: 0; display: grid; width: 24px; height: 24px; place-items: center; border: 0; border-radius: 4px; background: transparent; color: var(--muted); opacity: 0; cursor: pointer; }
.clone-detail-description-block:hover .clone-edit-description, .clone-edit-description:focus-visible { opacity: 1; }
.clone-inline-description-editor { display: grid; gap: 8px; }
.clone-inline-description-editor > div { display: flex; justify-content: flex-end; gap: 7px; }
.clone-inline-description-editor > div > button:first-child { min-height: 32px; padding: 0 10px; border: 1px solid var(--line); border-radius: 5px; background: transparent; color: var(--ink2); }
.clone-detail-properties { display: grid; border-top: 1px solid var(--line); }
.clone-detail-properties button { display: flex; justify-content: space-between; gap: 12px; min-width: 0; padding: 9px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; color: var(--muted); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.clone-detail-properties button:hover strong { color: var(--accent); }
.clone-detail-properties strong { min-width: 0; overflow: hidden; color: var(--ink2); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.clone-detail-edit-fields { display: inline-flex; align-items: center; justify-self: start; gap: 6px; min-height: 32px; padding: 0 2px; border: 0; background: transparent; color: var(--muted); font: inherit; font-size: 12px; cursor: pointer; }
.clone-detail-edit-fields:hover { color: var(--accent); }
.clone-checklist-editor,
.clone-checklist-read { display: grid; gap: 0; border-top: 1px solid var(--line); }
.clone-checklist-editor > header,
.clone-checklist-read > header { display: flex; align-items: center; justify-content: space-between; padding: 12px 0 5px; color: var(--ink2); font-size: 12px; }
.clone-checklist-editor > header button,
.clone-detail-tags-row button { display: grid; width: 26px; height: 26px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-checklist-editor > header button:hover,
.clone-detail-tags-row button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-checklist-row { display: flex; align-items: center; gap: 8px; min-height: 38px; border-bottom: 1px solid color-mix(in srgb, var(--line) 75%, transparent); }
.clone-checklist-row input[type="checkbox"] { flex: 0 0 auto; width: 15px; min-height: 15px; margin: 0; accent-color: var(--accent); }
.clone-checklist-row input[type="text"], .clone-checklist-row > input:not([type]) { flex: 1; min-width: 0; min-height: 30px; padding: 4px 0; border: 0; background: transparent; }
.clone-checklist-row button { display: grid; place-items: center; width: 26px; height: 26px; border: 0; background: transparent; color: var(--muted); opacity: .35; cursor: pointer; }
.clone-checklist-row:hover button { opacity: 1; }
.clone-checklist-row span { flex: 1; color: var(--ink2); font-size: 13px; }
.clone-checklist-row .completed, .clone-checklist-row input.completed { color: var(--muted); text-decoration: line-through; }
.clone-checklist-add { display: inline-flex; align-items: center; gap: 6px; justify-self: start; margin-top: 7px; padding: 5px 0; border: 0; background: transparent; color: var(--muted); font-size: 12px; cursor: pointer; }
.clone-checklist-add:hover { color: var(--accent); }
.clone-checklist-read > header span { color: var(--muted); font-size: 11px; }
.clone-detail-tags-row { display: flex; align-items: center; gap: 6px; padding: 10px 0; border-top: 1px solid var(--line); color: var(--muted); }
.clone-detail-tag { padding: 3px 7px; border-radius: 4px; background: var(--accent-soft); color: var(--accent); font-size: 11px; }
.clone-detail-description {
  margin: 0;
  white-space: pre-wrap;
  color: var(--ink2);
  font-size: 13px;
  line-height: 1.7;
}
.clone-detail-description.placeholder { color: var(--muted); }
.clone-priority-toolbar-popover { right: 12px; left: auto; min-width: 118px; }
.clone-detail-read dl {
  margin: 0;
}
.clone-detail-read dl div {
  display: grid;
  grid-template-columns: 58px 1fr;
  gap: 8px;
  padding: 11px 0;
  border-top: 1px solid var(--line);
  font-size: 12px;
}
.clone-detail-read dt {
  color: var(--muted);
}
.clone-detail-read dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.clone-collaboration-section,
.clone-activity-section {
  display: grid;
  gap: 10px;
  margin-top: 2px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}
.clone-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.clone-section-heading h3,
.clone-activity-section h3 {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
}
.clone-section-heading h3 span {
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
}
.clone-comment {
  display: grid;
  gap: 7px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: color-mix(in srgb, var(--paper) 76%, var(--card));
}
.clone-comment > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  color: var(--ink2);
  font-size: 12px;
}
.clone-comment > header time,
.clone-activity-section time {
  color: var(--muted);
  font-size: 11px;
  font-weight: 400;
}
.clone-comment textarea {
  width: 100%;
  min-height: 88px;
  box-sizing: border-box;
  resize: vertical;
}
.clone-comment-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}
.clone-comment-actions button,
.clone-retry {
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 5px 8px;
  background: var(--card);
  color: var(--ink2);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.clone-comment-actions button:hover,
.clone-retry:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.clone-comment-compose {
  display: grid;
  gap: 8px;
}
.clone-comment-compose .clone-primary-button {
  justify-self: end;
}
.clone-attachment-upload {
  display: inline-flex !important;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 5px 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--card);
  color: var(--ink2) !important;
  font-size: 12px !important;
  cursor: pointer;
}
.clone-attachment-upload:hover {
  border-color: var(--accent);
  color: var(--accent) !important;
}
.clone-collaboration-section progress {
  width: 100%;
  height: 5px;
  accent-color: var(--accent);
}
.clone-attachment-list,
.clone-activity-section ol {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.clone-attachment-list li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 7px;
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--ink2);
  font-size: 12px;
}
.clone-attachment-list li > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clone-attachment-list small {
  color: var(--muted);
  font-size: 10px;
}
.clone-attachment-list button {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.clone-attachment-list button:hover {
  background: var(--paper);
  color: var(--accent);
}
.clone-activity-section ol {
  border-left: 2px solid var(--line);
}
.clone-activity-section li {
  display: grid;
  gap: 3px;
  padding: 0 0 8px 10px;
  color: var(--ink2);
  font-size: 12px;
}
.clone-muted {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}
.clone-retry {
  justify-self: start;
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line));
  color: var(--accent);
}
@media (max-width: 1279px) {
  .clone-task-shell {
    grid-template-columns: 220px minmax(420px, 1fr);
  }
  .clone-detail-pane {
    grid-column: 2;
    border-top: 1px solid var(--line);
  }
  .clone-task-shell.detail-open {
    grid-template-rows: minmax(0, 1fr) minmax(320px, auto);
  }
  .clone-detail-pane {
    grid-row: 2;
  }
}
@media (max-width: 900px) {
  .clone-task-shell {
    display: block;
    height: auto;
    min-height: calc(100vh - 72px);
    margin: 0;
    overflow: visible;
  }
  .clone-task-sidebar {
    display: none;
  }
  .clone-task-shell:not(.sidebar-hidden) .clone-task-sidebar {
    display: flex;
    min-height: 0;
    max-height: 340px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
  .clone-task-main {
    padding: 16px 14px 100px;
  }
  .clone-task-header {
    align-items: flex-start;
  }
  .clone-header-actions .clone-sync-state {
    display: none;
  }
  .clone-primary-button {
    font-size: 0;
    width: 36px;
    padding: 0;
  }
  .clone-primary-button svg {
    margin: 0;
  }
  .clone-detail-pane {
    margin: 0 14px 90px;
    border: 1px solid var(--line);
    border-radius: 8px;
  }
  .clone-batch-bar {
    right: 14px;
    left: 14px;
    justify-content: center;
    transform: none;
    flex-wrap: wrap;
  }
  .clone-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
