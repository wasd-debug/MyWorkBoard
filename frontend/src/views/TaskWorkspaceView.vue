<template>
  <section
    class="clone-task-shell"
    :style="{ '--clone-detail-width': `${detailWidth}px` }"
    :class="{
      'sidebar-hidden': sidebarHidden,
      'detail-open': Boolean(selectedTask),
    }"
    @touchstart.passive="startSidebarSwipe"
    @touchend.passive="finishSidebarSwipe"
  >
    <button v-if="!sidebarHidden && isMobileSidebar" class="clone-sidebar-backdrop" type="button" aria-label="关闭任务侧栏" @click="sidebarHidden = true"></button>
    <aside class="clone-task-sidebar" :class="{ 'is-editing': sidebarEditing }" aria-label="任务导航" @click="handleSidebarClick">
      <div class="clone-sidebar-scroll">
      <div class="clone-sidebar-head">
        <strong>任务</strong
        ><button type="button" :aria-label="sidebarHidden ? '展开任务侧栏' : '折叠任务侧栏'" :title="sidebarHidden ? '展开任务侧栏' : '折叠任务侧栏'" @click="toggleSidebar">
          <PanelLeft v-if="sidebarHidden" :size="17" /><PanelLeftClose v-else :size="17" />
        </button>
      </div>
      <section class="clone-sidebar-block clone-sidebar-system" aria-label="系统集合">
        <div class="clone-sidebar-block-title">系统集合</div>
        <nav class="clone-primary-nav">
        <div v-for="item in pinnedPrimaryViews" :key="`pinned-${item.key}`" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', item, $event)">
          <RouterLink :to="item.to" class="clone-nav-item" :class="{ active: isActive(item.to) }"><component :is="item.icon" :size="16" /><span>{{ item.label }}</span><small>{{ itemCount(item.key) }}</small></RouterLink>
          <button class="clone-item-menu-trigger" type="button" :aria-label="`${item.label}的更多操作`" @click.stop="openOrganizationMenu('system', item, $event)"><Ellipsis :size="14" /></button>
        </div>
        <div v-for="item in pinnedShortcutViews" :key="`pinned-${item.key}`" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', item, $event)"><RouterLink :to="item.to" class="clone-nav-item" :class="{ active: isActive(item.to) }"><component :is="item.icon" :size="15" /><span>{{ item.label }}</span><small>{{ itemCount(item.key) }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" :aria-label="`${item.label}的更多操作`" @click.stop="openOrganizationMenu('system', item, $event)"><Ellipsis :size="14" /></button></div>
        <div v-for="item in pinnedBottomViews" :key="`pinned-${item.key}`" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', item, $event)"><RouterLink :to="item.to" class="clone-nav-item" :class="{ active: isActive(item.to) }"><component :is="item.icon" :size="15" /><span>{{ item.label }}</span><small>{{ itemCount(item.key) }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" :aria-label="`${item.label}的更多操作`" @click.stop="openOrganizationMenu('system', item, $event)"><Ellipsis :size="14" /></button></div>
        <div v-for="item in visiblePrimaryViews" :key="item.key" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', item, $event)">
        <RouterLink
          :to="item.to"
          class="clone-nav-item"
          :class="{ active: isActive(item.to) }"
          ><component :is="item.icon" :size="16" /><span>{{ item.label }}</span
          ><small>{{ itemCount(item.key) }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" :aria-label="`${item.label}的更多操作`" @click.stop="openOrganizationMenu('system', item, $event)"><Ellipsis :size="14" /></button></div
        >
        </nav>
      </section>
      <div class="clone-sidebar-mobile-actions" aria-label="侧栏操作">
        <button type="button" @click="createList()"><Plus :size="15" />新建清单</button>
        <button type="button" :aria-pressed="sidebarEditing" @click="sidebarEditing = !sidebarEditing"><Pencil :size="14" />{{ sidebarEditing ? '完成' : '编辑' }}</button>
      </div>
      <div class="clone-sidebar-block clone-sidebar-organization" aria-label="清单、筛选器和标签">
      <section class="clone-sidebar-section">
        <header @click="toggleSection('lists')"><button type="button" :aria-expanded="sections.lists"><ChevronDown :size="14" class="clone-section-chevron" :class="{ rotated: !sections.lists }" />清单</button><div class="clone-sidebar-heading-actions"><button class="sidebar-create" type="button" aria-label="新建清单" title="新建清单" @click.stop="createList()"><Plus :size="14" /></button></div></header>
        <div class="clone-collapse-panel" :class="{ 'is-collapsed': !sections.lists }" :aria-hidden="!sections.lists"><div class="clone-sidebar-items clone-collapse-inner" :inert="!sections.lists" @dragover.prevent="organizationDrag && (organizationDropTarget = 'list-root')" @drop.prevent="dropOrganizationRoot('list')">
        <section v-if="pinnedListGroups.length" class="clone-pinned-zone" aria-label="置顶分组"><div class="clone-pinned-title">置顶分组</div><section v-for="group in pinnedListGroups" :key="`pinned-group-${group.publicId}`" class="clone-list-group clone-sortable-group" :class="{ 'drag-over': organizationDropTarget === `group:${group.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'group' && organizationDrag.item.publicId === group.publicId }" @dragover.prevent="organizationDropTarget = `group:${group.publicId}`" @drop.prevent.stop="dropGroup(group)"><header class="clone-list-group-heading" draggable="true" @dragstart="startOrganizationDrag('group', group)"><button type="button" class="clone-group-toggle" :aria-expanded="!groupCollapsed[group.publicId]" :aria-label="`${groupCollapsed[group.publicId] ? '展开' : '折叠'}${group.name}`" @click.stop="toggleListGroupCollapsed(group)"><ChevronDown :size="13" :class="{ rotated: groupCollapsed[group.publicId] }" /></button><RouterLink class="clone-group-navigation" :class="{ active: route.name === 'tasks-group' && route.params.publicId === group.publicId }" :to="`/tasks/group/${group.publicId}`"><TaskOrganizationIcon v-if="group.icon" class="clone-list-group-icon" :value="group.icon" /><Folder v-else class="clone-list-group-icon" :size="15" /><span>{{ group.name }}</span><small>{{ groupOpenCount(group.publicId) }}</small></RouterLink><button class="clone-group-menu" type="button" :aria-label="`${group.name}的更多操作`" @click.stop="openOrganizationMenu('group', group, $event)"><Ellipsis :size="14" /></button></header><div class="clone-collapse-panel" :class="{ 'is-collapsed': groupCollapsed[group.publicId] }" :aria-hidden="Boolean(groupCollapsed[group.publicId])"><div class="clone-group-lists clone-collapse-inner" :inert="Boolean(groupCollapsed[group.publicId])"><RouterLink v-for="list in listsInGroup(group.publicId)" :key="`pinned-group-list-${list.publicId}`" :to="`/tasks/list/${list.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }" draggable="true" @dragstart="startOrganizationDrag('list', list)" @dragover.prevent="handleListDragOver(list)" @dragleave="handleListDragLeave(list)" @drop.prevent.stop="dropListOnList(list)" @contextmenu.prevent="openOrganizationMenu('list', list, $event)"><ListTodo :size="15" /><span>{{ list.name }}</span><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink></div></div></section></section>
        <section v-if="pinnedLists.length" class="clone-pinned-zone" aria-label="置顶清单"><div class="clone-pinned-title">置顶</div><RouterLink v-for="list in pinnedLists" :key="`pinned-${list.publicId}`" :to="`/tasks/list/${list.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }" draggable="true" @dragstart="startOrganizationDrag('list', list)" @dragover.prevent="handleListDragOver(list)" @dragleave="handleListDragLeave(list)" @drop.prevent.stop="dropListOnList(list)" @contextmenu.prevent="openOrganizationMenu('list', list, $event)"><TaskOrganizationIcon v-if="list.icon" class="clone-object-icon" :value="list.icon" :style="{ color: list.color || undefined }" /><ListTodo v-else :size="15" :style="{ color: list.color || undefined }" /><span>{{ list.name }}</span><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink></section>
        <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack">
        <section v-for="group in visibleListGroups" :key="group.publicId" class="clone-list-group clone-sortable-group" :class="{ 'drag-over': organizationDropTarget === `group:${group.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'group' && organizationDrag.item.publicId === group.publicId }" @dragover.prevent="organizationDropTarget = `group:${group.publicId}`" @drop.prevent.stop="dropGroup(group)" @contextmenu.prevent="openOrganizationMenu('group', group, $event)">
          <header class="clone-list-group-heading" draggable="true" @dragstart="startOrganizationDrag('group', group)"><button type="button" class="clone-group-toggle" :aria-expanded="!groupCollapsed[group.publicId]" :aria-label="`${groupCollapsed[group.publicId] ? '展开' : '折叠'}${group.name}`" @click.stop="toggleListGroupCollapsed(group)"><ChevronDown :size="13" :class="{ rotated: groupCollapsed[group.publicId] }" /></button><RouterLink class="clone-group-navigation" :class="{ active: route.name === 'tasks-group' && route.params.publicId === group.publicId }" :to="`/tasks/group/${group.publicId}`"><TaskOrganizationIcon v-if="group.icon" class="clone-list-group-icon" :value="group.icon" /><Folder v-else class="clone-list-group-icon" :size="15" /><span>{{ group.name }}</span><small>{{ groupOpenCount(group.publicId) }}</small></RouterLink><button class="clone-group-menu" type="button" :aria-label="`${group.name}的更多操作`" @click.stop="openOrganizationMenu('group', group, $event)"><Ellipsis :size="14" /></button></header>
          <div class="clone-collapse-panel" :class="{ 'is-collapsed': groupCollapsed[group.publicId] }" :aria-hidden="Boolean(groupCollapsed[group.publicId])"><TransitionGroup name="clone-sort" tag="div" class="clone-group-lists clone-collapse-inner" :inert="Boolean(groupCollapsed[group.publicId])"><RouterLink v-for="list in listsInGroup(group.publicId)" :key="list.publicId" :to="list.systemKey === 'INBOX' ? '/tasks/inbox' : `/tasks/list/${list.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId || organizationDropTarget === `new-group:${list.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }" :draggable="list.systemKey !== 'INBOX'" @dragstart="startOrganizationDrag('list', list)" @dragover.prevent="handleListDragOver(list)" @dragleave="handleListDragLeave(list)" @drop.prevent.stop="dropListOnList(list)" @contextmenu.prevent="openOrganizationMenu('list', list, $event)"><TaskOrganizationIcon v-if="list.icon" class="clone-object-icon" :value="list.icon" :style="{ color: list.color || undefined }" /><ListTodo v-else :size="15" :style="{ color: list.color || undefined }" /><span>{{ list.name }}</span><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink></TransitionGroup></div>
        </section>
        </TransitionGroup>
        <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack">
        <RouterLink
          v-for="list in ungroupedLists"
          :key="list.publicId"
          :to="
            list.systemKey === 'INBOX'
              ? '/tasks/inbox'
              : `/tasks/list/${list.publicId}`
          "
          class="clone-nav-item clone-sortable-item"
          :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId || organizationDropTarget === `new-group:${list.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }"
          :draggable="list.systemKey !== 'INBOX'"
          @dragstart="startOrganizationDrag('list', list)"
          @dragover.prevent="handleListDragOver(list)"
          @dragleave="handleListDragLeave(list)"
          @drop.prevent.stop="dropListOnList(list)"
          @contextmenu.prevent="openOrganizationMenu('list', list, $event)"
          ><TaskOrganizationIcon v-if="list.icon" class="clone-object-icon" :value="list.icon" :style="{ color: list.color || undefined }" /><ListTodo v-else :size="15" :style="{ color: list.color || undefined }" /><span>{{ list.name }}</span><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink
        >
        </TransitionGroup>
        <div v-if="!isHiddenObject('system', archivedView) && !isPinnedObject('system', archivedView)" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', archivedView, $event)"><RouterLink to="/tasks/shortcut/archived" class="clone-nav-item" :class="{ active: route.params.shortcut === 'archived' }"><Archive :size="15" /><span>归档</span><small>{{ archivedTaskCount }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" aria-label="归档的更多操作" @click.stop="openOrganizationMenu('system', archivedView, $event)"><Ellipsis :size="14" /></button></div>
        <span v-if="!activeLists.length && !archivedLists.length" class="clone-empty-item">暂无清单</span>
        </div></div>
      </section>
      <section class="clone-sidebar-section">
        <header @click="toggleSection('filters')"><button type="button" :aria-expanded="sections.filters"><ChevronDown :size="14" class="clone-section-chevron" :class="{ rotated: !sections.filters }" />筛选器</button><div class="clone-sidebar-heading-actions"><button class="sidebar-create" type="button" aria-label="新建筛选器" title="新建筛选器" @click.stop="createFilter"><Plus :size="14" /></button><button class="sidebar-create" type="button" aria-label="筛选器显示选项" title="筛选器显示选项" :aria-expanded="sectionDisplayMenu === 'filters'" @click.stop="toggleSectionDisplayMenu('filters')"><Ellipsis :size="14" /></button><div v-if="sectionDisplayMenu === 'filters'" class="clone-section-display-menu" role="menu" aria-label="筛选器显示选项" @pointerdown.stop @click.stop><button v-for="option in sectionDisplayOptions('filters')" :key="option.value" type="button" role="menuitemradio" :aria-checked="sectionDisplayModes.filters === option.value" @click.stop="setSectionDisplayMode('filters', option.value)">{{ option.label }}<Check v-if="sectionDisplayModes.filters === option.value" :size="14" /></button></div></div></header>
        <div class="clone-collapse-panel" :class="{ 'is-collapsed': !sections.filters || !isSectionDisplayed('filters') }" :aria-hidden="!sections.filters || !isSectionDisplayed('filters')"><div class="clone-sidebar-items clone-collapse-inner" :inert="!sections.filters || !isSectionDisplayed('filters')">
        <section v-if="pinnedFilters.length" class="clone-pinned-zone" aria-label="置顶筛选器"><div class="clone-pinned-title">置顶</div><RouterLink v-for="filter in pinnedFilters" :key="`pinned-${filter.publicId}`" :to="`/tasks/filter/${filter.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === filter.publicId, 'sorting-dragging': organizationDrag?.kind === 'filter' && organizationDrag.item.publicId === filter.publicId }" draggable="true" @dragstart="startOrganizationDrag('filter', filter)" @dragover.prevent="organizationDropTarget = filter.publicId" @drop.prevent.stop="dropOrganization('filter', filter)" @contextmenu.prevent="openOrganizationMenu('filter', filter, $event)"><TaskOrganizationIcon v-if="filter.icon" class="clone-object-icon" :value="filter.icon" /><SlidersHorizontal v-else :size="15" /><span>{{ filter.name }}</span><button class="clone-item-menu-trigger" type="button" :aria-label="`${filter.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('filter', filter, $event)"><Ellipsis :size="14" /></button></RouterLink></section>
        <div v-for="item in visibleShortcutViews" :key="item.to" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', item, $event)"><RouterLink :to="item.to" class="clone-nav-item" :class="{ active: isActive(item.to) }"><component :is="item.icon" :size="15" /><span>{{ item.label }}</span><small>{{ itemCount(item.key) }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" :aria-label="`${item.label}的更多操作`" @click.stop="openOrganizationMenu('system', item, $event)"><Ellipsis :size="14" /></button></div>
        <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack">
        <RouterLink
          v-for="filter in visibleFilters"
          :key="filter.publicId"
          :to="`/tasks/filter/${filter.publicId}`"
          class="clone-nav-item clone-sortable-item"
          :class="{ 'drag-over': organizationDropTarget === filter.publicId, 'sorting-dragging': organizationDrag?.kind === 'filter' && organizationDrag.item.publicId === filter.publicId }"
          draggable="true"
          @dragstart="startOrganizationDrag('filter', filter)"
          @dragover.prevent="organizationDropTarget = filter.publicId"
          @drop.prevent.stop="dropOrganization('filter', filter)"
          @contextmenu.prevent="openOrganizationMenu('filter', filter, $event)"
          ><TaskOrganizationIcon v-if="filter.icon" class="clone-object-icon" :value="filter.icon" /><SlidersHorizontal v-else :size="15" /><span>{{
            filter.name
          }}</span><button class="clone-item-menu-trigger" type="button" :aria-label="`${filter.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('filter', filter, $event)"><Ellipsis :size="14" /></button></RouterLink
        >
        </TransitionGroup>
        <span v-if="!filters.length" class="clone-empty-item"
          >暂无自定义筛选器</span
        >
        </div></div>
      </section>
      <section class="clone-sidebar-section">
        <header @click="toggleSection('tags')"><button type="button" :aria-expanded="sections.tags"><ChevronDown :size="14" class="clone-section-chevron" :class="{ rotated: !sections.tags }" />标签</button><div class="clone-sidebar-heading-actions"><button class="sidebar-create" type="button" aria-label="标签显示选项" title="标签显示选项" :aria-expanded="sectionDisplayMenu === 'tags'" @click.stop="toggleSectionDisplayMenu('tags')"><Ellipsis :size="14" /></button><button class="sidebar-create" type="button" aria-label="新建标签" title="新建标签" @click.stop="createTag"><Plus :size="14" /></button><div v-if="sectionDisplayMenu === 'tags'" class="clone-section-display-menu" role="menu" aria-label="标签显示选项" @pointerdown.stop @click.stop><button v-for="option in sectionDisplayOptions('tags')" :key="option.value" type="button" role="menuitemradio" :aria-checked="sectionDisplayModes.tags === option.value" @click.stop="setSectionDisplayMode('tags', option.value)">{{ option.label }}<Check v-if="sectionDisplayModes.tags === option.value" :size="14" /></button></div></div></header>
        <div class="clone-collapse-panel" :class="{ 'is-collapsed': !sections.tags || !isSectionDisplayed('tags') }" :aria-hidden="!sections.tags || !isSectionDisplayed('tags')"><div class="clone-sidebar-items clone-collapse-inner" :inert="!sections.tags || !isSectionDisplayed('tags')">
        <section v-if="pinnedTags.length" class="clone-pinned-zone" aria-label="置顶标签"><div class="clone-pinned-title">置顶</div><RouterLink v-for="tag in pinnedTags" :key="`pinned-${tag.publicId}`" :to="`/tasks/tag/${tag.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === tag.publicId, 'sorting-dragging': organizationDrag?.kind === 'tag' && organizationDrag.item.publicId === tag.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', tag)" @dragover.prevent="organizationDropTarget = tag.publicId" @drop.prevent.stop="dropOrganization('tag', tag)" @contextmenu.prevent="openOrganizationMenu('tag', tag, $event)"><TaskOrganizationIcon v-if="tag.icon" class="clone-object-icon" :value="tag.icon" /><Tag v-else :size="15" /><span>{{ tag.name }}</span><i class="clone-tag-color-dot" :style="{ backgroundColor: tag.color || 'transparent' }" aria-hidden="true"></i><small>{{ tagOpenCount(tag.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${tag.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', tag, $event)"><Ellipsis :size="14" /></button></RouterLink></section>
        <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack">
        <div v-for="tag in rootTags" :key="tag.publicId" class="clone-tag-group">
          <RouterLink :to="`/tasks/tag/${tag.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === tag.publicId, 'sorting-dragging': organizationDrag?.kind === 'tag' && organizationDrag.item.publicId === tag.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', tag)" @dragover.prevent="organizationDropTarget = tag.publicId" @drop.prevent.stop="dropOrganization('tag', tag)" @contextmenu.prevent="openOrganizationMenu('tag', tag, $event)"><TaskOrganizationIcon v-if="tag.icon" class="clone-object-icon" :value="tag.icon" /><Tag v-else :size="15" /><span>{{ tag.name }}</span><i class="clone-tag-color-dot" :style="{ backgroundColor: tag.color || 'transparent' }" aria-hidden="true"></i><small>{{ tagOpenCount(tag.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${tag.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', tag, $event)"><Ellipsis :size="14" /></button></RouterLink>
          <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack"><RouterLink v-for="child in childTags.filter(item => item.parentId === tag.publicId)" :key="child.publicId" :to="`/tasks/tag/${child.publicId}`" class="clone-nav-item clone-child-tag clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === child.publicId, 'sorting-dragging': organizationDrag?.kind === 'tag' && organizationDrag.item.publicId === child.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', child)" @dragover.prevent="organizationDropTarget = child.publicId" @drop.prevent.stop="dropOrganization('tag', child)" @contextmenu.prevent="openOrganizationMenu('tag', child, $event)"><TaskOrganizationIcon v-if="child.icon" class="clone-object-icon" :value="child.icon" /><Tag v-else :size="14" /><span>{{ child.name }}</span><i class="clone-tag-color-dot" :style="{ backgroundColor: child.color || 'transparent' }" aria-hidden="true"></i><small>{{ tagOpenCount(child.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${child.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', child, $event)"><Ellipsis :size="14" /></button></RouterLink></TransitionGroup>
        </div>
        </TransitionGroup>
        <span v-if="!store.tags.length" class="clone-empty-item">暂无标签</span>
        </div></div>
      </section>
      </div>
      <section class="clone-sidebar-block clone-sidebar-status" aria-label="任务状态集合">
        <div class="clone-sidebar-block-title">任务状态</div>
        <div v-if="!isHiddenObject('system', completedView) && !isPinnedObject('system', completedView)" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', completedView, $event)"><RouterLink to="/tasks/completed" class="clone-nav-item" :class="{ active: isActive('/tasks/completed') }"><CheckCircle2 :size="15" /><span>已完成</span><small>{{ completedTaskCount }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" aria-label="已完成的更多操作" @click.stop="openOrganizationMenu('system', completedView, $event)"><Ellipsis :size="14" /></button></div>
        <div v-if="!isHiddenObject('system', abandonedView) && !isPinnedObject('system', abandonedView)" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', abandonedView, $event)"><RouterLink to="/tasks/abandoned" class="clone-nav-item" :class="{ active: route.name === 'tasks-abandoned' }"><X :size="15" /><span>已放弃</span><small>{{ abandonedTaskCount }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" aria-label="已放弃的更多操作" @click.stop="openOrganizationMenu('system', abandonedView, $event)"><Ellipsis :size="14" /></button></div>
        <div v-if="!isHiddenObject('system', trashView) && !isPinnedObject('system', trashView)" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', trashView, $event)"><RouterLink to="/tasks/trash" class="clone-nav-item" :class="{ active: isActive('/tasks/trash') }"><Trash2 :size="15" /><span>垃圾桶</span><small>{{ trashedTaskCount }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" aria-label="垃圾桶的更多操作" @click.stop="openOrganizationMenu('system', trashView, $event)"><Ellipsis :size="14" /></button></div>
      </section>
      </div>
      <section class="clone-sidebar-block clone-mini-calendar" :class="{ 'is-expanded': miniCalendarExpanded }" aria-label="迷你日历">
        <button type="button" class="clone-mini-calendar-toggle" :aria-label="miniCalendarExpanded ? '收起月历' : '展开月历'" :aria-expanded="miniCalendarExpanded" :title="miniCalendarExpanded ? '收起月历' : '展开月历'" @click="miniCalendarExpanded = !miniCalendarExpanded"><ChevronDown :size="14" /></button>
        <header v-if="miniCalendarExpanded" class="clone-mini-calendar-head">
          <strong>{{ miniMonthLabel }}</strong>
          <div>
            <button type="button" aria-label="上个月" title="上个月" @click="moveMiniMonth(-1)"><ChevronLeft :size="15" /></button>
            <button type="button" aria-label="今天" title="回到今天" @click="goMiniCalendarToday"><Circle :size="13" /></button>
            <button type="button" aria-label="下个月" title="下个月" @click="moveMiniMonth(1)"><ChevronRight :size="15" /></button>
          </div>
        </header>
        <div class="clone-mini-calendar-grid" :class="{ expanded: miniCalendarExpanded }">
          <span v-for="label in weekDayLabels" :key="`weekday-${label}`">{{ label }}</span>
          <RouterLink v-for="cell in visibleMiniCalendarDays" :key="cell.key" :to="{ path: '/tasks/today', query: { date: cell.key } }" :class="{ 'other-month': cell.otherMonth, today: cell.today, selected: cell.selected, 'has-task': cell.hasTask, 'holiday-off': cell.holiday?.off, 'holiday-work': cell.holiday && !cell.holiday.off }" :aria-label="miniCalendarAriaLabel(cell)">
            <em v-if="miniCalendarExpanded && cell.holiday" :title="cell.holiday.name">{{ cell.holiday.off ? '休' : '班' }}</em><span>{{ cell.date.getDate() }}</span><small>{{ cell.lunar }}</small><i v-if="cell.hasTask" aria-hidden="true"></i>
          </RouterLink>
        </div>
        <p v-if="miniCalendarError" class="clone-mini-calendar-error" role="status">{{ miniCalendarError }}</p>
      </section>
    </aside>

    <main class="clone-task-main">
      <header class="clone-task-header">
        <div class="clone-title-block">
          <button
            type="button"
            class="clone-icon-button"
            aria-label="展开或折叠任务侧栏"
            :aria-expanded="!sidebarHidden"
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
            :placeholder="`添加任务至“${title === '今天' ? '收集箱' : title}” · 支持日期、优先级、#标签、@清单`"
          /><button type="submit" :disabled="!quickTitle.trim()">添加</button
          ><kbd>N</kbd>
        </form>
      </div>
      <section v-if="quickDraft" class="clone-quick-draft" aria-label="快速添加预览">
        <header><strong>确认任务</strong><span v-if="quickDraft.unrecognized?.length">未识别：{{ quickDraft.unrecognized.join('、') }}</span></header>
        <label>标题<input v-model="quickDraft.title" aria-label="快速添加任务标题预览" maxlength="500" /></label>
        <div class="clone-quick-draft-fields">
          <label>日期<input v-model="quickDraftDue" type="datetime-local" aria-label="快速添加日期" /></label>
          <label>优先级<select v-model="quickDraft.priority" aria-label="快速添加优先级"><option value="NONE">无</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></label>
          <label>清单<select v-model="quickDraft.listId" aria-label="快速添加清单"><option v-for="list in store.lists.filter((item) => !item.archived)" :key="list.publicId" :value="list.publicId">{{ list.name }}</option></select></label>
        </div>
        <fieldset v-if="store.tags.length" class="clone-quick-draft-tags"><legend>标签</legend><label v-for="tag in store.tags" :key="tag.publicId"><input v-model="quickDraft.tagIds" type="checkbox" :value="tag.publicId" />{{ tag.name }}</label></fieldset>
        <footer><button type="button" @click="cancelQuickDraft">取消</button><button type="button" class="clone-primary-button" :disabled="quickSaving || !quickDraft.title.trim()" @click="confirmQuickAdd">{{ quickSaving ? '保存中…' : '确认添加' }}</button></footer>
      </section>
      <p v-if="quickParseError" class="clone-error" role="alert">{{ quickParseError }}</p>
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
            :aria-expanded="!collapsed[group.key]"
            @click="collapsed[group.key] = !collapsed[group.key]"
          >
            <ChevronDown
              :size="16"
              :class="{ rotated: collapsed[group.key] }"
            /><strong>{{ group.label }}</strong
            ><span>{{ group.tasks.length }}</span>
          </button>
          <div class="clone-collapse-panel" :class="{ 'is-collapsed': collapsed[group.key] }" :aria-hidden="Boolean(collapsed[group.key])"><TransitionGroup name="clone-sort" tag="div" class="clone-task-rows clone-collapse-inner" :inert="Boolean(collapsed[group.key])">
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
          </TransitionGroup></div>
        </section>
        <section v-if="todayCountdowns.length || todayHabits.length" class="clone-efficiency-groups">
          <section v-if="todayCountdowns.length" class="clone-task-group clone-efficiency-group">
            <button class="clone-group-heading" type="button" :aria-expanded="!collapsed.countdowns" @click="collapsed.countdowns = !collapsed.countdowns">
              <ChevronDown :size="16" :class="{ rotated: collapsed.countdowns }" /><strong>倒数纪念日</strong><span>{{ todayCountdowns.length }}</span>
            </button>
            <div class="clone-collapse-panel" :class="{ 'is-collapsed': collapsed.countdowns }" :aria-hidden="collapsed.countdowns"><div class="clone-efficiency-rows clone-collapse-inner" :inert="collapsed.countdowns">
              <article v-for="item in todayCountdowns" :key="item.publicId" class="clone-efficiency-row countdown-row">
                <span class="clone-efficiency-icon">{{ item.icon || '◷' }}</span><strong>{{ item.name || item.title }}</strong><span class="clone-efficiency-meta">{{ countdownLabel(item) }}</span>
              </article>
            </div></div>
          </section>
          <section v-if="todayHabits.length" class="clone-task-group clone-efficiency-group">
            <button class="clone-group-heading" type="button" :aria-expanded="!collapsed.habits" @click="collapsed.habits = !collapsed.habits">
              <ChevronDown :size="16" :class="{ rotated: collapsed.habits }" /><strong>习惯</strong><span>{{ todayHabits.length }}</span>
            </button>
            <div class="clone-collapse-panel" :class="{ 'is-collapsed': collapsed.habits }" :aria-hidden="collapsed.habits"><div class="clone-efficiency-rows clone-collapse-inner" :inert="collapsed.habits">
              <article v-for="item in todayHabits" :key="item.publicId" class="clone-efficiency-row">
                <button type="button" class="clone-efficiency-check" :class="{ checked: habitDone(item) }" :aria-label="`${habitDone(item) ? '取消' : '完成'}习惯${item.name}`" @click="toggleHabitToday(item)">{{ habitDone(item) ? '✓' : '' }}</button><strong>{{ item.name }}</strong><span class="clone-efficiency-meta">{{ habitDone(item) ? '已完成' : '今天' }}</span>
              </article>
            </div></div>
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

    <div
      class="clone-detail-divider"
      role="separator"
      aria-orientation="vertical"
      aria-label="调整详情栏宽度"
      :aria-valuemin="320"
      :aria-valuemax="560"
      :aria-valuenow="detailWidth"
      tabindex="0"
      @pointerdown="startDetailResize"
      @dblclick="resetDetailWidth"
      @keydown="resizeDetailByKey"
    ></div>

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
          <button type="button" class="clone-detail-tool" aria-label="设置日期" @click.stop="toggleDatePopover">
            <CalendarClock :size="15" /><span>{{ editorOpen && draft.dueAt ? formatDate(draft.dueAt) : selectedTask?.dueAt ? formatDate(selectedTask.dueAt) : '设置日期' }}</span>
          </button>
          <button type="button" class="clone-detail-tool" aria-label="设置重复" :aria-expanded="repeatPopoverOpen" @click.stop="toggleRepeatPopover">
            <span class="clone-detail-repeat-icon">↻</span><span>{{ recurrenceLabel(editorOpen ? draft.rrule : selectedTask?.rrule) }}</span>
          </button>
          <div v-if="datePopoverOpen" class="clone-toolbar-popover clone-date-toolbar-popover" @click.stop>
            <strong>日期和时间</strong>
            <input v-if="editorOpen" v-model="draft.dueAt" type="datetime-local" aria-label="截止日期时间" />
            <template v-else><input v-model="datePopoverDue" type="datetime-local" aria-label="截止日期时间" /><button type="button" class="clone-popover-save" :disabled="dateSaving" @click="saveTaskDueDate">{{ dateSaving ? '保存中…' : '保存日期' }}</button></template>
            <div class="clone-popover-actions">
              <button type="button" @click="editorOpen ? draft.dueAt = toLocal(new Date()) : applyTaskDueOffset(0)">今天</button>
              <button type="button" @click="editorOpen ? draft.dueAt = toLocal(addDays(new Date(), 1)) : applyTaskDueOffset(1)">明天</button>
              <button type="button" @click="editorOpen ? draft.dueAt = '' : applyTaskDueOffset(null)">清除</button>
            </div>
          </div>
          <div v-if="repeatPopoverOpen" class="clone-toolbar-popover clone-repeat-toolbar-popover" @click.stop>
            <strong>重复</strong>
            <button v-for="option in recurrenceOptions" :key="option.value" type="button" :class="{ active: recurrenceValue(editorOpen ? draft.rrule : selectedTask?.rrule) === option.value }" @click="selectTaskRepeat(option.value)">{{ option.label }}</button>
          </div>
        </div>
        <div class="clone-detail-toolbar-actions">
          <button type="button" class="clone-detail-tool clone-reminder-tool" :class="{ active: editorOpen ? draft.reminderEnabled : quickReminder.reminder }" aria-label="设置提醒" :aria-expanded="reminderPopoverOpen" @click.stop="toggleReminderPopover"><Bell :size="15" /></button>
          <div v-if="reminderPopoverOpen" class="clone-toolbar-popover clone-reminder-popover" @click.stop>
            <strong>提醒</strong>
            <p v-if="reminderLoading" class="clone-muted">正在读取提醒…</p>
            <template v-else>
              <label class="clone-inline-check"><input v-model="reminderEnabledModel" type="checkbox" />启用站内提醒</label>
              <template v-if="reminderEnabledModel">
                <label class="clone-reminder-popover-field">方式<select v-model="reminderKindModel"><option value="RELATIVE">相对截止时间</option><option value="ABSOLUTE">指定时间</option></select></label>
                <label v-if="reminderKindModel === 'RELATIVE'" class="clone-reminder-popover-field">提前<select v-model.number="reminderOffsetModel"><option :value="0">准时</option><option :value="5">5 分钟</option><option :value="15">15 分钟</option><option :value="30">30 分钟</option><option :value="60">1 小时</option><option :value="1440">1 天</option></select></label>
                <label v-else class="clone-reminder-popover-field">提醒时间<input v-model="remindLocalModel" type="datetime-local" /></label>
                <label class="clone-inline-check"><input v-model="dailyUntilDoneModel" type="checkbox" />未完成时每日提醒</label>
              </template>
              <div class="clone-popover-actions"><button v-if="!editorOpen && quickReminder.reminder" type="button" @click="disableTaskReminder">关闭提醒</button><button v-if="editorOpen" type="button" @click="reminderPopoverOpen = false">完成后保存</button><button v-else type="button" class="clone-popover-save" :disabled="reminderSaving" @click="saveTaskReminder">{{ reminderSaving ? '保存中…' : '保存提醒' }}</button></div>
            </template>
          </div>
          <button type="button" class="clone-detail-priority-tool" :data-priority="editorOpen ? draft.priority : selectedTask?.priority" aria-label="设置优先级" :aria-expanded="priorityPopoverOpen" @click.stop="togglePriorityPopover">⚑</button>
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
          <div v-for="(item, index) in draft.checklistItems" :key="item.id || index" class="clone-checklist-row" :class="{ 'drop-target': checklistDropTarget === index }" @dragover.prevent="checklistDropTarget = index" @dragleave="checklistDropTarget = null" @drop.prevent="dropChecklistItem(index)">
            <button type="button" class="clone-checklist-grip" draggable="true" :aria-label="`拖动检查项 ${index + 1}`" @dragstart="dragChecklistItem(index, $event)" @dragend="checklistDropTarget = null"><GripVertical :size="14" /></button>
            <input v-model="item.completed" type="checkbox" :aria-label="`完成检查项 ${index + 1}`" />
            <input v-model="item.title" :class="{ completed: item.completed }" :aria-label="`检查项 ${index + 1}`" placeholder="添加检查项" />
            <button type="button" :aria-label="`上移检查项 ${index + 1}`" :disabled="index === 0" @click="moveChecklistItem(index, -1)">↑</button>
            <button type="button" :aria-label="`下移检查项 ${index + 1}`" :disabled="index === draft.checklistItems.length - 1" @click="moveChecklistItem(index, 1)">↓</button>
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

    <div v-if="context.open" class="clone-context-menu clone-task-row-menu" :style="{ left: `${context.x}px`, top: `${context.y}px` }" role="menu" @pointerdown.stop @click.stop>
      <strong>{{ context.task?.title }}</strong>
      <button type="button" @click="openDetail(context.task); closeContext()"><Pencil :size="14" />打开详情</button>
      <button type="button" @click="toggleComplete(context.task); closeContext()"><CheckCircle2 :size="14" />{{ context.task?.status === "COMPLETED" ? "重新打开" : "标记完成" }}</button>
      <button v-if="context.task?.status === 'ABANDONED'" type="button" @click="restoreAbandoned(context.task); closeContext()"><RotateCcw :size="14" />恢复任务</button>
      <button v-else type="button" @click="store.abandon(context.task); closeContext()"><X :size="14" />放弃任务</button>
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
    <div v-if="organizationMenuOpen" class="clone-context-menu clone-organization-menu" role="menu" :style="{ left: `${organizationMenu.x}px`, top: `${organizationMenu.y}px` }" @pointerdown.stop @click.stop>
      <template v-if="organizationMenu.kind === 'system' && organizationMenu.item.key === 'inbox'"><button type="button" @click="editInboxFromSystemMenu"><Pencil :size="14" />编辑</button></template>
      <template v-else-if="organizationMenu.kind === 'system'"><button type="button" @click="togglePinnedObject('system', organizationMenu.item)"><Pin :size="14" />{{ isPinnedObject('system', organizationMenu.item) ? '取消置顶' : '置顶' }}</button><button type="button" @click="organizationMenu.submenu = organizationMenu.submenu === 'display' ? '' : 'display'"><Eye :size="14" />显示选项<ChevronDown :size="13" /></button><div v-if="organizationMenu.submenu === 'display'" class="clone-context-submenu"><button v-for="option in displayOptionsFor('system', organizationMenu.item)" :key="option.value" type="button" @click="setDisplayOption('system', organizationMenu.item, option.value)">{{ option.label }}<Check v-if="getDisplayOption('system', organizationMenu.item) === option.value" :size="14" /></button></div></template>
      <template v-else-if="organizationMenu.kind === 'list'">
        <button v-if="isInboxColorList(organizationMenu.item)" type="button" @click="editOrganization('list')"><Pencil :size="14" />编辑</button>
        <template v-else><button type="button" @click="editOrganization('list')"><Pencil :size="14" />编辑</button><button type="button" @click="togglePinnedObject('list', organizationMenu.item)"><Pin :size="14" />{{ isPinnedObject('list', organizationMenu.item) ? '取消置顶' : '置顶' }}</button><button type="button" @click="openListCopy"><Copy :size="14" />复制</button><button type="button" @click="shareOrganization('list')"><Copy :size="14" />共享</button><button type="button" @click="requestArchiveList"><Archive :size="14" />{{ organizationMenu.item.archived ? '取消归档' : '归档' }}</button><button type="button" class="danger" @click="requestDeleteOrganization('list')"><Trash2 :size="14" />删除</button></template>
      </template>
      <template v-else-if="organizationMenu.kind === 'tag'"><button type="button" @click="editOrganization('tag')"><Pencil :size="14" />编辑</button><button type="button" @click="togglePinnedObject('tag', organizationMenu.item)"><Pin :size="14" />{{ isPinnedObject('tag', organizationMenu.item) ? '取消置顶' : '置顶' }}</button><button type="button" @click="createChildTag"><Plus :size="14" />添加子标签</button><button type="button" @click="openTagMerge"><Merge :size="14" />合并标签到…</button><button type="button" class="danger" @click="deleteOrganization('tag')"><Trash2 :size="14" />删除</button></template>
      <template v-else-if="organizationMenu.kind === 'filter'"><button type="button" @click="editOrganization('filter')"><Pencil :size="14" />编辑</button><button type="button" @click="toggleFilterPinned"><Pin :size="14" />{{ isPinnedObject('filter', organizationMenu.item) ? '取消置顶' : '置顶' }}</button><button type="button" @click="duplicateFilter"><Copy :size="14" />复制</button><button type="button" class="danger" @click="deleteOrganization('filter')"><Trash2 :size="14" />删除</button></template>
      <template v-else><button type="button" @click="createList(organizationMenu.item.publicId)"><Plus :size="14" />添加清单</button><button type="button" @click="editListGroup"><Pencil :size="14" />编辑</button><button type="button" @click="togglePinnedObject('group', organizationMenu.item)"><Pin :size="14" />{{ organizationMenu.item.pinned ? '取消置顶' : '置顶' }}</button><button type="button" class="danger" @click="requestDissolveListGroup"><Ungroup :size="14" />解散</button></template>
    </div>
    <Dialog v-model:open="organizationDialogOpen" :title="organizationDialogTitle" :content-class="organizationDialog.kind === 'filter' ? 'task-filter-dialog' : organizationDialog.kind === 'list' && !isInboxColorEditor ? 'task-list-dialog' : ''">
      <form class="clone-organization-form" :class="{ 'clone-inbox-color-form': isInboxColorEditor, 'clone-list-form': organizationDialog.kind === 'list' && !isInboxColorEditor, 'clone-tag-form': organizationDialog.kind === 'tag', 'clone-filter-form': organizationDialog.kind === 'filter' }" @submit.prevent="saveOrganization">
        <div v-if="organizationDialog.kind === 'list' && !isInboxColorEditor" class="clone-list-name-row">
          <button type="button" class="clone-list-icon-button" aria-label="选择清单图标" @click="openOrganizationIconPicker('list')"><TaskOrganizationIcon v-if="organizationDialog.icon" :value="organizationDialog.icon" :size="18" /><ListTodo v-else :size="16" /></button>
          <input v-model.trim="organizationDialog.name" aria-label="清单名称" placeholder="名称" required maxlength="120" autofocus />
        </div>
        <label v-else-if="!isInboxColorEditor && !['tag','filter','group'].includes(organizationDialog.kind)">名称<input v-model.trim="organizationDialog.name" required maxlength="120" autofocus /></label>
        <div v-if="isInboxColorEditor" class="clone-inbox-color-row"><span>颜色</span><TaskColorPicker v-model="organizationDialog.color" inline /></div>
        <div v-else-if="organizationDialog.kind === 'list' && !isInboxColorEditor" class="clone-list-setting-row clone-list-color-row"><span>颜色</span><TaskColorPicker v-model="organizationDialog.color" inline /></div>
        <div v-if="organizationDialog.kind === 'tag'" class="clone-list-name-row clone-tag-name-row"><button type="button" class="clone-list-icon-button" aria-label="选择标签图标" @click="openOrganizationIconPicker('tag')"><TaskOrganizationIcon v-if="organizationDialog.icon" :value="organizationDialog.icon" :size="18" /><Tag v-else :size="18" /></button><input v-model.trim="organizationDialog.name" aria-label="标签名称" placeholder="名称" required maxlength="30" autofocus /></div>
        <div v-if="organizationDialog.kind === 'tag'" class="clone-list-setting-row clone-list-color-row"><span>颜色</span><TaskColorPicker v-model="organizationDialog.color" inline /></div>
        <div v-if="organizationDialog.kind === 'group' && !isInboxColorEditor" class="clone-list-name-row"><button type="button" class="clone-list-icon-button" aria-label="选择文件夹图标" @click="openOrganizationIconPicker('group')"><TaskOrganizationIcon v-if="organizationDialog.icon" :value="organizationDialog.icon" :size="18" /><Folder v-else :size="18" /></button><input v-model.trim="organizationDialog.name" aria-label="文件夹名称" placeholder="名称" required maxlength="100" /></div>
        <div v-if="organizationDialog.kind === 'list' && !isInboxColorEditor" class="clone-list-setting-row"><span>视图</span><div class="clone-list-view-picker" role="group" aria-label="默认视图"><button v-for="view in listViewOptions" :key="view.value" type="button" :aria-pressed="organizationDialog.viewType === view.value" :aria-label="view.label" :title="view.label" @click="organizationDialog.viewType = view.value"><component :is="view.icon" :size="17" /><span>{{ view.label }}</span></button></div></div>
        <label v-if="organizationDialog.kind === 'list' && !isInboxColorEditor" class="clone-list-setting-row"><span>文件夹</span><select :value="organizationDialog.groupId" aria-label="文件夹" @change="handleListFolderChange($event.target.value)"><option value="" :selected="organizationDialog.groupId === ''">无</option><option v-for="group in sortedListGroups" :key="group.publicId" :value="group.publicId" :selected="organizationDialog.groupId === group.publicId">{{ group.icon ? `${group.icon} ` : '' }}{{ group.name }}</option><option value="__CREATE_GROUP__" :selected="organizationDialog.groupId === '__CREATE_GROUP__'">{{ organizationDialog.newGroupName ? `新建文件夹：${organizationDialog.newGroupName}` : '＋ 添加文件夹…' }}</option></select></label>
        <label v-if="organizationDialog.kind === 'list' && !isInboxColorEditor" class="clone-list-setting-row"><span>类型</span><select v-model="organizationDialog.objectType" aria-label="清单类型"><option value="TASK">任务清单</option><option value="NOTE">笔记清单</option></select></label>
        <label v-if="organizationDialog.kind === 'list' && !isInboxColorEditor" class="clone-list-setting-row"><span>在智能清单中显示</span><select v-model="organizationDialog.showInSmartLists" aria-label="在智能清单中显示"><option :value="true">所有任务</option><option :value="false">不显示</option></select></label>
        <label v-if="organizationDialog.kind === 'tag'">父标签<select v-model="organizationDialog.parentId"><option value="">无</option><option v-for="tag in store.tags.filter(item => !item.parentId && item.publicId !== organizationDialog.item?.publicId)" :key="tag.publicId" :value="tag.publicId">{{ tag.name }}</option></select></label>
        <template v-if="organizationDialog.kind === 'filter'">
          <template v-if="organizationDialog.filter.previewed">
            <section class="clone-filter-preview-screen" aria-label="筛选器预览结果">
              <strong>预览 · {{ filterPreview.length }} 项</strong>
              <section v-for="group in filterPreviewGroups" :key="group.key" class="clone-filter-preview-group">
                <strong><Folder v-if="group.isFolder" :size="13" /><ListTodo v-else :size="13" />{{ group.name }} <small>{{ group.items.length }}</small></strong>
                <div v-for="list in group.lists" :key="list.key" class="clone-filter-preview-list">
                  <strong v-if="group.isFolder"><ListTodo :size="12" />{{ list.name }} <small>{{ list.items.length }}</small></strong>
                  <span v-for="task in list.items" :key="task.publicId"><span class="clone-filter-preview-check" aria-hidden="true"></span>{{ task.title }}<small>{{ task.dueAt ? formatDate(task.dueAt) : '' }}</small></span>
                </div>
              </section>
              <span v-if="!filterPreview.length">当前没有匹配任务</span>
            </section>
          </template>
          <template v-else>
            <div class="clone-filter-name-row"><button type="button" class="clone-list-icon-button" aria-label="选择筛选器图标" @click="openOrganizationIconPicker('filter')"><TaskOrganizationIcon v-if="organizationDialog.icon" :value="organizationDialog.icon" :size="18" /><SlidersHorizontal v-else :size="18" /></button><input v-model.trim="organizationDialog.name" aria-label="筛选器名称" placeholder="名称" required maxlength="120" autofocus /><button v-if="organizationDialog.icon" type="button" class="clone-icon-clear" @click="organizationDialog.icon = ''">使用默认</button></div>
            <div class="clone-filter-mode" role="tablist" aria-label="筛选器模式"><button type="button" role="tab" :aria-selected="organizationDialog.filter.mode === 'BASIC'" @click="organizationDialog.filter.mode = 'BASIC'">普通</button><button type="button" role="tab" :aria-selected="organizationDialog.filter.mode === 'ADVANCED'" @click="organizationDialog.filter.mode = 'ADVANCED'">高级</button></div>
            <template v-if="organizationDialog.filter.mode === 'BASIC'">
              <div class="clone-filter-field"><span>清单</span><details class="clone-filter-select"><summary @click="closeOtherFilterSelect('list')">{{ filterListSummary }}<ChevronDown :size="14" /></summary><div class="clone-filter-select-menu" role="group" aria-label="清单范围" @click.stop><label><input type="checkbox" :checked="filterListAllSelected" @change="organizationDialog.filter.listIds = []" />所有清单</label><hr /><label v-for="group in sortedListGroups" :key="group.publicId"><input type="checkbox" :checked="groupListSelected(group.publicId)" @change="toggleFilterGroup(group.publicId, $event.target.checked)" />{{ group.name }}（文件夹）</label><label v-for="list in activeLists" :key="list.publicId" :class="{ 'is-child-list': list.groupId }"><input type="checkbox" :checked="filterListAllSelected || organizationDialog.filter.listIds.includes(list.publicId)" @change="toggleFilterList(list.publicId, $event.target.checked)" />{{ list.name }}</label></div></details></div>
              <div class="clone-filter-field"><span>标签</span><details class="clone-filter-select"><summary @click="closeOtherFilterSelect('tag')">{{ filterTagSummary }}<ChevronDown :size="14" /></summary><div class="clone-filter-select-menu" role="group" aria-label="标签范围" @click.stop><label><input type="radio" v-model="organizationDialog.filter.tagMode" value="ALL" @change="organizationDialog.filter.tagIds = []" />所有</label><hr /><label><input type="radio" v-model="organizationDialog.filter.tagMode" value="HAS_TAGS" @change="organizationDialog.filter.tagIds = []" />有标签</label><label><input type="radio" v-model="organizationDialog.filter.tagMode" value="NO_TAGS" @change="organizationDialog.filter.tagIds = []" />无标签</label><hr /><label v-for="tag in sortedTags" :key="tag.publicId" class="clone-filter-tag-option"><input type="checkbox" :checked="organizationDialog.filter.tagMode === 'SELECTED' && organizationDialog.filter.tagIds.includes(tag.publicId)" @change="toggleFilterTag(tag.publicId, $event.target.checked)" />#{{ tag.name }}</label></div></details></div>
              <label class="clone-filter-field"><span>日期</span><select v-model="organizationDialog.filter.dueRange" @change="setFilterDueRange"><option value="ANY">所有</option><option value="NONE">无日期</option><option value="OVERDUE">已过期</option><option value="REPEATING">重复日期</option><option value="TODAY">今天</option><option value="TOMORROW">明天</option><option value="THIS_WEEK">本周</option><option value="NEXT_WEEK">下周</option><option value="THIS_MONTH">本月</option><option value="NEXT_MONTH">下月</option><option value="CUSTOM">时间段</option></select></label>
              <div v-if="organizationDialog.filter.dueRange === 'CUSTOM'" class="clone-filter-date-range"><label><span><CalendarClock :size="14" />时间段</span><select aria-label="开始日期" :value="organizationDialog.filter.dueStart" @change="setFilterDateEndpoint('start', $event.target.value)"><option v-for="option in filterDateOptions" :key="option.value" :value="option.value" :disabled="option.offset === 30">{{ option.label }}</option></select></label><label class="clone-filter-date-separator">~</label><label><select aria-label="结束日期" :value="organizationDialog.filter.dueEnd" @change="setFilterDateEndpoint('end', $event.target.value)"><option v-for="option in filterDateOptions" :key="option.value" :value="option.value" :disabled="option.value <= organizationDialog.filter.dueStart">{{ option.label }}</option></select></label></div>
              <fieldset class="clone-filter-priorities"><legend>优先级</legend><label><input type="radio" name="filter-priority-all" :checked="organizationDialog.filter.priorities.length === 0" @change="organizationDialog.filter.priorities = []" />所有</label><label v-for="option in filterPriorityOptions" :key="option.value"><input v-model="organizationDialog.filter.priorities" type="checkbox" :value="option.value" />{{ option.label }}</label></fieldset>
              <label class="clone-filter-field"><span>内容包含</span><input v-model.trim="organizationDialog.filter.keyword" maxlength="200" placeholder="输入任务关键词" /></label>
              <fieldset class="clone-filter-task-types"><legend>任务类型</legend><label><input type="radio" name="filter-task-type-all" :checked="organizationDialog.filter.taskTypes.length === 0" @change="organizationDialog.filter.taskTypes = []" />所有</label><label><input v-model="organizationDialog.filter.taskTypes" type="checkbox" value="TASK" />任务</label><label><input v-model="organizationDialog.filter.taskTypes" type="checkbox" value="NOTE" />笔记</label></fieldset>
            </template>
            <TaskFilterRuleEditor v-if="organizationDialog.filter.mode === 'ADVANCED'" v-model="organizationDialog.filter.advanced" :lists="activeLists" :groups="sortedListGroups" :tags="sortedTags" />
          </template>
        </template>
        <footer :class="{ 'clone-inbox-color-actions': isInboxColorEditor, 'clone-filter-preview-actions': organizationDialog.kind === 'filter' && organizationDialog.filter.previewed }"><template v-if="isInboxColorEditor"><button type="submit" class="clone-primary-button">保存</button><button type="button" @click="organizationDialogOpen = false">取消</button></template><template v-else-if="organizationDialog.kind === 'filter' && organizationDialog.filter.previewed"><button type="button" @click="organizationDialog.filter.previewed = false">取消</button><button type="submit" class="clone-primary-button" :disabled="!filterDraftValid">保存</button></template><template v-else-if="organizationDialog.kind === 'filter'"><button type="button" class="clone-filter-preview-button" :disabled="!filterDraftValid" @click="previewFilterDraft"><Eye :size="14" />预览</button><button type="button" @click="organizationDialogOpen = false">取消</button><button type="submit" class="clone-primary-button" :disabled="!filterDraftValid">保存</button></template><template v-else><button type="button" @click="organizationDialogOpen = false">取消</button><button type="submit" class="clone-primary-button">保存</button></template></footer>
      </form>
    </Dialog>
    <Dialog v-model:open="folderDialogOpen" title="添加文件夹" content-class="task-folder-dialog">
      <form class="clone-folder-form" @submit.prevent="stageNewListGroup">
        <div class="clone-list-name-row"><button type="button" class="clone-list-icon-button" aria-label="选择文件夹图标" @click="openOrganizationIconPicker('new-group')"><TaskOrganizationIcon :value="organizationDialog.newGroupIcon" :size="18" /></button><input v-model.trim="organizationDialog.newGroupName" aria-label="文件夹名称" placeholder="名称" required maxlength="100" autofocus /></div>
        <footer><button type="button" @click="cancelNewListGroup">取消</button><button type="submit" class="clone-primary-button">保存</button></footer>
      </form>
    </Dialog>
    <Dialog v-model:open="organizationIconPickerOpen" title="选择图标" content-class="task-icon-dialog">
      <div class="clone-icon-picker"><input v-model.trim="organizationIconSearch" aria-label="搜索图标" placeholder="搜索中文名称或图标名" /><div class="clone-icon-categories" role="tablist" aria-label="图标分类"><button v-for="category in organizationIconCategories" :key="category.value" type="button" role="tab" :aria-selected="organizationIconCategory === category.value" @click="organizationIconCategory = category.value">{{ category.label }}</button></div><div class="clone-icon-grid" role="listbox" aria-label="可选图标"><button v-for="icon in filteredOrganizationIcons" :key="icon.value" type="button" role="option" :aria-selected="currentOrganizationIcon === icon.value" :aria-label="icon.label" :title="`${icon.label} · ${icon.value}`" @click="selectOrganizationIcon(icon.value)"><component :is="icon.component" v-if="icon.component" :size="19" :stroke-width="2" aria-hidden="true" /><span v-else>{{ icon.value }}</span></button><p v-if="!filteredOrganizationIcons.length" class="clone-icon-empty">没有匹配的图标</p></div><small class="clone-icon-count">{{ filteredOrganizationIcons.length }} 个可选图标</small></div>
    </Dialog>
    <Dialog v-model:open="listCopyOpen" title="复制清单"><form class="clone-organization-form" @submit.prevent="confirmListCopy"><fieldset><legend>复制任务</legend><label><input v-model="listCopy.mode" type="radio" value="OPEN_ONLY" />只复制未完成任务</label><label><input v-model="listCopy.mode" type="radio" value="ALL_PRESERVE" />复制全部任务，保留已完成状态</label><label><input v-model="listCopy.mode" type="radio" value="ALL_OPEN" />复制全部任务，并将任务设为未完成</label></fieldset><p class="clone-dialog-note">新清单将命名为“{{ listCopy.name }}”。</p><footer><button type="button" :disabled="listCopy.busy" @click="listCopyOpen = false">取消</button><button type="submit" class="clone-primary-button" :disabled="listCopy.busy">{{ listCopy.busy ? '复制中…' : '复制' }}</button></footer></form></Dialog>
    <Dialog v-model:open="organizationConfirmOpen" :title="organizationConfirmation.title"><template #title v-if="organizationConfirmation.action === 'delete-list'">删除清单“<TaskOrganizationIcon v-if="organizationConfirmation.item?.icon" class="clone-confirmation-title-icon" :value="organizationConfirmation.item.icon" :size="16" /><ListTodo v-else class="clone-confirmation-title-icon" :size="16" /> {{ organizationConfirmation.item?.name }}”？</template><div class="clone-confirmation"><p v-if="organizationConfirmation.action === 'dissolve-group'">解散后，文件夹（<TaskOrganizationIcon v-if="organizationConfirmation.item?.icon" class="clone-confirmation-title-icon" :value="organizationConfirmation.item.icon" :size="16" /><Folder v-else class="clone-confirmation-title-icon" :size="16" /> {{ organizationConfirmation.item?.name }}）中的清单将直接显示在侧边栏。</p><p v-else>{{ organizationConfirmation.message }}</p><footer><button type="button" :disabled="organizationConfirmation.busy" @click="organizationConfirmOpen = false">取消</button><button type="button" class="clone-danger-button" :disabled="organizationConfirmation.busy" @click="confirmOrganizationAction">{{ organizationConfirmation.busy ? '处理中…' : organizationConfirmation.confirmLabel }}</button></footer></div></Dialog>
    <Dialog v-model:open="tagMergeOpen" title="合并标签到…"><form class="clone-organization-form" @submit.prevent="confirmTagMerge"><p>你可以将标签“{{ tagMerge.source?.name }}”下的任务合并到另一个标签。合并后，标签“{{ tagMerge.source?.name }}”将被删除；其子标签会迁移至目标标签层级。</p><label>选择标签<select v-model="tagMerge.targetId" required><option value="" disabled>选择标签</option><option v-for="tag in mergeTargets" :key="tag.publicId" :value="tag.publicId">{{ tag.name }}</option></select></label><footer><button type="button" :disabled="tagMerge.busy" @click="tagMergeOpen = false">取消</button><button type="submit" class="clone-primary-button" :disabled="tagMerge.busy || !mergeTargets.length">{{ tagMerge.busy ? '合并中…' : '确定' }}</button></footer></form></Dialog>
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
  Bell,
  Check,
  CheckCircle2,
  CheckSquare,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Circle,
  CalendarDays,
  Columns3,
  ClipboardList,
  Copy,
  Ellipsis,
  Download,
  Eye,
  EyeOff,
  Flag,
  GanttChart,
  GripVertical,
  Inbox,
  ListFilter,
  List,
  ListTodo,
  Merge,
  PanelLeft,
  PanelLeftClose,
  Paperclip,
  Pencil,
  Pin,
  Plus,
  RotateCcw,
  Search,
  SquareKanban,
  Repeat2,
  SlidersHorizontal,
  Tag,
  Timer,
  Trash2,
  Ungroup,
  WifiOff,
  X,
  UserRound,
  Folder,
} from "lucide-vue-next";
import TaskRichTextEditor from "../components/tasks/TaskRichTextEditor.vue";
import TaskFilterRuleEditor from "../components/tasks/TaskFilterRuleEditor.vue";
import TaskColorPicker from "../components/TaskColorPicker.vue";
import TaskOrganizationIcon from "../components/tasks/TaskOrganizationIcon.vue";
import Dialog from "../components/ui/Dialog.vue";
import { organizationIconCategories, searchOrganizationIcons } from "./taskOrganizationIcons.js";
import { moveItem } from "./taskSidebar.js";
import { groupTaskItems, sortTaskItems } from "./taskList.js";
import { moveChecklistItem as reorderChecklistItems } from "./taskChecklist.js";
import { matchesFilterRules, serializeFilterRules } from "./taskFilterRules.js";
import { advancedFilterConditionsFromRules, advancedFilterConditionsValid, buildAdvancedFilterRules } from "./taskAdvancedFilter.js";
import { buildTaskSidebarCounts } from "./taskSidebarCounts.js";
import { mergeSidebarPreferenceChanges } from "./taskSidebarPreferences.js";
import { useTasksStore } from "../stores/tasks.js";
import { useTaskFocusStore } from "../stores/taskFocus.js";
import { message } from "../services/message.js";
import {
  apiGetTaskSidebarPreferences,
  apiUpdateTaskSidebarPreferences,
  apiParseTaskQuickAdd,
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
  apiMergeTaskTag,
  apiUpdateTaskComment,
  apiUpdateTaskReminder,
  apiUploadTaskAttachment,
  apiGetTaskCalendar,
  apiUpdateTask,
} from "../../packages/api-client/src/index.js";

const route = useRoute();
const router = useRouter();
const store = useTasksStore();
const focusStore = useTaskFocusStore();
const filters = ref([]);
const quickTitle = ref("");
const quickDraft = ref(null);
const quickDraftDue = ref("");
const quickParseError = ref("");
const quickParsing = ref(false);
const quickSaving = ref(false);
const checklistDragIndex = ref(null);
const checklistDropTarget = ref(null);
const sidebarHidden = ref(false);
const sidebarEditing = ref(false);
const isMobileSidebar = ref(typeof window !== "undefined" && window.matchMedia("(max-width: 900px)").matches);
let sidebarTouchStart = null;
const miniCalendarExpanded = ref(false);
const miniCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const miniCalendarResponse = ref({ tasks: [], lunar: [], holidays: [] });
const miniCalendarError = ref("");
let miniCalendarRequest = 0;
const sidebarPreferenceScope = ref("");
const detailWidth = ref(390);
const detailPreferenceScope = ref("");
let detailResizeState = null;
let detailPreviousUserSelect = "";
const moreOpen = ref(false);
const datePopoverOpen = ref(false);
const repeatPopoverOpen = ref(false);
const reminderPopoverOpen = ref(false);
const datePopoverDue = ref("");
const dateSaving = ref(false);
const reminderLoading = ref(false);
const reminderSaving = ref(false);
let reminderLoadSequence = 0;
const quickReminder = reactive({ enabled: false, kind: "RELATIVE", offsetMinutes: 30, remindLocal: "", dailyUntilDone: false, reminder: null });
const reminderEnabledModel = computed({ get: () => editorOpen.value ? draft.reminderEnabled : quickReminder.enabled, set: (value) => { if (editorOpen.value) draft.reminderEnabled = value; else quickReminder.enabled = value; } });
const reminderKindModel = computed({ get: () => editorOpen.value ? draft.reminderKind : quickReminder.kind, set: (value) => { if (editorOpen.value) draft.reminderKind = value; else quickReminder.kind = value; } });
const reminderOffsetModel = computed({ get: () => editorOpen.value ? draft.reminderOffset : quickReminder.offsetMinutes, set: (value) => { if (editorOpen.value) draft.reminderOffset = value; else quickReminder.offsetMinutes = value; } });
const remindLocalModel = computed({ get: () => editorOpen.value ? draft.remindLocal : quickReminder.remindLocal, set: (value) => { if (editorOpen.value) draft.remindLocal = value; else quickReminder.remindLocal = value; } });
const dailyUntilDoneModel = computed({ get: () => editorOpen.value ? draft.dailyUntilDone : quickReminder.dailyUntilDone, set: (value) => { if (editorOpen.value) draft.dailyUntilDone = value; else quickReminder.dailyUntilDone = value; } });
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
const sectionDisplayModes = reactive({ filters: "SHOW", tags: "SHOW" });
const sectionDisplayMenu = ref("");
const groupCollapsed = reactive({});
const organizationDrag = ref(null);
const organizationDropTarget = ref("");
const organizationMenuOpen = ref(false);
const organizationDialogOpen = ref(false);
const organizationMenu = reactive({ open: false, kind: "", item: null, x: 0, y: 0 });
const organizationDialog = reactive({ open: false, kind: "", item: null, name: "", color: "#2563eb", icon: "", parentId: "", groupId: "", newGroupName: "", newGroupIcon: "📁", newGroupColor: "#64748b", groupListIds: [], showInSmartLists: true, filter: {} });
const folderDialogOpen = ref(false);
const organizationIconPickerOpen = ref(false);
const organizationIconTarget = ref("list");
const organizationIconSearch = ref("");
const organizationIconCategory = ref("all");
const listViewOptions = [
  { value: "LIST", label: "列表", icon: List },
  { value: "KANBAN", label: "看板", icon: Columns3 },
  { value: "TIMELINE", label: "时间线", icon: GanttChart },
];
const filteredOrganizationIcons = computed(() => searchOrganizationIcons(organizationIconSearch.value, organizationIconCategory.value));
const currentOrganizationIcon = computed(() => organizationIconTarget.value === "new-group" ? organizationDialog.newGroupIcon : organizationDialog.icon);
const sidebarObjectPrefs = reactive({ pinnedSystem: [], hiddenSystem: [], conditionalSystem: [], pinnedLists: [], hiddenLists: [], pinnedTags: [], hiddenTags: [], pinnedFilters: [], hiddenFilters: [], displayModes: {} });
const sidebarPrefsRevision = ref(0);
let sidebarPrefsSave = Promise.resolve();
let sidebarPrefsSaveTimer;
let sidebarPrefsScope = "";
let sidebarPrefsLoading = false;
let sidebarPrefsPendingSave = false;
let sidebarPrefsMutationVersion = 0;
const listCopyOpen = ref(false);
const listCopy = reactive({ source: null, name: "", mode: "ALL_PRESERVE", busy: false });
const organizationConfirmOpen = ref(false);
const organizationConfirmation = reactive({ action: "", item: null, title: "", message: "", confirmLabel: "确认", busy: false });
const tagMergeOpen = ref(false);
const tagMerge = reactive({ source: null, targetId: "", busy: false });
let listHoverTimer = 0;
const filterPreview = ref([]);
const filterPreviewGroups = computed(() => {
  const folders = new Map();
  for (const task of filterPreview.value) {
    const list = store.lists.find(item => item.publicId === task.listId);
    const folder = list?.groupId ? store.listGroups.find(item => item.publicId === list.groupId) : null;
    const key = folder ? `folder:${folder.publicId}` : `list:${list?.publicId || "inbox"}`;
    if (!folders.has(key)) folders.set(key, { key, name: folder?.name || list?.name || "收集箱", isFolder: Boolean(folder), items: [], lists: new Map() });
    const section = folders.get(key);
    section.items.push(task);
    const listKey = list?.publicId || "inbox";
    if (!section.lists.has(listKey)) section.lists.set(listKey, { key: listKey, name: list?.name || "收集箱", items: [] });
    section.lists.get(listKey).items.push(task);
  }
  return [...folders.values()].map(section => ({ ...section, lists: [...section.lists.values()] }));
});
const filterDraftValid = computed(() => {
  if (organizationDialog.filter.mode === "ADVANCED") {
    return advancedFilterConditionsValid(organizationDialog.filter.advanced.conditions, organizationDialog.filter.advanced.joins);
  }
  return organizationDialog.filter.dueRange !== "CUSTOM" || Boolean(organizationDialog.filter.dueStart && organizationDialog.filter.dueEnd && organizationDialog.filter.dueEnd > organizationDialog.filter.dueStart);
});
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
  dailyUntilDone: false,
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
  { key: "inbox", label: "收集箱", to: "/tasks/inbox", icon: Inbox },
  { key: "summary", label: "摘要", to: "/tasks/shortcut/summary", icon: ClipboardList },
];
const completedView = { key: "completed", label: "已完成", to: "/tasks/completed", icon: CheckCircle2 };
const abandonedView = { key: "abandoned", label: "已放弃", to: "/tasks/abandoned", icon: X };
const trashView = { key: "trash", label: "垃圾桶", to: "/tasks/trash", icon: Trash2 };
const archivedView = { key: "archived", label: "归档", to: "/tasks/shortcut/archived", icon: Archive };
const allSidebarSystemViews = computed(() => [...primaryViews, ...shortcutViews, completedView, abandonedView, trashView, archivedView]);
const visiblePrimaryViews = computed(() => primaryViews.filter(item => !isHiddenObject("system", item) && !isPinnedObject("system", item)));
const pinnedPrimaryViews = computed(() => primaryViews.filter(item => !isHiddenObject("system", item) && isPinnedObject("system", item)));
const sortedListGroups = computed(() => [...store.listGroups].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const filterDateOptions = computed(() => Array.from({ length: 61 }, (_, index) => {
  const offset = index - 30;
  const value = dateKey(addDays(new Date(), offset));
  return { value, offset, label: offset === 0 ? "今天" : offset < 0 ? `往前第${Math.abs(offset)}天` : `往后第${offset}天` };
}));
const pinnedListGroups = computed(() => sortedListGroups.value.filter(group => group.pinned));
const visibleListGroups = computed(() => sortedListGroups.value.filter(group => !group.pinned));
const activeLists = computed(() => store.lists.filter(item => !item.archived));
const sidebarLists = computed(() => activeLists.value.filter(item => item.systemKey !== "INBOX"));
const pinnedLists = computed(() => sidebarLists.value.filter(item => !isHiddenObject("list", item) && isPinnedObject("list", item)).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const archivedLists = computed(() => store.lists.filter(item => item.archived).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const sidebarTaskCounts = computed(() => buildTaskSidebarCounts(
  store.tasks,
  archivedLists.value.map(list => list.publicId),
  store.inbox?.publicId,
  new Map(activeLists.value.filter(list => list.groupId).map(list => [list.publicId, list.groupId])),
));
const archivedTaskCount = computed(() => sidebarTaskCounts.value.counts.archived);
const abandonedTaskCount = computed(() => sidebarTaskCounts.value.counts.abandoned);
const completedTaskCount = computed(() => sidebarTaskCounts.value.counts.completed);
const trashedTaskCount = computed(() => store.tasks.filter(task => task.deleted).length);
const ungroupedLists = computed(() => sidebarLists.value.filter(item => !item.groupId && !isHiddenObject("list", item) && !isPinnedObject("list", item)).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const rootTags = computed(() => store.tags.filter(item => !item.parentId && !isHiddenObject("tag", item) && !isPinnedObject("tag", item)).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const childTags = computed(() => store.tags.filter(item => item.parentId && !isHiddenObject("tag", item) && !isPinnedObject("tag", item)).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const sortedTags = computed(() => [...store.tags].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const sortedFilters = computed(() => [...filters.value].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
const pinnedTags = computed(() => sortedTags.value.filter(item => !isHiddenObject("tag", item) && isPinnedObject("tag", item)));
const visibleFilters = computed(() => sortedFilters.value.filter(item => !isHiddenObject("filter", item) && !isPinnedObject("filter", item)));
const pinnedFilters = computed(() => sortedFilters.value.filter(item => !isHiddenObject("filter", item) && isPinnedObject("filter", item)));
const filterListAllSelected = computed(() => organizationDialog.filter.listIds.length === 0 || (activeLists.value.length > 0 && activeLists.value.every(list => organizationDialog.filter.listIds.includes(list.publicId))));
const filterListSummary = computed(() => filterListAllSelected.value ? "所有" : `已选 ${organizationDialog.filter.listIds.length} 项`);
const filterTagSummary = computed(() => {
  const { tagMode, tagIds } = organizationDialog.filter;
  if (tagMode === "HAS_TAGS") return "有标签";
  if (tagMode === "NO_TAGS") return "无标签";
  if (tagMode !== "SELECTED" || !tagIds.length) return "所有";
  const names = tagIds.map(id => store.tags.find(tag => tag.publicId === id)?.name).filter(Boolean);
  return names.length <= 2 ? names.map(name => `#${name}`).join("、") : `已选 ${names.length} 个标签`;
});
const mergeTargets = computed(() => {
  if (!tagMerge.source) return [];
  const sourceHasChildren = store.tags.some(item => item.parentId === tagMerge.source.publicId);
  const descendants = new Set();
  let changed = true;
  while (changed) {
    changed = false;
    for (const item of store.tags) if ((item.parentId === tagMerge.source.publicId || descendants.has(item.parentId)) && !descendants.has(item.publicId)) { descendants.add(item.publicId); changed = true; }
  }
  return store.tags.filter(item => item.publicId !== tagMerge.source.publicId && !descendants.has(item.publicId)
    && (!sourceHasChildren || !item.parentId));
});
function listOpenCount(listId) { return sidebarTaskCounts.value.openByList.get(listId) || 0; }
function groupOpenCount(groupId) { return sidebarTaskCounts.value.openByGroup.get(groupId) || 0; }
function tagOpenCount(tagId) { return sidebarTaskCounts.value.openByTag.get(tagId) || 0; }
function tagHasTasks(tagId) { return sidebarTaskCounts.value.taggedTasks.has(tagId); }
function toggleListGroupCollapsed(group) { groupCollapsed[group.publicId] = !groupCollapsed[group.publicId]; persistListGroupState(); scheduleSidebarPreferencesSave(); }
function persistListGroupState() { try { localStorage.setItem(`task_sidebar_groups:${store.accountScope || "anonymous"}`, JSON.stringify(groupCollapsed)); } catch {} }
function loadListGroupState() { try { Object.assign(groupCollapsed, JSON.parse(localStorage.getItem(`task_sidebar_groups:${store.accountScope || "anonymous"}`) || "{}")); } catch {} }
const isInboxColorEditor = computed(() => organizationDialog.kind === "list" && organizationDialog.item?.systemKey === "INBOX");
const organizationDialogTitle = computed(() => isInboxColorEditor.value ? '编辑“收集箱”' : `${organizationDialog.item ? "编辑" : "新建"}${({ list: "清单", tag: "标签", filter: "筛选器", group: "文件夹" })[organizationDialog.kind] || "项目"}`);
const shortcutViews = [
  { key: "this-week", label: "本周", to: "/tasks/shortcut/this-week", icon: CalendarClock },
  { key: "next-week", label: "下周", to: "/tasks/shortcut/next-week", icon: CalendarClock },
  { key: "no-date", label: "无日期任务", to: "/tasks/shortcut/no-date", icon: ListFilter },
  { key: "no-tag", label: "无标签", to: "/tasks/shortcut/no-tag", icon: ListFilter },
];
const pinnedShortcutViews = computed(() => shortcutViews.filter(item => !isHiddenObject("system", item) && isPinnedObject("system", item)));
const visibleShortcutViews = computed(() => shortcutViews.filter(item => !isHiddenObject("system", item) && !isPinnedObject("system", item)));
const pinnedBottomViews = computed(() => [completedView, abandonedView, trashView, archivedView].filter(item => !isHiddenObject("system", item) && isPinnedObject("system", item)));
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
const filterPriorityOptions = [
  { value: "HIGH", label: "高" },
  { value: "MEDIUM", label: "中" },
  { value: "LOW", label: "低" },
  { value: "NONE", label: "无" },
];
const title = computed(() =>
  route.name === "tasks-today"
    ? "今天"
    : route.name === "tasks-tomorrow"
      ? "明天"
      : route.name === "tasks-next7"
        ? "最近 7 天"
        : route.name === "tasks-abandoned"
          ? "已放弃"
        : route.name === "tasks-inbox"
          ? "收集箱"
          : route.name === "tasks-list"
            ? listName(route.params.publicId)
            : route.name === "tasks-group"
              ? store.listGroups.find(group => group.publicId === route.params.publicId)?.name || "文件夹"
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
const miniMonthLabel = computed(() => new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long" }).format(miniCalendarMonth.value));
const miniCalendarRange = computed(() => {
  const first = new Date(miniCalendarMonth.value.getFullYear(), miniCalendarMonth.value.getMonth(), 1);
  const mondayOffset = (first.getDay() + 6) % 7;
  const from = addDays(first, -mondayOffset);
  return { from, to: addDays(from, 42) };
});
const miniCalendarDays = computed(() => {
  const today = dateKey(new Date());
  const selected = dateKey(selectedDate.value);
  const currentMonth = miniCalendarMonth.value.getMonth();
  const lunarByDate = new Map((miniCalendarResponse.value.lunar || []).map(item => [item.date, item]));
  const holidayByDate = new Map((miniCalendarResponse.value.holidays || []).map(item => [item.date, item]));
  const taskById = new Map([...store.tasks, ...(miniCalendarResponse.value.tasks || [])].map(task => [task.publicId, task]));
  const taskDates = new Set([...taskById.values()].filter(task => !task.deleted && task.status !== "COMPLETED").flatMap(task => [task.startAt, task.dueAt].filter(Boolean).map(value => dateKey(new Date(value)))));
  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(miniCalendarRange.value.from, index);
    const key = dateKey(date);
    const lunar = lunarByDate.get(key);
    return { date, key, otherMonth: date.getMonth() !== currentMonth, today: key === today, selected: key === selected, hasTask: taskDates.has(key), holiday: holidayByDate.get(key) || null, lunar: lunar ? (lunar.festival || lunar.solarTerm || lunarDayLabel(lunar.lunarDate) || "") : "" };
  });
});
function lunarDayLabel(value) {
  if (!value) return "";
  const text = String(value).trim();
  if (/^(?:闰)?(?:正|冬|腊|[一二三四五六七八九十]{1,3}|\d{1,2})月/.test(text)) return text.replace(/^(?:闰)?(?:正|冬|腊|[一二三四五六七八九十]{1,3}|\d{1,2})月/, "");
  return text;
}
const visibleMiniCalendarDays = computed(() => miniCalendarExpanded.value ? miniCalendarDays.value : miniCalendarDays.value.filter(cell => {
  const selected = selectedDate.value;
  const month = miniCalendarMonth.value;
  const anchor = selected.getFullYear() === month.getFullYear() && selected.getMonth() === month.getMonth()
    ? selected
    : new Date(month.getFullYear(), month.getMonth(), 1);
  const weekday = (anchor.getDay() + 6) % 7;
  const start = addDays(anchor, -weekday);
  return cell.key >= dateKey(start) && cell.key < dateKey(addDays(start, 7));
}));
function miniCalendarAriaLabel(cell) { return `${cell.date.getMonth() + 1}月${cell.date.getDate()}日${cell.lunar ? `，${cell.lunar}` : ""}${cell.holiday ? `，${cell.holiday.name}${cell.holiday.off ? "休息" : "调休上班"}` : ""}${cell.hasTask ? "，有待办任务" : ""}`; }
function moveMiniMonth(offset) { miniCalendarMonth.value = new Date(miniCalendarMonth.value.getFullYear(), miniCalendarMonth.value.getMonth() + offset, 1); }
function goMiniCalendarToday() { const today = new Date(); miniCalendarMonth.value = new Date(today.getFullYear(), today.getMonth(), 1); router.push({ path: "/tasks/today", query: { date: dateKey(today) } }); }
async function loadMiniCalendar() {
  const request = ++miniCalendarRequest;
  miniCalendarError.value = "";
  try {
    const { from, to } = miniCalendarRange.value;
    const result = await apiGetTaskCalendar({ from: dateKey(from), to: dateKey(to), layers: "holiday", lunar: 1 });
    if (request === miniCalendarRequest) miniCalendarResponse.value = result || { tasks: [], lunar: [], holidays: [] };
  } catch (error) {
    if (request === miniCalendarRequest) {
      miniCalendarResponse.value = { tasks: [], lunar: [], holidays: [] };
      miniCalendarError.value = error?.response?.data?.detail || "月历信息暂不可用";
    }
  }
}
watch(miniCalendarMonth, loadMiniCalendar);
watch(selectedDate, (value) => { miniCalendarMonth.value = new Date(value.getFullYear(), value.getMonth(), 1); });
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
        : route.name === "tasks-abandoned"
          ? task.status === "ABANDONED"
        : route.params.shortcut === "archived"
          ? archivedLists.value.some((list) => list.publicId === task.listId)
          : task.status !== "ABANDONED" && (!hideCompleted.value || task.status !== "COMPLETED")),
  ),
);
const visibleTasks = computed(() => {
  let values = [...sourceTasks.value];
  const today = startOfDay(new Date());
  const tomorrow = addDays(today, 1);
  const end = addDays(today, 8);
  if (route.name === "tasks-completed")
    values = values.filter((task) => task.status === "COMPLETED");
  else if (route.name === "tasks-abandoned")
    values = values.filter((task) => task.status === "ABANDONED");
  else if (route.name === "tasks-list")
    values = values.filter((task) => task.listId === route.params.publicId);
  else if (route.name === "tasks-group")
    values = values.filter((task) => store.lists.some(list => list.publicId === task.listId && list.groupId === route.params.publicId));
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
  visibleTasks.value.filter((task) => !["COMPLETED", "ABANDONED"].includes(task.status)),
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
  const list = store.lists.find((item) => item.publicId === id);
  return list?.systemKey === "INBOX" ? "收集箱" : list?.name || "收集箱";
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
function recurrenceValue(value) {
  return recurrenceOptions.some((option) => option.value === value) ? value : value ? "CUSTOM" : "";
}
function itemCount(key) {
  return sidebarTaskCounts.value.counts[key] ?? "";
}
function isActive(to) {
  return route.path === to || route.path.startsWith(`${to}/`);
}
function toggleSidebar() {
  sidebarHidden.value = !sidebarHidden.value;
}
function startSidebarSwipe(event) {
  const touch = event.changedTouches?.[0];
  if (touch) sidebarTouchStart = { x: touch.clientX, y: touch.clientY };
}
function finishSidebarSwipe(event) {
  const start = sidebarTouchStart;
  sidebarTouchStart = null;
  const touch = event.changedTouches?.[0];
  if (!start || !touch || !isMobileSidebar.value) return;
  const dx = touch.clientX - start.x;
  const dy = Math.abs(touch.clientY - start.y);
  if (dy > 70 || Math.abs(dx) < 70) return;
  if (sidebarHidden.value && start.x < 84 && dx > 0) sidebarHidden.value = false;
  else if (!sidebarHidden.value && dx < 0) sidebarHidden.value = true;
}
function toggleDatePopover() {
  datePopoverOpen.value = !datePopoverOpen.value;
  repeatPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  if (datePopoverOpen.value) datePopoverDue.value = toLocal(selectedTask.value?.dueAt);
}
function toggleRepeatPopover() {
  repeatPopoverOpen.value = !repeatPopoverOpen.value;
  datePopoverOpen.value = false;
  reminderPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
}
function togglePriorityPopover() {
  priorityPopoverOpen.value = !priorityPopoverOpen.value;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
}
async function applyTaskDueOffset(offset) {
  if (editorOpen.value) {
    draft.dueAt = offset === null ? "" : toLocal(addDays(startOfDay(new Date()), offset));
    return;
  }
  if (!selectedTask.value) return;
  try {
    selectedTask.value = await store.update(selectedTask.value, { dueAt: offset === null ? "" : addDays(startOfDay(new Date()), offset).toISOString() });
    store.error = "";
    datePopoverDue.value = toLocal(selectedTask.value.dueAt);
    datePopoverOpen.value = false;
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "日期保存失败"; }
}
async function saveTaskDueDate() {
  if (!selectedTask.value || dateSaving.value) return;
  dateSaving.value = true;
  try {
    selectedTask.value = await store.update(selectedTask.value, { dueAt: datePopoverDue.value ? new Date(datePopoverDue.value).toISOString() : "" });
    store.error = "";
    datePopoverOpen.value = false;
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "日期保存失败"; }
  finally { dateSaving.value = false; }
}
async function selectTaskRepeat(value) {
  if (value === "CUSTOM" && !editorOpen.value) {
    const task = selectedTask.value;
    openEdit(task);
    draft.rrule = "CUSTOM";
    draft.customRrule = recurrenceValue(task?.rrule) === "CUSTOM" ? task.rrule : "";
    repeatPopoverOpen.value = false;
    return;
  }
  if (editorOpen.value) draft.rrule = value;
  else await setTaskRepeat(value);
  repeatPopoverOpen.value = false;
}
async function toggleReminderPopover() {
  reminderPopoverOpen.value = !reminderPopoverOpen.value;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  const loadSequence = ++reminderLoadSequence;
  if (!reminderPopoverOpen.value || editorOpen.value) return;
  Object.assign(quickReminder, { enabled: false, kind: "RELATIVE", offsetMinutes: 30, remindLocal: "", dailyUntilDone: false, reminder: null });
  const task = selectedTask.value;
  if (!task?.publicId) return;
  if (!store.online) { store.error = "提醒需要联网读取和保存"; return; }
  reminderLoading.value = true;
  try {
    const reminders = await apiListTaskReminders(task.publicId);
    if (loadSequence !== reminderLoadSequence || selectedTask.value?.publicId !== task.publicId || !reminderPopoverOpen.value) return;
    const reminder = reminders?.[0] || null;
    quickReminder.reminder = reminder;
    if (reminder) Object.assign(quickReminder, {
      enabled: true,
      kind: reminder.kind || "RELATIVE",
      offsetMinutes: reminder.offsetMinutes ?? 30,
      remindLocal: reminder.remindAt ? toLocal(reminder.remindAt) : "",
      dailyUntilDone: Boolean(reminder.dailyUntilDone),
    });
  } catch (error) {
    if (loadSequence === reminderLoadSequence) store.error = error?.response?.data?.detail || error?.message || "提醒读取失败";
  } finally {
    if (loadSequence === reminderLoadSequence) reminderLoading.value = false;
  }
}
async function saveTaskReminder() {
  const task = selectedTask.value;
  if (!task?.publicId || reminderSaving.value) return;
  if (!store.online) { store.error = "提醒需要联网保存"; return; }
  if (quickReminder.enabled && quickReminder.kind === "RELATIVE" && !task.dueAt) { store.error = "相对提醒需要先设置任务截止时间"; return; }
  if (quickReminder.enabled && quickReminder.kind === "ABSOLUTE" && !quickReminder.remindLocal) { store.error = "请设置提醒时间"; return; }
  reminderSaving.value = true;
  try {
    if (!quickReminder.enabled) {
      if (quickReminder.reminder) await apiDeleteTaskReminder(task.publicId, quickReminder.reminder.publicId, quickReminder.reminder.revision);
      quickReminder.reminder = null;
    } else {
      const payload = {
        kind: quickReminder.kind,
        offsetMinutes: quickReminder.kind === "RELATIVE" ? Number(quickReminder.offsetMinutes) : null,
        remindAt: quickReminder.kind === "ABSOLUTE" ? new Date(quickReminder.remindLocal).toISOString() : null,
        channel: "IN_APP",
        dailyUntilDone: quickReminder.dailyUntilDone,
      };
      quickReminder.reminder = quickReminder.reminder
        ? await apiUpdateTaskReminder(task.publicId, quickReminder.reminder.publicId, payload, quickReminder.reminder.revision)
        : await apiCreateTaskReminder(task.publicId, payload);
    }
    store.error = "";
    reminderPopoverOpen.value = false;
    await loadActivity(task);
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "提醒保存失败"; }
  finally { reminderSaving.value = false; }
}
async function disableTaskReminder() {
  quickReminder.enabled = false;
  await saveTaskReminder();
}
function detailWidthKey() {
  return `task_detail_width:${store.accountScope || "anonymous"}`;
}
function loadDetailWidth() {
  const scope = store.accountScope || "anonymous";
  detailPreferenceScope.value = scope;
  detailWidth.value = 390;
  try {
    const value = Number(localStorage.getItem(detailWidthKey()));
    if (Number.isFinite(value) && value >= 320 && value <= 560) detailWidth.value = value;
  } catch {}
}
function persistDetailWidth() {
  if (detailPreferenceScope.value !== (store.accountScope || "anonymous")) return;
  try { localStorage.setItem(detailWidthKey(), String(detailWidth.value)); } catch {}
}
function startDetailResize(event) {
  if (window.innerWidth < 1280 || event.button !== 0) return;
  detailResizeState = { startX: event.clientX, startWidth: detailWidth.value };
  detailPreviousUserSelect = document.body.style.userSelect;
  event.currentTarget.setPointerCapture?.(event.pointerId);
  document.body.style.userSelect = "none";
  window.addEventListener("pointermove", updateDetailResize);
  window.addEventListener("pointerup", stopDetailResize, { once: true });
  window.addEventListener("pointercancel", stopDetailResize, { once: true });
  event.preventDefault();
}
function updateDetailResize(event) {
  if (!detailResizeState) return;
  detailWidth.value = Math.min(560, Math.max(320, detailResizeState.startWidth - (event.clientX - detailResizeState.startX)));
}
function stopDetailResize() {
  if (!detailResizeState) return;
  detailResizeState = null;
  document.body.style.userSelect = detailPreviousUserSelect;
  window.removeEventListener("pointermove", updateDetailResize);
  window.removeEventListener("pointercancel", stopDetailResize);
  persistDetailWidth();
}
function resetDetailWidth() {
  detailWidth.value = 390;
  persistDetailWidth();
}
function resizeDetailByKey(event) {
  if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
  event.preventDefault();
  const step = event.shiftKey ? 48 : 16;
  detailWidth.value = Math.min(560, Math.max(320, detailWidth.value + (event.key === 'ArrowLeft' ? step : -step)));
  persistDetailWidth();
}
function sidebarPreferenceKey() {
  const device = window.matchMedia("(max-width: 900px)").matches ? "mobile" : "desktop";
  const base = `task_sidebar_collapsed:${store.accountScope || "anonymous"}`;
  return device === "mobile" ? `${base}:mobile` : base;
}
function persistSidebarVisibility() {
  if (sidebarPreferenceScope.value !== store.accountScope) return;
  if (window.matchMedia("(max-width: 900px)").matches) return;
  try { localStorage.setItem(sidebarPreferenceKey(), JSON.stringify(sidebarHidden.value)); } catch {}
}
function loadSidebarVisibility() {
  const scope = store.accountScope || "anonymous";
  sidebarPreferenceScope.value = scope;
  isMobileSidebar.value = window.matchMedia("(max-width: 900px)").matches;
  if (isMobileSidebar.value) {
    sidebarHidden.value = true;
    return;
  }
  try {
    const saved = localStorage.getItem(sidebarPreferenceKey());
    sidebarHidden.value = saved === null
      ? window.matchMedia("(max-width: 900px)").matches
      : JSON.parse(saved);
  } catch {
    sidebarHidden.value = window.matchMedia("(max-width: 900px)").matches;
  }
}
function handleSidebarViewportChange(event) {
  isMobileSidebar.value = event.matches;
  loadSidebarVisibility();
}
function handleSidebarClick(event) {
  if (window.matchMedia("(max-width: 900px)").matches && event.target.closest("a[href]")) sidebarHidden.value = true;
}
watch(sidebarHidden, persistSidebarVisibility);
watch(isMobileSidebar, (mobile) => { if (mobile) sidebarHidden.value = true; }, { immediate: true });
watch(() => store.accountScope, () => loadSidebarVisibility());
watch(() => store.accountScope, () => loadDetailWidth());
function toggleSection(key) {
  sections[key] = !sections[key];
  persistSidebarSections();
}
function closeContext() {
  context.open = false;
  context.task = null;
  contextSubmenu.value = "";
  organizationMenuOpen.value = false;
  sectionDisplayMenu.value = "";
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
function dragChecklistItem(index, event) {
  checklistDragIndex.value = index;
  event.dataTransfer?.setData("text/plain", String(index));
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
}
function moveChecklistItem(from, offset) {
  const to = from + offset;
  if (to < 0 || to >= draft.checklistItems.length) return;
  draft.checklistItems = reorderChecklistItems(draft.checklistItems, from, to);
  checklistDropTarget.value = null;
}
function dropChecklistItem(target) {
  const from = checklistDragIndex.value;
  checklistDragIndex.value = null;
  checklistDropTarget.value = null;
  if (from === null || from === target) return;
  draft.checklistItems = reorderChecklistItems(draft.checklistItems, from, target);
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
  reminderLoadSequence += 1;
  closeAttributePopovers();
  Object.assign(quickReminder, { enabled: false, kind: "RELATIVE", offsetMinutes: 30, remindLocal: "", dailyUntilDone: false, reminder: null });
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
  reminderLoadSequence += 1;
  closeAttributePopovers();
  Object.assign(quickReminder, { enabled: false, kind: "RELATIVE", offsetMinutes: 30, remindLocal: "", dailyUntilDone: false, reminder: null });
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
    dailyUntilDone: false,
    reminder: null,
    checklistText: (task.checklist || []).map((item) => item.title).join("\n"),
    checklistItems: (task.checklist || []).map((item, index) => ({ id: item.publicId || `item-${index}`, title: item.title || "", completed: Boolean(item.completed) })),
  });
  editorOpen.value = true;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
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
          dailyUntilDone: Boolean(reminder.dailyUntilDone),
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
    dailyUntilDone: false,
    reminder: null,
    checklistText: "",
    checklistItems: [],
  });
  editorOpen.value = true;
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
}
function createSubtask(task) {
  if (!task) return;
  openCreate(task.publicId);
  draft.listId = task.listId || store.inbox?.publicId || "";
}
function closeDetail() {
  reminderLoadSequence += 1;
  selectedTask.value = null;
  editing.value = null;
  editorOpen.value = false;
  inlineTitleEditing.value = false;
  inlineDescriptionEditing.value = false;
  resetCollaboration();
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
}
function closeAttributePopovers() {
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
}
function toLocal(value) {
  if (!value) return "";
  const date = new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
async function quickAdd() {
  const text = quickTitle.value.trim();
  if (!text || quickParsing.value) return;
  quickParsing.value = true;
  quickParseError.value = "";
  try {
    const parsed = await apiParseTaskQuickAdd(text);
    const fallbackListId = route.name === "tasks-list" ? route.params.publicId : store.inbox?.publicId || "";
    const list = store.lists.find((item) => item.name.toLocaleLowerCase() === String(parsed.listName || "").toLocaleLowerCase());
    quickDraft.value = {
      ...parsed,
      title: parsed.title || text,
      priority: parsed.priority || "NONE",
      listId: list?.publicId || fallbackListId,
      tagIds: store.tags.filter((tag) => parsed.tagNames?.some((name) => name.toLocaleLowerCase() === tag.name.toLocaleLowerCase())).map((tag) => tag.publicId),
    };
    quickDraftDue.value = parsed.dueAt ? toLocal(parsed.dueAt) : route.name === "tasks-today" ? toLocal(new Date()) : "";
  } catch (error) {
    quickParseError.value = error?.response?.data?.detail || error?.message || "解析快速添加内容失败";
  } finally {
    quickParsing.value = false;
  }
}
function cancelQuickDraft() {
  quickDraft.value = null;
  quickDraftDue.value = "";
}
async function confirmQuickAdd() {
  if (!quickDraft.value?.title?.trim() || quickSaving.value) return;
  quickSaving.value = true;
  quickParseError.value = "";
  try {
    const saved = await store.create({
      title: quickDraft.value.title.trim(),
      description: "",
      listId: quickDraft.value.listId || store.inbox?.publicId || "",
      priority: quickDraft.value.priority || "NONE",
      startAt: "",
      dueAt: quickDraftDue.value ? new Date(quickDraftDue.value).toISOString() : "",
      durationMinutes: 0,
      allDay: false,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      rrule: quickDraft.value.rrule || "",
      recurrenceAnchor: quickDraft.value.rrule ? "DUE_DATE" : "",
      tagIds: [...quickDraft.value.tagIds],
      checklist: [],
    });
    quickTitle.value = "";
    cancelQuickDraft();
    if (saved?.publicId) openDetail(saved);
  } catch (error) {
    quickParseError.value = error?.response?.data?.detail || error?.message || "新增任务失败";
  } finally {
    quickSaving.value = false;
  }
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
        dailyUntilDone: draft.dailyUntilDone,
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
async function restoreAbandoned(task) {
  if (!task || task.status !== "ABANDONED") return;
  await store.reopen(task);
  selectedTask.value = store.tasks.find(item => item.publicId === task.publicId) || null;
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
function listsInGroup(groupId) { return sidebarLists.value.filter(item => item.groupId === groupId && !isHiddenObject("list", item) && !isPinnedObject("list", item)).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)); }
function persistSidebarSections() { try { localStorage.setItem(`task_sidebar_sections:${store.accountScope || "anonymous"}`, JSON.stringify(sections)); } catch {} sidebarPrefsMutationVersion += 1; scheduleSidebarPreferencesSave(); }
function loadSidebarSections() { try { Object.assign(sections, JSON.parse(localStorage.getItem(`task_sidebar_sections:${store.accountScope || "anonymous"}`) || "{}")); } catch {} }
function loadSectionDisplayModes() {
  try {
    Object.assign(sectionDisplayModes, { filters: "SHOW", tags: "SHOW" }, JSON.parse(localStorage.getItem(`task_sidebar_section_display:${store.accountScope || "anonymous"}`) || "{}"));
  } catch {
    Object.assign(sectionDisplayModes, { filters: "SHOW", tags: "SHOW" });
  }
}
function sectionDisplayOptions(section) {
  if (section === "tags") return [
    { value: "WHEN_HAS_CONTENT", label: "显示（非空标签）" },
    { value: "SHOW", label: "显示" },
    { value: "HIDE", label: "隐藏" },
  ];
  return [{ value: "SHOW", label: "显示" }, { value: "HIDE", label: "隐藏" }];
}
function isSectionDisplayed(section) {
  const mode = sectionDisplayModes[section];
  if (mode === "HIDE") return false;
  if (mode === "WHEN_HAS_CONTENT") return store.tags.some(tag => tagOpenCount(tag.publicId) > 0);
  return true;
}
function toggleSectionDisplayMenu(section) { sectionDisplayMenu.value = sectionDisplayMenu.value === section ? "" : section; }
function setSectionDisplayMode(section, mode) {
  if (!sectionDisplayOptions(section).some(option => option.value === mode)) return;
  sectionDisplayModes[section] = mode;
  try { localStorage.setItem(`task_sidebar_section_display:${store.accountScope || "anonymous"}`, JSON.stringify(sectionDisplayModes)); } catch {}
  sectionDisplayMenu.value = "";
}
function sidebarObjectPrefsKey() { return `task_sidebar_objects:${store.accountScope || "anonymous"}`; }
function persistSidebarObjectPrefs() { try { localStorage.setItem(sidebarObjectPrefsKey(), JSON.stringify(sidebarObjectPrefs)); } catch {} sidebarPrefsMutationVersion += 1; scheduleSidebarPreferencesSave(); }
function loadSidebarObjectPrefs() {
  const empty = { pinnedSystem: [], hiddenSystem: [], conditionalSystem: [], pinnedLists: [], hiddenLists: [], pinnedTags: [], hiddenTags: [], pinnedFilters: [], hiddenFilters: [], displayModes: {} };
  try { Object.assign(sidebarObjectPrefs, empty, JSON.parse(localStorage.getItem(sidebarObjectPrefsKey()) || "{}")); }
  catch { Object.assign(sidebarObjectPrefs, empty); }
}
function sidebarPreferencesPayload() {
  const collapsedSections = Object.fromEntries(Object.entries(sections).map(([key, expanded]) => [key, !expanded]));
  return {
    hiddenSystemViews: [...sidebarObjectPrefs.hiddenSystem], pinnedSystemViews: [...sidebarObjectPrefs.pinnedSystem],
    conditionalSystemViews: [...sidebarObjectPrefs.conditionalSystem],
    hiddenListIds: [...sidebarObjectPrefs.hiddenLists], pinnedListIds: [...sidebarObjectPrefs.pinnedLists],
    hiddenTagIds: [...sidebarObjectPrefs.hiddenTags], pinnedTagIds: [...sidebarObjectPrefs.pinnedTags],
    hiddenFilterIds: [...sidebarObjectPrefs.hiddenFilters], pinnedFilterIds: [...sidebarObjectPrefs.pinnedFilters],
    collapsedSections,
  };
}
function applySidebarPreferences(preferences) {
  sidebarPrefsRevision.value = Number(preferences?.revision || 0);
  Object.assign(sidebarObjectPrefs, {
    hiddenSystem: preferences?.hiddenSystemViews || [], pinnedSystem: preferences?.pinnedSystemViews || [],
    conditionalSystem: preferences?.conditionalSystemViews || [],
    hiddenLists: preferences?.hiddenListIds || [], pinnedLists: preferences?.pinnedListIds || [],
    hiddenTags: preferences?.hiddenTagIds || [], pinnedTags: preferences?.pinnedTagIds || [],
    hiddenFilters: preferences?.hiddenFilterIds || [], pinnedFilters: preferences?.pinnedFilterIds || [], displayModes: {},
  });
  for (const [key, collapsed] of Object.entries(preferences?.collapsedSections || {})) if (key in sections) sections[key] = !collapsed;
  sidebarPrefsScope = store.accountScope;
  persistLocalSidebarPreferences();
}
function persistLocalSidebarPreferences() {
  try { localStorage.setItem(sidebarObjectPrefsKey(), JSON.stringify(sidebarObjectPrefs)); } catch {}
  try { localStorage.setItem(`task_sidebar_sections:${store.accountScope || "anonymous"}`, JSON.stringify(sections)); } catch {}
  persistListGroupState();
}
function scheduleSidebarPreferencesSave() {
  if (!store.accountScope || !store.online) return;
  if (sidebarPrefsLoading || sidebarPrefsScope !== store.accountScope) {
    sidebarPrefsPendingSave = true;
    return;
  }
  sidebarPrefsPendingSave = false;
  clearTimeout(sidebarPrefsSaveTimer);
  sidebarPrefsSaveTimer = setTimeout(() => {
    const expectedScope = store.accountScope;
    sidebarPrefsSave = sidebarPrefsSave.catch(() => {}).then(async () => {
      if (expectedScope !== store.accountScope) return;
      const payload = sidebarPreferencesPayload();
      try {
        const saved = await apiUpdateTaskSidebarPreferences(payload, sidebarPrefsRevision.value);
        if (expectedScope === store.accountScope) applySidebarPreferences(saved);
      } catch (error) {
        if (error?.response?.status === 409 || error?.response?.status === 412) {
          try {
            const latest = await apiGetTaskSidebarPreferences();
            if (expectedScope !== store.accountScope) return;
            sidebarPrefsRevision.value = Number(latest?.revision || 0);
            const saved = await apiUpdateTaskSidebarPreferences(payload, sidebarPrefsRevision.value);
            if (expectedScope === store.accountScope) applySidebarPreferences(saved);
            return;
          } catch (retryError) { error = retryError; }
        }
        if (expectedScope === store.accountScope) store.error = error?.response?.data?.detail || error?.message || "侧栏偏好同步失败，当前设备设置仍保留";
      }
    });
  }, 180);
}
async function loadSidebarPreferences(filtersReady = Promise.resolve()) {
  const scope = store.accountScope;
  sidebarPrefsScope = scope;
  if (!scope || !store.online) return loadSidebarObjectPrefs();
  const mutationVersion = sidebarPrefsMutationVersion;
  const localSnapshot = sidebarPreferencesPayload();
  sidebarPrefsLoading = true;
  try {
    const saved = await apiGetTaskSidebarPreferences();
    if (scope !== store.accountScope) return;
    const serverHasPreferences = [saved?.hiddenSystemViews, saved?.pinnedSystemViews, saved?.conditionalSystemViews, saved?.hiddenListIds, saved?.pinnedListIds,
      saved?.hiddenTagIds, saved?.pinnedTagIds, saved?.hiddenFilterIds, saved?.pinnedFilterIds].some(values => values?.length)
      || Object.keys(saved?.collapsedSections || {}).length > 0;
    if (!serverHasPreferences && Number(saved?.revision) <= 1) {
      await filtersReady;
      if (scope !== store.accountScope) return;
      const legacy = sidebarPreferencesPayload();
      legacy.pinnedFilterIds = [...new Set([...legacy.pinnedFilterIds, ...filters.value.filter(item => item.pinned).map(item => item.publicId)])];
      const migrated = await apiUpdateTaskSidebarPreferences(legacy, Number(saved?.revision || 0));
      if (scope === store.accountScope) {
        applySidebarPreferences(migrated);
        sidebarPrefsPendingSave = false;
      }
      return;
    }
    const changedDuringLoad = mutationVersion !== sidebarPrefsMutationVersion;
    const preferences = changedDuringLoad
      ? mergeSidebarPreferenceChanges(saved, localSnapshot, sidebarPreferencesPayload())
      : saved;
    applySidebarPreferences(preferences);
    if (changedDuringLoad || sidebarPrefsPendingSave) scheduleSidebarPreferencesSave();
  } catch (error) {
    loadSidebarObjectPrefs();
    loadSidebarSections();
    store.error = error?.response?.data?.detail || error?.message || "侧栏偏好读取失败，使用当前设备缓存";
  } finally {
    if (scope === store.accountScope) {
      sidebarPrefsLoading = false;
      if (sidebarPrefsPendingSave && sidebarPrefsScope === scope) scheduleSidebarPreferencesSave();
    }
  }
}
function objectPreferenceKey(kind, pinned = false) {
  return ({ system: pinned ? "pinnedSystem" : "hiddenSystem", list: pinned ? "pinnedLists" : "hiddenLists", tag: pinned ? "pinnedTags" : "hiddenTags", filter: "hiddenFilters" })[kind];
}
function objectIdentity(kind, item) { return kind === "system" ? item.key : item.publicId; }
function displayOptionKey(kind, item) { return `${kind}:${objectIdentity(kind, item)}`; }
function displayOptionsFor(kind, item) {
  const options = [{ value: "SHOW", label: "显示" }, { value: "HIDE", label: "隐藏" }];
  if (kind === "system" && item?.key === "abandoned") options.push({ value: "WHEN_HAS_CONTENT", label: "有内容时显示" });
  else if (kind === "system" && !["summary", "completed", "trash"].includes(item?.key)) options.push({ value: "WHEN_HAS_CONTENT", label: "有内容时显示" });
  return options;
}
function getDisplayOption(kind, item) {
  if (kind === "system" && sidebarObjectPrefs.conditionalSystem.includes(item?.key)) return "WHEN_HAS_CONTENT";
  if (isHiddenObject(kind, item)) return "HIDE";
  return "SHOW";
}
function setDisplayOption(kind, item, mode) {
  const value = displayOptionsFor(kind, item).some(option => option.value === mode) ? mode : "SHOW";
  const id = objectIdentity(kind, item), hiddenKey = objectPreferenceKey(kind, false), pinnedKey = objectPreferenceKey(kind, true);
  if (kind === "system") sidebarObjectPrefs.conditionalSystem = value === "WHEN_HAS_CONTENT"
    ? [...new Set([...sidebarObjectPrefs.conditionalSystem, id])]
    : sidebarObjectPrefs.conditionalSystem.filter(key => key !== id);
  sidebarObjectPrefs[hiddenKey] = value === "HIDE" ? [...new Set([...sidebarObjectPrefs[hiddenKey], id])] : sidebarObjectPrefs[hiddenKey].filter(key => key !== id);
  if (pinnedKey && value !== "SHOW") sidebarObjectPrefs[pinnedKey] = sidebarObjectPrefs[pinnedKey].filter(key => key !== id);
  persistSidebarObjectPrefs();
  organizationMenuOpen.value = false;
}
function isPinnedObject(kind, item) {
  if (!item) return false;
  if (kind === "filter") return Boolean(sidebarObjectPrefs.pinnedFilters.includes(item.publicId));
  if (kind === "group") return Boolean(item.pinned);
  const key = objectPreferenceKey(kind, true);
  return Boolean(key && sidebarObjectPrefs[key].includes(objectIdentity(kind, item)));
}
function isHiddenObject(kind, item) {
  if (!item) return false;
  const key = objectPreferenceKey(kind, false);
  if (key && sidebarObjectPrefs[key].includes(objectIdentity(kind, item))) return true;
  if (kind === "system" && sidebarObjectPrefs.conditionalSystem.includes(item.key)) return Number(itemCount(item.key)) <= 0;
  return false;
}
function setObjectVisibility(kind, item, visible) {
  const key = objectPreferenceKey(kind, false), id = objectIdentity(kind, item);
  sidebarObjectPrefs[key] = visible ? sidebarObjectPrefs[key].filter(value => value !== id) : [...new Set([...sidebarObjectPrefs[key], id])];
  if (kind === "system") sidebarObjectPrefs.conditionalSystem = sidebarObjectPrefs.conditionalSystem.filter(value => value !== id);
  sidebarObjectPrefs.displayModes[displayOptionKey(kind, item)] = visible ? "SHOW" : "HIDE";
  persistSidebarObjectPrefs();
}
function toggleHiddenObject(kind, item) {
  setObjectVisibility(kind, item, isHiddenObject(kind, item));
  organizationMenuOpen.value = false;
}
function setFilterPinned(item, pinned) {
  organizationMenuOpen.value = false;
  return updateFilterPinned(item, pinned);
}
async function updateFilterPinned(item, pinned) {
  sidebarObjectPrefs.pinnedFilters = pinned ? [...new Set([...sidebarObjectPrefs.pinnedFilters, item.publicId])] : sidebarObjectPrefs.pinnedFilters.filter(value => value !== item.publicId);
  persistSidebarObjectPrefs();
}
function togglePinnedObject(kind, item) {
  if (kind === "group") {
    store.updateListGroup(item, { name: item.name, color: item.color, icon: item.icon, sortOrder: item.sortOrder, pinned: !item.pinned }).catch(error => { store.error = error?.response?.data?.detail || error?.message || "更新分组置顶失败"; });
    organizationMenuOpen.value = false;
    return;
  }
  if (kind === "filter") return updateFilterPinned(item, !isPinnedObject(kind, item));
  const key = objectPreferenceKey(kind, true), id = objectIdentity(kind, item);
  if (!key) return;
  const pinned = !isPinnedObject(kind, item);
  sidebarObjectPrefs[key] = pinned ? [...sidebarObjectPrefs[key], id] : sidebarObjectPrefs[key].filter(value => value !== id);
  if (isHiddenObject(kind, item)) sidebarObjectPrefs[objectPreferenceKey(kind, false)] = sidebarObjectPrefs[objectPreferenceKey(kind, false)].filter(value => value !== id);
  persistSidebarObjectPrefs();
  organizationMenuOpen.value = false;
}
watch(() => store.accountScope, () => { loadSidebarObjectPrefs(); loadSidebarSections(); loadSectionDisplayModes(); void loadSidebarPreferences(); });
function openOrganizationDialog(kind, item = null, overrides = {}) {
  const defaults = { list: { color: "#2563eb", icon: "", groupId: "" }, tag: { color: "#2f746f", parentId: "" }, filter: {}, group: { color: "#64748b", icon: "📁" } }[kind] || {};
  const filter = kind === "filter" ? { mode: item?.mode || "BASIC", listIds: [...(item?.listIds || [])], tagIds: [...(item?.tagIds || [])], tagMode: item?.tagMode || (item?.tagIds?.length ? "SELECTED" : "ALL"), taskTypes: [...(item?.taskTypes || [])], priorities: [...(item?.priorities || [])], status: item?.status || "OPEN", dueRange: item?.dueRange || "ANY", dueStart: item?.dueStart || "", dueEnd: item?.dueEnd || "", keyword: item?.keyword || "", includeSubtasks: item?.includeSubtasks !== false, matchAllTags: Boolean(item?.matchAllTags), pinned: isPinnedObject("filter", item), rules: item?.rules || null, advanced: advancedFilterConditionsFromRules(item?.rules), previewed: false } : {};
  Object.assign(organizationDialog, { open: true, kind, item, name: item?.name || overrides.name || "", color: item?.color || defaults.color || "#2563eb", icon: item?.icon || defaults.icon || "", parentId: item?.parentId || overrides.parentId || defaults.parentId || "", groupId: item?.groupId || overrides.groupId || "", newGroupName: "", newGroupIcon: "📁", newGroupColor: "#64748b", groupListIds: [...(overrides.groupListIds || [])], viewType: item?.viewType || "LIST", objectType: item?.objectType || "TASK", showInSmartLists: item?.showInSmartLists !== false, pinned: Boolean(item?.pinned), filter });
  filterPreview.value = [];
  organizationDialogOpen.value = true;
  organizationMenuOpen.value = false;
}
function handleListFolderChange(value) {
  if (value === "__CREATE_GROUP__") {
    organizationDialog.newGroupName = "";
    organizationDialog.newGroupIcon = "📁";
    folderDialogOpen.value = true;
    return;
  }
  organizationDialog.groupId = value;
}
function stageNewListGroup() {
  if (!organizationDialog.newGroupName.trim()) return;
  organizationDialog.groupId = "__CREATE_GROUP__";
  folderDialogOpen.value = false;
}
function cancelNewListGroup() { folderDialogOpen.value = false; }
function openOrganizationIconPicker(target) {
  organizationIconTarget.value = target;
  organizationIconSearch.value = "";
  organizationIconCategory.value = "all";
  organizationIconPickerOpen.value = true;
}
function selectOrganizationIcon(icon) {
  if (organizationIconTarget.value === "new-group") organizationDialog.newGroupIcon = icon;
  else organizationDialog.icon = icon;
  organizationIconPickerOpen.value = false;
}
function openOrganizationMenu(kind, item, event) {
  const width = 210, height = 230;
  Object.assign(organizationMenu, { open: true, kind, item, x: Math.max(8, Math.min(event.clientX, window.innerWidth - width - 8)), y: Math.max(8, Math.min(event.clientY, window.innerHeight - height - 8)) });
  organizationMenuOpen.value = true;
}
function isInboxColorList(item) { return item?.systemKey === "INBOX"; }
function editInboxFromSystemMenu() {
  const inbox = store.lists.find(item => item.systemKey === "INBOX");
  organizationMenuOpen.value = false;
  if (inbox) openOrganizationDialog("list", inbox);
}
function editOrganization(kind) { openOrganizationDialog(kind, organizationMenu.item); }
function organizationPath(kind, item) {
  if (kind === "system") return item.to;
  if (kind === "list") return item.systemKey === "INBOX" ? "/tasks/inbox" : `/tasks/list/${item.publicId}`;
  if (kind === "tag") return `/tasks/tag/${item.publicId}`;
  if (kind === "filter") return `/tasks/filter/${item.publicId}`;
  return `/tasks/group/${item.publicId}`;
}
async function shareOrganization(kind) {
  const url = new URL(organizationPath(kind, organizationMenu.item), window.location.origin).href;
  organizationMenuOpen.value = false;
  try { await navigator.clipboard.writeText(url); message.success("清单链接已复制"); }
  catch { window.prompt("复制清单链接", url); }
}
function editListGroup() { openOrganizationDialog("group", organizationMenu.item); organizationMenuOpen.value = false; }
function createChildTag() { const tag = organizationMenu.item; openOrganizationDialog("tag", null, { parentId: tag.publicId }); }
function createFilter() { openOrganizationDialog("filter"); }
function openListCopy() {
  const source = organizationMenu.item;
  Object.assign(listCopy, { source, name: `${source.name} 副本`, mode: "ALL_PRESERVE", busy: false });
  organizationMenuOpen.value = false;
  listCopyOpen.value = true;
}
async function confirmListCopy() {
  if (!listCopy.source || listCopy.busy) return;
  listCopy.busy = true;
  try {
    const source = listCopy.source;
    const copiedList = await store.createList({ name: listCopy.name.trim(), color: source.color || "", icon: source.icon || "", archived: false, sortOrder: store.lists.length, groupId: source.groupId || "" });
    const newListId = copiedList?.publicId || copiedList?.id;
    if (!newListId) throw new Error("新清单已创建，但无法取得其标识");
    let copiedCount = 0;
    if (listCopy.mode !== "NONE") {
      const pending = store.tasks.filter(item => !item.deleted && item.listId === source.publicId && (listCopy.mode !== "OPEN_ONLY" || item.status !== "COMPLETED"));
      const mapped = new Map();
      while (pending.length) {
        const index = pending.findIndex(item => !item.parentId || mapped.has(item.parentId));
        if (index < 0) throw new Error("任务父子关系不完整，清单已创建但任务复制未完成");
        const item = pending.splice(index, 1)[0];
        const created = await store.create({ listId: newListId, parentId: item.parentId ? mapped.get(item.parentId) : null,
          title: item.title, description: item.description || "", priority: item.priority || "NONE", startAt: item.startAt,
          dueAt: item.dueAt, allDay: item.allDay, timezone: item.timezone, durationMinutes: item.durationMinutes,
          tagIds: [...(item.tagIds || [])], checklist: (item.checklist || []).map((check, sortOrder) => ({ title: check.title, completed: Boolean(check.completed), sortOrder })),
          rrule: item.rrule || null, recurrenceAnchor: item.recurrenceAnchor || null });
        const createdId = created?.publicId || created?.id;
        if (!createdId) throw new Error(`“${item.title}”已写入本地，但无法建立子任务映射`);
        if (listCopy.mode === "ALL_PRESERVE" && item.status === "COMPLETED") await store.complete(created);
        mapped.set(item.publicId, createdId);
        copiedCount += 1;
      }
    }
    listCopyOpen.value = false;
    await store.refreshServer();
    message.success(listCopy.mode !== "NONE" ? `已复制清单和 ${copiedCount} 条任务` : "已复制清单设置");
  } catch (error) {
    store.error = `${error?.response?.data?.detail || error?.message || "复制清单失败"}；已创建的内容会保留，请检查后再继续`;
  } finally { listCopy.busy = false; }
}
function openTagMerge() {
  Object.assign(tagMerge, { source: organizationMenu.item, targetId: "", busy: false });
  organizationMenuOpen.value = false;
  tagMergeOpen.value = true;
}
async function confirmTagMerge() {
  if (!tagMerge.source || !tagMerge.targetId || tagMerge.busy) return;
  if (!store.online) { store.error = "合并标签需要联网执行"; return; }
  const source = tagMerge.source, target = store.tags.find(item => item.publicId === tagMerge.targetId);
  if (!target) { store.error = "目标标签已不存在，请刷新后重试"; return; }
  tagMerge.busy = true;
  try {
    await store.syncNow();
    const result = await apiMergeTaskTag(source.publicId, target.publicId, target.revision, source.revision);
    tagMergeOpen.value = false;
    await store.refreshServer();
    message.success(`已合并标签，迁移 ${result.movedTasks} 条任务和 ${result.movedChildren} 个子标签`);
  } catch (error) {
    store.error = `${error?.response?.data?.detail || error?.message || "合并失败"}；源标签已保留，可刷新后重试`;
    await store.refreshServer();
  } finally { tagMerge.busy = false; }
}
function filterPayload(item, overrides = {}, useDraft = false) {
  const source = useDraft ? organizationDialog.filter : item || organizationDialog.filter;
  const basic = source.mode !== "ADVANCED";
  return { name: useDraft || !item ? organizationDialog.name.trim() : source.name, mode: source.mode || "BASIC", listIds: basic ? [...(source.listIds || [])] : [], tagIds: basic ? [...(source.tagIds || [])] : [],
    tagMode: basic ? source.tagMode || "SELECTED" : "SELECTED", taskTypes: basic ? [...(source.taskTypes || [])] : [],
    priorities: [...(source.priorities || [])], status: source.status || "OPEN", dueRange: source.dueRange || "ANY", keyword: source.keyword || "",
    dueStart: basic ? source.dueStart || null : null, dueEnd: basic ? source.dueEnd || null : null,
    includeSubtasks: source.includeSubtasks !== false, matchAllTags: Boolean(source.matchAllTags), sortOrder: source.sortOrder ?? filters.value.length,
    pinned: false, icon: useDraft || !item ? organizationDialog.icon || null : source.icon || null,
    rules: source.mode === "ADVANCED" ? serializeFilterRules(useDraft ? buildAdvancedFilterRules(source.advanced.conditions, source.advanced.joins) : source.rules) : null, ...overrides };
}
function groupListSelected(groupId) {
  const ids = activeLists.value.filter(list => list.groupId === groupId).map(list => list.publicId);
  return filterListAllSelected.value || (ids.length > 0 && ids.every(id => organizationDialog.filter.listIds.includes(id)));
}
function closeOtherFilterSelect(active) {
  const details = [...document.querySelectorAll("[role=dialog] .clone-filter-select")];
  details.forEach((item, index) => { if ((active === "list" ? index !== 0 : index !== 1)) item.open = false; });
}
function toggleFilterGroup(groupId, checked) {
  const ids = activeLists.value.filter(list => list.groupId === groupId).map(list => list.publicId);
  const selected = filterListAllSelected.value ? activeLists.value.map(list => list.publicId) : organizationDialog.filter.listIds;
  organizationDialog.filter.listIds = checked
    ? [...new Set([...selected, ...ids])]
    : selected.filter(id => !ids.includes(id));
}
function toggleFilterList(listId, checked) {
  const selected = filterListAllSelected.value ? activeLists.value.map(list => list.publicId) : organizationDialog.filter.listIds;
  organizationDialog.filter.listIds = checked
    ? [...new Set([...selected, listId])]
    : selected.filter(id => id !== listId);
}
function toggleFilterTag(tagId, checked) {
  organizationDialog.filter.tagMode = "SELECTED";
  organizationDialog.filter.tagIds = checked
    ? [...new Set([...organizationDialog.filter.tagIds, tagId])]
    : organizationDialog.filter.tagIds.filter(id => id !== tagId);
  if (!organizationDialog.filter.tagIds.length) organizationDialog.filter.tagMode = "ALL";
}
function setFilterDueRange() {
  if (organizationDialog.filter.dueRange !== "CUSTOM") return;
  const options = filterDateOptions.value;
  const today = options.find(option => option.offset === 0)?.value || dateKey(new Date());
  if (!options.some(option => option.value === organizationDialog.filter.dueStart)) organizationDialog.filter.dueStart = today;
  if (!options.some(option => option.value === organizationDialog.filter.dueEnd && option.value > organizationDialog.filter.dueStart)) {
    organizationDialog.filter.dueEnd = options.find(option => option.offset === 7)?.value || options.find(option => option.value > organizationDialog.filter.dueStart)?.value || "";
  }
}
function setFilterDateEndpoint(endpoint, value) {
  if (endpoint === "start") {
    organizationDialog.filter.dueStart = value;
    if (!organizationDialog.filter.dueEnd || organizationDialog.filter.dueEnd <= value) {
      organizationDialog.filter.dueEnd = filterDateOptions.value.find(option => option.value > value)?.value || "";
    }
    return;
  }
  if (value > organizationDialog.filter.dueStart) organizationDialog.filter.dueEnd = value;
}
async function toggleFilterPinned() {
  const item = organizationMenu.item;
  organizationMenuOpen.value = false;
  await updateFilterPinned(item, !isPinnedObject("filter", item));
}
async function duplicateFilter() {
  const item = organizationMenu.item;
  organizationMenuOpen.value = false;
  try { await apiCreateTaskFilter(filterPayload(item, { name: `${item.name} 副本`, sortOrder: filters.value.length })); await loadFilters(); message.success("已复制筛选器"); }
  catch (error) { store.error = error?.response?.data?.detail || error?.message || "复制筛选器失败"; }
}
function matchesFilterDraft(task, filter) {
  if (task.deleted) return false;
  const objectType = store.lists.find(list => list.publicId === task.listId)?.objectType || "TASK";
  if (filter.mode === "ADVANCED") {
    try { return matchesFilterRules({ ...task, objectType }, buildAdvancedFilterRules(filter.advanced.conditions, filter.advanced.joins)); }
    catch { return false; }
  }
  if (filter.status !== "ALL" && task.status !== filter.status) return false;
  if (!filter.includeSubtasks && task.parentId) return false;
  if (filter.listIds.length && !filter.listIds.includes(task.listId)) return false;
  if (filter.priorities.length && !filter.priorities.includes(task.priority)) return false;
  const taskTags = task.tagIds || [];
  if (filter.tagMode === "NO_TAGS" && taskTags.length) return false;
  if (filter.tagMode === "HAS_TAGS" && !taskTags.length) return false;
  if (filter.tagMode === "SELECTED" && filter.tagIds.length && (filter.matchAllTags ? !filter.tagIds.every(id => taskTags.includes(id)) : !filter.tagIds.some(id => taskTags.includes(id)))) return false;
  if (filter.taskTypes?.length && !filter.taskTypes.includes(objectType)) return false;
  if (filter.keyword && !`${task.title} ${task.description || ""}`.toLocaleLowerCase().includes(filter.keyword.toLocaleLowerCase())) return false;
  const dueDate = task.dueAt ? new Date(task.dueAt) : null, today = new Date(); today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today); tomorrow.setDate(tomorrow.getDate() + 1);
  if (filter.dueRange === "NONE" && dueDate) return false;
  if (filter.dueRange === "REPEATING" && !task.rrule) return false;
  if (filter.dueRange === "CUSTOM" && (!dueDate || !filter.dueStart || !filter.dueEnd || dateKey(dueDate) < filter.dueStart || dateKey(dueDate) > filter.dueEnd)) return false;
  if (["TODAY", "TOMORROW", "NEXT7", "OVERDUE", "THIS_WEEK", "NEXT_WEEK", "THIS_MONTH", "NEXT_MONTH"].includes(filter.dueRange)) {
    if (!dueDate) return false;
    const day = new Date(dueDate); day.setHours(0, 0, 0, 0);
    if (filter.dueRange === "TODAY" && day.getTime() !== today.getTime()) return false;
    if (filter.dueRange === "TOMORROW" && day.getTime() !== tomorrow.getTime()) return false;
    if (filter.dueRange === "NEXT7" && !(day >= today && day < new Date(today.getTime() + 8 * 86400000))) return false;
    if (filter.dueRange === "OVERDUE" && !(day < today && task.status === "OPEN")) return false;
    const weekStart = new Date(today); weekStart.setDate(today.getDate() - ((today.getDay() + 6) % 7));
    const nextWeek = new Date(weekStart); nextWeek.setDate(nextWeek.getDate() + 7);
    if (filter.dueRange === "THIS_WEEK" && !(day >= weekStart && day < nextWeek)) return false;
    if (filter.dueRange === "NEXT_WEEK" && !(day >= nextWeek && day < new Date(nextWeek.getTime() + 7 * 86400000))) return false;
    if (filter.dueRange === "THIS_MONTH" && (day.getMonth() !== today.getMonth() || day.getFullYear() !== today.getFullYear())) return false;
    if (filter.dueRange === "NEXT_MONTH") { const nextMonth = new Date(today.getFullYear(), today.getMonth() + 1, 1); if (day.getMonth() !== nextMonth.getMonth() || day.getFullYear() !== nextMonth.getFullYear()) return false; }
  }
  return true;
}
function previewFilterDraft() {
  filterPreview.value = store.tasks.filter(task => matchesFilterDraft(task, organizationDialog.filter));
  organizationDialog.filter.previewed = true;
}
async function saveOrganization() {
  const item = organizationDialog.item, kind = organizationDialog.kind;
  const name = organizationDialog.name.trim();
  if (kind === "list" && isInboxColorList(item)) {
    try {
      await store.updateList(item, { color: organizationDialog.color });
      organizationDialogOpen.value = false;
      await store.refreshServer();
    } catch (error) { store.error = error?.response?.data?.detail || error?.message || "保存收集箱颜色失败"; }
    return;
  }
  if (!name) return;
  try {
    if (kind === "list") {
      const payload = { name, color: organizationDialog.color, icon: organizationDialog.icon || "", archived: item?.archived || false, sortOrder: item?.sortOrder ?? store.lists.length, viewType: organizationDialog.viewType, objectType: organizationDialog.objectType, showInSmartLists: organizationDialog.showInSmartLists };
      let groupId = organizationDialog.groupId;
      if (groupId === "__CREATE_GROUP__") {
        const folderName = organizationDialog.newGroupName.trim();
        if (!folderName) return;
        const created = await apiCreateTaskListGroup({ name: folderName, color: organizationDialog.newGroupColor, icon: organizationDialog.newGroupIcon, sortOrder: store.listGroups.length, pinned: false });
        await store.refreshServer();
        groupId = created?.publicId || store.listGroups.find(group => group.name === folderName)?.publicId || "";
        if (!groupId) throw new Error("文件夹已创建，请刷新后重新选择");
        organizationDialog.groupId = groupId;
      }
      const saved = item ? await store.updateList(item, payload) : await store.createList({ ...payload, groupId });
      const target = store.lists.find(value => value.publicId === (item?.publicId || saved?.id || saved?.publicId));
      if (target && (groupId || target.groupId)) await store.assignListGroup(target, groupId);
    } else if (kind === "tag") {
      const payload = { name, color: organizationDialog.color, icon: organizationDialog.icon || null, parentId: organizationDialog.parentId || null, sortOrder: item?.sortOrder ?? store.tags.length };
      if (item) await store.updateTag(item, payload); else await store.createTag(payload);
      await store.refreshServer();
    } else if (kind === "filter") {
      const payload = filterPayload(item, { name, sortOrder: item?.sortOrder ?? filters.value.length }, true);
      if (item) await apiUpdateTaskFilter(item.publicId, payload, item.revision);
      else await apiCreateTaskFilter(payload);
      await loadFilters();
    } else if (kind === "group") {
      if (item) await store.updateListGroup(item, { name, color: organizationDialog.color, icon: organizationDialog.icon || "", sortOrder: item.sortOrder, pinned: organizationDialog.pinned });
      else {
        const group = await apiCreateTaskListGroup({ name, color: organizationDialog.color, icon: organizationDialog.icon || "", sortOrder: store.listGroups.length, pinned: organizationDialog.pinned });
        await store.refreshServer();
        const createdGroup = store.listGroups.find(value => value.publicId === group?.publicId || value.name === name);
        const ids = organizationDialog.groupListIds.length ? organizationDialog.groupListIds : organizationDialog.groupId ? [organizationDialog.groupId] : [];
        if (createdGroup) for (const id of ids) {
          const list = store.lists.find(value => value.publicId === id);
          if (list) await store.assignListGroup(list, createdGroup.publicId);
        }
      }
    }
    organizationDialogOpen.value = false;
    await store.refreshServer();
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "保存失败"; }
}
function requestOrganizationConfirmation(action, item, title, message, confirmLabel) {
  Object.assign(organizationConfirmation, { action, item, title, message, confirmLabel, busy: false });
  organizationMenuOpen.value = false;
  organizationConfirmOpen.value = true;
}
function requestArchiveList() {
  const item = organizationMenu.item;
  requestOrganizationConfirmation("archive-list", item, item.archived ? `取消归档“${item.name}”？` : `归档清单“${item.name}”？`, item.archived ? "取消归档后，清单将重新显示在侧边栏中。" : "归档后，该清单将放入文件夹‘已归档的清单’中。清单中的任务或笔记将在智能清单中不再显示，到期也不提醒。", item.archived ? "取消归档" : "归档");
}
function requestDeleteOrganization(kind) {
  const item = organizationMenu.item;
  if (kind !== "list") return deleteOrganization(kind);
  requestOrganizationConfirmation("delete-list", item, `删除清单“${item.name}”？`, "清单内的所有任务也将被删除。", "删除");
}
function requestDissolveListGroup() {
  const group = organizationMenu.item;
  requestOrganizationConfirmation("dissolve-group", group, `解散文件夹“${group.name}”？`, "", "解散");
}
async function confirmOrganizationAction() {
  const { action, item } = organizationConfirmation;
  if (!item || organizationConfirmation.busy) return;
  organizationConfirmation.busy = true;
  try {
    if (action === "archive-list") {
      await store.updateList(item, { archived: !item.archived });
      await store.refreshServer();
    } else if (action === "delete-list") {
      await store.deleteList(item);
      await store.refreshServer();
    } else if (action === "dissolve-group") {
      const members = activeLists.value.filter(list => list.groupId === item.publicId);
      for (const list of members) await store.assignListGroup(list, "");
      await store.deleteListGroup(item);
      if (route.name === "tasks-group" && route.params.publicId === item.publicId) await router.push("/tasks/all");
    }
    organizationConfirmOpen.value = false;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "操作失败";
    await store.refreshServer();
  } finally { organizationConfirmation.busy = false; }
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
function startOrganizationDrag(kind, item) { organizationDrag.value = { kind, item }; organizationDropTarget.value = ""; }
function handleListDragOver(target) {
  organizationDropTarget.value = target.publicId;
  const source = organizationDrag.value;
  if (!source || source.kind !== "list" || source.item.publicId === target.publicId || listHoverTarget === target.publicId) return;
  clearTimeout(listHoverTimer);
  listHoverTarget = target.publicId;
  listHoverTimer = window.setTimeout(() => {
    if (organizationDrag.value?.kind === "list" && organizationDrag.value.item.publicId !== target.publicId) organizationDropTarget.value = `new-group:${target.publicId}`;
  }, 650);
}
let listHoverTarget = "";
function handleListDragLeave(target) {
  if (listHoverTarget === target.publicId) { clearTimeout(listHoverTimer); listHoverTarget = ""; }
}
function dropListOnList(target) {
  if (organizationDropTarget.value === `new-group:${target.publicId}` && organizationDrag.value?.kind === "list") {
    const source = organizationDrag.value.item;
    listHoverTarget = "";
    clearTimeout(listHoverTimer);
    organizationDrag.value = null;
    openOrganizationDialog("group", null, { groupListIds: [source.publicId, target.publicId], name: `${source.name}、${target.name}` });
    return;
  }
  handleListDragLeave(target);
  dropOrganization("list", target);
}
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
function endOrganizationDrag() { clearTimeout(listHoverTimer); listHoverTarget = ""; organizationDrag.value = null; organizationDropTarget.value = ""; }
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
    if (window.matchMedia("(max-width: 900px)").matches) sidebarHidden.value = true;
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
  loadSidebarVisibility();
  loadSidebarObjectPrefs();
  loadDetailWidth();
  loadViewPreferences();
  loadSidebarSections();
  loadSectionDisplayModes();
  loadListGroupState();
  const filtersLoad = loadFilters();
  const sidebarPreferencesLoad = loadSidebarPreferences(filtersLoad);
  await store.init();
  await Promise.all([filtersLoad, sidebarPreferencesLoad]);
  miniCalendarMonth.value = new Date(selectedDate.value.getFullYear(), selectedDate.value.getMonth(), 1);
  await loadMiniCalendar();
  document.addEventListener("pointerdown", closeContext);
  window.addEventListener("keydown", onKeydown);
  window.matchMedia("(max-width: 900px)").addEventListener("change", handleSidebarViewportChange);
});
onBeforeUnmount(() => {
  stopDetailResize();
  clearTimeout(listHoverTimer);
  document.removeEventListener("pointerdown", closeContext);
  window.removeEventListener("keydown", onKeydown);
  window.matchMedia("(max-width: 900px)").removeEventListener("change", handleSidebarViewportChange);
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
  grid-template-columns: 248px minmax(420px, 1fr) 5px var(--clone-detail-width, 390px);
  height: 100vh;
  margin: 0;
  background: var(--card);
  color: var(--ink);
  overflow: hidden;
  transition: grid-template-columns 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}
.clone-task-shell.sidebar-hidden { grid-template-columns: 44px minmax(420px, 1fr) 5px var(--clone-detail-width, 390px); }
.clone-task-shell.sidebar-hidden .clone-task-sidebar { align-items: center; gap: 0; padding: 10px 4px; overflow: hidden; }
.clone-task-shell.sidebar-hidden .clone-sidebar-head { display: flex; justify-content: center; width: 100%; padding: 0; }
.clone-task-shell.sidebar-hidden .clone-sidebar-head strong { opacity: 0; }
.clone-task-shell.sidebar-hidden .clone-task-sidebar > :not(.clone-sidebar-head) { opacity: 0; transform: translateX(-7px); visibility: hidden; pointer-events: none; transition-delay: 0s, 0s, 0.2s; }
.clone-task-shell.sidebar-hidden .clone-sidebar-head button { width: 34px; height: 34px; }
.clone-task-sidebar {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0;
  padding: 14px 10px 12px;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--card) 96%, var(--paper));
  overflow: hidden;
  transition: padding 0.24s ease, gap 0.24s ease, opacity 0.18s ease;
}
.clone-task-sidebar > :not(.clone-sidebar-head) { transition: opacity 0.16s ease, transform 0.2s cubic-bezier(0.22, 1, 0.36, 1), visibility 0s linear; }
.clone-sidebar-head strong { transition: opacity 0.12s ease; }
.clone-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
}
.clone-sidebar-head { display: none; }
.clone-sidebar-scroll { display: flex; min-height: 0; flex: 1 1 auto; flex-direction: column; gap: 12px; overflow-x: hidden; overflow-y: auto; scrollbar-width: thin; overscroll-behavior: contain; }
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
.clone-sidebar-block-title { padding: 3px 10px 0; color: var(--muted); font-size: 10px; font-weight: 700; }
.clone-sidebar-system { display: grid; gap: 5px; }
.clone-sidebar-organization { display: grid; flex: none; min-height: 0; align-content: start; gap: 10px; padding-top: 4px; border-top: 1px solid var(--line); overflow: visible; }
.clone-sidebar-status { display: grid; gap: 2px; padding-top: 7px; border-top: 1px solid var(--line); }
.clone-sidebar-object-row { position: relative; display: flex; min-width: 0; align-items: center; }
.clone-sidebar-object-row > .clone-nav-item { min-width: 0; flex: 1 1 auto; padding-right: 32px; }
.clone-sidebar-object-row > .clone-nav-item > small { position: absolute; right: 4px; top: 50%; display: grid; width: 24px; height: 24px; margin: 0; place-items: center; transform: translateY(-50%); color: var(--muted); font-size: 11px; font-variant-numeric: tabular-nums; transition: opacity 120ms ease; pointer-events: none; }
.clone-sidebar-object-row > .clone-item-menu-trigger { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); }
.clone-sidebar-object-row:hover > .clone-nav-item > small,
.clone-sidebar-object-row:focus-within > .clone-nav-item > small { opacity: 0; }
.clone-sidebar-object-row:hover > .clone-item-menu-trigger,
.clone-sidebar-object-row:focus-within > .clone-item-menu-trigger { opacity: 1; }
.clone-unsupported-item { color: var(--muted); opacity: .62; cursor: not-allowed; }
.clone-unsupported-item small { margin-left: auto; font-size: 10px; }
.clone-sidebar-mobile-actions { display: none; }
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
.clone-object-icon { display: inline-grid; flex: none; width: 16px; place-items: center; font-size: 14px; line-height: 1; }
.clone-primary-nav a:hover,
.clone-primary-nav a.active,
.clone-nav-item:hover,
.clone-nav-item.active,
.clone-sidebar-footer a:hover {
  background: color-mix(in srgb, var(--accent) 9%, var(--card));
  color: var(--accent);
}
.clone-primary-nav a.active {
  font-weight: 700;
  box-shadow: inset 2px 0 var(--accent);
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
  color: var(--ink2);
  font-size: 11px;
}
.clone-sidebar-section {
  display: grid;
  gap: 7px;
}
.clone-collapse-panel {
  display: grid;
  min-height: 0;
  grid-template-rows: 1fr;
  opacity: 1;
  transition: grid-template-rows 0.22s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.16s ease;
}
.clone-collapse-panel.is-collapsed { grid-template-rows: 0fr; opacity: 0; pointer-events: none; visibility: hidden; transition: grid-template-rows 0.22s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.16s ease, visibility 0s linear 0.22s; }
.clone-collapse-inner { min-height: 0; overflow: hidden; }
.clone-group-lists { display: grid; gap: 3px; }
.clone-sort-stack { display: grid; min-width: 0; gap: 2px; }
.clone-sort-move { transition: transform 0.24s cubic-bezier(0.22, 1, 0.36, 1); }
.clone-sort-enter-active,
.clone-sort-leave-active { transition: opacity 0.16s ease, transform 0.2s cubic-bezier(0.22, 1, 0.36, 1); }
.clone-sort-enter-from,
.clone-sort-leave-to { opacity: 0; transform: translateY(-5px) scale(0.985); }
.clone-sortable-group.drag-over { outline: 1px dashed var(--accent); outline-offset: 2px; border-radius: 6px; background: color-mix(in srgb, var(--accent) 5%, transparent); }
.clone-sortable-item.sorting-dragging,
.clone-sortable-group.sorting-dragging { opacity: 0.48; transform: scale(0.985); }
.clone-list-group { display: grid; min-width: 0; gap: 2px; }
.clone-list-group-heading { display: flex; align-items: center; gap: 4px; min-height: 28px; padding: 0 8px 0 2px; color: var(--muted); font-size: 10px; font-weight: 600; }
.clone-list-group-heading span { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-list-group-heading button { display: grid; width: 22px; height: 22px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-list-group-heading button:hover, .clone-item-menu-trigger:hover { background: var(--paper); color: var(--ink); }
.clone-list-group-heading .clone-list-group-icon { flex: none; width: 15px; text-align: center; }
.clone-group-toggle { flex: 0 0 22px; }
.clone-group-navigation { display: flex; min-width: 0; min-height: 28px; flex: 1; align-items: center; gap: 7px; color: var(--muted); text-decoration: none; }
.clone-group-navigation:hover, .clone-group-navigation.active { color: var(--ink); }
.clone-group-navigation .clone-list-group-icon { color: inherit; }
.clone-group-navigation small { flex: none; margin-left: auto; color: var(--muted); font-size: 10px; }
.clone-group-menu { flex: 0 0 22px; }
.clone-group-lists { padding-left: 0; }
.clone-sidebar-heading-actions { position: relative; display: flex; flex: none; align-items: center; gap: 2px; white-space: nowrap; opacity: 0; visibility: hidden; transition: opacity 120ms ease, visibility 120ms ease; }
.clone-sidebar-section > header:hover .clone-sidebar-heading-actions,
.clone-sidebar-section > header:focus-within .clone-sidebar-heading-actions { opacity: 1; visibility: visible; }
.clone-section-display-menu { position: absolute; z-index: 90; top: calc(100% + 5px); right: 0; display: grid; min-width: 168px; padding: 5px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 8px 24px rgb(15 23 42 / 16%); }
.clone-section-display-menu button { display: flex; min-height: 34px; align-items: center; justify-content: space-between; gap: 12px; padding: 0 9px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; text-align: left; cursor: pointer; }
.clone-section-display-menu button:hover, .clone-section-display-menu button:focus-visible { background: var(--paper); color: var(--ink); }
.clone-sortable-item { position: relative; }
.clone-sortable-item.drag-over { outline: 1px solid var(--accent); background: color-mix(in srgb, var(--accent) 9%, var(--card)); }
.clone-item-menu-trigger { display: grid; flex: none; width: 24px; height: 24px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); opacity: 0; cursor: pointer; }
.clone-sortable-item > small { transition: opacity 120ms ease; }
.clone-sortable-item:hover > small, .clone-sortable-item:focus-within > small { opacity: 0; }
.clone-sortable-item > .clone-item-menu-trigger { position: absolute; right: 4px; top: 50%; transform: translateY(-50%); margin: 0; }
.clone-sortable-item:hover .clone-item-menu-trigger, .clone-sortable-item:focus-within .clone-item-menu-trigger { opacity: 1; }
.clone-child-tag { margin-left: 16px; }
.clone-tag-group { display: grid; gap: 2px; }
.clone-tag-color-dot { flex: 0 0 7px; width: 7px; height: 7px; margin: 0 3px; border: 1px solid color-mix(in srgb, var(--muted) 48%, transparent); border-radius: 50%; }
.clone-tag-form .clone-tag-name-row { margin-bottom: 2px; }
.clone-tag-form .clone-list-color-row { align-items: start; }
.clone-organization-form { display: grid; gap: 14px; }
.clone-organization-form label { display: grid; gap: 6px; color: var(--muted); font-size: 13px; }
.clone-organization-form input:not([type="color"]), .clone-organization-form select { min-height: 38px; padding: 7px 9px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink); }
.clone-organization-form input[type="color"] { width: 46px; height: 34px; padding: 2px; border: 1px solid var(--line); background: var(--card); }
.clone-organization-form fieldset { display: grid; gap: 7px; min-width: 0; margin: 0; padding: 10px; border: 1px solid var(--line); border-radius: 7px; }
.clone-organization-form fieldset legend { padding: 0 5px; color: var(--ink2); font-size: 12px; }
.clone-organization-form fieldset > label { display: flex; align-items: center; gap: 8px; min-width: 0; }
.clone-organization-form fieldset > label input[type="checkbox"], .clone-organization-form fieldset > label input[type="radio"] { flex: 0 0 auto; width: 15px; min-height: 15px; margin: 0; accent-color: var(--accent); }
.clone-visibility-form { display: grid; gap: 10px; width: min(420px, calc(100vw - 36px)); max-height: min(72vh, 640px); overflow: auto; padding-right: 4px; }
.clone-visibility-form fieldset { display: grid; grid-template-columns: minmax(0, 1fr); gap: 3px; min-width: 0; margin: 0; padding: 9px 10px; border: 1px solid var(--line); border-radius: 7px; }
.clone-visibility-form legend { padding: 0 5px; color: var(--muted); font-size: 12px; }
.clone-visibility-row { display: flex; align-items: center; gap: 8px; min-width: 0; min-height: 32px; }
.clone-visibility-row > input { flex: 0 0 auto; width: 15px; min-height: 15px; margin: 0; accent-color: var(--accent); }
.clone-visibility-row > label { flex: 1 1 auto; min-width: 0; margin: 0; overflow: hidden; color: var(--ink); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.clone-visibility-row > button { flex: 0 0 30px; width: 30px; height: 30px; margin-left: auto; display: grid; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-visibility-row > button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-visibility-form footer { display: flex; justify-content: flex-end; padding-top: 4px; }
.clone-dialog-note { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.5; }
.clone-filter-preview { display: grid; gap: 5px; max-height: 220px; overflow: auto; padding: 9px; border: 1px solid var(--line); border-radius: 7px; color: var(--ink2); font-size: 12px; }
.clone-filter-mode { display: grid; grid-template-columns: 1fr 1fr; gap: 2px; padding: 3px; border-radius: 6px; background: var(--paper); }
.clone-filter-mode button { min-height: 32px; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-filter-mode button[aria-selected="true"] { background: var(--card); color: var(--ink); box-shadow: 0 1px 3px #00000014; }
.clone-filter-date-range { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.clone-filter-date-range label { min-width: 0; }
.clone-filter-date-range input { width: 100%; min-width: 0; }
.clone-filter-preview strong { color: var(--ink); }
.clone-filter-preview-modal { min-width: min(430px, calc(100vw - 40px)); max-height: min(65vh, 520px); }
.clone-filter-preview-modal a { overflow: hidden; color: var(--ink2); text-decoration: none; text-overflow: ellipsis; white-space: nowrap; }
.clone-filter-preview-modal a:hover { color: var(--accent); }
.clone-filter-preview footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 8px; }
.clone-organization-form footer { display: flex; justify-content: flex-end; gap: 8px; padding-top: 6px; }
.clone-organization-form footer.clone-inbox-color-actions { justify-content: center; gap: 10px; }
.clone-filter-form { gap: 11px; width: 100%; }
.clone-filter-name-row { display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; align-items: center; gap: 8px; }
.clone-filter-name-row input { min-width: 0; }
.clone-filter-mode { width: 136px; margin: 0 auto; }
.clone-filter-field { display: grid !important; grid-template-columns: 68px minmax(0, 1fr); align-items: center; gap: 8px !important; min-width: 0; color: var(--ink2) !important; }
.clone-filter-field > span { color: var(--ink2); }
.clone-filter-field > input,
.clone-filter-field > select { width: 100%; min-width: 0; }
.clone-filter-select { position: relative; min-width: 0; }
.clone-filter-select > summary { display: flex; align-items: center; justify-content: space-between; min-height: 36px; padding: 6px 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink); cursor: pointer; list-style: none; }
.clone-filter-select > summary::-webkit-details-marker { display: none; }
.clone-filter-select[open] > summary { border-color: var(--accent); }
.clone-filter-select-menu { position: absolute; z-index: 25; top: calc(100% + 4px); right: 0; left: 0; display: grid; gap: 2px; max-height: min(250px, 36vh); overflow: auto; padding: 7px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 12px 28px rgb(0 0 0 / 20%); }
.clone-filter-select-menu label { display: flex !important; align-items: center; gap: 8px; min-height: 30px; margin: 0; padding: 3px 5px; border-radius: 4px; color: var(--ink) !important; cursor: pointer; }
.clone-filter-select-menu label:hover { background: var(--accent-soft); }
.clone-filter-select-menu label input { flex: 0 0 15px; width: 15px; min-height: 15px; margin: 0; accent-color: var(--accent); }
.clone-filter-select-menu label.is-child-list { padding-left: 23px; }
.clone-filter-select-menu hr { width: 100%; margin: 3px 0; border: 0; border-top: 1px solid var(--line); }
.clone-filter-date-range { display: grid; grid-template-columns: minmax(0, 1fr) 16px minmax(0, 1fr); align-items: end; gap: 6px; }
.clone-filter-date-range label { display: grid; gap: 5px; min-width: 0; color: var(--ink2); font-size: 12px; }
.clone-filter-date-range label > span { display: flex; align-items: center; gap: 5px; }
.clone-filter-date-range select { width: 100%; min-width: 0; padding: 6px; }
.clone-filter-date-separator { align-self: center; padding-top: 21px; text-align: center; }
.clone-filter-priorities,
.clone-filter-task-types { display: flex !important; flex-wrap: wrap; align-items: center; gap: 8px 12px !important; min-width: 0; padding: 0 !important; border: 0 !important; }
.clone-filter-priorities legend,
.clone-filter-task-types legend { flex: 0 0 68px; float: left; padding: 0 !important; color: var(--ink2) !important; }
.clone-filter-priorities label,
.clone-filter-task-types label { display: flex !important; align-items: center; gap: 5px !important; margin: 0; color: var(--ink) !important; white-space: nowrap; }
.clone-filter-priorities input,
.clone-filter-task-types input { flex: 0 0 14px !important; width: 14px !important; min-height: 14px !important; margin: 0; accent-color: var(--accent); }
.clone-filter-preview-button { display: inline-flex; align-items: center; gap: 5px; margin-right: auto; padding: 5px 0; border: 0; background: transparent; color: var(--accent); cursor: pointer; }
.clone-organization-form footer.clone-filter-preview-actions { justify-content: flex-end; }
.clone-filter-preview-screen { display: grid; gap: 10px; max-height: min(55vh, 460px); overflow: auto; padding: 8px 3px; }
.clone-filter-preview-screen > strong { color: var(--ink2); font-size: 13px; }
.clone-filter-preview-group { display: grid; gap: 6px; padding: 8px; border: 1px solid var(--line); border-radius: 6px; }
.clone-filter-preview-group > strong,
.clone-filter-preview-list > strong { display: flex; align-items: center; gap: 6px; color: var(--ink2); font-size: 12px; }
.clone-filter-preview-group small,
.clone-filter-preview-list small { margin-left: auto; color: var(--muted); font-size: 11px; }
.clone-filter-preview-list { display: grid; gap: 4px; padding-left: 12px; }
.clone-filter-preview-list > span { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 5px; border-bottom: 1px solid color-mix(in srgb, var(--line) 60%, transparent); color: var(--ink); font-size: 12px; }
.clone-filter-preview-list > span:last-child { border-bottom: 0; }
.clone-filter-preview-check { flex: 0 0 13px; width: 13px; height: 13px; border: 1px solid var(--muted); border-radius: 50%; }
.clone-filter-preview-list > span:not(:has(small:empty)) small { white-space: nowrap; }
.clone-filter-preview-actions .clone-primary-button { min-width: 112px; }
:global(.task-filter-dialog) { width: min(540px, calc(100vw - 32px)); max-width: 540px; }
:global(.task-filter-dialog .ui-dialog-head) { position: relative; justify-content: flex-start; padding-right: 52px; }
:global(.task-filter-dialog .ui-dialog-close) { position: absolute; top: 12px; right: 12px; }
:global(.task-filter-dialog .ui-dialog-body) { padding: 16px 20px; }
.clone-inbox-color-form { gap: 20px; width: min(390px, 100%); min-width: 0; padding: 2px 2px 0; }
.clone-inbox-color-form .clone-inbox-color-row { display: flex; min-width: 0; flex-wrap: wrap; align-items: center; gap: 12px 20px; padding: 4px 4px 10px; color: var(--ink2); font-size: 13px; }
.clone-inbox-color-form .clone-inbox-color-row > span { flex: none; }
.clone-inbox-color-form .clone-inbox-color-row > .task-color-picker { flex: 1 1 180px; min-width: 0; }
.clone-inbox-color-form footer.clone-inbox-color-actions { padding-top: 0; }
.clone-inbox-color-form footer.clone-inbox-color-actions > button { min-width: 88px; min-height: 36px; }
.clone-organization-form footer > button:first-child { min-height: 34px; padding: 0 12px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); }
.clone-confirmation { display: grid; gap: 18px; color: var(--ink2); font-size: 14px; line-height: 1.6; }
.clone-confirmation p { margin: 0; }
.clone-confirmation-title-icon { display: inline-block; vertical-align: -3px; color: var(--muted); }
.clone-confirmation footer { display: flex; justify-content: flex-end; gap: 8px; }
.clone-confirmation footer button { min-height: 36px; padding: 0 13px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); cursor: pointer; }
.clone-confirmation footer .clone-danger-button { border-color: #c43d3d; background: #c43d3d; color: #fff; }
.clone-list-form { gap: 15px; min-width: 0; }
.clone-list-name-row { position: relative; display: flex; min-width: 0; align-items: center; gap: 0; }
.clone-list-name-row > input { flex: 1; width: 100%; min-width: 0; }
.clone-list-icon-button { display: grid; flex: none; width: 38px; height: 38px; place-items: center; border: 1px solid var(--line); border-right: 0; border-radius: 6px 0 0 6px; background: var(--paper); color: var(--ink2); font-size: 17px; cursor: pointer; }
.clone-list-name-row > input { border-radius: 0 6px 6px 0 !important; }
.clone-list-icon-button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-list-setting-row { display: grid !important; grid-template-columns: minmax(104px, 0.38fr) minmax(0, 1fr); align-items: center; gap: 10px !important; min-width: 0; margin: 0; }
.clone-list-setting-row > span { color: var(--ink2); font-size: 12px; }
.clone-list-setting-row > select { width: 100%; min-width: 0; }
.clone-list-setting-row > .task-color-picker { min-width: 0; }
.clone-list-view-picker { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; min-width: 0; }
.clone-list-view-picker button { display: grid; min-width: 0; min-height: 52px; justify-items: center; align-content: center; gap: 3px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--muted); cursor: pointer; }
.clone-list-view-picker button span { font-size: 10px; }
.clone-list-view-picker button[aria-pressed="true"] { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); box-shadow: inset 0 0 0 1px var(--accent); }
.clone-folder-form { display: grid; gap: 18px; min-width: 0; }
.clone-folder-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.clone-folder-form footer > button { min-height: 36px; padding: 0 14px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); cursor: pointer; }
.clone-folder-form footer > .clone-primary-button { border-color: var(--accent); background: var(--accent); color: white; }
.clone-icon-picker { display: grid; gap: 10px; min-width: 0; }
.clone-icon-picker > input { min-width: 0; height: 38px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink); }
.clone-icon-categories { display: flex; flex-wrap: wrap; gap: 4px; }
.clone-icon-categories button { min-height: 28px; padding: 0 8px; border: 1px solid transparent; border-radius: 5px; background: transparent; color: var(--ink2); font-size: 11px; white-space: nowrap; cursor: pointer; }
.clone-icon-categories button:hover,
.clone-icon-categories button[aria-selected="true"] { border-color: var(--line); background: var(--accent-soft); color: var(--accent); }
.clone-icon-grid { display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 4px; max-height: min(46vh, 360px); overflow: auto; overscroll-behavior: contain; }
.clone-icon-grid button { display: grid; aspect-ratio: 1; min-width: 0; place-items: center; border: 1px solid transparent; border-radius: 6px; background: transparent; color: var(--ink2); font-size: 19px; cursor: pointer; }
.clone-icon-grid button:hover,
.clone-icon-grid button[aria-selected="true"] { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); }
.clone-icon-empty { grid-column: 1 / -1; margin: 0; padding: 20px 8px; color: var(--ink3); font-size: 13px; text-align: center; }
.clone-icon-count { color: var(--ink3); font-size: 11px; }
.clone-icon-setting-row { display: flex; align-items: center; gap: 10px; }
.clone-icon-setting-row > span { flex: 1; }
.clone-icon-clear { min-height: 32px; padding: 0 9px; border: 1px solid var(--line); border-radius: 5px; background: var(--card); color: var(--ink2); cursor: pointer; }
:global(.task-list-dialog) { width: min(480px, calc(100vw - 32px)); max-width: 480px; }
:global(.task-list-dialog .ui-dialog-head) { justify-content: center; }
:global(.task-list-dialog .ui-dialog-head h2) { text-align: center; }
:global(.task-list-dialog .ui-dialog-close) { position: absolute; top: 12px; right: 12px; }
:global(.task-list-dialog .ui-dialog-body) { padding: 18px 22px; }
:global(.task-folder-dialog) { width: min(390px, calc(100vw - 32px)); max-width: 390px; }
:global(.task-icon-dialog) { width: min(400px, calc(100vw - 32px)); max-width: 400px; }
@media (max-width: 480px) {
  .clone-icon-grid { grid-template-columns: repeat(7, minmax(0, 1fr)); max-height: min(42vh, 330px); }
}
.clone-list-form footer { justify-content: center; gap: 10px; padding-top: 2px; }
.clone-list-form footer > button { min-width: 88px; }
.clone-list-form footer .clone-primary-button,
.clone-folder-form footer .clone-primary-button { width: auto; padding: 0 14px; font-size: 13px; }
:deep(.clone-list-setting-row .task-color-inline-swatches) { flex-wrap: nowrap; gap: 0; }
:deep(.clone-list-setting-row .task-color-inline-swatches > button) { flex: none; width: 32px; height: 32px; }
:deep(.clone-list-setting-row .task-color-inline-swatches > button .task-color-dot),
:deep(.clone-list-setting-row .task-color-inline-swatches > button .task-color-wheel span) { width: 20px; height: 20px; }
@media (max-width: 520px) {
  :global(.task-list-dialog .ui-dialog-body) { padding: 16px; }
  .clone-list-setting-row { grid-template-columns: minmax(86px, 0.34fr) minmax(0, 1fr); gap: 8px !important; }
  .clone-list-color-row { grid-template-columns: minmax(0, 1fr); align-items: start; }
  .clone-list-view-picker { gap: 5px; }
  .clone-list-view-picker button { min-height: 48px; }
}
.clone-sidebar-section > header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 120ms ease;
}
.clone-sidebar-section > header:hover,
.clone-sidebar-section > header:focus-within { background: color-mix(in srgb, var(--accent) 6%, var(--card)); }
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
.clone-sidebar-section > header > button:first-child { flex: 1; min-width: 0; }
.clone-sidebar-section > header > button:first-child { gap: 0; padding: 0 0 0 10px; text-align: left; }
.clone-sidebar-section > header .clone-section-chevron { position: absolute; left: 0; width: 14px; pointer-events: none; }
.clone-sidebar-section > header svg {
  transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1);
}
.clone-sidebar-section > header .clone-section-chevron { opacity: 0; transition: opacity 120ms ease, transform 0.2s cubic-bezier(0.22, 1, 0.36, 1); }
.clone-sidebar-section > header:hover .clone-section-chevron,
.clone-sidebar-section > header:focus-within .clone-section-chevron { opacity: 1; }
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
.clone-mini-calendar { position: relative; display: grid; flex: none; gap: 4px; padding: 12px 6px 0; border-top: 1px solid var(--line); }
.clone-mini-calendar-toggle { position: absolute; z-index: 1; top: -1px; left: 50%; display: grid; width: 42px; height: 16px; place-items: center; transform: translateX(-50%); border: 0; border-radius: 0 0 7px 7px; background: var(--line); color: var(--muted); opacity: 0; cursor: pointer; transition: opacity 120ms ease, background-color 120ms ease; }
.clone-mini-calendar:hover .clone-mini-calendar-toggle,
.clone-mini-calendar:focus-within .clone-mini-calendar-toggle,
.clone-mini-calendar-toggle:focus-visible { opacity: 1; }
.clone-mini-calendar.is-expanded .clone-mini-calendar-toggle svg { transform: rotate(180deg); }
.clone-mini-calendar-toggle:hover { background: var(--line2); color: var(--ink); }
.clone-mini-calendar-head { display: flex; min-height: 28px; align-items: center; justify-content: space-between; gap: 4px; padding: 0 5px; }
.clone-mini-calendar-head strong { flex: 1 1 auto; min-width: 0; overflow: hidden; color: var(--ink2); font-size: 14px; font-weight: 650; text-overflow: clip; white-space: nowrap; }
.clone-mini-calendar-head > div { display: flex; align-items: center; gap: 1px; }
.clone-mini-calendar-head button { display: grid; flex: 0 0 23px; width: 23px; height: 25px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-mini-calendar-head button:hover { background: var(--paper); color: var(--ink); }
.clone-mini-calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; text-align: center; }
.clone-mini-calendar-grid > span { padding: 2px 0 4px; color: var(--muted); font-size: 10px; font-weight: 600; }
.clone-mini-calendar-grid > a { position: relative; display: grid; min-width: 0; min-height: 54px; align-content: center; justify-items: center; gap: 0; border-radius: 6px; color: var(--ink2); text-decoration: none; }
.clone-mini-calendar-grid.expanded > a { min-height: 56px; }
.clone-mini-calendar-grid > a > span { display: grid; min-width: 28px; height: 28px; padding: 0 4px; place-items: center; border-radius: 50%; font-size: 15px; line-height: 18px; font-variant-numeric: tabular-nums; }
.clone-mini-calendar-grid > a > small { max-width: 100%; overflow: hidden; color: var(--ink2); font-size: 10px; line-height: 12px; text-overflow: ellipsis; white-space: nowrap; }
.clone-mini-calendar-grid > a.other-month { color: var(--muted); opacity: .5; }
.clone-mini-calendar-grid > a.today:not(.selected) > span { background: var(--accent); color: white; }
.clone-mini-calendar-grid > a.selected { color: #4d78db; }
.clone-mini-calendar-grid > a.selected > span { width: 30px; height: 30px; background: color-mix(in srgb, #4d78db 16%, var(--card)); color: #4d78db; }
.clone-mini-calendar-grid > a:hover { background: color-mix(in srgb, var(--accent) 10%, var(--card)); }
.clone-mini-calendar-grid > a > i { position: absolute; bottom: 3px; width: 6px; height: 6px; border-radius: 50%; background: #4d78db; }
.clone-mini-calendar-grid > a > em { position: absolute; top: 0; right: 1px; display: grid; width: 16px; height: 16px; place-items: center; border-radius: 50%; color: #fff; font-size: 9px; font-style: normal; line-height: 1; }
.clone-mini-calendar-grid > a.holiday-off > em { background: #218c70; }
.clone-mini-calendar-grid > a.holiday-work > em { background: #c94d57; }
.clone-mini-calendar-error { margin: 0; color: var(--muted); font-size: 10px; }
.clone-sidebar-backdrop { display: none; }
.clone-task-sidebar.is-editing .clone-item-menu-trigger { opacity: 1; }
.clone-task-main {
  position: relative;
  min-width: 0;
  overflow: auto;
  padding: 20px 30px 100px;
}
.clone-task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 54px;
  max-width: 860px;
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
  font-size: 23px;
  font-weight: 650;
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
  gap: 10px;
  width: min(100%, 820px);
  margin: 24px 0 18px;
  padding: 0 14px;
  min-height: 46px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--card);
  color: var(--muted);
  box-shadow: 0 1px 2px rgba(20, 27, 38, 0.035);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.clone-quick-add:focus-within {
  border-color: color-mix(in srgb, var(--accent) 48%, var(--line));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent), 0 2px 6px rgba(20, 27, 38, 0.05);
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
.clone-quick-draft {
  display: grid;
  width: min(100%, 820px);
  gap: 10px;
  margin: -8px 0 16px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--card);
}
.clone-quick-draft > header,
.clone-quick-draft > footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.clone-quick-draft > header span { color: var(--muted); font-size: 11px; }
.clone-quick-draft > label,
.clone-quick-draft-fields label { display: grid; gap: 5px; color: var(--muted); font-size: 11px; }
.clone-quick-draft input:not([type="checkbox"]),
.clone-quick-draft select { min-width: 0; width: 100%; height: 34px; padding: 0 8px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); }
.clone-quick-draft-fields { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.clone-quick-draft-tags { display: flex; flex-wrap: wrap; gap: 8px 12px; margin: 0; padding: 7px 8px; border: 1px solid var(--line); border-radius: 5px; }
.clone-quick-draft-tags legend { padding: 0 4px; color: var(--muted); font-size: 11px; }
.clone-quick-draft-tags label { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; }
.clone-quick-draft > footer button:not(.clone-primary-button) { border: 0; background: transparent; color: var(--muted); cursor: pointer; }
.clone-quick-draft > footer .clone-primary-button { min-height: 30px; }
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
  max-width: 860px;
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
  max-width: 860px;
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
  transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1);
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
  width: min(100%, 820px);
  min-height: 210px;
  align-content: center;
  color: var(--muted);
  border-top: 1px solid var(--line);
}
.clone-empty-state strong { color: var(--ink2); font-size: 15px; font-weight: 600; }
.clone-empty-state p { max-width: 340px; margin: 0; font-size: 12px; line-height: 1.6; text-align: center; }
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
.clone-detail-divider { position: relative; z-index: 3; cursor: ew-resize; touch-action: none; outline: 0; }
.clone-detail-divider::after { content: ""; position: absolute; top: 0; bottom: 0; left: 2px; width: 1px; background: var(--line); }
.clone-detail-divider:hover::after,
.clone-detail-divider:focus-visible::after { left: 1px; width: 3px; background: var(--accent); }
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
.clone-date-toolbar-popover { width: min(258px, calc(100vw - 32px)); }
.clone-toolbar-popover strong { color: var(--ink2); font-size: 12px; }
.clone-toolbar-popover button { min-height: 29px; padding: 5px 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); text-align: left; cursor: pointer; }
.clone-toolbar-popover button:focus-visible,
.clone-toolbar-popover input:focus-visible,
.clone-toolbar-popover select:focus-visible { outline: 2px solid color-mix(in srgb, var(--accent) 60%, transparent); outline-offset: 1px; }
.clone-toolbar-popover button:hover, .clone-toolbar-popover button.active { background: var(--accent-soft); color: var(--accent); }
.clone-toolbar-popover input { width: 100%; box-sizing: border-box; }
.clone-repeat-toolbar-popover { left: 150px; }
.clone-reminder-popover { right: 48px; left: auto; width: min(250px, calc(100vw - 28px)); }
.clone-reminder-popover-field { display: grid; gap: 4px; color: var(--muted); font-size: 11px; }
.clone-reminder-popover-field select { min-height: 32px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); }
.clone-toolbar-popover .clone-inline-check { display: flex; align-items: center; gap: 7px; color: var(--ink2); font-size: 12px; }
.clone-toolbar-popover .clone-inline-check input { width: 15px; min-height: 15px; margin: 0; }
.clone-toolbar-popover .clone-popover-save { justify-self: end; background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-toolbar-popover .clone-popover-save:hover { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-reminder-tool.active { color: var(--accent); }
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
.clone-checklist-row.drop-target { border-top: 2px solid var(--accent); }
.clone-checklist-row .clone-checklist-grip { opacity: .55; cursor: grab; touch-action: none; }
.clone-checklist-row .clone-checklist-grip:active { cursor: grabbing; }
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
  .clone-detail-divider { display: none; }
  .clone-task-shell {
    grid-template-columns: 220px minmax(420px, 1fr);
  }
  .clone-task-shell.sidebar-hidden { grid-template-columns: 44px minmax(420px, 1fr); }
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
  .clone-quick-draft-fields { grid-template-columns: 1fr; }
  .clone-task-shell {
    display: block;
    height: auto;
    min-height: calc(100vh - 72px);
    margin: 0;
    overflow: visible;
  }
  .clone-task-sidebar {
    display: flex;
    position: fixed;
    z-index: 80;
    top: 72px;
    bottom: 0;
    left: 0;
    width: min(320px, 86vw);
    height: auto;
    min-height: 0;
    max-height: none;
    padding: 14px 10px 12px;
    overflow: hidden;
    border-bottom-width: 0;
    box-shadow: 12px 0 32px rgb(15 23 42 / 16%);
    opacity: 0;
    transform: translateX(-100%);
    visibility: hidden;
    pointer-events: none;
    transition: opacity 0.18s ease, transform 0.24s cubic-bezier(0.22, 1, 0.36, 1), visibility 0s linear 0.24s;
  }
  .clone-task-shell:not(.sidebar-hidden) .clone-task-sidebar {
    display: flex;
    min-height: 0;
    max-height: none;
    padding-top: 14px;
    padding-bottom: 12px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
    opacity: 1;
    transform: translateX(0);
    visibility: visible;
    pointer-events: auto;
    transition-delay: 0s;
  }
  .clone-sidebar-backdrop { position: fixed; z-index: 79; inset: 72px 0 0; display: block; border: 0; background: rgb(15 23 42 / 34%); }
  .clone-sidebar-head { display: flex; }
  .clone-task-sidebar { gap: 0; }
  .clone-sidebar-scroll { gap: 8px; }
  .clone-sidebar-system { max-height: none; overflow: visible; }
  .clone-sidebar-mobile-actions { display: flex; align-items: center; gap: 8px; padding: 0 8px 8px; border-bottom: 1px solid var(--line); }
  .clone-sidebar-mobile-actions,
  .clone-sidebar-status,
  .clone-mini-calendar { flex: none; }
  .clone-sidebar-mobile-actions button { display: inline-flex; min-height: 34px; align-items: center; justify-content: center; gap: 6px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); font: inherit; font-size: 12px; }
  .clone-sidebar-mobile-actions button:first-child { border-color: color-mix(in srgb, var(--accent) 35%, var(--line)); color: var(--accent); }
  .clone-task-sidebar .clone-item-menu-trigger { opacity: 0; }
  .clone-task-sidebar.is-editing .clone-item-menu-trigger,
  .clone-task-sidebar .clone-item-menu-trigger:focus-visible { opacity: 1; }
  .clone-mini-calendar-grid > a { min-height: 52px; }
  .clone-mini-calendar-grid.expanded > a { min-height: 54px; }
  .clone-sidebar-organization { gap: 7px; }
  .clone-sidebar-heading-actions { opacity: 1; visibility: visible; }
  .clone-sidebar-section > header .clone-section-chevron { opacity: 1; }
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
@media (prefers-reduced-motion: reduce) {
  .clone-task-shell,
  .clone-task-sidebar,
  .clone-task-sidebar > *,
  .clone-collapse-panel,
  .clone-sidebar-section > header svg,
  .clone-group-heading svg,
  .clone-sort-move,
  .clone-sort-enter-active,
  .clone-sort-leave-active,
  .clone-task-row {
    transition-duration: 0.01ms !important;
    transition-delay: 0s !important;
  }
}
</style>
