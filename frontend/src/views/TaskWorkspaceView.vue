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
        <div v-show="sections.lists" class="clone-sidebar-items">
        <RouterLink
          v-for="list in store.lists.filter((item) => !item.archived)"
          :key="list.publicId"
          :to="
            list.systemKey === 'INBOX'
              ? '/tasks/inbox'
              : `/tasks/list/${list.publicId}`
          "
          class="clone-nav-item"
          :class="{ active: route.params.publicId === list.publicId }"
          ><ListTodo :size="15" /><span>{{ list.name }}</span></RouterLink
        >
        <span v-if="!store.lists.length" class="clone-empty-item">暂无清单</span>
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
          ><ListFilter :size="15" /><span>{{ item.label }}</span></RouterLink
        >
        <RouterLink
          v-for="filter in filters"
          :key="filter.publicId"
          :to="`/tasks/filter/${filter.publicId}`"
          class="clone-nav-item"
          ><SlidersHorizontal :size="15" /><span>{{
            filter.name
          }}</span></RouterLink
        >
        <span v-if="!filters.length" class="clone-empty-item"
          >暂无自定义筛选器</span
        >
        </div>
      </section>
      <section class="clone-sidebar-section">
        <header><button type="button" @click="toggleSection('tags')"><ChevronDown :size="14" :class="{ rotated: !sections.tags }" />标签</button><button class="sidebar-create" type="button" aria-label="新建标签" @click="createTag"><Plus :size="14" /></button></header>
        <div v-show="sections.tags" class="clone-sidebar-items">
        <RouterLink
          v-for="tag in store.tags"
          :key="tag.publicId"
          :to="`/tasks/tag/${tag.publicId}`"
          class="clone-nav-item"
          ><Tag :size="15" /><span>#{{ tag.name }}</span></RouterLink
        >
        <span v-if="!store.tags.length" class="clone-empty-item">暂无标签</span>
        </div>
      </section>
      <div class="clone-sidebar-footer">
        <RouterLink to="/tasks/completed"
          ><CheckCircle2 :size="15" />已完成</RouterLink
        ><RouterLink to="/tasks/trash"><Trash2 :size="15" />垃圾桶</RouterLink>
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
      <div v-if="moreOpen" class="clone-floating-menu" role="menu">
        <button
          type="button"
          @click="
            sort = sort === 'manual' ? 'due' : 'manual';
            moreOpen = false;
          "
        >
          排序：{{ sort === "manual" ? "手动" : "截止时间" }}</button
        ><button
          type="button"
          @click="
            showDescription = !showDescription;
            moreOpen = false;
          "
        >
          {{ showDescription ? "隐藏详细" : "显示详细" }}</button
        ><button
          type="button"
          @click="
            router.push('/tasks/board');
            moreOpen = false;
          "
        >
          打开看板
        </button>
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
        ><span>{{ visibleTasks.length }} 项任务</span>
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
              }"
              :data-priority="task.priority"
              draggable="true"
              @dragstart="dragged = task"
              @dragover.prevent
              @drop="dropTask(task)"
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
                  ><span v-if="task.totalSubtasks"
                    >{{ task.completedSubtasks }}/{{
                      task.totalSubtasks
                    }}
                    子任务</span
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
      <header>
        <span>{{
          editorOpen ? (editing ? "编辑任务" : "新建任务") : "任务详情"
        }}</span
        ><button type="button" aria-label="关闭任务详情" @click="closeDetail">
          <X :size="17" />
        </button>
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
        <label
          >检查项<textarea
            v-model="draft.checklistText"
            placeholder="每行一个检查项"
          />
        </label>
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
          <h2>{{ selectedTask.title }}</h2>
        </div>
        <p v-if="selectedTask.description" class="clone-detail-description">
          {{ selectedTask.description }}
        </p>
        <dl>
          <div>
            <dt>清单</dt>
            <dd>{{ listName(selectedTask.listId) }}</dd>
          </div>
          <div>
            <dt>日期</dt>
            <dd>
              {{
                selectedTask.dueAt ? formatDate(selectedTask.dueAt) : "无日期"
              }}
            </dd>
          </div>
          <div>
            <dt>优先级</dt>
            <dd>{{ priorityLabel(selectedTask.priority) }}</dd>
          </div>
          <div v-if="selectedTask.rrule">
            <dt>重复</dt>
            <dd>{{ selectedTask.rrule }}</dd>
          </div>
        </dl>
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
        <button
          type="button"
          class="clone-primary-button full"
          @click="openEdit(selectedTask)"
        >
          <Pencil :size="15" />编辑任务
        </button>
      </div>
    </aside>

    <div
      v-if="context.open"
      class="clone-context-menu"
      :style="{ left: `${context.x}px`, top: `${context.y}px` }"
      role="menu"
      @click.stop
    >
      <strong>{{ context.task?.title }}</strong
      ><button
        type="button"
        @click="
          openDetail(context.task);
          closeContext();
        "
      >
        <Pencil :size="14" />打开详情</button
      ><button
        type="button"
        @click="
          toggleComplete(context.task);
          closeContext();
        "
      >
        <CheckCircle2 :size="14" />{{
          context.task?.status === "COMPLETED" ? "重新打开" : "标记完成"
        }}</button
      ><button type="button" @click="setContextDue(0)"><CalendarClock :size="14" />安排今天</button
      ><button type="button" @click="setContextDue(1)"><CalendarClock :size="14" />安排明天</button
      ><button type="button" @click="setContextDue(null)"><X :size="14" />清除日期</button
      ><div class="clone-context-divider" aria-hidden="true"></div
      ><button
        type="button"
        @click="
          duplicateTask(context.task);
          closeContext();
        "
      >
        <Copy :size="14" />创建副本</button
      ><button
        type="button"
        class="danger"
        @click="
          deleteTask(context.task);
          closeContext();
        "
      >
        <Trash2 :size="14" />删除任务
      </button>
    </div>
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
  CalendarClock,
  Check,
  CheckCircle2,
  ChevronDown,
  Copy,
  Ellipsis,
  Download,
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
  SlidersHorizontal,
  Tag,
  Trash2,
  WifiOff,
  X,
} from "lucide-vue-next";
import TaskRichTextEditor from "../components/tasks/TaskRichTextEditor.vue";
import { useTasksStore } from "../stores/tasks.js";
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
  apiUpdateTaskComment,
  apiUpdateTaskReminder,
  apiUploadTaskAttachment,
} from "../../packages/api-client/src/index.js";

const route = useRoute();
const router = useRouter();
const store = useTasksStore();
const filters = ref([]);
const quickTitle = ref("");
const sidebarHidden = ref(false);
const moreOpen = ref(false);
const datePopoverOpen = ref(false);
const repeatPopoverOpen = ref(false);
const batchMode = ref(false);
const selectedIds = reactive(new Set());
const collapsed = reactive({
  overdue: false,
  today: false,
  later: false,
  none: false,
  open: false,
});
const sections = reactive({ lists: true, filters: true, tags: true });
const selectedTask = ref(null);
const editorOpen = ref(false);
const editing = ref(null);
const dragged = ref(null);
const sort = ref("manual");
const showDescription = ref(true);
const context = reactive({ open: false, x: 0, y: 0, task: null });
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
  { key: "inbox", label: "收件箱", to: "/tasks/inbox", icon: Inbox },
  { key: "summary", label: "摘要", to: "/tasks/shortcut/summary", icon: ListTodo },
];
const shortcutViews = [
  { label: "无日期任务", to: "/tasks/shortcut/no-date" },
  { label: "本周", to: "/tasks/shortcut/this-week" },
  { label: "下周", to: "/tasks/shortcut/next-week" },
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
                    }[route.params.shortcut] || "任务"
                  : "所有",
);
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
      : !task.deleted &&
        (task.status !== "COMPLETED" || route.name === "tasks-completed"),
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
  else if (route.name === "tasks-shortcut")
    values = values.filter((task) =>
      route.params.shortcut === "no-date"
        ? !task.dueAt
        : route.params.shortcut === "this-week"
          ? inRange(task.dueAt, today, addDays(today, 7))
          : inRange(task.dueAt, addDays(today, 7), end),
    );
  if (sort.value === "due")
    values.sort(
      (a, b) =>
        (a.dueAt ? Date.parse(a.dueAt) : Infinity) -
        (b.dueAt ? Date.parse(b.dueAt) : Infinity),
    );
  else values.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  return values;
});
const openTasks = computed(() =>
  visibleTasks.value.filter((task) => task.status !== "COMPLETED"),
);
const completedTasks = computed(() =>
  visibleTasks.value.filter((task) => task.status === "COMPLETED"),
);
const groups = computed(() => {
  if (
    route.name === "tasks-all" ||
    route.name === "tasks-list" ||
    route.name === "tasks-tag" ||
    route.name === "tasks-inbox" ||
    route.name === "tasks-shortcut"
  )
    return [{ key: "open", label: "待完成", tasks: visibleTasks.value }];
  const today = startOfDay(new Date());
  const result = [
    { key: "overdue", label: "逾期", tasks: [] },
    { key: "today", label: title.value, tasks: [] },
    { key: "later", label: "之后", tasks: [] },
    { key: "none", label: "无日期", tasks: [] },
  ];
  visibleTasks.value.forEach((task) => {
    if (!task.dueAt) result[3].tasks.push(task);
    else if (new Date(task.dueAt) < today) result[0].tasks.push(task);
    else if (new Date(task.dueAt) < addDays(today, 1))
      result[1].tasks.push(task);
    else result[2].tasks.push(task);
  });
  return result.filter((group) => group.tasks.length);
});
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
function priorityLabel(value) {
  return { NONE: "无", LOW: "低", MEDIUM: "中", HIGH: "高" }[value] || value;
}
function recurrenceLabel(value) {
  return recurrenceOptions.find((option) => option.value === value)?.label || (value ? "自定义" : "不重复");
}
function itemCount(key) {
  return key === "all"
    ? store.tasks.filter((item) => !item.deleted).length
    : key === "inbox"
      ? store.tasks.filter(
          (item) =>
            item.listId === store.inbox?.publicId &&
            item.status !== "COMPLETED",
        ).length
      : "";
}
function isActive(to) {
  return route.path === to || route.path.startsWith(`${to}/`);
}
function toggleSidebar() {
  sidebarHidden.value = !sidebarHidden.value;
}
function toggleSection(key) {
  sections[key] = !sections[key];
}
function closeContext() {
  context.open = false;
  context.task = null;
}
async function setContextDue(offset) {
  const task = context.task;
  closeContext();
  if (!task) return;
  const dueAt = offset === null ? "" : addDays(startOfDay(new Date()), offset).toISOString();
  await store.update(task, { dueAt });
}
function openContext(task, event) {
  if (!task) return;
  context.task = task;
  context.x = Math.max(8, Math.min(event.clientX, window.innerWidth - 230));
  context.y = Math.max(8, Math.min(event.clientY, window.innerHeight - 330));
  context.open = true;
}
function openDetail(task) {
  if (!task) return;
  selectedTask.value = task;
  editorOpen.value = false;
  void loadActivity(task);
  void loadCollaboration(task);
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
  });
  editorOpen.value = true;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
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
function openCreate() {
  editing.value = null;
  selectedTask.value = {};
  resetCollaboration();
  Object.assign(draft, {
    title: "",
    description: "",
    listId: store.inbox?.publicId || "",
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
  });
  editorOpen.value = true;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
}
function closeDetail() {
  selectedTask.value = null;
  editing.value = null;
  editorOpen.value = false;
  resetCollaboration();
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
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
    checklist: draft.checklistText
      .split("\n")
      .map((title, index) => ({
        title: title.trim(),
        completed: false,
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
async function dropTask(target) {
  if (!dragged.value || dragged.value.publicId === target.publicId) return;
  await store.sortTask(dragged.value, target.sortOrder || 0);
  dragged.value = null;
}
async function createList() {
  const name = window.prompt("新建清单");
  if (!name?.trim()) return;
  try {
    await store.createList({
      name: name.trim(),
      color: "#2563eb",
      icon: "",
      archived: false,
      sortOrder: store.lists.length,
    });
  } catch (error) {
    store.error = error?.message || "新建清单失败";
  }
}
async function createTag() {
  const name = window.prompt("新建标签");
  if (!name?.trim()) return;
  try {
    await store.createTag({ name: name.trim() });
  } catch (error) {
    store.error = error?.message || "标签创建失败";
  }
}
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
.clone-list-toolbar { display: none; }
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
  transition: background 0.12s ease, box-shadow 0.12s ease;
}
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
  margin: 1px 0;
  font-size: 19px;
  line-height: 1.4;
}
.clone-detail-description {
  margin: 0;
  white-space: pre-wrap;
  color: var(--ink2);
  font-size: 13px;
  line-height: 1.7;
}
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
