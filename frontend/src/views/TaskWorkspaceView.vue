<template>
  <section
    class="clone-task-shell"
    :style="{ '--clone-detail-width': `${detailWidth}px`, '--clone-sidebar-width': `${sidebarWidth}px` }"
    :class="{
      'sidebar-hidden': sidebarHidden,
      'is-summary': isSummary,
      'detail-open': Boolean(selectedTask || listActivityOpen),
      'workspace-overlay-view': activeWorkspaceView !== 'LIST',
      'workspace-detail-open': activeWorkspaceView !== 'LIST' && Boolean(selectedTask || editorOpen || listActivityOpen),
    }"
    @touchstart.passive="startSidebarSwipe"
    @touchend.passive="finishSidebarSwipe"
  >
    <button v-if="!sidebarHidden && isMobileSidebar" class="clone-sidebar-backdrop" type="button" aria-label="关闭任务侧栏" @click="sidebarHidden = true"></button>
    <button v-if="!isSummary && selectedIds.size && isMobileSidebar" class="clone-batch-backdrop" type="button" aria-label="关闭批量操作" @click="clearBatchSelection"></button>
    <button v-if="listActivityOpen && isMobileSidebar" class="clone-activity-backdrop" type="button" aria-label="关闭清单动态" @click="closeListActivity"></button>
    <aside class="clone-task-sidebar" :class="{ 'is-editing': sidebarEditing }" aria-label="任务导航" @click="handleSidebarClick" @dragover.capture="handleOrganizationDragOver" @drop.capture.prevent.stop="handleSidebarDrop" @dragend.capture="endOrganizationDrag">
      <div class="clone-sidebar-scroll">
      <div class="clone-sidebar-head">
        <strong>任务</strong
        ><button type="button" :aria-label="sidebarHidden ? '展开任务侧栏' : '折叠任务侧栏'" :title="sidebarHidden ? '展开任务侧栏' : '折叠任务侧栏'" @click="toggleSidebar">
          <PanelLeft v-if="sidebarHidden" :size="17" /><PanelLeftClose v-else :size="17" />
        </button>
      </div>
      <section class="clone-sidebar-block clone-sidebar-system" aria-label="系统集合">
        <nav class="clone-primary-nav">
        <div v-for="item in pinnedPrimaryViews" :key="`pinned-${item.key}`" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', item, $event)">
          <RouterLink :to="item.to" class="clone-nav-item" :class="{ active: isActive(item.to) }"><component :is="item.icon" :size="16" /><span>{{ item.label }}</span><small>{{ itemCount(item.key) }}</small></RouterLink>
          <button class="clone-item-menu-trigger" type="button" :aria-label="`${item.label}的更多操作`" @click.stop="openOrganizationMenu('system', item, $event)"><Ellipsis :size="14" /></button>
        </div>
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
        <div class="clone-collapse-panel" :class="{ 'is-collapsed': !sections.lists }" :aria-hidden="!sections.lists"><div class="clone-sidebar-items clone-collapse-inner" :inert="!sections.lists" @dragover.prevent="handleListRootDragOver" @drop.prevent="dropOrganizationRoot('list')">
        <section v-if="pinnedListGroups.length" class="clone-pinned-zone" aria-label="置顶分组"><div class="clone-pinned-title">置顶分组</div><section v-for="group in pinnedListGroups" :key="`pinned-group-${group.publicId}`" class="clone-list-group clone-sortable-group" :class="{ 'drag-over': organizationDropTarget === `group:${group.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'group' && organizationDrag.item.publicId === group.publicId }" @dragover.prevent="handleGroupDragOver(group, $event)" @drop.prevent.stop="dropGroup(group)"><header class="clone-list-group-heading" draggable="true" @dragstart="startOrganizationDrag('group', group, $event)"><RouterLink class="clone-group-navigation" :class="{ active: route.name === 'tasks-group' && route.params.publicId === group.publicId }" :to="`/tasks/group/${group.publicId}`"><span class="clone-group-toggle" role="button" tabindex="0" :aria-label="`${groupCollapsed[group.publicId] ? '展开' : '折叠'}${group.name}`" @click.prevent.stop="toggleListGroupCollapsed(group)" @keydown.enter.prevent.stop="toggleListGroupCollapsed(group)" @keydown.space.prevent.stop="toggleListGroupCollapsed(group)"><ChevronDown :size="13" :class="{ rotated: groupCollapsed[group.publicId] }" /></span><TaskOrganizationIcon v-if="group.icon" class="clone-list-group-icon" :value="group.icon" /><Folder v-else class="clone-list-group-icon" :size="15" /><span>{{ group.name }}</span><small>{{ groupOpenCount(group.publicId) }}</small></RouterLink><button class="clone-group-menu" type="button" :aria-label="`${group.name}的更多操作`" @click.stop="openOrganizationMenu('group', group, $event)"><Ellipsis :size="14" /></button></header><div class="clone-collapse-panel" :class="{ 'is-collapsed': groupCollapsed[group.publicId] }" :aria-hidden="Boolean(groupCollapsed[group.publicId])"><div class="clone-group-lists clone-collapse-inner" :inert="Boolean(groupCollapsed[group.publicId])"><template v-for="list in listsInGroup(group.publicId)" :key="list.publicId"><div v-if="list.dropPlaceholder" class="clone-nav-item clone-drag-placeholder" aria-hidden="true"><ListTodo :size="15" /><span>{{ list.name }}</span></div><RouterLink v-else :to="`/tasks/list/${list.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }" draggable="true" @dragstart="startOrganizationDrag('list', list, $event)" @dragover.prevent="handleListDragOver(list, $event)" @dragleave="handleListDragLeave(list)" @drop.prevent.stop="dropListOnList(list)" @contextmenu.prevent="openOrganizationMenu('list', list, $event)"><ListTodo :size="15" /><span>{{ list.name }}</span><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink></template></div></div></section></section>
        <section v-if="pinnedLists.length" class="clone-pinned-zone" aria-label="置顶清单"><div class="clone-pinned-title">置顶</div><RouterLink v-for="list in pinnedLists" :key="`pinned-${list.publicId}`" :to="`/tasks/list/${list.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }" draggable="true" @dragstart="startOrganizationDrag('list', list, $event)" @dragover.prevent="handleListDragOver(list, $event)" @dragleave="handleListDragLeave(list)" @drop.prevent.stop="dropListOnList(list)" @contextmenu.prevent="openOrganizationMenu('list', list, $event)"><TaskOrganizationIcon v-if="list.icon" class="clone-object-icon" :value="list.icon" /><ListTodo v-else :size="15" /><span>{{ list.name }}</span><i class="clone-list-color-dot" :style="{ backgroundColor: list.color || 'transparent' }" aria-hidden="true"></i><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink></section>
        <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack">
        <section v-for="group in visibleListGroups" :key="group.publicId" class="clone-list-group clone-sortable-group" :class="{ 'drag-over': organizationDropTarget === `group:${group.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'group' && organizationDrag.item.publicId === group.publicId }" @dragover.prevent="handleGroupDragOver(group, $event)" @drop.prevent.stop="dropGroup(group)" @contextmenu.prevent="openOrganizationMenu('group', group, $event)">
          <header class="clone-list-group-heading" draggable="true" @dragstart="startOrganizationDrag('group', group, $event)"><RouterLink class="clone-group-navigation" :class="{ active: route.name === 'tasks-group' && route.params.publicId === group.publicId }" :to="`/tasks/group/${group.publicId}`"><span class="clone-group-toggle" role="button" tabindex="0" :aria-label="`${groupCollapsed[group.publicId] ? '展开' : '折叠'}${group.name}`" @click.prevent.stop="toggleListGroupCollapsed(group)" @keydown.enter.prevent.stop="toggleListGroupCollapsed(group)" @keydown.space.prevent.stop="toggleListGroupCollapsed(group)"><ChevronDown :size="13" :class="{ rotated: groupCollapsed[group.publicId] }" /></span><TaskOrganizationIcon v-if="group.icon" class="clone-list-group-icon" :value="group.icon" /><Folder v-else class="clone-list-group-icon" :size="15" /><span>{{ group.name }}</span><small>{{ groupOpenCount(group.publicId) }}</small></RouterLink><button class="clone-group-menu" type="button" :aria-label="`${group.name}的更多操作`" @click.stop="openOrganizationMenu('group', group, $event)"><Ellipsis :size="14" /></button></header>
          <div class="clone-collapse-panel" :class="{ 'is-collapsed': groupCollapsed[group.publicId] }" :aria-hidden="Boolean(groupCollapsed[group.publicId])"><TransitionGroup name="clone-sort" tag="div" class="clone-group-lists clone-collapse-inner" :inert="Boolean(groupCollapsed[group.publicId])"><template v-for="list in listsInGroup(group.publicId)" :key="list.publicId"><div v-if="list.dropPlaceholder" class="clone-nav-item clone-drag-placeholder" aria-hidden="true"><ListTodo :size="15" /><span>{{ list.name }}</span></div><RouterLink v-else :to="list.systemKey === 'INBOX' ? '/tasks/inbox' : `/tasks/list/${list.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId || organizationDropTarget === `new-group:${list.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }" :draggable="list.systemKey !== 'INBOX'" @dragstart="startOrganizationDrag('list', list, $event)" @dragover.prevent="handleListDragOver(list, $event)" @dragleave="handleListDragLeave(list)" @drop.prevent.stop="dropListOnList(list)" @contextmenu.prevent="openOrganizationMenu('list', list, $event)"><TaskOrganizationIcon v-if="list.icon" class="clone-object-icon" :value="list.icon" /><ListTodo v-else :size="15" /><span>{{ list.name }}</span><i class="clone-list-color-dot" :style="{ backgroundColor: list.color || 'transparent' }" aria-hidden="true"></i><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink></template></TransitionGroup></div>
        </section>
        </TransitionGroup>
        <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack">
        <template v-for="list in ungroupedLists" :key="list.publicId">
        <div v-if="list.dropPlaceholder" class="clone-nav-item clone-drag-placeholder" aria-hidden="true"><ListTodo :size="15" /><span>{{ list.name }}</span></div>
        <RouterLink
          v-else
          :to="
            list.systemKey === 'INBOX'
              ? '/tasks/inbox'
              : `/tasks/list/${list.publicId}`
          "
          class="clone-nav-item clone-sortable-item"
          :class="{ active: route.params.publicId === list.publicId, 'drag-over': organizationDropTarget === list.publicId || organizationDropTarget === `new-group:${list.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'list' && organizationDrag.item.publicId === list.publicId }"
          :draggable="list.systemKey !== 'INBOX'"
          @dragstart="startOrganizationDrag('list', list, $event)"
          @dragover.prevent="handleListDragOver(list, $event)"
          @dragleave="handleListDragLeave(list)"
          @drop.prevent.stop="dropListOnList(list)"
          @contextmenu.prevent="openOrganizationMenu('list', list, $event)"
          ><TaskOrganizationIcon v-if="list.icon" class="clone-object-icon" :value="list.icon" /><ListTodo v-else :size="15" /><span>{{ list.name }}</span><i class="clone-list-color-dot" :style="{ backgroundColor: list.color || 'transparent' }" aria-hidden="true"></i><small>{{ listOpenCount(list.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${list.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('list', list, $event)"><Ellipsis :size="14" /></button></RouterLink>
        </template>
        </TransitionGroup>
        <div v-if="!isHiddenObject('system', archivedView) && !isPinnedObject('system', archivedView)" class="clone-sidebar-object-row" @contextmenu.prevent="openOrganizationMenu('system', archivedView, $event)"><RouterLink to="/tasks/shortcut/archived" class="clone-nav-item" :class="{ active: route.params.shortcut === 'archived' }"><Archive :size="15" /><span>归档</span><small>{{ archivedTaskCount }}</small></RouterLink><button class="clone-item-menu-trigger" type="button" aria-label="归档的更多操作" @click.stop="openOrganizationMenu('system', archivedView, $event)"><Ellipsis :size="14" /></button></div>
        <span v-if="!activeLists.length && !archivedLists.length" class="clone-empty-item">暂无清单</span>
        </div></div>
      </section>
      <section class="clone-sidebar-section">
        <header @click="toggleSection('filters')"><button type="button" :aria-expanded="sections.filters"><ChevronDown :size="14" class="clone-section-chevron" :class="{ rotated: !sections.filters }" />筛选器</button><div class="clone-sidebar-heading-actions"><button class="sidebar-create" type="button" aria-label="新建筛选器" title="新建筛选器" @click.stop="createFilter"><Plus :size="14" /></button><button class="sidebar-create" type="button" aria-label="筛选器显示选项" title="筛选器显示选项" :aria-expanded="sectionDisplayMenu === 'filters'" @click.stop="toggleSectionDisplayMenu('filters')"><Ellipsis :size="14" /></button><div v-if="sectionDisplayMenu === 'filters'" class="clone-section-display-menu" role="menu" aria-label="筛选器显示选项" @pointerdown.stop @click.stop><button v-for="option in sectionDisplayOptions('filters')" :key="option.value" type="button" role="menuitemradio" :aria-checked="sectionDisplayModes.filters === option.value" @click.stop="setSectionDisplayMode('filters', option.value)">{{ option.label }}<Check v-if="sectionDisplayModes.filters === option.value" :size="14" /></button></div></div></header>
        <div class="clone-collapse-panel" :class="{ 'is-collapsed': !sections.filters || !isSectionDisplayed('filters') }" :aria-hidden="!sections.filters || !isSectionDisplayed('filters')"><div class="clone-sidebar-items clone-collapse-inner" :inert="!sections.filters || !isSectionDisplayed('filters')">
        <section v-if="pinnedFilters.length" class="clone-pinned-zone" aria-label="置顶筛选器"><div class="clone-pinned-title">置顶</div><RouterLink v-for="filter in pinnedFilters" :key="`pinned-${filter.publicId}`" :to="`/tasks/filter/${filter.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === filter.publicId, 'sorting-dragging': organizationDrag?.kind === 'filter' && organizationDrag.item.publicId === filter.publicId }" draggable="true" @dragstart="startOrganizationDrag('filter', filter)" @dragover.prevent="organizationDropTarget = filter.publicId" @drop.prevent.stop="dropOrganization('filter', filter)" @contextmenu.prevent="openOrganizationMenu('filter', filter, $event)"><TaskOrganizationIcon v-if="filter.icon" class="clone-object-icon" :value="filter.icon" /><SlidersHorizontal v-else :size="15" /><span>{{ filter.name }}</span><button class="clone-item-menu-trigger" type="button" :aria-label="`${filter.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('filter', filter, $event)"><Ellipsis :size="14" /></button></RouterLink></section>
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
        </div></div>
      </section>
      <section class="clone-sidebar-section">
        <header @click="toggleSection('tags')"><button type="button" :aria-expanded="sections.tags"><ChevronDown :size="14" class="clone-section-chevron" :class="{ rotated: !sections.tags }" />标签</button><div class="clone-sidebar-heading-actions"><button class="sidebar-create" type="button" aria-label="标签显示选项" title="标签显示选项" :aria-expanded="sectionDisplayMenu === 'tags'" @click.stop="toggleSectionDisplayMenu('tags')"><Ellipsis :size="14" /></button><button class="sidebar-create" type="button" aria-label="新建标签" title="新建标签" @click.stop="createTag"><Plus :size="14" /></button><div v-if="sectionDisplayMenu === 'tags'" class="clone-section-display-menu" role="menu" aria-label="标签显示选项" @pointerdown.stop @click.stop><button v-for="option in sectionDisplayOptions('tags')" :key="option.value" type="button" role="menuitemradio" :aria-checked="sectionDisplayModes.tags === option.value" @click.stop="setSectionDisplayMode('tags', option.value)">{{ option.label }}<Check v-if="sectionDisplayModes.tags === option.value" :size="14" /></button></div></div></header>
        <div class="clone-collapse-panel" :class="{ 'is-collapsed': !sections.tags || !isSectionDisplayed('tags') }" :aria-hidden="!sections.tags || !isSectionDisplayed('tags')"><div class="clone-sidebar-items clone-collapse-inner" :inert="!sections.tags || !isSectionDisplayed('tags')">
        <section v-if="pinnedTags.length" class="clone-pinned-zone" aria-label="置顶标签"><div class="clone-pinned-title">置顶</div><RouterLink v-for="tag in pinnedTags" :key="`pinned-${tag.publicId}`" :to="`/tasks/tag/${tag.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === tag.publicId || organizationDropTarget === `tag-merge:${tag.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'tag' && organizationDrag.item.publicId === tag.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', tag, $event)" @dragover.prevent="handleOrganizationDragOver($event)" @drop.prevent.stop="dropOrganization('tag', tag)" @contextmenu.prevent="openOrganizationMenu('tag', tag, $event)"><TaskOrganizationIcon v-if="tag.icon" class="clone-object-icon" :value="tag.icon" /><Tag v-else :size="15" /><span>{{ tag.name }}</span><i class="clone-tag-color-dot" :style="{ backgroundColor: tag.color || 'transparent' }" aria-hidden="true"></i><small>{{ tagOpenCount(tag.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${tag.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', tag, $event)"><Ellipsis :size="14" /></button></RouterLink></section>
        <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack">
        <div v-for="tag in rootTags" :key="tag.publicId" class="clone-tag-group">
          <RouterLink :to="`/tasks/tag/${tag.publicId}`" class="clone-nav-item clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === tag.publicId || organizationDropTarget === `tag-merge:${tag.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'tag' && organizationDrag.item.publicId === tag.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', tag, $event)" @dragover.prevent="handleOrganizationDragOver($event)" @drop.prevent.stop="dropOrganization('tag', tag)" @contextmenu.prevent="openOrganizationMenu('tag', tag, $event)"><TaskOrganizationIcon v-if="tag.icon" class="clone-object-icon" :value="tag.icon" /><Tag v-else :size="15" /><span>{{ tag.name }}</span><i class="clone-tag-color-dot" :style="{ backgroundColor: tag.color || 'transparent' }" aria-hidden="true"></i><small>{{ tagOpenCount(tag.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${tag.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', tag, $event)"><Ellipsis :size="14" /></button></RouterLink>
          <TransitionGroup name="clone-sort" tag="div" class="clone-sort-stack"><RouterLink v-for="child in childTags.filter(item => item.parentId === tag.publicId)" :key="child.publicId" :to="`/tasks/tag/${child.publicId}`" class="clone-nav-item clone-child-tag clone-sortable-item" :class="{ 'drag-over': organizationDropTarget === child.publicId || organizationDropTarget === `tag-merge:${child.publicId}`, 'sorting-dragging': organizationDrag?.kind === 'tag' && organizationDrag.item.publicId === child.publicId }" draggable="true" @dragstart="startOrganizationDrag('tag', child, $event)" @dragover.prevent="handleOrganizationDragOver($event)" @drop.prevent.stop="dropOrganization('tag', child)" @contextmenu.prevent="openOrganizationMenu('tag', child, $event)"><TaskOrganizationIcon v-if="child.icon" class="clone-object-icon" :value="child.icon" /><Tag v-else :size="14" /><span>{{ child.name }}</span><i class="clone-tag-color-dot" :style="{ backgroundColor: child.color || 'transparent' }" aria-hidden="true"></i><small>{{ tagOpenCount(child.publicId) }}</small><button class="clone-item-menu-trigger" type="button" :aria-label="`${child.name}的更多操作`" @click.prevent.stop="openOrganizationMenu('tag', child, $event)"><Ellipsis :size="14" /></button></RouterLink></TransitionGroup>
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
      <button v-if="!isMobileSidebar && !sidebarHidden" class="clone-sidebar-resize" type="button" role="separator" aria-label="调整任务侧栏宽度" aria-orientation="vertical" :aria-valuemin="184" :aria-valuemax="420" :aria-valuenow="sidebarWidth" @pointerdown="startSidebarResize" @keydown="resizeSidebarByKey"></button>
    </aside>

    <button
      v-if="!isSummary && activeWorkspaceView !== 'LIST' && (selectedTask || editorOpen)"
      class="clone-workspace-detail-backdrop"
      type="button"
      aria-label="关闭任务详情"
      @click="closeDetail"
    ></button>

    <main class="clone-task-main" :class="{ 'quick-action-open': Boolean(quickAction) }" @scroll.passive="loadMoreCompletedOnScroll">
      <TaskSummaryWorkspace v-if="isSummary" />
      <template v-else>
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
          <h1>{{ title }}</h1>
        </div>
        <div v-if="isTrashRoute" class="clone-header-actions clone-trash-header-actions">
          <button type="button" class="clone-icon-button" aria-label="清空垃圾桶" :title="store.online ? '清空垃圾桶' : '联网后才能清空垃圾桶'" :disabled="!trashedTaskCount || !store.online || trashClearBusy" @click="trashClearOpen = true"><BrushCleaning :size="19" /></button>
        </div>
        <div v-else-if="isCompletionRoute" class="clone-header-actions clone-completion-header-actions">
          <button type="button" class="clone-icon-button" :aria-label="`打印${isAbandonedRoute ? '已放弃' : '已完成'}任务`" title="打印" @click="printTaskList('CONTENT')"><Printer :size="19" /></button>
        </div>
        <div v-else class="clone-header-actions">
          <button
            type="button"
            class="clone-icon-button"
            aria-label="搜索任务"
            @click="router.push('/tasks/search')"
          >
            <Search :size="17" /></button
          ><button
            type="button"
            class="clone-icon-button"
            data-workspace-menu-trigger="group"
            aria-label="分组与排序"
            title="分组与排序"
            :aria-pressed="moreOpen"
            @click="toggleWorkspaceMenu('group')"
          >
            <ListFilter :size="18" /></button
          ><button
            type="button"
            class="clone-icon-button"
            data-workspace-menu-trigger="more"
            aria-label="中间栏更多选项"
            title="更多选项"
            :aria-pressed="listOptionsOpen"
            @click="toggleWorkspaceMenu('more')"
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
      <div v-if="isCompletionRoute" class="clone-completion-filters" aria-label="任务筛选" @click.stop>
        <div class="clone-completion-filter">
          <button type="button" class="clone-completion-filter-trigger" :aria-expanded="completionFilterMenu === 'date'" @click="toggleCompletionFilterMenu('date')">{{ completionDateLabel }}<ChevronDown :size="14" /></button>
          <div v-if="completionFilterMenu === 'date'" class="clone-completion-filter-menu" role="menu" aria-label="完成日期筛选">
            <button v-for="option in completionDateOptions" :key="option.value" type="button" role="menuitemradio" :aria-checked="completionFilters.dateRange === option.value" :class="{ selected: completionFilters.dateRange === option.value }" @click="selectCompletionDateRange(option.value)">{{ option.label }}<Check v-if="completionFilters.dateRange === option.value" :size="15" /></button>
            <div v-if="completionFilters.dateRange === 'CUSTOM'" class="clone-completion-custom-range">
              <label><span>开始</span><input v-model="completionFilters.customStart" type="date" aria-label="完成日期开始" /></label>
              <label><span>结束</span><input v-model="completionFilters.customEnd" type="date" :min="completionFilters.customStart || undefined" aria-label="完成日期结束" /></label>
            </div>
          </div>
        </div>
        <div class="clone-completion-filter">
          <button type="button" class="clone-completion-filter-trigger" :aria-expanded="completionFilterMenu === 'list'" @click="toggleCompletionFilterMenu('list')">{{ completionListLabel }}<ChevronDown :size="14" /></button>
          <div v-if="completionFilterMenu === 'list'" class="clone-completion-filter-menu clone-completion-list-menu" role="group" aria-label="清单筛选">
            <label class="clone-completion-list-option"><input type="checkbox" :checked="completedListDraft === null || completedListDraft.length === completionAvailableListIds().length" :indeterminate="completedListDraft !== null && completedListDraft.length > 0 && completedListDraft.length < completionAvailableListIds().length" @change="completedListDraft = $event.target.checked ? null : []" /><span>所有</span></label>
            <label v-if="store.inbox" class="clone-completion-list-option"><input type="checkbox" :checked="completedListDraft === null || completedListDraft.includes(store.inbox.publicId)" @change="toggleCompletedList(store.inbox.publicId, $event.target.checked)" /><Inbox :size="15" /><span>收集箱</span></label>
            <template v-for="group in sortedListGroups" :key="group.publicId">
              <div class="clone-completion-folder-option">
                <button type="button" class="clone-completion-folder-toggle" :aria-label="`${completedFolderExpanded[group.publicId] ? '收起' : '展开'}${group.name}`" @click="completedFolderExpanded[group.publicId] = !completedFolderExpanded[group.publicId]"><ChevronRight :size="14" :class="{ expanded: completedFolderExpanded[group.publicId] }" /></button>
                <label class="clone-completion-list-option"><input type="checkbox" :checked="completedFolderSelected(group.publicId)" @change="toggleCompletedFolder(group.publicId, $event.target.checked)" /><Folder :size="15" /><span>{{ group.name }}</span></label>
              </div>
              <label v-for="list in completionListsInGroup(group.publicId)" v-show="completedFolderExpanded[group.publicId]" :key="list.publicId" class="clone-completion-list-option is-child"><input type="checkbox" :checked="completedListDraft === null || completedListDraft.includes(list.publicId)" @change="toggleCompletedList(list.publicId, $event.target.checked)" /><span class="clone-completion-list-dot" :style="{ backgroundColor: list.color || 'transparent' }"></span><span>{{ list.name }}</span></label>
            </template>
            <label v-for="list in completionUngroupedLists" :key="list.publicId" class="clone-completion-list-option"><input type="checkbox" :checked="completedListDraft === null || completedListDraft.includes(list.publicId)" @change="toggleCompletedList(list.publicId, $event.target.checked)" /><span class="clone-completion-list-dot" :style="{ backgroundColor: list.color || 'transparent' }"></span><span>{{ list.name }}</span></label>
            <footer><button type="button" @click="cancelCompletedListSelection">取消</button><button type="button" class="clone-completion-confirm" @click="applyCompletedListSelection">确定</button></footer>
          </div>
        </div>
        <div class="clone-completion-filter">
          <button type="button" class="clone-completion-filter-trigger" :aria-expanded="completionFilterMenu === 'scope'" @click="toggleCompletionFilterMenu('scope')">{{ completionScopeLabel }}<ChevronDown :size="14" /></button>
          <div v-if="completionFilterMenu === 'scope'" class="clone-completion-filter-menu" role="menu" :aria-label="isAbandonedRoute ? '放弃范围' : '完成范围'">
            <button v-for="option in completionScopeOptions" :key="option.value" type="button" role="menuitemradio" :aria-checked="completionFilters.scope === option.value" :class="{ selected: completionFilters.scope === option.value }" @click="completionFilters.scope = option.value; completionFilterMenu = ''">{{ option.label }}<Check v-if="completionFilters.scope === option.value" :size="15" /></button>
          </div>
        </div>
      </div>
      <div v-if="moreOpen" class="clone-floating-menu clone-view-menu" role="menu" aria-label="分组与排序设置" :style="workspaceMenuStyle" @click.stop>
        <template v-if="!workspaceMenuSection">
          <button type="button" class="clone-menu-submenu-trigger" role="menuitem" @click="workspaceMenuSection = 'group'"><Layers3 :size="18" /><span>分组</span><small>{{ groupOptionLabel }}</small><ChevronRight :size="16" /></button>
          <button type="button" class="clone-menu-submenu-trigger" role="menuitem" @click="workspaceMenuSection = 'sort'"><ListFilter :size="18" /><span>排序</span><small>{{ sortOptionLabel }}</small><ChevronRight :size="16" /></button>
          <button type="button" class="clone-menu-submenu-trigger" role="menuitem" @click="workspaceMenuSection = 'direction'"><ArrowDownUp :size="18" /><span>顺序</span><small>{{ sortDirectionLabel }}</small><ChevronRight :size="16" /></button>
          <hr />
          <button type="button" class="clone-menu-reset" :disabled="sort === 'time' && sortDirection === 'asc' && groupBy === 'time'" @click="sort = 'time'; sortDirection = 'asc'; groupBy = 'time'; closeWorkspaceMenus()">恢复默认时间顺序</button>
        </template>
        <template v-else>
          <button type="button" class="clone-menu-back" @click="workspaceMenuSection = ''"><ChevronLeft :size="16" />{{ workspaceMenuHeading }}</button>
          <button v-for="option in workspaceMenuOptions" :key="option.value" type="button" role="menuitemradio" :aria-checked="workspaceMenuValue === option.value" @click="selectWorkspaceMenuOption(option.value)">
            <Check v-if="workspaceMenuValue === option.value" :size="14" /><span v-else class="clone-menu-check-spacer"></span>{{ option.label }}
          </button>
        </template>
      </div>
      <div v-if="listOptionsOpen" class="clone-floating-menu clone-view-menu clone-list-options-menu" role="menu" aria-label="中间栏更多选项" :style="workspaceMenuStyle" @click.stop>
        <strong>视图</strong>
        <div class="clone-view-segmented" role="group" aria-label="视图">
          <button v-for="option in workspaceViews" :key="option.value" type="button" :aria-label="option.label" :title="option.label" :aria-pressed="activeWorkspaceView === option.value" @click="switchWorkspaceView(option.value)"><component :is="option.icon" :size="21" /></button>
        </div>
        <hr />
        <button type="button" role="menuitemcheckbox" :aria-checked="hideCompleted" @click="hideCompleted = !hideCompleted; listOptionsOpen = false"><Check v-if="hideCompleted" :size="14" /><span v-else class="clone-menu-check-spacer"></span>隐藏已完成</button>
        <button type="button" role="menuitemcheckbox" :aria-checked="showDescription" @click="showDescription = !showDescription; listOptionsOpen = false"><Check v-if="showDescription" :size="14" /><span v-else class="clone-menu-check-spacer"></span>显示详细</button>
        <button v-if="showChecklistMenuItem" type="button" role="menuitemcheckbox" :aria-checked="showChecklist" @click="showChecklist = !showChecklist; listOptionsOpen = false"><Check v-if="showChecklist" :size="14" /><span v-else class="clone-menu-check-spacer"></span>显示检查事项</button>
        <button type="button" @click="openDisplaySettings"><SlidersHorizontal :size="14" />显示设置</button>
        <template v-if="isOrdinaryTaskListView">
          <button type="button" @click="addGroupForCurrentList"><Plus :size="14" />添加分组</button>
          <button type="button" @click="shareCurrentList"><Copy :size="14" />共享</button>
        </template>
        <button v-if="isOrdinaryTaskListView || route.name === 'tasks-inbox'" type="button" @click="openListActivity"><History :size="14" />清单动态</button>
        <div class="clone-print-submenu" @pointerenter="printOptionsOpen = true" @pointerleave="printOptionsOpen = false">
          <button type="button" class="clone-print-submenu-trigger" :aria-expanded="printOptionsOpen" @click="printOptionsOpen = true"><Printer :size="14" /><span>打印</span><ChevronRight :size="14" /></button>
          <div v-if="printOptionsOpen" class="clone-print-submenu-options" role="menu" aria-label="打印选项" @pointerdown.stop @click.stop>
            <button type="button" role="menuitem" @click="printTaskList('TITLE')">打印标题</button>
            <button type="button" role="menuitem" @click="printTaskList('CONTENT')">打印标题和内容</button>
          </div>
        </div>
      </div>
      <div class="clone-quick-add-anchor">
      <div v-if="!isTrashRoute && !isCompletionRoute" class="clone-quick-add" :class="{ 'is-simple': quickInputMode === 'SIMPLE', 'is-collapsed': !quickAddExpanded }">
        <button v-if="!quickAddExpanded" type="button" class="clone-quick-add-collapsed" @click="expandQuickAdd"><Plus :size="17" /><span>添加任务至“{{ title === '今天' ? '收集箱' : title }}”</span></button>
        <form v-else class="clone-quick-add-form" @submit.prevent="quickAdd">
          <div class="clone-quick-title-row">
            <Repeat2 v-if="quickRepeatRuleActive" class="clone-quick-repeat-badge" :size="14" />
            <input
              ref="quickTitleInput"
              v-model="quickTitle"
              aria-label="快速添加任务标题"
              placeholder="准备做什么？"
              @input="updateQuickSuggestion"
              @keydown="handleQuickSuggestionKeydown"
            />
          </div>
          <div class="clone-quick-actions-row">
            <button ref="quickDateToggleRef" type="button" class="clone-quick-action clone-quick-date-action" :class="{ active: quickEntry.dateSelected && Boolean(quickEntry.dueAt) }" aria-label="快速设置日期和提醒" :aria-expanded="quickAction === 'date'" @click="toggleQuickAction('date')"><CalendarDays :size="15" /><span>{{ quickDateSummary }}</span></button>
            <button v-if="!quickAddIsNote" ref="quickPriorityToggleRef" type="button" class="clone-quick-action clone-quick-priority-action" :class="{ active: quickEntry.priority !== 'NONE' }" :data-priority="quickEntry.priority" aria-label="快速设置优先级" :aria-expanded="quickAction === 'priority'" @click="toggleQuickAction('priority')"><Flag :size="15" /></button>
            <button v-if="!quickAddIsNote" ref="quickListToggleRef" type="button" class="clone-quick-action clone-quick-list-action" :class="{ active: quickEntry.listSelected && Boolean(quickEntry.listId) }" aria-label="快速选择清单" :aria-expanded="quickAction === 'list'" @click="toggleQuickAction('list')"><TaskOrganizationIcon v-if="quickSelectedList?.icon" :value="quickSelectedList.icon" :size="15" /><ListTodo v-else :size="15" /><i v-if="quickSelectedList?.color" class="clone-quick-list-dot" :style="{ backgroundColor: quickSelectedList.color }" aria-hidden="true"></i><span>{{ quickListSummary }}</span></button>
            <button v-if="!quickAddIsNote" ref="quickMoreToggleRef" type="button" class="clone-quick-icon-action" aria-label="快速新增更多操作" :aria-expanded="quickAction === 'more' || quickAction === 'tags'" @click="toggleQuickAction('more')"><Ellipsis :size="16" /></button>
            <button type="submit" class="clone-quick-submit" :disabled="!quickTitle.trim() || quickParsing || quickSaving">{{ quickParsing ? '解析中…' : quickSaving ? '添加中…' : '添加' }}</button>
          </div>
        </form>
        <input ref="quickAttachmentInput" class="clone-quick-file-input" type="file" multiple aria-label="选择快捷新增附件" @change="selectQuickAttachments" />
        <div v-if="quickSuggestion && quickSuggestionOptions.length" class="clone-quick-suggestions" role="listbox" :aria-label="quickSuggestionLabel">
          <button v-for="(option, index) in quickSuggestionOptions" :key="option.id" type="button" role="option" :aria-selected="index === quickSuggestionIndex" :class="{ active: index === quickSuggestionIndex }" @pointerdown.prevent="selectQuickSuggestion(option)"><span v-if="option.icon" class="clone-quick-suggestion-icon">{{ option.icon }}</span><Flag v-else-if="quickSuggestion.kind === 'priority'" :size="14" /><Tag v-else-if="quickSuggestion.kind === 'tag'" :size="14" /><ListTodo v-else :size="14" /><span>{{ option.label }}</span><small v-if="option.meta">{{ option.meta }}</small></button>
        </div>
      </div>
      <div v-if="quickAttachments.length" class="clone-quick-attachments" aria-label="已选择附件">
        <span v-for="item in quickAttachments" :key="item.id"><Paperclip :size="13" /><span>{{ item.name }}</span><small>{{ formatAttachmentSize(item.size) }}</small><button type="button" :aria-label="`移除附件 ${item.name}`" @click="removeQuickAttachment(item.id)"><X :size="13" /></button></span>
      </div>
      <div v-for="batch in pendingQuickAttachmentBatches" :key="batch.id" class="clone-quick-attachments" aria-label="待上传附件">
        <span v-for="item in batch.attachments" :key="item.id"><Paperclip :size="13" /><span>{{ item.name }}</span><small>{{ formatAttachmentSize(item.size) }}</small><button type="button" :disabled="quickAttachmentUploading" :aria-label="`移除待上传附件 ${item.name}`" @click="removePendingQuickAttachment(batch.id, item.id)"><X :size="13" /></button></span>
      </div>
      <p v-if="quickAttachmentMessage" class="clone-quick-attachment-state" :role="quickAttachmentError || quickAttachmentSelectionError ? 'alert' : 'status'">{{ quickAttachmentMessage }}<button v-if="pendingQuickAttachmentBatches.length && (quickAttachmentError || !store.online)" type="button" :disabled="quickAttachmentUploading" @click="retryQuickAttachments">{{ quickAttachmentUploading ? `上传中 ${quickAttachmentProgress}%` : '重试附件上传' }}</button></p>
      <div v-if="quickAction === 'date'" ref="quickPanelMenuRef" class="clone-quick-action-panel is-date" :style="quickPanelStyle" @click.stop>
        <template v-if="quickAction === 'date'">
          <TaskDateTimePopover
            :mode="quickDateMode"
            :month="quickCalendarMonth"
            :calendar-days="quickDateTimeCalendarDays"
            :selected-key="quickSelectedDate"
            :time="quickEntry.allDay ? '' : quickDateTime"
            :all-day="quickEntry.allDay"
            :month-label="quickCalendarTitle"
            :range-start="quickRangeStart"
            :range-end="quickRangeEnd"
            :range-start-time="quickRangeStartTime"
            :range-end-time="quickRangeEndTime"
            apply-label="确定"
            @update:mode="setQuickDateMode"
            @quick-date="chooseQuickDatePreset"
            @move-month="moveQuickMonth"
            @select-date="selectQuickCalendarDate"
            @select-time="selectQuickTime"
            @clear-time="clearQuickTime"
            @clear-date="clearQuickDate"
            @toggle-all-day="setQuickAllDay"
            @update:range-start="quickRangeStart = $event"
            @update:range-end="quickRangeEnd = $event"
            @update:range-start-time="quickRangeStartTime = $event"
            @update:range-end-time="quickRangeEndTime = $event"
            @apply="applyQuickDate"
          >
            <template #rows="{ mode: rowMode }">
              <div class="clone-date-prop-rows" aria-label="任务属性">
                <div v-if="rowMode === 'DATE'" class="clone-date-prop-row">
                  <button ref="quickTimeToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: Boolean(quickDateTime) }" :aria-expanded="quickDateMenu === 'time'" @click="toggleQuickDateMenu('time')"><Clock3 :size="15" /><span>{{ quickDateTime || '时间' }}</span><ChevronRight :size="14" :class="{ 'is-open': quickDateMenu === 'time' }" /></button>
                  <button v-if="quickDateTime" type="button" class="clone-date-prop-clear" aria-label="清除时间" title="清除时间" @click="clearQuickTime"><X :size="14" /></button>
                </div>
                <div v-if="quickDateMenu === 'time'" class="clone-date-time-list" role="listbox" aria-label="选择时间">
                  <button v-for="option in quickTimeOptions" :key="option" type="button" role="option" :aria-selected="option === quickDateTime" :class="{ active: option === quickDateTime }" @click="selectQuickTime(option)">{{ option }}<Check v-if="option === quickDateTime" :size="14" /></button>
                </div>
                <div class="clone-date-prop-row">
                  <button ref="quickReminderToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: quickAddReminder.enabled }" :aria-expanded="quickDateMenu === 'reminder'" @click="toggleQuickDateMenu('reminder')"><Bell :size="15" /><span>{{ quickAddReminder.presets.length ? quickReminderSummary : '提醒' }}</span><ChevronRight :size="14" :class="{ 'is-open': quickDateMenu === 'reminder' }" /></button>
                  <button v-if="quickAddReminder.presets.length" type="button" class="clone-date-prop-clear" aria-label="清除全部提醒" title="清除全部提醒" @click="clearQuickReminders"><X :size="14" /></button>
                </div>
                <div class="clone-date-prop-row">
                  <button ref="quickRepeatToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: Boolean(buildQuickRepeatRule(quickRepeat)) }" :aria-expanded="quickDateMenu === 'repeat'" @click="toggleQuickDateMenu('repeat')"><Repeat2 :size="15" /><span>{{ quickRepeatLabel(quickRepeat) }}</span><ChevronRight :size="14" :class="{ 'is-open': quickDateMenu === 'repeat' }" /></button>
                </div>
                <div v-if="quickShowEndRow" class="clone-date-prop-row">
                  <button ref="quickEndToggleRef" type="button" class="clone-date-prop-trigger" :aria-expanded="quickDateMenu === 'end'" @click="toggleQuickDateMenu('end')"><Timer :size="15" /><span>{{ quickEndLabel }}</span><ChevronRight :size="14" :class="{ 'is-open': quickDateMenu === 'end' }" /></button>
                </div>
              </div>
              <teleport to="body">
                <div v-if="quickDateMenu === 'reminder'" ref="quickReminderMenuRef" class="clone-quick-reminder-menu clone-quick-flyout" :style="quickReminderMenuStyle" role="listbox" aria-label="提醒设置" aria-multiselectable="true" @click.stop>
                  <button v-for="option in quickReminderMenuOptions" :key="option.value" type="button" role="option" :aria-selected="quickAddReminder.presets.includes(option.value)" :class="{ active: quickAddReminder.presets.includes(option.value) }" @click="selectQuickReminder(option.value)">{{ option.label }}<Check v-if="quickAddReminder.presets.includes(option.value)" :size="14" /></button>
                  <template v-if="!quickEntry.allDay && quickReminderRecentOptions.length">
                    <span class="clone-quick-flyout-separator"></span>
                    <span class="clone-quick-flyout-title">最近使用</span>
                    <button v-for="option in quickReminderRecentOptions" :key="`recent-${option.value}`" type="button" role="option" :aria-selected="quickAddReminder.presets.includes(option.value)" :class="{ active: quickAddReminder.presets.includes(option.value) }" @click="selectQuickReminder(option.value)">{{ option.label }}<Check v-if="quickAddReminder.presets.includes(option.value)" :size="14" /></button>
                  </template>
                  <template v-if="quickAddReminder.presets.includes('CUSTOM')">
                    <span class="clone-quick-flyout-separator"></span>
                    <div class="clone-quick-reminder-custom">
                      <label><span>提前</span><input v-model.number="quickAddReminder.amount" type="number" min="1" :max="quickEntry.allDay ? 365 : 10080" aria-label="自定义提醒数量" /><select v-model="quickAddReminder.unit" aria-label="自定义提醒单位"><option v-if="!quickEntry.allDay" value="MINUTE">分钟</option><option v-if="!quickEntry.allDay" value="HOUR">小时</option><option value="DAY">天</option><option value="WEEK">周</option></select></label>
                      <label v-if="quickEntry.allDay">提醒时间<select v-model="quickAddReminder.time" aria-label="自定义提醒时间"><option v-for="option in quickTimeOptions" :key="`reminder-time-${option}`" :value="option">{{ option }}</option></select></label>
                      <p class="clone-quick-reminder-preview">{{ quickReminderCustomPreview }}</p>
                      <div class="clone-quick-flyout-actions"><button type="button" @click="cancelQuickReminderCustom">取消</button><button type="button" class="primary" @click="applyQuickReminderCustom">确定</button></div>
                    </div>
                  </template>
                </div>
                <div v-if="quickDateMenu === 'repeat'" ref="quickRepeatMenuRef" class="clone-quick-repeat-menu clone-quick-flyout" :style="quickRepeatMenuStyle" role="listbox" aria-label="重复规则" @click.stop>
                  <template v-for="option in quickRepeatMenuOptions" :key="option.key">
                    <span v-if="option.separator" class="clone-quick-flyout-separator"></span>
                    <button v-else-if="option.isGroup" type="button" :aria-expanded="quickRepeatGroupOpen === option.key" @click="toggleQuickRepeatGroup(option.key)">{{ option.label }}<ChevronRight :size="14" :class="{ 'is-open': quickRepeatGroupOpen === option.key }" /></button>
                    <button v-else-if="option.child" v-show="quickRepeatGroupOpen === option.groupKey" type="button" role="option" :aria-selected="option.active" :class="{ active: option.active }" @click="onQuickRepeatMenuSelect(option)">{{ option.label }}<Check v-if="option.active" :size="14" /></button>
                    <button v-else type="button" role="option" :aria-selected="option.active" :class="{ active: option.active }" @click="onQuickRepeatMenuSelect(option)">{{ option.label }}<Check v-if="option.active" :size="14" /></button>
                  </template>
                </div>
                <div v-if="quickDateMenu === 'end'" ref="quickEndMenuRef" class="clone-quick-end-menu clone-quick-flyout" :style="quickEndMenuStyle" role="listbox" aria-label="结束重复" @click.stop>
                  <button type="button" role="option" :aria-selected="quickRepeat.endMode === 'NEVER'" :class="{ active: quickRepeat.endMode === 'NEVER' }" @click="selectQuickEndMode('NEVER')">永不结束<Check v-if="quickRepeat.endMode === 'NEVER'" :size="14" /></button>
                  <span class="clone-quick-flyout-separator"></span>
                  <button type="button" :aria-expanded="quickEndPanel === 'date'" @click="toggleQuickEndPanel('date')">按日期结束<ChevronRight :size="14" :class="{ 'is-open': quickEndPanel === 'date' }" /></button>
                  <div v-if="quickEndPanel === 'date'" class="clone-quick-end-calendar">
                    <div class="clone-quick-end-cal-head"><button type="button" aria-label="上个月" @click="moveQuickEndMonth(-1)"><ChevronLeft :size="15" /></button><strong>{{ quickEndCalendarTitle }}</strong><button type="button" aria-label="下个月" @click="moveQuickEndMonth(1)"><ChevronRight :size="15" /></button></div>
                    <div class="clone-quick-end-cal-grid"><span v-for="weekday in quickWeekdays" :key="`end-${weekday}`">{{ weekday }}</span><button v-for="day in quickEndCalendarDays" :key="day.key" type="button" :class="{ 'other-month': !day.inMonth, selected: day.selected }" @click="pickQuickEndDate(day.key)">{{ day.date.getDate() }}</button></div>
                  </div>
                  <button type="button" :aria-expanded="quickEndPanel === 'count'" @click="toggleQuickEndPanel('count')">按次数结束<ChevronRight :size="14" :class="{ 'is-open': quickEndPanel === 'count' }" /></button>
                  <div v-if="quickEndPanel === 'count'" class="clone-quick-end-count">
                    <label>重复<input v-model.number="quickEndCountDraft" type="number" min="1" aria-label="重复次数" />次</label>
                    <div class="clone-quick-flyout-actions"><button type="button" @click="cancelQuickEndCount">取消</button><button type="button" class="primary" @click="applyQuickEndCount">确定</button></div>
                  </div>
                </div>
              </teleport>
            </template>
          </TaskDateTimePopover>
        </template>
      </div>
      <teleport to="body">
        <div v-if="quickAction === 'priority'" ref="quickPriorityMenuRef" class="clone-quick-priority-menu clone-quick-flyout" :style="quickPriorityMenuStyle" role="listbox" aria-label="优先级" @click.stop>
          <button v-for="option in quickPriorityOptions" :key="option.value" type="button" role="option" :aria-selected="quickEntry.priority === option.value" class="clone-quick-priority-option" :data-priority="option.value" :class="{ active: quickEntry.priority === option.value }" @click="selectQuickPriority(option.value)"><Flag :size="17" /><span>{{ option.label }}</span><Check v-if="quickEntry.priority === option.value" :size="15" /></button>
        </div>
        <div v-else-if="quickAction === 'list'" ref="quickListMenuRef" class="clone-quick-list-menu clone-quick-flyout" :style="quickListMenuStyle" role="listbox" aria-label="选择清单" @click.stop>
          <label class="clone-quick-list-search"><Search :size="15" /><input v-model="quickListQuery" type="search" placeholder="搜索" aria-label="搜索清单" /></label>
          <button type="button" role="option" :aria-selected="!quickEntry.listId" :class="{ active: !quickEntry.listId }" @click="selectQuickList('')"><Inbox :size="16" /><span>{{ store.inbox?.name || '收集箱' }}</span><Check v-if="!quickEntry.listId" :size="14" /></button>
          <template v-for="group in quickListMenuGroups" :key="group.publicId">
            <button type="button" class="clone-quick-list-folder" :aria-expanded="quickListFolderExpanded[group.publicId]" @click="quickListFolderExpanded[group.publicId] = !quickListFolderExpanded[group.publicId]"><Folder :size="16" /><span>{{ group.name }}</span><ChevronRight :size="14" :class="{ 'is-open': quickListFolderExpanded[group.publicId] }" /></button>
            <button v-for="list in quickListMenuListsInGroup(group.publicId)" v-show="quickListQuery.trim() || quickListFolderExpanded[group.publicId]" :key="list.publicId" type="button" role="option" :aria-selected="quickEntry.listId === list.publicId" :class="{ active: quickEntry.listId === list.publicId }" @click="selectQuickList(list.publicId)"><TaskOrganizationIcon v-if="list.icon" :value="list.icon" :size="16" /><i v-else-if="list.color" class="clone-quick-list-dot" :style="{ backgroundColor: list.color }" aria-hidden="true"></i><ListTodo v-else :size="16" /><span>{{ list.name }}</span><Check v-if="quickEntry.listId === list.publicId" :size="14" /></button>
          </template>
          <button v-for="list in quickListMenuUngrouped" :key="list.publicId" type="button" role="option" :aria-selected="quickEntry.listId === list.publicId" :class="{ active: quickEntry.listId === list.publicId }" @click="selectQuickList(list.publicId)"><TaskOrganizationIcon v-if="list.icon" :value="list.icon" :size="16" /><i v-else-if="list.color" class="clone-quick-list-dot" :style="{ backgroundColor: list.color }" aria-hidden="true"></i><ListTodo v-else :size="16" /><span>{{ list.name }}</span><Check v-if="quickEntry.listId === list.publicId" :size="14" /></button>
        </div>
        <div v-else-if="quickAction === 'more'" ref="quickMoreMenuRef" class="clone-quick-more-menu clone-quick-flyout" :style="quickMoreMenuStyle" role="menu" aria-label="更多任务属性" @click.stop>
          <button type="button" role="menuitem" aria-label="快速选择标签" :aria-expanded="quickAction === 'tags'" @click="toggleQuickAction('tags', quickFlyoutAnchor)"><Tag :size="15" /><span>标签</span><small v-if="quickEntry.tagIds.length">{{ quickEntry.tagIds.length }}</small><ChevronRight :size="14" /></button>
          <button type="button" role="menuitem" @click="quickFlyoutAnchor ? openBoardAttachmentPicker() : openQuickAttachmentPicker()"><Paperclip :size="15" /><span>附件</span></button>
          <button type="button" role="menuitem" @click="openTaskTemplates"><ClipboardList :size="15" /><span>从模板添加</span></button>
          <button v-if="!quickFlyoutAnchor" type="button" role="menuitem" :disabled="quickAddIsNote || !noteLists.length" :title="quickAddIsNote ? '当前已是笔记' : noteLists.length ? '' : '请先创建笔记清单'" @click="convertQuickAddToNote"><ListTodo :size="15" /><span>转换为笔记</span></button>
        </div>
        <div v-else-if="quickAction === 'tags'" ref="quickTagsMenuRef" class="clone-quick-tags-menu clone-quick-flyout" :style="quickTagsMenuStyle" aria-label="选择标签" @click.stop>
          <div v-if="quickTagsDraft.length" class="clone-quick-tags-selected">
            <span v-for="tag in quickTagsDraftTags" :key="tag.publicId" class="clone-quick-tag-chip">{{ tag.name }}<button type="button" :aria-label="`移除标签 ${tag.name}`" @click="toggleQuickTagsDraft(tag.publicId)"><X :size="12" /></button></span>
          </div>
          <label class="clone-quick-list-search"><Search :size="15" /><input v-model="quickTagsQuery" type="search" placeholder="输入标签" aria-label="搜索标签" /></label>
          <div class="clone-quick-tags-options" role="listbox" aria-label="标签列表" aria-multiselectable="true">
            <button v-for="tag in quickTagsMenuOptions" :key="tag.publicId" type="button" role="option" :aria-selected="quickTagsDraft.includes(tag.publicId)" :class="{ active: quickTagsDraft.includes(tag.publicId) }" @click="toggleQuickTagsDraft(tag.publicId)"><Tag :size="15" /><span>{{ tag.name }}</span><Check v-if="quickTagsDraft.includes(tag.publicId)" :size="14" /></button>
            <p v-if="!quickTagsMenuOptions.length" class="clone-quick-list-empty">没有匹配的标签</p>
          </div>
          <footer class="clone-quick-flyout-actions"><button type="button" @click="cancelQuickTags">取消</button><button type="button" class="primary" @click="applyQuickTags">确定</button></footer>
        </div>
      </teleport>
      <teleport to="body">
        <div v-if="tagFlyout.taskId" class="clone-tag-flyout" :style="tagFlyout.style" @mouseenter="cancelTagFlyoutHide" @mouseleave="hideTagFlyoutSoon">
          <span v-for="tagId in tagFlyout.tagIds" :key="tagId" class="clone-task-tag-wrap" :style="taskTagStyle(tagId)"><RouterLink :to="`/tasks/tag/${tagId}`" class="clone-task-tag" @click="hideTagFlyout">{{ tagName(tagId) }}</RouterLink></span>
        </div>
      </teleport>
      <teleport to="body">
        <div v-if="rowDateTaskId" ref="rowDatePanelRef" class="clone-row-date-panel" :style="rowDateStyle" @click.stop>
          <TaskDateTimePopover
            :mode="rowDatePanel.mode"
            :month="quickCalendarMonth"
            :calendar-days="rowDateCalendarDays"
            :selected-key="rowDatePanel.mode === 'RANGE' ? rowDatePanel.rangeEnd : rowDatePanel.selectedDate"
            :time="rowDatePanel.time"
            :all-day="rowDatePanel.allDay"
            :month-label="quickCalendarTitle"
            :range-start="rowDatePanel.rangeStart"
            :range-end="rowDatePanel.rangeEnd"
            :range-start-time="rowDatePanel.rangeStartTime"
            :range-end-time="rowDatePanel.rangeEndTime"
            apply-label="确定"
            @update:mode="setRowDateMode"
            @quick-date="chooseRowDatePreset"
            @move-month="moveQuickMonth"
            @select-date="selectRowDate"
            @select-time="selectRowTime"
            @clear-time="clearRowTime"
            @clear-date="clearRowDateFromPanel"
            @toggle-all-day="rowDatePanel.allDay = $event"
            @update:range-start="rowDatePanel.rangeStart = $event"
            @update:range-end="rowDatePanel.rangeEnd = $event"
            @update:range-start-time="rowDatePanel.rangeStartTime = $event"
            @update:range-end-time="rowDatePanel.rangeEndTime = $event"
            @apply="applyRowDatePanel"
          >
            <template #rows="{ mode: rowMode }">
              <div v-if="!rowRepeatEditorOpen" class="clone-date-prop-rows" aria-label="任务属性">
                <div v-if="rowMode === 'DATE'" class="clone-date-prop-row">
                  <button type="button" class="clone-date-prop-trigger" :class="{ active: Boolean(rowDatePanel.time) }" :aria-expanded="rowTimeListOpen" @click="rowTimeListOpen = !rowTimeListOpen"><Clock3 :size="15" /><span>{{ rowDatePanel.time || '时间' }}</span><ChevronRight :size="14" :class="{ 'is-open': rowTimeListOpen }" /></button>
                  <button v-if="rowDatePanel.time" type="button" class="clone-date-prop-clear" aria-label="清除时间" title="清除时间" @click="clearRowTime"><X :size="14" /></button>
                </div>
                <div v-if="rowMode === 'DATE' && rowTimeListOpen" class="clone-date-time-list" role="listbox" aria-label="选择时间">
                  <button v-for="option in quickTimeOptions" :key="option" type="button" role="option" :aria-selected="option === rowDatePanel.time" :class="{ active: option === rowDatePanel.time }" @click="selectRowTime(option)">{{ option }}<Check v-if="option === rowDatePanel.time" :size="14" /></button>
                </div>
                <div class="clone-date-prop-row">
                  <button ref="rowReminderToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: rowReminderConfig.presets.length > 0 }" :aria-expanded="rowDateMenu === 'reminder'" @click="toggleRowDateMenu('reminder')"><Bell :size="15" /><span>{{ rowReminderConfig.presets.length ? rowReminderSummary : '提醒' }}</span><ChevronRight :size="14" :class="{ 'is-open': rowDateMenu === 'reminder' }" /></button>
                  <button v-if="rowReminderConfig.presets.length" type="button" class="clone-date-prop-clear" aria-label="清除全部提醒" title="清除全部提醒" @click="clearRowReminders"><X :size="14" /></button>
                </div>
                <div class="clone-date-prop-row">
                  <button ref="rowRepeatToggleRef" type="button" class="clone-date-prop-trigger" :class="{ active: Boolean(buildQuickRepeatRule(rowRepeat)) }" :aria-expanded="rowDateMenu === 'repeat'" @click="toggleRowDateMenu('repeat')"><Repeat2 :size="15" /><span>{{ quickRepeatLabel(rowRepeat) }}</span><ChevronRight :size="14" :class="{ 'is-open': rowDateMenu === 'repeat' }" /></button>
                </div>
                <div v-if="rowShowEndRow" class="clone-date-prop-row">
                  <button ref="rowEndToggleRef" type="button" class="clone-date-prop-trigger" :aria-expanded="rowDateMenu === 'end'" @click="toggleRowDateMenu('end')"><Timer :size="15" /><span>{{ rowEndLabel }}</span><ChevronRight :size="14" :class="{ 'is-open': rowDateMenu === 'end' }" /></button>
                </div>
              </div>
              <section v-else class="clone-quick-repeat-panel" aria-label="自定义重复设置">
                <header><strong>重复</strong><button type="button" aria-label="关闭重复设置" @click="rowRepeatEditorOpen = false"><X :size="14" /></button></header>
                <div class="clone-quick-repeat-mode"><select v-model="rowRepeat.mode" aria-label="重复方式"><option value="DUE_DATE">按到期日期</option><option value="COMPLETION_DATE">按完成日期</option></select></div>
                <div class="clone-quick-repeat-interval"><span>每</span><input v-model.number="rowRepeat.interval" type="number" min="1" max="365" aria-label="重复间隔" /><select :value="rowRepeat.frequency" @change="setRowRepeatFrequency" aria-label="重复频率"><option value="DAILY">日</option><option value="WEEKLY">周</option><option value="MONTHLY">月</option><option value="YEARLY">年</option></select></div>
                <div v-if="rowRepeat.frequency === 'WEEKLY'" class="clone-quick-repeat-circles" aria-label="重复星期"><button v-for="day in DAY_CODES" :key="day" type="button" :aria-pressed="rowRepeat.weekdays.includes(day)" :class="{ active: rowRepeat.weekdays.includes(day) }" @click="toggleRowRepeatWeekday(day)">{{ { MO: '一', TU: '二', WE: '三', TH: '四', FR: '五', SA: '六', SU: '日' }[day] }}</button></div>
                <div v-if="['MONTHLY', 'YEARLY'].includes(rowRepeat.frequency)" class="clone-quick-repeat-grid"><label>重复日期<select v-model="rowRepeat.monthMode"><option value="DATE">按日期</option><option value="WEEKDAY">按星期</option><option v-if="rowRepeat.frequency === 'MONTHLY'" value="WORKDAY">按工作日</option></select></label><label v-if="rowRepeat.frequency === 'YEARLY'">月份<select v-model.number="rowRepeat.month"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><template v-if="rowRepeat.monthMode === 'WEEKDAY'"><label>第几周<select v-model.number="rowRepeat.ordinal"><option v-for="ordinal in 5" :key="ordinal" :value="ordinal">第 {{ ordinal }} 个</option><option :value="-1">最后一个</option></select></label><label>星期<select v-model="rowRepeat.weekday"><option v-for="day in DAY_CODES" :key="day" :value="day">{{ { MO: '周一', TU: '周二', WE: '周三', TH: '四', FR: '周五', SA: '周六', SU: '周日' }[day] }}</option></select></label></template><label v-if="rowRepeat.monthMode === 'WORKDAY'">工作日<select v-model.number="rowRepeat.workdayPosition"><option :value="1">第一个工作日</option><option :value="-1">最后一个工作日</option></select></label></div>
                <div v-if="['MONTHLY', 'YEARLY'].includes(rowRepeat.frequency) && rowRepeat.monthMode === 'DATE'" class="clone-quick-repeat-date-calendar" aria-label="选择重复日期"><span v-for="weekday in quickWeekdays" :key="`row-repeat-${weekday}`">{{ weekday }}</span><span v-for="(day, index) in rowRepeatCalendarDays.filter(value => !value)" :key="`row-repeat-day-${index}`" class="clone-quick-repeat-date-spacer"></span><button v-for="day in rowRepeatCalendarDays.filter(Boolean)" :key="`row-repeat-date-${day}`" type="button" :aria-pressed="!rowRepeat.lastDay && rowRepeat.monthDays.includes(day)" :class="{ active: !rowRepeat.lastDay && rowRepeat.monthDays.includes(day) }" @click="toggleRowRepeatMonthDay(day)">{{ day }}</button><button type="button" class="clone-quick-repeat-last-day" :aria-pressed="rowRepeat.lastDay" :class="{ active: rowRepeat.lastDay }" @click="rowRepeat.lastDay = true">最后一天</button></div>
                <div class="clone-quick-repeat-checks"><label v-if="rowRepeat.frequency !== 'WEEKLY' && rowRepeat.monthMode !== 'WORKDAY'" class="clone-quick-repeat-check"><input v-model="rowRepeat.skipWeekends" type="checkbox" />跳过双休日</label><label class="clone-quick-repeat-check"><input v-model="rowRepeat.skipLegalHolidays" type="checkbox" />跳过法定节假日</label></div>
                <p v-if="rowRepeatError" class="clone-error" role="alert">{{ rowRepeatError }}</p>
                <footer><button type="button" @click="cancelRowRepeat">取消</button><button type="button" class="clone-quick-date-apply" @click="applyRowRepeat">确定</button></footer>
              </section>
              <teleport to="body">
                <div v-if="rowDateMenu === 'reminder' && !rowRepeatEditorOpen" ref="rowReminderMenuRef" class="clone-quick-reminder-menu clone-quick-flyout" :style="rowReminderMenuStyle" role="listbox" aria-label="提醒设置" aria-multiselectable="true" @click.stop>
                  <button v-for="option in rowReminderMenuOptions" :key="option.value" type="button" role="option" :aria-selected="rowReminderConfig.presets.includes(option.value)" :class="{ active: rowReminderConfig.presets.includes(option.value) }" @click="selectRowReminder(option.value)">{{ option.label }}<Check v-if="rowReminderConfig.presets.includes(option.value)" :size="14" /></button>
                  <template v-if="!rowDatePanel.allDay && rowReminderRecentOptions.length">
                    <span class="clone-quick-flyout-separator"></span>
                    <span class="clone-quick-flyout-title">最近使用</span>
                    <button v-for="option in rowReminderRecentOptions" :key="`row-recent-${option.value}`" type="button" role="option" :aria-selected="rowReminderConfig.presets.includes(option.value)" :class="{ active: rowReminderConfig.presets.includes(option.value) }" @click="selectRowReminder(option.value)">{{ option.label }}<Check v-if="rowReminderConfig.presets.includes(option.value)" :size="14" /></button>
                  </template>
                  <template v-if="rowReminderConfig.presets.includes('CUSTOM')">
                    <span class="clone-quick-flyout-separator"></span>
                    <div class="clone-quick-reminder-custom">
                      <label><span>提前</span><input v-model.number="rowReminderConfig.amount" type="number" min="1" :max="rowDatePanel.allDay ? 365 : 10080" aria-label="自定义提醒数量" /><select v-model="rowReminderConfig.unit" aria-label="自定义提醒单位"><option v-if="!rowDatePanel.allDay" value="MINUTE">分钟</option><option v-if="!rowDatePanel.allDay" value="HOUR">小时</option><option value="DAY">天</option><option value="WEEK">周</option></select></label>
                      <label v-if="rowDatePanel.allDay">提醒时间<select v-model="rowReminderConfig.time" aria-label="自定义提醒时间"><option v-for="option in quickTimeOptions" :key="`row-reminder-time-${option}`" :value="option">{{ option }}</option></select></label>
                      <p class="clone-quick-reminder-preview">{{ rowReminderCustomPreview }}</p>
                      <div class="clone-quick-flyout-actions"><button type="button" @click="cancelRowReminderCustom">取消</button><button type="button" class="primary" @click="applyRowReminderCustom">确定</button></div>
                    </div>
                  </template>
                </div>
                <div v-if="rowDateMenu === 'repeat' && !rowRepeatEditorOpen" ref="rowRepeatMenuRef" class="clone-quick-repeat-menu clone-quick-flyout" :style="rowRepeatMenuStyle" role="listbox" aria-label="重复规则" @click.stop>
                  <template v-for="option in rowRepeatMenuOptions" :key="option.key">
                    <span v-if="option.separator" class="clone-quick-flyout-separator"></span>
                    <button v-else-if="option.isGroup" type="button" :aria-expanded="rowRepeatGroupOpen === option.key" @click="rowRepeatGroupOpen = rowRepeatGroupOpen === option.key ? '' : option.key">{{ option.label }}<ChevronRight :size="14" :class="{ 'is-open': rowRepeatGroupOpen === option.key }" /></button>
                    <button v-else-if="option.child" v-show="rowRepeatGroupOpen === option.groupKey" type="button" role="option" :aria-selected="option.active" :class="{ active: option.active }" @click="onRowRepeatMenuSelect(option)">{{ option.label }}<Check v-if="option.active" :size="14" /></button>
                    <button v-else type="button" role="option" :aria-selected="option.active" :class="{ active: option.active }" @click="onRowRepeatMenuSelect(option)">{{ option.label }}<Check v-if="option.active" :size="14" /></button>
                  </template>
                </div>
                <div v-if="rowDateMenu === 'end' && !rowRepeatEditorOpen" ref="rowEndMenuRef" class="clone-quick-end-menu clone-quick-flyout" :style="rowEndMenuStyle" role="listbox" aria-label="结束重复" @click.stop>
                  <button type="button" role="option" :aria-selected="rowRepeat.endMode === 'NEVER'" :class="{ active: rowRepeat.endMode === 'NEVER' }" @click="selectRowEndMode('NEVER')">永不结束<Check v-if="rowRepeat.endMode === 'NEVER'" :size="14" /></button>
                  <span class="clone-quick-flyout-separator"></span>
                  <button type="button" :aria-expanded="rowEndPanel === 'date'" @click="toggleRowEndPanel('date')">按日期结束<ChevronRight :size="14" :class="{ 'is-open': rowEndPanel === 'date' }" /></button>
                  <div v-if="rowEndPanel === 'date'" class="clone-quick-end-calendar">
                    <div class="clone-quick-end-cal-head"><button type="button" aria-label="上个月" @click="moveRowEndMonth(-1)"><ChevronLeft :size="15" /></button><strong>{{ rowEndCalendarTitle }}</strong><button type="button" aria-label="下个月" @click="moveRowEndMonth(1)"><ChevronRight :size="15" /></button></div>
                    <div class="clone-quick-end-cal-grid"><span v-for="weekday in quickWeekdays" :key="`row-end-${weekday}`">{{ weekday }}</span><button v-for="day in rowEndCalendarDays" :key="day.key" type="button" :class="{ 'other-month': !day.inMonth, selected: day.selected }" @click="pickRowEndDate(day.key)">{{ day.date.getDate() }}</button></div>
                  </div>
                  <button type="button" :aria-expanded="rowEndPanel === 'count'" @click="toggleRowEndPanel('count')">按次数结束<ChevronRight :size="14" :class="{ 'is-open': rowEndPanel === 'count' }" /></button>
                  <div v-if="rowEndPanel === 'count'" class="clone-quick-end-count">
                    <label>重复<input v-model.number="rowEndCountDraft" type="number" min="1" aria-label="重复次数" />次</label>
                    <div class="clone-quick-flyout-actions"><button type="button" @click="cancelRowEndCount">取消</button><button type="button" class="primary" @click="applyRowEndCount">确定</button></div>
                  </div>
                </div>
              </teleport>
            </template>
          </TaskDateTimePopover>
        </div>
      </teleport>
      <div v-if="quickAction === 'repeat'" ref="quickPanelMenuRef" class="clone-quick-action-panel is-repeat" :style="quickPanelStyle" @click.stop>
          <section class="clone-quick-repeat-panel" aria-label="自定义重复设置">
            <header><strong>重复</strong><button type="button" aria-label="关闭重复设置" @click="quickAction = ''"><X :size="14" /></button></header>
            <template v-if="quickRepeat.preset === 'CUSTOM'">
              <div class="clone-quick-repeat-mode"><select v-model="quickRepeat.mode" aria-label="重复方式"><option value="DUE_DATE">按到期日期</option><option value="COMPLETION_DATE">按完成日期</option></select></div>
              <div class="clone-quick-repeat-interval"><span>每</span><input v-model.number="quickRepeat.interval" type="number" min="1" max="365" aria-label="重复间隔" /><select :value="quickRepeat.frequency" @change="setQuickRepeatFrequency" aria-label="重复频率"><option value="DAILY">日</option><option value="WEEKLY">周</option><option value="MONTHLY">月</option><option value="YEARLY">年</option></select></div>
              <div v-if="quickRepeat.frequency === 'WEEKLY'" class="clone-quick-repeat-circles" aria-label="重复星期"><button v-for="day in DAY_CODES" :key="day" type="button" :aria-pressed="quickRepeat.weekdays.includes(day)" :class="{ active: quickRepeat.weekdays.includes(day) }" @click="toggleQuickRepeatWeekday(day)">{{ { MO: '一', TU: '二', WE: '三', TH: '四', FR: '五', SA: '六', SU: '日' }[day] }}</button></div>
              <div v-if="['MONTHLY', 'YEARLY'].includes(quickRepeat.frequency)" class="clone-quick-repeat-grid"><label>重复日期<select v-model="quickRepeat.monthMode"><option value="DATE">按日期</option><option value="WEEKDAY">按星期</option><option v-if="quickRepeat.frequency === 'MONTHLY'" value="WORKDAY">按工作日</option></select></label><label v-if="quickRepeat.frequency === 'YEARLY'">月份<select v-model.number="quickRepeat.month"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><template v-if="quickRepeat.monthMode === 'WEEKDAY'"><label>第几周<select v-model.number="quickRepeat.ordinal"><option v-for="ordinal in 5" :key="ordinal" :value="ordinal">第 {{ ordinal }} 个</option><option :value="-1">最后一个</option></select></label><label>星期<select v-model="quickRepeat.weekday"><option v-for="day in DAY_CODES" :key="day" :value="day">{{ { MO: '周一', TU: '周二', WE: '周三', TH: '周四', FR: '周五', SA: '周六', SU: '周日' }[day] }}</option></select></label></template><label v-if="quickRepeat.monthMode === 'WORKDAY'">工作日<select v-model.number="quickRepeat.workdayPosition"><option :value="1">第一个工作日</option><option :value="-1">最后一个工作日</option></select></label></div>
              <div v-if="['MONTHLY', 'YEARLY'].includes(quickRepeat.frequency) && quickRepeat.monthMode === 'DATE'" class="clone-quick-repeat-date-calendar" aria-label="选择重复日期"><span v-for="weekday in quickWeekdays" :key="`repeat-${weekday}`">{{ weekday }}</span><span v-for="(day, index) in quickRepeatCalendarDays.filter(value => !value)" :key="`repeat-day-${index}`" class="clone-quick-repeat-date-spacer"></span><button v-for="day in quickRepeatCalendarDays.filter(Boolean)" :key="`repeat-date-${day}`" type="button" :aria-pressed="!quickRepeat.lastDay && quickRepeat.monthDays.includes(day)" :class="{ active: !quickRepeat.lastDay && quickRepeat.monthDays.includes(day) }" @click="toggleQuickRepeatMonthDay(day)">{{ day }}</button><button type="button" class="clone-quick-repeat-last-day" :aria-pressed="quickRepeat.lastDay" :class="{ active: quickRepeat.lastDay }" @click="quickRepeat.lastDay = true">最后一天</button></div>
              <div class="clone-quick-repeat-checks"><label v-if="quickRepeat.frequency !== 'WEEKLY' && quickRepeat.monthMode !== 'WORKDAY'" class="clone-quick-repeat-check"><input v-model="quickRepeat.skipWeekends" type="checkbox" />跳过双休日</label><label class="clone-quick-repeat-check"><input v-model="quickRepeat.skipLegalHolidays" type="checkbox" />跳过法定节假日</label></div>
            </template>
            <div v-if="quickRepeat.preset === 'LUNAR_MONTHLY' || quickRepeat.preset === 'LUNAR_YEARLY'" class="clone-quick-repeat-grid"><label v-if="quickRepeat.preset === 'LUNAR_YEARLY'">农历月份<select v-model.number="quickRepeat.lunarMonth"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><label>农历日期<select v-model.number="quickRepeat.lunarDay"><option v-for="day in 30" :key="day" :value="day">{{ day }} 日</option></select></label></div>
            <p v-if="quickRepeatError" class="clone-error" role="alert">{{ quickRepeatError }}</p>
            <footer><button type="button" @click="cancelQuickRepeat">取消</button><button type="button" class="clone-quick-date-apply" @click="applyQuickRepeat">确定</button></footer>
          </section>
      </div>
      </div>
      <div v-if="displaySettingsOpen" class="clone-modal-backdrop" @click.self="displaySettingsOpen = false">
        <section class="clone-display-settings" role="dialog" aria-modal="true" aria-label="显示设置">
          <header><h2>显示设置</h2><button type="button" aria-label="关闭显示设置" @click="displaySettingsOpen = false"><X :size="16" /></button></header>
          <label><input v-model="showTags" type="checkbox" />显示标签</label>
          <label><input v-model="showListLink" type="checkbox" />显示所属清单</label>
          <label><input v-model="showDescription" type="checkbox" />显示详细内容图标</label>
          <label><input v-model="showChecklist" type="checkbox" />显示检查事项进度</label>
          <footer><button class="clone-primary-button" type="button" @click="displaySettingsOpen = false">完成</button></footer>
        </section>
      </div>
      <Teleport to="body">
      <div v-if="taskTemplatesOpen" class="clone-modal-backdrop clone-task-template-backdrop" @click.self="taskTemplatesOpen = false">
        <section class="clone-task-template-dialog" role="dialog" aria-modal="true" aria-label="任务模板" @keydown.esc.stop.prevent="taskTemplatesOpen = false">
          <header>
            <h2>任务模板</h2>
            <label><Search :size="15" /><input v-model="taskTemplateQuery" aria-label="搜索任务模板" placeholder="搜索" /></label>
            <button type="button" aria-label="关闭任务模板" @click="taskTemplatesOpen = false"><X :size="17" /></button>
          </header>
          <div v-if="filteredQuickTaskTemplates.length" class="clone-task-template-grid">
            <button v-for="template in filteredQuickTaskTemplates" :key="template.id" type="button" class="clone-task-template-card" @click="applyQuickTaskTemplate(template)">
              <strong>{{ template.title }}</strong>
              <ul v-if="template.checklist.length"><li v-for="item in template.checklist.slice(0, 7)" :key="item"><span aria-hidden="true"></span>{{ item }}</li></ul>
              <p v-else>{{ template.description }}</p>
            </button>
          </div>
          <p v-else class="clone-task-template-empty">没有匹配的模板</p>
          <footer><button type="button" @click="message.info('模板管理暂未开放；选择内置模板可直接填入快捷新增框')">管理模板</button></footer>
        </section>
      </div>
      </Teleport>
      <p v-if="quickParseError" class="clone-error" role="alert">{{ quickParseError }}</p>
      <div v-if="store.error" class="clone-error" role="alert">
        {{ store.error }}
      </div>
      <div v-if="store.conflicts.length" class="clone-error" role="alert">
        {{ store.conflicts.length }} 项变更需要处理冲突；本地数据已保留。
        <div v-for="conflict in store.conflicts" :key="conflict.id" class="clone-conflict-actions">
          <span>{{ conflict.operation?.payload?.name || conflict.operation?.payload?.title || conflict.operation?.entityId }}（本地版本 {{ conflict.operation?.baseRevision }} / 服务端版本 {{ conflict.result?.serverRevision }}）</span>
          <button type="button" :disabled="resolvingTaskConflict" @click="resolveTaskConflict(conflict, 'local')">保留本地并重试</button>
          <button type="button" :disabled="resolvingTaskConflict" @click="resolveTaskConflict(conflict, 'server')">采用服务端</button>
        </div>
      </div>
      <div v-if="activeWorkspaceView === 'LIST'" class="clone-list-toolbar"><span>{{ selectedIds.size ? `已选 ${selectedIds.size} 项` : `${visibleTasks.length} 项任务` }}</span></div>
      <div v-if="activeWorkspaceView === 'KANBAN'" class="clone-inline-board" aria-label="任务看板">
        <section v-for="group in groups" :key="group.key" class="clone-inline-board-column">
          <header class="clone-inline-board-column-head"><div><strong>{{ group.label }}</strong><small>{{ group.tasks.length }}</small></div><button type="button" :aria-label="`在${group.label}添加任务`" :title="`在${group.label}添加任务`" @click="openBoardQuickAdd(group)"><Plus :size="17" /></button></header>
          <form v-if="boardQuickAddOpen && boardQuickAddGroup?.key === group.key" class="clone-board-quick-inline" @submit.prevent="saveBoardQuickAdd" @keydown.esc.stop.prevent="closeBoardQuickAdd">
            <div class="clone-board-quick-entry" :class="{ 'has-error': boardQuickAddError }">
              <input ref="boardQuickAddInput" v-model.trim="boardQuickAddTitle" autofocus maxlength="500" placeholder="准备做什么？" aria-label="看板任务标题" />
              <div class="clone-board-quick-actions">
                <button type="button" class="clone-board-quick-action clone-board-quick-date-action" :class="{ active: quickEntry.dateSelected && Boolean(quickEntry.dueAt) }" aria-label="设置日期" title="设置日期" :aria-expanded="quickAction === 'date'" @click="toggleQuickAction('date', $event.currentTarget)"><CalendarDays :size="16" /></button>
                <button type="button" class="clone-board-quick-action clone-board-quick-priority-action" :class="{ active: quickEntry.priority !== 'NONE' }" :data-priority="quickEntry.priority" aria-label="设置优先级" title="设置优先级" :aria-expanded="quickAction === 'priority'" @click="toggleQuickAction('priority', $event.currentTarget)"><Flag :size="16" /></button>
                <button type="button" class="clone-board-quick-action clone-board-quick-list-action" :class="{ active: quickEntry.listSelected && Boolean(quickEntry.listId) }" aria-label="选择清单" title="选择清单" :aria-expanded="quickAction === 'list'" @click="toggleQuickAction('list', $event.currentTarget)"><TaskOrganizationIcon v-if="quickSelectedList?.icon" :value="quickSelectedList.icon" :size="16" /><ListTodo v-else :size="16" /></button>
                <button type="button" class="clone-board-quick-icon-action" aria-label="更多任务属性" title="更多任务属性" :aria-expanded="quickAction === 'more' || quickAction === 'tags'" @click="toggleQuickAction('more', $event.currentTarget)"><Ellipsis :size="16" /></button>
                <button type="submit" class="clone-board-quick-submit" aria-label="添加任务" title="添加任务" :disabled="!boardQuickAddTitle || boardQuickAddSaving"><ArrowRight :size="18" /></button>
              </div>
              <input ref="boardQuickAttachmentInput" class="clone-quick-file-input" type="file" multiple aria-label="选择看板新增附件" @change="selectBoardQuickAttachments" />
              <div v-if="boardQuickAddAttachments.length" class="clone-board-quick-attachments" aria-label="已选择看板附件"><span v-for="item in boardQuickAddAttachments" :key="item.id"><Paperclip :size="12" /><span>{{ item.name }}</span><button type="button" :aria-label="`移除附件 ${item.name}`" @click="boardQuickAddAttachments = boardQuickAddAttachments.filter(value => value.id !== item.id)"><X :size="12" /></button></span></div>
              <p v-if="boardQuickAddError" class="clone-board-quick-error" role="alert">{{ boardQuickAddError }}</p>
            </div>
          </form>
          <article v-for="task in group.tasks" :key="task.publicId" class="clone-inline-board-card" :class="{ selected: selectedTask?.publicId === task.publicId, checked: task.status === 'COMPLETED', 'is-note': isNoteTask(task) }" :data-priority="task.priority" tabindex="0" @click="openTaskRow(task, $event)" @keydown.enter.prevent="openTaskRow(task, $event)">
            <div class="clone-inline-board-card-title">
              <button v-if="!isNoteTask(task)" type="button" class="clone-inline-board-check" :class="{ completed: task.status === 'COMPLETED' }" :aria-label="`${task.status === 'COMPLETED' ? '重新打开' : '完成'}${task.title}`" @click.stop="toggleComplete(task)"><Check v-if="task.status === 'COMPLETED'" :size="12" /></button>
              <FileText v-else class="clone-inline-board-note-icon" :size="15" aria-hidden="true" />
              <strong>{{ task.title }}</strong>
            </div>
            <div v-if="showTags && task.tagIds?.length" class="clone-inline-board-tags" :aria-label="taskTagSummary(task)">
              <span v-for="tagId in visibleTaskTagIds(task)" :key="tagId" class="clone-inline-board-tag" :style="taskTagStyle(tagId)">{{ tagName(tagId) }}</span>
              <span v-if="task.tagIds.length > visibleTaskTagIds(task).length" class="clone-inline-board-tag-more" tabindex="0" :aria-label="taskTagSummary(task)" @mouseenter="showTagFlyout(task, $event)" @mouseleave="hideTagFlyoutSoon" @focus="showTagFlyout(task, $event)" @blur="hideTagFlyoutSoon">+{{ task.tagIds.length - visibleTaskTagIds(task).length }}</span>
            </div>
            <div class="clone-inline-board-meta">
              <button v-if="task.dueAt" type="button" class="clone-inline-board-date" :class="{ overdue: isTaskOverdue(task), today: isTaskDueToday(task) }" data-row-date-trigger @click.stop="openRowDateEditor(task, $event.currentTarget)"><CalendarClock :size="13" />{{ taskDateLabel(task) }}</button>
              <span v-if="task.rrule" class="clone-inline-board-indicator" :title="recurrenceLabel(task.rrule)"><Repeat2 :size="13" /></span>
              <span v-if="task.checklist?.length" class="clone-inline-board-indicator clone-inline-board-checklist" :title="`检查项 ${task.checklist.filter(item => item.completed).length}/${task.checklist.length}`"><span class="clone-inline-board-checklist-ring" :style="{ '--check-progress': `${task.checklist.filter(item => item.completed).length / task.checklist.length * 100}%` }"></span>{{ task.checklist.filter(item => item.completed).length }}/{{ task.checklist.length }}</span>
              <span v-if="task.description" class="clone-inline-board-indicator" title="有详细内容"><FileText :size="13" /></span>
              <span v-if="task.attachmentCount || task.attachments?.length" class="clone-inline-board-indicator" :title="`附件 ${task.attachmentCount || task.attachments.length} 个`"><Paperclip :size="13" />{{ task.attachmentCount || task.attachments.length }}</span>
              <span v-if="showListLink" class="clone-inline-board-list" @click.stop="navigateToTaskList(task)"><span class="clone-inline-board-list-dot" :style="{ backgroundColor: taskListColor(task) }"></span>{{ listName(task.listId) }}</span>
            </div>
          </article>
          <p v-if="!group.tasks.length" class="clone-inline-board-empty">无任务</p>
        </section>
      </div>
      <TasksTimelineView v-else-if="activeWorkspaceView === 'TIMELINE'" embedded :tasks="visibleTasks" @select-task="openTaskRow" />
      <template v-else>
      <section v-if="todayCountdowns.length" class="clone-efficiency-group clone-countdown-group">
        <button class="clone-group-heading" type="button" :aria-expanded="!collapsed.countdowns" @click="collapsed.countdowns = !collapsed.countdowns">
          <ChevronDown :size="16" :class="{ rotated: collapsed.countdowns }" /><strong>倒数纪念日</strong><span>{{ todayCountdowns.length }}</span>
        </button>
        <div class="clone-collapse-panel" :class="{ 'is-collapsed': collapsed.countdowns }" :aria-hidden="collapsed.countdowns"><div class="clone-efficiency-rows clone-collapse-inner" :inert="collapsed.countdowns">
          <article v-for="item in todayCountdowns" :key="item.publicId" class="clone-efficiency-row countdown-row">
            <span class="clone-efficiency-icon">{{ item.icon || '◷' }}</span><strong>{{ item.name || item.title }}</strong><span class="clone-efficiency-meta">{{ countdownLabel(item) }}</span>
          </article>
        </div></div>
      </section>
      <div v-if="!visibleTasks.length && !todayHabits.length && !completedTasks.length" class="clone-empty-state">
        <Inbox :size="28" /><strong>{{ isTrashRoute ? "垃圾桶是空的" : title === "今天" ? "今天没有安排" : "这里还没有任务" }}</strong>
        <span v-if="!isTrashRoute && title !== '今天'">使用上方输入框快速创建第一项任务</span>
      </div>
      <div v-else class="clone-task-groups">
        <section
          v-for="group in listGroups"
          :key="group.key"
          class="clone-task-group"
          :class="{ 'drag-target': dragOverGroupKey === group.key }"
          @dragover.prevent="setTaskGroupDragTarget(group)"
          @drop.prevent.stop="dropTaskIntoGroup(group)"
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
            ><span>{{ group.visibleCount }}</span>
          </button>
          <div class="clone-collapse-panel" :class="{ 'is-collapsed': collapsed[group.key] }" :aria-hidden="Boolean(collapsed[group.key])"><TransitionGroup name="clone-sort" tag="div" class="clone-task-rows clone-collapse-inner" :inert="Boolean(collapsed[group.key])">
            <article
              v-for="task in group.rows"
              :key="task.publicId"
              class="clone-task-row"
              :class="{
                selected: selectedTask?.publicId === task.publicId,
                checked: task.status === 'COMPLETED',
                'is-note': isNoteTask(task),
                'has-children': task.hasVisibleChildren,
                multi: selectedIds.has(task.publicId),
                'print-content': printMode === 'CONTENT',
                dragging: dragged?.publicId === task.publicId,
                'drag-over': dragOverTask?.publicId === task.publicId,
                'drop-before': dragOverTask?.publicId === task.publicId && dragOverPlacement === 'before',
                'drop-after': dragOverTask?.publicId === task.publicId && dragOverPlacement === 'after',
              }"
              :data-depth="task.treeDepth"
              :data-priority="task.priority"
              :style="{ '--task-group-color': groupColor(task), '--task-depth': task.treeDepth }"
              @dragover.prevent.stop="setTaskDragTarget(task, $event)"
              @drop.prevent.stop="dropTask(task, group.tasks.indexOf(task), group.key)"
              @dragend="endTaskDrag"
              @contextmenu.prevent="openContext(task, $event)"
            >
              <button v-if="task.hasVisibleChildren" type="button" class="clone-subtask-toggle" :aria-label="`${collapsedSubtaskIds.has(task.publicId) ? '展开' : '收起'}${task.title}的子任务`" :aria-expanded="!collapsedSubtaskIds.has(task.publicId)" @click.stop="toggleSubtaskBranch(task.publicId)"><ChevronDown :size="14" :class="{ rotated: collapsedSubtaskIds.has(task.publicId) }" /></button>
              <button
                v-if="!isNoteTask(task)"
                type="button"
                class="clone-check"
                :aria-label="
                  task.status === 'COMPLETED'
                    ? `重新打开${task.title}`
                    : `完成${task.title}`
                "
                @click="handleTaskCheckClick(task, $event)"
              >
                <Check v-if="task.status === 'COMPLETED'" :size="14" /></button
              ><button type="button" class="clone-drag-handle" :draggable="!batchMode" :aria-label="`拖动${task.title}`" @click.stop @dragstart.stop="startTaskDrag(task, $event)"><GripVertical :size="15" /></button><button
                type="button"
                class="clone-task-content"
                @click="handleTaskContentClick(task, $event)"
              >
                <template v-if="inlineRowTitleTaskId === task.publicId"><input v-model="inlineRowTitleDraft" type="text" maxlength="500" :aria-label="`编辑${task.title}标题`" @click.stop @keydown.enter.prevent="saveInlineRowTitle(task)" @keydown.esc.prevent="cancelInlineRowTitle" @blur="saveInlineRowTitle(task)" /></template><template v-else><span class="clone-print-task-copy"><strong>{{ task.title }}</strong><small v-if="task.description" class="clone-print-description">{{ printDescription(task.description) }}</small></span></template
                ></button
              ><div class="clone-task-metadata">
                <span v-if="showTags && task.tagIds?.length" class="clone-task-tags" :aria-label="taskTagSummary(task)">
                  <span v-for="tagId in task.tagIds" :key="tagId" class="clone-task-tag-wrap" :style="taskTagStyle(tagId)"><RouterLink :to="`/tasks/tag/${tagId}`" class="clone-task-tag" @click.stop>{{ tagName(tagId) }}</RouterLink><button type="button" class="clone-task-tag-remove" :aria-label="`移除标签${tagName(tagId)}`" @click.stop="removeTaskTag(task, tagId)"><X :size="11" /></button></span>
                  <span class="clone-task-tag-count" :data-total="task.tagIds.length" :data-h4="Math.max(0, task.tagIds.length - 4)" :data-h2="Math.max(0, task.tagIds.length - 2)" :data-h1="Math.max(0, task.tagIds.length - 1)" :aria-label="taskTagSummary(task)" tabindex="0" @mouseenter="showTagFlyout(task, $event)" @mouseleave="hideTagFlyoutSoon" @focus="showTagFlyout(task, $event)" @blur="hideTagFlyoutSoon"></span>
                </span>
                <button v-if="showDescription && task.description" type="button" class="clone-task-indicator clone-task-indicator-action" :title="task.description" :aria-label="`查看${task.title}详细内容`" @click.stop="openTaskIndicator(task, 'description')"><FileText :size="14" /></button>
                <button v-if="showChecklist && task.checklist?.length" type="button" class="clone-task-indicator clone-task-indicator-action" :title="`检查项 ${task.checklist.filter(item => item.completed).length}/${task.checklist.length}`" :aria-label="`查看${task.title}检查项进度`" @click.stop="openTaskIndicator(task, 'checklist')"><span class="clone-checklist-progress" :style="{ '--check-progress': `${task.checklist.filter(item => item.completed).length / task.checklist.length * 100}%` }"></span><small>{{ task.checklist.filter(item => item.completed).length }}/{{ task.checklist.length }}</small></button>
                <button v-if="task.totalSubtasks" type="button" class="clone-task-indicator clone-task-indicator-action" :title="`子任务 ${task.completedSubtasks || 0}/${task.totalSubtasks}`" :aria-label="`查看${task.title}子任务`" @click.stop="openTaskIndicator(task, 'subtasks')"><ListTodo :size="14" /><small>{{ task.completedSubtasks || 0 }}/{{ task.totalSubtasks }}</small></button>
                <button v-if="task.rrule" type="button" class="clone-task-indicator clone-task-indicator-action" :title="recurrenceLabel(task.rrule)" :aria-label="`调整${task.title}重复规则`" @click.stop="openTaskIndicator(task, 'repeat')"><Repeat2 :size="14" /></button>
                <button v-if="task.focusMinutes" type="button" class="clone-task-indicator clone-task-indicator-action" :title="`累计专注 ${task.focusMinutes} 分钟`" :aria-label="`查看${task.title}专注记录`" @click.stop="openTaskIndicator(task, 'focus')"><Timer :size="14" /></button>
                <button v-if="task.attachmentCount || task.attachments?.length" type="button" class="clone-task-indicator clone-task-indicator-action" :title="`附件 ${task.attachmentCount || task.attachments.length} 个`" :aria-label="`查看${task.title}附件`" @click.stop="openTaskIndicator(task, 'attachments')"><Paperclip :size="14" /><small>{{ task.attachmentCount || task.attachments.length }}</small></button>
                <button v-if="task.dueAt" type="button" class="clone-task-date" :class="{ overdue: isTaskOverdue(task), today: isTaskDueToday(task) }" data-row-date-trigger :title="`调整日期 ${formatDate(task.dueAt)}`" @click.stop="openRowDateEditor(task, $event.currentTarget)"><CalendarClock :size="14" />{{ taskDateLabel(task) }}</button>
                <button v-if="showListLink" type="button" class="clone-task-list-link" :title="listName(task.listId)" @click.stop="navigateToTaskList(task)"><span class="clone-task-list-dot" :style="{ backgroundColor: taskListColor(task) }"></span>{{ listName(task.listId) }}</button>
              </div><button
              v-if="!selectedIds.has(task.publicId)"
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
        <section v-if="todayHabits.length" class="clone-task-group clone-efficiency-group clone-habit-group">
            <button class="clone-group-heading" type="button" :aria-expanded="!collapsed.habits" @click="collapsed.habits = !collapsed.habits">
              <ChevronDown :size="16" :class="{ rotated: collapsed.habits }" /><strong>习惯</strong><span>{{ todayHabits.length }}</span>
            </button>
            <div class="clone-collapse-panel" :class="{ 'is-collapsed': collapsed.habits }" :aria-hidden="collapsed.habits"><div class="clone-efficiency-rows clone-collapse-inner" :inert="collapsed.habits">
              <article v-for="item in todayHabits" :key="item.publicId" class="clone-efficiency-row">
                <button type="button" class="clone-efficiency-check" :class="{ checked: habitDone(item) }" :aria-label="`${habitDone(item) ? '取消' : '完成'}习惯${item.name}`" @click="toggleHabitToday(item)">{{ habitDone(item) ? '✓' : '' }}</button><strong>{{ item.name }}</strong><span class="clone-efficiency-meta">{{ habitDone(item) ? '已完成' : '今天' }}</span>
              </article>
            </div></div>
          </section>
        <section v-if="completedTasks.length" class="clone-task-group clone-completed-group" aria-label="已完成任务">
          <button class="clone-group-heading" type="button" :aria-expanded="!collapsed.completed" @click="collapsed.completed = !collapsed.completed">
            <ChevronDown :size="16" :class="{ rotated: collapsed.completed }" /><strong>已完成</strong><span>{{ completedTasks.length }}</span>
          </button>
          <div class="clone-collapse-panel" :class="{ 'is-collapsed': collapsed.completed }" :aria-hidden="Boolean(collapsed.completed)">
            <TransitionGroup name="clone-sort" tag="div" class="clone-task-rows clone-collapse-inner" :inert="Boolean(collapsed.completed)">
              <article v-for="task in completedPreviewTasks" :key="task.publicId" class="clone-task-row checked" :class="{ selected: selectedTask?.publicId === task.publicId, 'is-note': isNoteTask(task), multi: selectedIds.has(task.publicId), 'print-content': printMode === 'CONTENT' }" :data-priority="task.priority" :style="{ '--task-group-color': groupColor(task) }" @contextmenu.prevent="openContext(task, $event)">
                <button v-if="!isNoteTask(task)" type="button" class="clone-check" :aria-label="`重新打开${task.title}`" @click="handleTaskCheckClick(task, $event)"><Check :size="14" /></button><GripVertical class="clone-drag-handle" :size="15" /><button type="button" class="clone-task-content" @click="handleTaskContentClick(task, $event)"><span class="clone-print-task-copy"><strong>{{ task.title }}</strong><small v-if="task.description" class="clone-print-description">{{ printDescription(task.description) }}</small></span></button>
                <div class="clone-task-metadata"><span v-if="showTags && task.tagIds?.length" class="clone-task-tags" :aria-label="taskTagSummary(task)"><RouterLink v-for="tagId in visibleTaskTagIds(task)" :key="tagId" :to="`/tasks/tag/${tagId}`" class="clone-task-tag" :style="taskTagStyle(tagId)" @click.stop>{{ tagName(tagId) }}</RouterLink></span><button v-if="task.dueAt" type="button" class="clone-task-date" data-row-date-trigger @click.stop="openRowDateEditor(task, $event.currentTarget)"><CalendarClock :size="14" />{{ taskDateLabel(task) }}</button><button v-if="showListLink" type="button" class="clone-task-list-link" :title="listName(task.listId)" @click.stop="navigateToTaskList(task)"><span class="clone-task-list-dot" :style="{ backgroundColor: taskListColor(task) }"></span>{{ listName(task.listId) }}</button></div>
              </article>
            </TransitionGroup>
            <button v-if="completedPreviewTasks.length < completedTasks.length" type="button" class="clone-completed-more" @click="showMoreCompleted">查看更多（{{ completedTasks.length - completedPreviewTasks.length }}）</button>
          </div>
        </section>
      </div>
      </template>
      </template>
    </main>

    <div
      class="clone-detail-divider"
      role="separator"
      aria-orientation="vertical"
      aria-label="调整详情栏宽度"
      :aria-valuemin="320"
      :aria-valuemax="720"
      :aria-valuenow="detailWidth"
      tabindex="0"
      @pointerdown="startDetailResize"
      @dblclick="resetDetailWidth"
      @keydown="resizeDetailByKey"
    ></div>

    <aside
      class="clone-detail-pane"
      :style="detailPaneStyle"
      :class="{ 'is-empty': !selectedTask && !editorOpen && !selectedIds.size && !listActivityOpen, 'is-batch': Boolean(selectedIds.size), 'is-list-activity': listActivityOpen }"
      :aria-label="selectedIds.size ? '批量操作' : listActivityOpen ? '清单动态' : '任务详情'"
      :role="selectedIds.size || listActivityOpen || (activeWorkspaceView !== 'LIST' && (selectedTask || editorOpen)) ? 'dialog' : undefined"
      :aria-modal="selectedIds.size || listActivityOpen || (activeWorkspaceView !== 'LIST' && (selectedTask || editorOpen)) ? 'true' : undefined"
      :aria-hidden="isMobileSidebar && !selectedIds.size && !listActivityOpen ? 'true' : undefined"
    >
      <section v-if="selectedIds.size" class="clone-batch-panel" aria-label="批量操作面板">
        <header class="clone-batch-panel-head">
          <div><strong>已选择 <b>{{ selectedIds.size }}</b> 项</strong></div>
          <button type="button" class="clone-batch-cancel" @click="clearBatchSelection">取消</button>
        </header>
        <div class="clone-batch-fields">
          <label class="clone-batch-field"><CalendarClock :size="17" /><input v-model="batchDueAt" type="datetime-local" aria-label="批量设置日期" @input="batchDueClear = false" /><button v-if="batchDueAt" type="button" aria-label="选择清除日期" title="选择清除日期" @click="batchDueAt = ''; batchDueClear = true"><X :size="14" /></button><button type="button" class="clone-batch-apply" :disabled="!batchDueAt && !batchDueClear" @click="applyBatch('RESCHEDULE', { dueAt: batchDueClear ? '' : new Date(batchDueAt).toISOString() })">{{ batchDueClear ? '清除' : '应用' }}</button></label>
          <label class="clone-batch-field"><Flag :size="17" /><select v-model="batchPriority" aria-label="批量设置优先级"><option v-for="option in priorityOptions" :key="option.value" :value="option.value">{{ option.label }}</option></select><button type="button" class="clone-batch-apply" @click="applyBatch('PRIORITY', { priority: batchPriority })">应用</button></label>
          <label class="clone-batch-field"><ListTodo :size="17" /><select v-model="batchListId" aria-label="批量移动到清单"><option value="">选择清单</option><option v-for="list in activeLists" :key="list.publicId" :value="list.publicId">{{ list.name }}</option></select><button type="button" class="clone-batch-apply" :disabled="!batchListId" @click="applyBatch('MOVE', { listId: batchListId })">应用</button></label>
          <details class="clone-batch-tags"><summary><Tag :size="17" /><span>选择标签</span><small>{{ batchTagIds.length || '' }}</small></summary><div><label v-for="tag in sortedTags" :key="tag.publicId"><input v-model="batchTagIds" type="checkbox" :value="tag.publicId" />{{ tag.name }}</label><button type="button" class="clone-batch-apply" :disabled="!batchTagIds.length" @click="applyBatch('TAGS', { tagIds: batchTagIds })">应用标签</button></div></details>
        </div>
        <div class="clone-batch-actions">
          <button type="button" class="batch-action-complete" @click="applyBatch('COMPLETE')"><CheckSquare :size="22" /><span>完成</span></button>
          <button type="button" class="batch-action-pin" @click="batchPin"><Pin :size="22" /><span>置顶</span></button>
          <button type="button" class="batch-action-parent" :disabled="!batchParentCandidates.length" @click="openBatchParentPicker"><ListTree :size="22" /><span>关联主任务</span></button>
          <button type="button" class="batch-action-merge" disabled title="当前任务 API 没有安全合并任务的契约"><Merge :size="22" /><span>合并</span></button>
          <button type="button" class="batch-action-duplicate" @click="batchDuplicate"><CopyPlus :size="22" /><span>创建副本</span></button>
          <button type="button" class="batch-action-note" disabled title="不能在多选模式下转换为笔记"><StickyNote :size="22" /><span>转换为笔记</span></button>
          <button type="button" class="batch-action-sticky" @click="openBatchStickyNote"><StickyNote :size="22" /><span>打开便签</span></button>
          <button type="button" class="batch-action-copy" @click="copyBatchTaskText"><Copy :size="22" /><span>复制文本</span></button>
          <button type="button" class="batch-action-delete danger" @click="batchDelete"><Trash2 :size="22" /><span>删除</span></button>
        </div>
      </section>
      <section v-else-if="listActivityOpen" class="clone-list-activity-pane" aria-label="清单动态时间线">
        <header class="clone-list-activity-header"><strong>{{ listActivityTitle }}动态</strong><button type="button" aria-label="关闭清单动态" @click="closeListActivity"><X :size="17" /></button></header>
        <div class="clone-list-activity-scroll" aria-live="polite">
          <p v-if="listActivityLoading && !listActivityItems.length" class="clone-muted">正在加载清单动态…</p>
          <p v-else-if="listActivityError" class="clone-error" role="alert">{{ listActivityError }} <button type="button" @click="loadListActivityPage(true)">重试</button></p>
          <p v-else-if="!listActivityItems.length" class="clone-muted">暂无清单动态</p>
          <section v-for="group in listActivityGroups" :key="group.key" class="clone-list-activity-group">
            <h3>{{ group.label }}</h3>
            <ol>
              <li v-for="item in group.items" :key="item.publicId">
                <span class="clone-list-activity-icon" :class="activityTone(item)"><component :is="activityIcon(item)" :size="16" /></span>
                <div class="clone-list-activity-copy"><p><strong>{{ item.actorName || '当前用户' }}</strong> {{ item.summary }} <button type="button" class="clone-list-activity-task" @click="openActivityTask(item)">{{ item.taskTitle || '未知任务' }}</button><RouterLink v-for="tag in activityTags(item)" :key="tag.publicId" :to="`/tasks/tag/${tag.publicId}`" class="clone-list-activity-tag" :style="{ '--activity-tag-color': tag.color || 'var(--accent)' }">{{ tag.name }}</RouterLink></p><time>{{ formatListActivityTime(item.createdAt) }}</time></div>
              </li>
            </ol>
          </section>
          <button v-if="listActivityItems.length < listActivityTotal" type="button" class="clone-list-activity-more" :disabled="listActivityLoading" @click="loadListActivityPage(false)">{{ listActivityLoading ? '加载中…' : '加载更多' }}</button>
        </div>
      </section>
      <template v-else>
      <header v-if="selectedTask || editorOpen" class="clone-detail-toolbar" @pointerdown.stop>
        <div class="clone-detail-toolbar-main">
          <button v-if="!editorOpen && !isNoteSelected" type="button" class="clone-detail-status-tool" :class="{ completed: selectedTask?.status === 'COMPLETED', abandoned: selectedTask?.status === 'ABANDONED' }" :aria-label="selectedTask?.status === 'COMPLETED' ? '重新打开任务' : '标记任务完成'" @click="toggleComplete(selectedTask)"><Check v-if="selectedTask?.status === 'COMPLETED'" :size="14" /></button>
          <button v-else-if="editorOpen && !isNoteSelected" type="button" class="clone-detail-status-tool" :class="{ completed: draft.status === 'COMPLETED', abandoned: draft.status === 'ABANDONED' }" aria-label="任务完成状态" @click="draft.status = draft.status === 'COMPLETED' ? 'OPEN' : 'COMPLETED'"><Check v-if="draft.status === 'COMPLETED'" :size="14" /></button>
          <button ref="datePopoverAnchor" type="button" class="clone-detail-tool" aria-label="设置日期" :aria-expanded="datePopoverOpen" @click.stop="toggleDatePopover">
            <CalendarClock :size="15" /><span>{{ editorOpen && draft.dueAt ? formatDate(draft.dueAt) : selectedTask?.dueAt ? formatDate(selectedTask.dueAt) : isNoteSelected ? '设置提醒' : '设置日期' }}</span>
          </button>
          <span v-if="editorOpen ? (draft.rrule === 'CUSTOM' ? draft.customRrule : draft.rrule) : selectedTask?.rrule" class="clone-detail-repeat-indicator" role="img" aria-label="重复任务" title="重复任务">
            <Repeat2 :size="15" />
          </span>
          <Teleport to="body"><div v-if="datePopoverOpen" class="clone-date-popover-shell" :style="detailDatePopoverStyle" @click.stop>
            <TaskDateTimePopover
              :mode="datePickerMode"
              :month="datePickerMonth"
              :calendar-days="datePickerDays"
              :month-label="datePickerMonthLabel"
              :selected-key="detailDateSelectedKey"
              :time="detailDateTimeValue"
              :all-day="detailDateAllDay"
              apply-label="确定"
              @update:mode="datePickerMode = $event"
              @quick-date="applyDetailQuickDate"
              @move-month="datePickerMonth = new Date(datePickerMonth.getFullYear(), datePickerMonth.getMonth() + $event, 1)"
              @select-date="selectDateKey($event)"
              @clear-date="applyDetailClearDate"
              @toggle-all-day="setDetailAllDay"
              @apply="confirmTaskDueDate"
            >
              <template #rows="{ mode: rowMode }">
                <div class="clone-date-rows">
                  <div class="clone-date-rowline">
                    <Clock3 :size="14" />
                    <span class="clone-date-rowlabel">时间</span>
                    <button type="button" class="clone-date-rowvalue" :aria-expanded="detailTimeRowOpen" @click.stop="detailTimeRowOpen = !detailTimeRowOpen">
                      {{ detailDateAllDay ? '全天' : (rowMode === 'RANGE' ? '时间段' : (detailDateTimeValue || '设置时间')) }}
                      <ChevronDown :size="13" :class="{ rotated: detailTimeRowOpen }" />
                    </button>
                  </div>
                  <div v-if="detailTimeRowOpen" class="clone-date-rowinputs">
                    <label v-if="rowMode === 'RANGE'">开始<input v-model="detailStartModel" type="datetime-local" aria-label="开始时间" /></label>
                    <label>{{ rowMode === 'RANGE' ? '结束' : '时间' }}<input v-model="detailDueModel" type="datetime-local" aria-label="日期时间" /></label>
                    <label class="clone-inline-check"><input type="checkbox" :checked="detailDateAllDay" @change="setDetailAllDay($event.target.checked)" />全天</label>
                  </div>

                  <div class="clone-date-rowline">
                    <Bell :size="14" />
                    <span class="clone-date-rowlabel">提醒</span>
                    <button type="button" class="clone-date-rowvalue" :class="{ active: editorOpen ? draft.reminderEnabled : quickReminder.reminder }" aria-label="设置提醒" :aria-expanded="reminderPopoverOpen" @click.stop="toggleReminderPopover">
                      {{ editorOpen ? (draft.reminderEnabled ? '已开启' : '未设置') : (quickReminder.reminder ? '已开启' : '未设置') }}
                      <ChevronDown :size="13" :class="{ rotated: reminderPopoverOpen }" />
                    </button>
                  </div>
                  <div v-if="reminderPopoverOpen" class="clone-detail-date-reminder-popover is-inline" @click.stop>
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

                  <div class="clone-date-rowline">
                    <Repeat2 :size="14" />
                    <span class="clone-date-rowlabel">重复</span>
                    <button type="button" class="clone-date-rowvalue" :aria-expanded="repeatPopoverOpen" @click.stop="toggleRepeatPopover">
                      {{ recurrenceLabel(editorOpen ? (draft.rrule === 'CUSTOM' ? draft.customRrule : draft.rrule) : selectedTask?.rrule) || '不重复' }}
                      <ChevronDown :size="13" :class="{ rotated: repeatPopoverOpen }" />
                    </button>
                  </div>
                  <div v-if="repeatPopoverOpen" class="clone-detail-repeat-inline is-inline" @click.stop>
                    <div class="clone-repeat-popover-header"><strong>重复</strong><button type="button" class="clone-popover-close" aria-label="关闭重复设置" @click="repeatPopoverOpen = false"><X :size="15" /></button></div>
                    <div class="clone-quick-repeat-presets clone-detail-repeat-presets"><button v-for="option in quickRepeatPresets" :key="option.value" type="button" :class="{ active: detailRepeat.preset === option.value }" @click="selectDetailRepeatPreset(option.value)">{{ option.label }}</button></div>
                    <template v-if="detailRepeat.preset === 'CUSTOM'">
                      <div class="clone-quick-repeat-grid"><label>重复方式<select v-model="detailRepeat.mode"><option value="DUE_DATE">到期重复</option><option value="COMPLETION_DATE">完成重复</option></select></label><label>频率<select v-model="detailRepeat.frequency" @change="setDetailRepeatFrequency"><option value="DAILY">每天</option><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option><option value="YEARLY">每年</option></select></label><label>间隔<input v-model.number="detailRepeat.interval" type="number" min="1" max="365" /></label></div>
                      <div v-if="detailRepeat.frequency === 'WEEKLY'" class="clone-quick-repeat-days"><span>每周</span><label v-for="day in DAY_CODES" :key="`detail-repeat-${day}`"><input v-model="detailRepeat.weekdays" type="checkbox" :value="day" />{{ { MO: '一', TU: '二', WE: '三', TH: '四', FR: '五', SA: '六', SU: '日' }[day] }}</label></div>
                      <div v-if="['MONTHLY', 'YEARLY'].includes(detailRepeat.frequency)" class="clone-quick-repeat-grid"><label>重复日期<select v-model="detailRepeat.monthMode"><option value="DATE">按日期</option><option value="WEEKDAY">按星期</option><option v-if="detailRepeat.frequency === 'MONTHLY'" value="WORKDAY">按工作日</option></select></label><label v-if="detailRepeat.frequency === 'YEARLY'">月份<select v-model.number="detailRepeat.month"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><template v-if="detailRepeat.monthMode === 'WEEKDAY'"><label>第几周<select v-model.number="detailRepeat.ordinal"><option v-for="ordinal in 5" :key="ordinal" :value="ordinal">第 {{ ordinal }} 个</option><option :value="-1">最后一个</option></select></label><label>星期<select v-model="detailRepeat.weekday"><option v-for="day in DAY_CODES" :key="day" :value="day">{{ { MO: '周一', TU: '周二', WE: '周三', TH: '周四', FR: '周五', SA: '周六', SU: '周日' }[day] }}</option></select></label></template><label v-if="detailRepeat.monthMode === 'WORKDAY'">工作日<select v-model.number="detailRepeat.workdayPosition"><option :value="1">第一个工作日</option><option :value="-1">最后一个工作日</option></select></label></div>
                      <div v-if="['MONTHLY', 'YEARLY'].includes(detailRepeat.frequency) && detailRepeat.monthMode === 'DATE'" class="clone-quick-repeat-date-calendar" aria-label="选择重复日期"><span v-for="weekday in quickWeekdays" :key="`detail-repeat-weekday-${weekday}`">{{ weekday }}</span><span v-for="(day, index) in detailRepeatCalendarDays.filter(value => !value)" :key="`detail-repeat-empty-${index}`" class="clone-quick-repeat-date-spacer"></span><button v-for="day in detailRepeatCalendarDays.filter(Boolean)" :key="`detail-repeat-day-${day}`" type="button" :aria-pressed="!detailRepeat.lastDay && detailRepeat.monthDays.includes(day)" :class="{ active: !detailRepeat.lastDay && detailRepeat.monthDays.includes(day) }" @click="toggleDetailRepeatMonthDay(day)">{{ day }}</button><button type="button" class="clone-quick-repeat-last-day" :aria-pressed="detailRepeat.lastDay" :class="{ active: detailRepeat.lastDay }" @click="detailRepeat.lastDay = true">最后一天</button></div>
                      <div v-if="detailRepeat.frequency === 'DAILY'" class="clone-repeat-special-grid"><label><input v-model="detailRepeat.skipWeekends" type="checkbox" />跳过双休日</label><label><input v-model="detailRepeat.skipLegalHolidays" type="checkbox" />跳过法定节假日</label></div>
                      <div v-else-if="detailRepeat.frequency === 'WEEKLY' || (['MONTHLY', 'YEARLY'].includes(detailRepeat.frequency) && detailRepeat.monthMode === 'WEEKDAY')" class="clone-repeat-special-grid"><label><input v-model="detailRepeat.skipLegalHolidays" type="checkbox" />跳过法定节假日</label></div>
                    </template>
                    <div v-if="['LUNAR_MONTHLY', 'LUNAR_YEARLY'].includes(detailRepeat.preset)" class="clone-quick-repeat-grid"><label v-if="detailRepeat.preset === 'LUNAR_YEARLY'">农历月份<select v-model.number="detailRepeat.lunarMonth"><option v-for="month in 12" :key="month" :value="month">{{ month }} 月</option></select></label><label>农历日期<select v-model.number="detailRepeat.lunarDay"><option v-for="day in 30" :key="day" :value="day">{{ day }} 日</option></select></label></div>
                    <div v-if="detailRepeat.preset" class="clone-quick-repeat-end"><span>结束条件</span><label><input v-model="detailRepeat.endMode" type="radio" value="NEVER" />永不结束</label><label><input v-model="detailRepeat.endMode" type="radio" value="UNTIL" />按日期结束<input v-if="detailRepeat.endMode === 'UNTIL'" v-model="detailRepeat.until" type="date" /></label><label><input v-model="detailRepeat.endMode" type="radio" value="COUNT" />按次数结束<input v-if="detailRepeat.endMode === 'COUNT'" v-model.number="detailRepeat.count" type="number" min="1" /></label></div>
                    <p v-if="detailRepeatError" class="clone-error" role="alert">{{ detailRepeatError }}</p>
                    <div class="clone-popover-actions"><button type="button" @click="resetDetailRepeat">清除</button><button type="button" class="clone-popover-save" @click="saveDetailRepeat">完成</button></div>
                  </div>
                </div>
              </template>
            </TaskDateTimePopover>
          </div></Teleport>
        </div>
        <div v-if="!isNoteSelected" class="clone-detail-toolbar-actions">
          <button ref="priorityPopoverAnchor" type="button" class="clone-detail-priority-tool" :data-priority="editorOpen ? draft.priority : selectedTask?.priority" aria-label="设置优先级" :aria-expanded="priorityPopoverOpen" @click.stop="togglePriorityPopover"><Flag :size="17" /></button>
          <Teleport to="body"><div v-if="priorityPopoverOpen" class="clone-toolbar-popover clone-priority-toolbar-popover" :style="attributePopoverStyle('priority')" @click.stop>
            <strong>优先级</strong>
            <button v-for="option in priorityOptions" :key="option.value" type="button" class="clone-priority-option" :class="[`priority-${option.value.toLowerCase()}`, { active: (editorOpen ? draft.priority : selectedTask?.priority) === option.value }]" @click="editorOpen ? (draft.priority = option.value) : setTaskPriority(option.value); priorityPopoverOpen = false"><i aria-hidden="true"></i><span>{{ option.label }}</span></button>
          </div></Teleport>
        </div>
        <div v-if="selectedTask?.checklist?.length" class="clone-detail-header-progress" aria-hidden="true"><span :style="{ transform: `scaleX(${selectedTask.checklist.filter((item) => item.completed).length / selectedTask.checklist.length})` }"></span></div>
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
          >描述<TaskMilkdownEditor
            v-model="draft.description"
            label="任务描述"
            placeholder="补充任务描述"
            :toolbar-visible="true"
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
            >父任务<select v-model="draft.parentId" aria-label="父任务">
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
          <h2 v-if="!inlineTitleEditing"><button type="button" aria-label="编辑任务标题" @click="beginInlineTitleEdit">{{ selectedTask.title }}</button></h2>
          <input v-else v-model="inlineTitleDraft" class="clone-inline-title-input" aria-label="任务标题" maxlength="500" autofocus @keydown.enter.prevent="$event.target.blur()" @keydown.esc.prevent="cancelInlineTitleEdit" @blur="saveInlineTitle" />
          <button v-if="!isNoteSelected" type="button" class="clone-detail-content-toggle" aria-label="在描述和检查事项之间转换" :title="selectedTask.checklist?.length ? '转换为描述' : '转换为检查事项'" @click="toggleDescriptionChecklist(selectedTask)"><ListTodo v-if="selectedTask.checklist?.length" :size="16" /><AlignLeft v-else :size="16" /></button>
        </div>
        <div class="clone-detail-description-block" :class="{ 'toolbar-open': detailRichToolbarOpen }">
          <div class="clone-detail-block-add">
            <button type="button" class="clone-detail-block-add-trigger" aria-label="插入内容" :aria-expanded="detailBlockMenuOpen" @click.stop="toggleDetailBlockMenu"><Plus :size="16" /></button>
            <div v-if="detailBlockMenuOpen" class="clone-detail-block-menu" role="menu" aria-label="插入内容" @click.stop>
              <button role="menuitem" type="button" @click="insertDetailBlock('heading1')"><Heading1 :size="16" />一级标题</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('heading2')"><Heading2 :size="16" />二级标题</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('heading3')"><Heading3 :size="16" />三级标题</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('list')"><List :size="16" />无序列表</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('ordered')"><ListOrdered :size="16" />有序列表</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('check')"><CheckSquare :size="16" />检查项</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('quote')"><Quote :size="16" />引用</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('divider')"><Minus :size="16" />水平分割线</button>
              <div class="clone-detail-more-separator" aria-hidden="true"></div>
              <button role="menuitem" type="button" @click="insertDetailBlock('attachment')"><Paperclip :size="16" />附件</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('subtask')"><ListPlus :size="16" />子任务</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('tag')"><Tag :size="16" />标签</button>
              <button role="menuitem" type="button" @click="insertDetailBlock('linkTask')"><Link :size="16" />关联任务/笔记</button>
            </div>
            <div v-if="detailLinkPickerOpen" class="clone-detail-link-picker" role="dialog" aria-label="关联任务或笔记" @click.stop>
              <input v-model="detailLinkQuery" type="text" aria-label="搜索任务或笔记" placeholder="搜索" />
              <div class="clone-detail-link-options">
                <button v-for="task in detailLinkOptions" :key="task.publicId" type="button" @click="chooseDetailLinkTask(task)"><span>{{ task.title }}</span><small>{{ listName(task.listId) }}</small></button>
                <span v-if="!detailLinkOptions.length">没有匹配的任务</span>
              </div>
              <div class="clone-popover-actions"><button type="button" @click="detailLinkPickerOpen = false">取消</button></div>
            </div>
          </div>
          <TaskMilkdownEditor ref="detailDescriptionEditor" v-model="inlineDescriptionDraft" :toolbar-visible="detailRichToolbarOpen" label="任务描述" placeholder="输入内容…" @update:model-value="scheduleInlineDescriptionSave" @focus="holdInlineDescriptionSync" @blur="releaseInlineDescriptionSync" />
          <small v-if="inlineDescriptionError" class="clone-inline-save-status error" role="alert">{{ inlineDescriptionError }}</small>
        </div>
        <section v-if="selectedTask.focusMinutes" class="clone-detail-focus-summary" aria-label="专注记录">
          <Timer :size="14" /><span>累计专注</span><strong>{{ selectedTask.focusMinutes }} 分钟</strong>
        </section>
        <section v-if="!isNoteSelected" class="clone-checklist-read" :class="{ 'is-empty': !selectedTask.checklist?.length }" aria-label="检查项">
          <div v-if="selectedTask.checklist?.length" class="clone-detail-checklist-progress" role="progressbar" :aria-valuenow="selectedTask.checklist.filter((item) => item.completed).length" :aria-valuemin="0" :aria-valuemax="selectedTask.checklist.length"><span :style="{ width: `${selectedTask.checklist.filter((item) => item.completed).length / selectedTask.checklist.length * 100}%` }"></span></div>
          <div
            v-for="(item, index) in selectedTask.checklist"
            :key="item.publicId || `${index}:${item.title}`"
            class="clone-checklist-row"
            :class="{ completed: item.completed, 'drop-target': detailChecklistDropTarget === index }"
            :data-checklist-index="index"
            draggable="true"
            @dragstart="startDetailChecklistDrag(index, $event)"
            @dragover.prevent="detailChecklistDropTarget = index"
            @dragleave="detailChecklistDropTarget === index ? detailChecklistDropTarget = null : null"
            @drop.prevent="dropDetailChecklist(index)"
            @dragend="detailChecklistDragIndex = null; detailChecklistDropTarget = null"
          >
            <button type="button" class="clone-checklist-grip" aria-label="拖动排序" tabindex="-1"><GripVertical :size="14" /></button>
            <input type="checkbox" :checked="item.completed" @change="toggleChecklistItem(selectedTask, item)" />
            <input v-if="detailChecklistEditingIndex === index" v-model="detailChecklistDraft" type="text" class="clone-detail-checklist-title-input" :aria-label="`编辑检查项 ${index + 1}`" @keydown.enter.prevent="saveDetailChecklistTitleAndNext(selectedTask, index)" @keydown.esc.prevent="cancelDetailChecklistEdit(item)" @blur="saveDetailChecklistTitle(selectedTask, index)" />
            <span v-else :class="{ completed: item.completed }" @click.stop.prevent="beginDetailChecklistEdit(item, index)">{{ item.title || '无标题' }}</span>
            <button type="button" class="clone-checklist-remind" :class="{ active: item.remindAt }" :aria-label="item.remindAt ? `检查项提醒 ${formatChecklistReminder(item.remindAt)}` : '设置检查项提醒'" :title="item.remindAt ? `提醒：${formatChecklistReminder(item.remindAt)}` : '设置提醒'" @click.stop="openChecklistReminder(item, index)"><Bell :size="14" /></button>
            <button type="button" class="clone-checklist-remove" :aria-label="`删除检查项${item.title || index + 1}`" @click.stop="removeDetailChecklistItem(selectedTask, index)"><Trash2 :size="14" /></button>
            <div v-if="checklistReminderIndex === index" class="clone-checklist-reminder-popover" @click.stop>
              <strong>提醒</strong>
              <div class="clone-checklist-reminder-quick"><button type="button" @click="setChecklistReminderOffset(0)">今天稍后</button><button type="button" @click="setChecklistReminderOffset(1)">明天 9:00</button></div>
              <label>时间<input v-model="checklistReminderDraft" type="datetime-local" aria-label="检查项提醒时间" /></label>
              <p v-if="item.remindAt" class="clone-muted">当前：{{ formatChecklistReminder(item.remindAt) }}</p>
              <div class="clone-popover-actions"><button type="button" @click="clearChecklistReminder(item, index)">清除</button><button type="button" class="clone-popover-save" :disabled="checklistReminderSaving" @click="saveChecklistReminder(item, index)">{{ checklistReminderSaving ? '保存中…' : '确定' }}</button></div>
            </div>
          </div>
          <form v-if="detailChecklistDraftOpen" class="clone-checklist-draft" @submit.prevent="submitDetailChecklistDraft">
            <span class="clone-checklist-grip is-placeholder" aria-hidden="true"><GripVertical :size="14" /></span>
            <input ref="detailChecklistDraftInput" v-model="detailChecklistNewDraft" type="text" aria-label="新增检查项" placeholder="输入检查项，回车创建下一条" @keydown.esc.prevent="cancelDetailChecklistDraft" />
          </form>
          <button v-else type="button" class="clone-checklist-add" @click="startDetailChecklistDraft"><Plus :size="15" />添加检查项</button>
        </section>
        <div class="clone-detail-tags-row" v-if="selectedTask && selectedTask.tagIds?.length">
          <span v-for="tagId in selectedTask.tagIds" :key="tagId" class="clone-detail-tag" :style="taskTagStyle(tagId)">
            <button type="button" class="clone-detail-tag-name" :aria-label="`查看标签${tagName(tagId)}`" @click.stop="router.push(`/tasks/tag/${tagId}`)">{{ tagName(tagId) }}</button>
            <button type="button" class="clone-detail-tag-remove" :aria-label="`移除标签${tagName(tagId)}`" @click.stop="removeDetailTag(tagId)"><X :size="9" /></button>
          </span>
        </div>
        <section v-if="selectedTask && !isNoteSelected" class="clone-detail-subtasks" aria-label="子任务">
          <header><strong>子任务</strong><span>{{ detailSubtasks.length }}</span><button type="button" aria-label="添加子任务" title="添加子任务" @click="createSubtask(selectedTask)"><Plus :size="15" /></button></header>
          <article
            v-for="(child, childIndex) in detailSubtasks"
            :key="child.publicId"
            class="clone-detail-subtask-row"
            :class="{ completed: child.status === 'COMPLETED', 'drop-target': detailSubtaskDropTarget === childIndex }"
            draggable="true"
            @dragstart="startDetailSubtaskDrag(childIndex, $event)"
            @dragover.prevent="detailSubtaskDropTarget = childIndex"
            @drop.prevent="dropDetailSubtask(childIndex)"
            @dragend="detailSubtaskDragIndex = null; detailSubtaskDropTarget = null"
            @contextmenu.prevent="openContext(child, $event)"
          >
            <span class="clone-detail-subtask-grip" aria-hidden="true"><GripVertical :size="14" /></span>
            <button type="button" class="clone-detail-subtask-check" :aria-label="`${child.status === 'COMPLETED' ? '重新打开' : '完成'}${child.title}`" @click.stop="toggleComplete(child)"><Check v-if="child.status === 'COMPLETED'" :size="12" /></button>
            <input v-if="inlineSubtaskTitleId === child.publicId" v-model="inlineSubtaskTitleDraft" class="clone-detail-subtask-title-input" :aria-label="`编辑子任务 ${child.title}`" @keydown.enter.prevent="$event.target.blur()" @keydown.esc.prevent="cancelInlineSubtaskTitle" @blur="saveInlineSubtaskTitle(child)" />
            <button v-else type="button" class="clone-detail-subtask-title" :aria-label="`打开子任务${child.title}`" @click.stop="openDetail(child)" @dblclick.stop.prevent="beginInlineSubtaskTitleEdit(child)">{{ child.title || '无标题' }}</button>
            <span v-if="child.priority && child.priority !== 'NONE'" class="clone-detail-subtask-priority" :data-priority="child.priority" :title="`优先级${priorityLabel(child.priority)}`"><Flag :size="12" /></span>
            <button type="button" class="clone-detail-subtask-date" data-row-date-trigger :aria-label="`调整${child.title}日期`" @click.stop="openRowDateEditor(child, $event.currentTarget)">{{ child.dueAt ? taskDateLabel(child) : '日期' }}</button>
          </article>
          <form v-if="detailSubtaskDraftOpen" class="clone-detail-subtask-draft" aria-label="添加子任务" @submit.prevent="saveDetailSubtask">
            <span class="clone-detail-subtask-check draft-check" aria-hidden="true"></span><input ref="detailSubtaskInput" v-model="detailSubtaskDraft" aria-label="子任务名称" placeholder="输入内容或使用 / 快速插入" @keydown.esc.prevent="cancelDetailSubtask" />
            <button type="submit" :disabled="!detailSubtaskDraft.trim() || detailSubtaskSaving" aria-label="保存子任务"><Check :size="15" /></button>
          </form>
          <button v-else type="button" class="clone-detail-subtask-add" @click="createSubtask(selectedTask)"><Plus :size="16" />添加子任务</button>
        </section>
        <section v-if="detailFieldsOpen" class="clone-detail-secondary" aria-label="更多任务字段">
          <section class="clone-detail-properties" aria-label="任务属性编辑">
            <label>清单<select v-model="detailFieldsDraft.listId"><option v-for="list in activeLists" :key="list.publicId" :value="list.publicId">{{ list.name }}</option></select></label>
            <label>父任务<select v-model="detailFieldsDraft.parentId"><option value="">无</option><option v-for="task in detailParentOptions" :key="task.publicId" :value="task.publicId">{{ task.title }}</option></select></label>
            <label>开始时间<input v-model="detailFieldsDraft.startAt" type="datetime-local" /></label>
            <label>持续分钟数<input v-model.number="detailFieldsDraft.durationMinutes" type="number" min="0" step="5" /></label>
            <label class="clone-inline-check"><input v-model="detailFieldsDraft.allDay" type="checkbox" />全天任务</label>
            <div class="clone-detail-property"><span>创建时间</span><strong>{{ formatCreatedAt(selectedTask.createdAt) }}</strong></div>
            <div class="clone-detail-field-actions"><button type="button" @click="detailFieldsOpen = false">取消</button><button type="button" class="clone-primary-button" :disabled="detailFieldsSaving" @click="saveDetailFields">{{ detailFieldsSaving ? '保存中…' : '保存字段' }}</button></div>
          </section>
        </section>
        <section v-if="detailSecondaryOpen" class="clone-detail-secondary" aria-label="任务附加信息">
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
        <section class="clone-collaboration-section" aria-label="附件">
          <span class="clone-detail-section-anchor" aria-hidden="true"></span>
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
        <section class="clone-detail-properties" aria-label="任务属性">
          <button type="button" @click="openEdit(selectedTask)"><span>开始时间</span><strong>{{ selectedTask.startAt ? formatDate(selectedTask.startAt) : '未设置' }}</strong></button>
          <button type="button" @click="openEdit(selectedTask)"><span>持续时间</span><strong>{{ selectedTask.durationMinutes ? `${selectedTask.durationMinutes} 分钟` : '未设置' }}</strong></button>
          <button type="button" @click="openEdit(selectedTask)"><span>父任务</span><strong>{{ selectedTask.parentId ? taskName(selectedTask.parentId) : '无' }}</strong></button>
          <button type="button" @click="openEdit(selectedTask)"><span>全天</span><strong>{{ selectedTask.allDay ? '是' : '否' }}</strong></button>
          <div class="clone-detail-property"><span>创建时间</span><strong>{{ formatCreatedAt(selectedTask.createdAt) }}</strong></div>
        </section>
        <button type="button" class="clone-detail-edit-fields" :aria-expanded="detailFieldsOpen" @click="toggleDetailFields"><Pencil :size="14" />{{ detailFieldsOpen ? '收起更多字段' : '编辑更多字段' }}</button>
        </section>
      </div>
      <form v-if="commentComposerOpen && !editorOpen" class="clone-detail-comment-composer" aria-label="添加评论" @submit.prevent="addComment">
        <textarea v-model="commentDraft" rows="1" aria-label="添加评论" placeholder="添加评论" :disabled="!store.online" @keydown.enter.exact.prevent="addComment" />
        <button v-if="commentDraft.trim()" type="submit" :disabled="!store.online" aria-label="发表评论"><ArrowUp :size="16" /></button>
        <p v-if="commentError" class="clone-error" role="alert">{{ commentError }}</p>
      </form>
      <footer v-if="selectedTask && !editorOpen" class="clone-detail-footer" aria-label="详情工具">
          <button type="button" class="clone-detail-footer-list" aria-label="任务清单" :aria-expanded="detailListPickerOpen" @click.stop="toggleDetailListPicker"><ListTodo :size="17" /><span>{{ listName(selectedTask.listId) }}</span></button>
          <div v-if="detailListPickerOpen" class="clone-detail-list-picker" role="listbox" aria-label="移动到清单" @click.stop>
            <input v-model="detailListQuery" type="text" aria-label="搜索清单" placeholder="搜索" />
            <button v-for="list in detailListOptions" :key="list.publicId" type="button" role="option" :aria-selected="list.publicId === selectedTask.listId" @click="moveDetailTaskToList(list)"><span class="clone-detail-list-dot" :style="{ backgroundColor: list.color || 'transparent' }"></span>{{ list.name }}</button>
            <span v-if="!detailListOptions.length">没有匹配的清单</span>
          </div>
          <div class="clone-detail-footer-actions">
            <button type="button" aria-label="文本样式" :aria-pressed="detailRichToolbarOpen" @click="detailRichToolbarOpen = !detailRichToolbarOpen; if (detailRichToolbarOpen) commentComposerOpen = false"><AlignLeft :size="17" /></button>
            <button type="button" aria-label="打开评论" :aria-pressed="commentComposerOpen" @click="commentComposerOpen = !commentComposerOpen; if (commentComposerOpen) detailRichToolbarOpen = false; commentError = ''"><MessageCircle :size="17" /><small v-if="comments.length">{{ comments.length }}</small></button>
            <button type="button" aria-label="更多详情操作" aria-haspopup="menu" :aria-expanded="detailMoreOpen" @click.stop="detailMoreOpen = !detailMoreOpen; detailMoreSection = ''"><Ellipsis :size="17" /></button>
          </div>
          <div v-if="detailMoreOpen" class="clone-detail-more-menu" role="menu" aria-label="任务详情更多操作" @pointerdown.stop @click.stop>
            <button v-if="!isNoteSelected" role="menuitem" type="button" @click="detailMoreOpen = false; createSubtask(selectedTask)"><ListPlus :size="17" />添加子任务</button>
            <button v-if="!isNoteSelected" role="menuitem" type="button" @click="associateTaskParent"><ListTree :size="17" />关联主任务</button>
            <button role="menuitem" type="button" @click="pinDetailTask"><Pin :size="17" />置顶</button>
            <button v-if="!isNoteSelected" role="menuitem" type="button" @click="abandonDetailTask"><CircleX :size="17" />放弃</button>
            <button role="menuitem" type="button" :aria-expanded="detailMoreSection === 'move'" @click="detailMoreSection = detailMoreSection === 'move' ? '' : 'move'"><FolderInput :size="17" />移动到<ChevronRight :size="15" /></button>
            <div v-if="detailMoreSection === 'move'" class="clone-detail-more-submenu" role="group" aria-label="移动到清单">
              <button v-for="list in activeLists" :key="list.publicId" type="button" :disabled="list.publicId === selectedTask.listId" @click="moveDetailTaskFromMenu(list)">{{ list.name }}</button>
            </div>
            <button role="menuitem" type="button" :aria-expanded="detailMoreSection === 'tags'" @click="detailMoreSection = detailMoreSection === 'tags' ? '' : 'tags'"><Tag :size="17" />标签<ChevronRight :size="15" /></button>
            <div v-if="detailMoreSection === 'tags'" class="clone-detail-more-submenu" role="group" aria-label="任务标签">
              <label v-for="tag in sortedTags" :key="tag.publicId"><input type="checkbox" :checked="selectedTask.tagIds?.includes(tag.publicId)" @change="toggleDetailTaskTag(tag)" />{{ tag.name }}</label>
              <span v-if="!sortedTags.length">暂无标签</span>
            </div>
            <button role="menuitem" type="button" @click="chooseDetailAttachment"><Paperclip :size="17" />上传附件</button>
            <button v-if="!isNoteSelected" role="menuitem" type="button" :aria-expanded="detailMoreSection === 'focus'" @click="detailMoreSection = detailMoreSection === 'focus' ? '' : 'focus'"><CircleDot :size="17" />开始专注<ChevronRight :size="15" /></button>
            <div v-if="detailMoreSection === 'focus'" class="clone-detail-more-submenu" role="group" aria-label="专注方式">
              <button type="button" @click="startDetailFocus('POMODORO')">开始番茄专注</button>
              <button type="button" @click="startEstimatedFocus">预计番茄时长…</button>
              <button type="button" @click="startDetailFocus('COUNT_UP')">开始正计时</button>
            </div>
            <div class="clone-detail-more-separator" aria-hidden="true"></div>
            <button role="menuitem" type="button" @click="openActivityPanel"><SlidersHorizontal :size="17" />{{ isNoteSelected ? '笔记动态' : '任务动态' }}</button>
            <button v-if="!isNoteSelected" role="menuitem" type="button" @click="openTaskTemplateDialog"><FileText :size="17" />保存为模板</button>
            <button role="menuitem" type="button" @click="duplicateDetailTask"><CopyPlus :size="17" />创建副本</button>
            <button role="menuitem" type="button" @click="copyDetailTaskLink"><Link :size="17" />复制链接</button>
            <button v-if="!isNoteSelected" role="menuitem" type="button" @click="convertDetailTaskToNoteNow"><StickyNote :size="17" />转换为笔记</button>
            <button v-else role="menuitem" type="button" @click="convertDetailNoteToTask"><FileText :size="17" />转换为任务</button>
            <button role="menuitem" type="button" @click="openExportDialog"><Download :size="17" />导出<ChevronRight :size="15" /></button>
            <button role="menuitem" type="button" @click="printDetailTask"><Printer :size="17" />打印</button>
            <button role="menuitem" type="button" @click="openDetailTaskNote"><StickyNote :size="17" />打开便签</button>
            <button role="menuitem" type="button" class="danger" @click="deleteDetailTask"><Trash2 :size="17" />删除</button>
          </div>
          <input ref="detailAttachmentInput" class="clone-detail-attachment-input" type="file" aria-label="选择任务附件" @change="uploadAttachment" />
      </footer>
      <section v-if="activityPanelOpen" class="clone-detail-activity-panel" role="dialog" :aria-label="isNoteTask(activityPanelTask) ? '笔记动态' : '任务动态'" @click.stop>
        <header>
          <strong>{{ isNoteTask(activityPanelTask) ? '笔记动态' : '任务动态' }}</strong>
          <button type="button" aria-label="关闭动态" @click="activityPanelOpen = false"><X :size="16" /></button>
        </header>
        <div class="clone-detail-activity-panel-body">
          <p v-if="activityLoading" class="clone-muted">加载中…</p>
          <p v-else-if="!activities.length" class="clone-muted">{{ store.online ? '暂无动态' : '离线时无法加载动态' }}</p>
          <ol v-else>
            <li v-for="item in activities" :key="item.publicId">
              <span class="clone-detail-activity-dot" aria-hidden="true"></span>
              <div>
                <p>{{ item.summary }}</p>
                <time>{{ formatListActivityTime(item.createdAt) }}</time>
              </div>
            </li>
          </ol>
        </div>
      </section>
      </template>
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
      <div v-if="contextSubmenu === 'tags'" class="clone-context-submenu"><label v-for="tag in sortedTags" :key="tag.publicId"><input type="checkbox" :checked="context.task?.tagIds?.includes(tag.publicId)" @change="toggleContextTag(tag)" />{{ tag.name }}</label><span v-if="!sortedTags.length">暂无标签</span></div>
      <button type="button" @click="createSubtask(context.task, { openDetail: true }); closeContext()"><Plus :size="14" />添加子任务</button>
      <button type="button" @click="startContextFocus"><Timer :size="14" />开始专注</button>
      <button type="button" @click="copyContextTaskLink"><Copy :size="14" />复制任务链接</button>
      <button type="button" @click="contextActivityTask = context.task; closeContext(); openActivityPanel()"><SlidersHorizontal :size="14" />{{ isNoteTask(context.task) ? '笔记动态' : '任务动态' }}</button>
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
              <div class="clone-filter-field"><span>标签</span><details class="clone-filter-select"><summary @click="closeOtherFilterSelect('tag')">{{ filterTagSummary }}<ChevronDown :size="14" /></summary><div class="clone-filter-select-menu" role="group" aria-label="标签范围" @click.stop><label><input type="radio" v-model="organizationDialog.filter.tagMode" value="ALL" @change="organizationDialog.filter.tagIds = []" />所有</label><hr /><label><input type="radio" v-model="organizationDialog.filter.tagMode" value="HAS_TAGS" @change="organizationDialog.filter.tagIds = []" />有标签</label><label><input type="radio" v-model="organizationDialog.filter.tagMode" value="NO_TAGS" @change="organizationDialog.filter.tagIds = []" />无标签</label><hr /><label v-for="tag in sortedTags" :key="tag.publicId" class="clone-filter-tag-option"><input type="checkbox" :checked="organizationDialog.filter.tagMode === 'SELECTED' && organizationDialog.filter.tagIds.includes(tag.publicId)" @change="toggleFilterTag(tag.publicId, $event.target.checked)" />{{ tag.name }}</label></div></details></div>
              <label class="clone-filter-field"><span>日期</span><select v-model="organizationDialog.filter.dueRange" @change="setFilterDueRange"><option value="ANY">所有</option><option value="NONE">无日期</option><option value="OVERDUE">已过期</option><option value="REPEATING">重复日期</option><option value="TODAY">今天</option><option value="TOMORROW">明天</option><option value="THIS_WEEK">本周</option><option value="NEXT_WEEK">下周</option><option value="THIS_MONTH">本月</option><option value="NEXT_MONTH">下月</option><option value="CUSTOM">时间段</option></select></label>
              <div v-if="organizationDialog.filter.dueRange === 'CUSTOM'" class="clone-filter-date-range"><label><span><CalendarClock :size="14" />时间段</span><select aria-label="开始日期" :value="organizationDialog.filter.dueStart" @change="setFilterDateEndpoint('start', $event.target.value)"><option v-for="option in filterDateOptions" :key="option.value" :value="option.value" :disabled="option.offset === 30">{{ option.label }}</option></select></label><label class="clone-filter-date-separator">~</label><label><select aria-label="结束日期" :value="organizationDialog.filter.dueEnd" @change="setFilterDateEndpoint('end', $event.target.value)"><option v-for="option in filterDateOptions" :key="option.value" :value="option.value" :disabled="option.value <= organizationDialog.filter.dueStart">{{ option.label }}</option></select></label></div>
              <fieldset class="clone-filter-priorities"><legend>优先级</legend><label><input type="radio" name="filter-priority-all" :checked="organizationDialog.filter.priorities.length === 0" @change="organizationDialog.filter.priorities = []" />所有</label><label v-for="option in filterPriorityOptions" :key="option.value"><input v-model="organizationDialog.filter.priorities" type="checkbox" :value="option.value" />{{ option.label }}</label></fieldset>
              <label class="clone-filter-field"><span>内容包含</span><input v-model.trim="organizationDialog.filter.keyword" maxlength="200" placeholder="输入任务关键词" /></label>
              <fieldset class="clone-filter-task-types"><legend>任务类型</legend><label><input type="radio" name="filter-task-type-all" :checked="organizationDialog.filter.taskTypes.length === 0" @change="organizationDialog.filter.taskTypes = []" />所有</label><label><input v-model="organizationDialog.filter.taskTypes" type="checkbox" value="TASK" />任务</label><label><input v-model="organizationDialog.filter.taskTypes" type="checkbox" value="NOTE" />笔记</label></fieldset>
            </template>
            <TaskFilterRuleEditor v-if="organizationDialog.filter.mode === 'ADVANCED'" v-model="organizationDialog.filter.advanced" :lists="activeLists" :groups="sortedListGroups" :tags="sortedTags" />
          </template>
        </template>
        <footer :class="{ 'clone-inbox-color-actions': isInboxColorEditor, 'clone-filter-preview-actions': organizationDialog.kind === 'filter' && organizationDialog.filter.previewed }"><template v-if="isInboxColorEditor"><button type="submit" class="clone-primary-button">保存</button><button type="button" @click="organizationDialogOpen = false">取消</button></template><template v-else-if="organizationDialog.kind === 'filter' && organizationDialog.filter.previewed"><button type="button" @click="organizationDialog.filter.previewed = false">取消</button><button type="submit" class="clone-primary-button" :disabled="!filterDraftValid"><Save :size="14" />保存</button></template><template v-else-if="organizationDialog.kind === 'filter'"><button type="button" class="clone-filter-preview-button" :disabled="!filterDraftValid" @click="previewFilterDraft"><Eye :size="14" />预览</button><button type="button" @click="organizationDialogOpen = false">取消</button><button type="submit" class="clone-primary-button" :disabled="!filterDraftValid"><Save :size="14" />保存</button></template><template v-else><button type="button" @click="organizationDialogOpen = false">取消</button><button type="submit" class="clone-primary-button">保存</button></template></footer>
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
    <Dialog v-model:open="trashClearOpen" title="清空垃圾桶"><div class="clone-confirmation"><p>将永久删除垃圾桶中的 {{ trashedTaskCount }} 项任务，且无法恢复。</p><footer><button type="button" :disabled="trashClearBusy" @click="trashClearOpen = false">取消</button><button type="button" class="clone-danger-button" :disabled="trashClearBusy || !trashedTaskCount" @click="clearTrashPermanently">{{ trashClearBusy ? '清除中…' : '永久清除' }}</button></footer></div></Dialog>
    <Dialog v-model:open="exportDialogOpen" title="导出" content-class="clone-export-dialog">
      <div class="clone-export-body">
        <div class="clone-export-tabs" role="tablist" aria-label="导出格式"><button type="button" role="tab" :aria-selected="exportFormat === 'markdown'" :class="{ active: exportFormat === 'markdown' }" @click="exportFormat = 'markdown'">Markdown</button><button type="button" role="tab" :aria-selected="exportFormat === 'text'" :class="{ active: exportFormat === 'text' }" @click="exportFormat = 'text'">纯文本</button></div>
        <pre class="clone-export-preview" aria-label="导出内容预览">{{ exportContent }}</pre>
        <footer><button type="button" @click="exportDialogOpen = false">取消</button><button type="button" @click="copyExportContent"><Copy :size="14" />复制</button><button type="button" class="clone-primary-button" @click="downloadExportContent"><Download :size="14" />下载</button></footer>
      </div>
    </Dialog>
    <Dialog v-model:open="taskTemplateDialogOpen" title="保存为模板">
      <form class="clone-template-form" @submit.prevent="saveTaskTemplateFromDialog">
        <p class="clone-muted">模板仅保存此任务的内容和标签。</p>
        <input v-model.trim="taskTemplateName" aria-label="模板名称" placeholder="模板名称" maxlength="120" />
        <footer><button type="button" @click="taskTemplateDialogOpen = false">取消</button><button type="submit" class="clone-primary-button" :disabled="!taskTemplateName">保存</button></footer>
      </form>
    </Dialog>
    <Dialog v-model:open="focusEstimateOpen" title="预计番茄时长">
      <form class="clone-template-form" @submit.prevent="confirmEstimatedFocus">
        <label class="clone-focus-estimate-field">时长（分钟）<input v-model.number="focusEstimateMinutes" type="number" min="1" max="180" aria-label="预计番茄时长（分钟）" /></label>
        <footer><button type="button" @click="focusEstimateOpen = false">取消</button><button type="submit" class="clone-primary-button">开始专注</button></footer>
      </form>
    </Dialog>
    <Dialog v-model:open="focusEstimateOpen" title="预计番茄时长">
      <form class="clone-template-form" @submit.prevent="confirmEstimatedFocus">
        <label class="clone-focus-estimate-field">时长（分钟）<input v-model.number="focusEstimateMinutes" type="number" min="1" max="180" aria-label="预计番茄时长（分钟）" /></label>
        <footer><button type="button" @click="focusEstimateOpen = false">取消</button><button type="submit" class="clone-primary-button">开始专注</button></footer>
      </form>
    </Dialog>
    <Dialog v-model:open="deleteTaskConfirmOpen" :title="deleteTaskTargets.length > 1 ? `删除 ${deleteTaskTargets.length} 项任务` : '删除任务'" content-class="clone-delete-task-dialog">
      <div class="clone-confirmation">
        <p v-if="deleteTaskTargets.length === 1">确定删除任务“{{ deleteTaskTargets[0].title }}”吗？</p>
        <p v-else>确定删除选中的 {{ deleteTaskTargets.length }} 项任务吗？删除后可在垃圾桶中恢复。</p>
        <footer><button type="button" :disabled="deleteTaskBusy" @click="cancelDeleteTasks">取消</button><button type="button" class="clone-danger-button" :disabled="deleteTaskBusy || !deleteTaskTargets.length" @click="confirmDeleteTasks">{{ deleteTaskBusy ? '删除中…' : '删除' }}</button></footer>
      </div>
    </Dialog>
    <Dialog v-model:open="listCopyOpen" title="复制清单"><form class="clone-organization-form" @submit.prevent="confirmListCopy"><fieldset><legend>复制任务</legend><label><input v-model="listCopy.mode" type="radio" value="OPEN_ONLY" />只复制未完成任务</label><label><input v-model="listCopy.mode" type="radio" value="ALL_PRESERVE" />复制全部任务，保留已完成状态</label><label><input v-model="listCopy.mode" type="radio" value="ALL_OPEN" />复制全部任务，并将任务设为未完成</label></fieldset><p class="clone-dialog-note">新清单将命名为“{{ listCopy.name }}”。</p><footer><button type="button" :disabled="listCopy.busy" @click="listCopyOpen = false">取消</button><button type="submit" class="clone-primary-button" :disabled="listCopy.busy">{{ listCopy.busy ? '复制中…' : '复制' }}</button></footer></form></Dialog>
    <Dialog v-model:open="organizationConfirmOpen" :title="organizationConfirmation.title"><template #title v-if="organizationConfirmation.action === 'delete-list'">删除清单“<TaskOrganizationIcon v-if="organizationConfirmation.item?.icon" class="clone-confirmation-title-icon" :value="organizationConfirmation.item.icon" :size="16" /><ListTodo v-else class="clone-confirmation-title-icon" :size="16" /> {{ organizationConfirmation.item?.name }}”？</template><div class="clone-confirmation"><p v-if="organizationConfirmation.action === 'dissolve-group'">解散后，文件夹（<TaskOrganizationIcon v-if="organizationConfirmation.item?.icon" class="clone-confirmation-title-icon" :value="organizationConfirmation.item.icon" :size="16" /><Folder v-else class="clone-confirmation-title-icon" :size="16" /> {{ organizationConfirmation.item?.name }}）中的清单将直接显示在侧边栏。</p><p v-else>{{ organizationConfirmation.message }}</p><footer><button type="button" :disabled="organizationConfirmation.busy" @click="organizationConfirmOpen = false">取消</button><button type="button" class="clone-danger-button" :disabled="organizationConfirmation.busy" @click="confirmOrganizationAction">{{ organizationConfirmation.busy ? '处理中…' : organizationConfirmation.confirmLabel }}</button></footer></div></Dialog>
    <Dialog v-model:open="tagMergeOpen" title="合并标签到…"><form class="clone-organization-form" @submit.prevent="confirmTagMerge"><p>你可以将标签“{{ tagMerge.source?.name }}”下的任务合并到另一个标签。合并后，标签“{{ tagMerge.source?.name }}”将被删除；其子标签会迁移至目标标签层级。</p><label>选择标签<select v-model="tagMerge.targetId" required><option value="" disabled>选择标签</option><option v-for="tag in mergeTargets" :key="tag.publicId" :value="tag.publicId">{{ tag.name }}</option></select></label><footer><button type="button" :disabled="tagMerge.busy" @click="tagMergeOpen = false">取消</button><button type="submit" class="clone-primary-button" :disabled="tagMerge.busy || !mergeTargets.length">{{ tagMerge.busy ? '合并中…' : '确定' }}</button></footer></form></Dialog>
    <Dialog v-model:open="detailParentPickerOpen" :title="selectedIds.size ? '批量关联主任务' : '关联主任务'" content-class="clone-parent-picker-dialog">
      <div class="clone-parent-picker">
        <label class="clone-parent-picker-search"><Search :size="15" /><input v-model="detailParentQuery" type="search" placeholder="搜索任务" aria-label="搜索主任务" /></label>
        <button v-if="!selectedIds.size" type="button" class="clone-parent-picker-none" @click="setDetailParent(null)">无主任务</button>
        <div class="clone-parent-picker-list">
          <button v-for="candidate in parentPickerCandidates" :key="candidate.publicId" type="button" @click="setDetailParent(candidate)">
            <span>{{ candidate.title }}</span><small>{{ listName(candidate.listId) }}</small>
          </button>
          <p v-if="!parentPickerCandidates.length">没有可关联的任务</p>
        </div>
      </div>
    </Dialog>
  </section>
</template>

<script setup>
import {
  computed,
  defineAsyncComponent,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter, RouterLink } from "vue-router";
import {
  Archive,
  AlignLeft,
  BrushCleaning,
  CalendarClock,
  Bell,
  Check,
  CheckCircle2,
  FolderInput,
  Heading1,
  Heading2,
  Heading3,
  CheckSquare,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Circle,
  CircleDot,
  CircleMinus,
  CircleX,
  Clock3,
  CalendarDays,
  Columns3,
  ArrowDownUp,
  ArrowRight,
  ArrowUp,
  Layers3,
  MessageCircle,
  ClipboardList,
  Copy,
  CopyPlus,
  Ellipsis,
  Download,
  Eye,
  EyeOff,
  Flag,
  FileText,
  GanttChart,
  History,
  GripVertical,
  Inbox,
  ListFilter,
  ListOrdered,
  Minus,
  Quote,
  List,
  ListPlus,
  ListTree,
  ListTodo,
  Link,
  Merge,
  PanelLeft,
  PanelLeftClose,
  Paperclip,
  Pencil,
  Printer,
  Pin,
  Plus,
  RotateCcw,
  Search,
  Save,
  Repeat2,
  SlidersHorizontal,
  StickyNote,
  Tag,
  Timer,
  Trash2,
  Ungroup,
  X,
  UserRound,
  Folder,
} from "lucide-vue-next";
import TaskRichTextEditor from "../components/tasks/TaskRichTextEditor.vue";
const TaskMilkdownEditor = defineAsyncComponent(() => import("../components/tasks/TaskMilkdownEditor.vue"));
import TaskSummaryWorkspace from "./TaskSummaryWorkspace.vue";
import TaskFilterRuleEditor from "../components/tasks/TaskFilterRuleEditor.vue";
import TaskColorPicker from "../components/TaskColorPicker.vue";
import { useExclusivePanels } from "../utils/exclusivePanels.js";
import TaskOrganizationIcon from "../components/tasks/TaskOrganizationIcon.vue";
import TaskDateTimePopover from "../components/tasks/TaskDateTimePopover.vue";
import TasksTimelineView from "./TasksTimelineView.vue";
import Dialog from "../components/ui/Dialog.vue";
import { organizationIconCategories, searchOrganizationIcons } from "./taskOrganizationIcons.js";
import { getListDropPlacement, moveItem, reorderListDrop } from "./taskSidebar.js";
import { groupTaskItems, sortTaskItems, taskGroupDropPatch } from "./taskList.js";
import { buildTaskListGroups } from "./taskWorkspaceTree.js";
import { moveChecklistItem as reorderChecklistItems } from "./taskChecklist.js";
import { matchesFilterRules, serializeFilterRules } from "./taskFilterRules.js";
import { advancedFilterConditionsFromRules, advancedFilterConditionsValid, buildAdvancedFilterRules } from "./taskAdvancedFilter.js";
import { buildTaskSidebarCounts } from "./taskSidebarCounts.js";
import { filterCompletionTasks, normalizeCompletionListSelection, toggleCompletionListSelection } from "./taskCompletionFilters.js";
import { mergeSidebarPreferenceChanges } from "./taskSidebarPreferences.js";
import { calendarMonthDays, localDateKey, nextWholeHour, quickReminderOptions as getQuickReminderOptions, quickReminderPayloads, quickReminderPresetLabel, QUICK_ALL_DAY_REMINDER_TIME } from "./taskQuickAddDate.js";
import { buildQuickRepeatRule, createQuickRepeatState, DAY_CODES, parseLunarLabel, quickRepeatLabel, quickRepeatMatchesDate, quickRepeatPresets, quickRepeatValidation } from "./taskQuickAddRepeat.js";
import { formatAttachmentSize, uploadQuickAttachments, validateQuickAttachmentFiles } from "./taskQuickAddAttachments.js";
import { quickAddTokenAtCaret, replaceQuickAddToken } from "./taskQuickAddSuggestions.js";
import { useTasksStore } from "../stores/tasks.js";
import { useTaskFocusStore } from "../stores/taskFocus.js";
import { message } from "../services/message.js";
import {
  apiGetTaskSidebarPreferences,
  apiGetTaskSettings,
  apiUpdateTaskSidebarPreferences,
  apiParseTaskQuickAdd,
  apiCreateTaskReminder,
  apiCreateTaskComment,
  apiDeleteTaskAttachment,
  apiDeleteTaskComment,
  apiDeleteTaskReminder,
  apiDownloadTaskAttachment,
  apiListTaskActivity,
  apiListTaskListActivity,
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
const isSummary = computed(() => route.name === "tasks-shortcut" && route.params.shortcut === "summary");
const focusStore = useTaskFocusStore();
const filters = ref([]);
const quickTitle = ref("");
const quickTitleInput = ref(null);
const quickSuggestion = ref(null);
const quickSuggestionIndex = ref(0);
const quickAttachmentInput = ref(null);
const quickAttachments = ref([]);
const quickAttachmentError = ref("");
const quickAttachmentSelectionError = ref("");
const pendingQuickAttachmentBatches = ref([]);
const quickAttachmentUploading = ref(false);
const quickAttachmentProgress = ref(0);
const quickAttachmentMessage = computed(() => {
  if (quickAttachmentUploading.value) return `正在上传快捷新增附件 ${quickAttachmentProgress.value}%`;
  if (quickAttachmentSelectionError.value) return quickAttachmentSelectionError.value;
  if (quickAttachmentError.value) return `附件上传未完成：${quickAttachmentError.value}；文件仍保留，可重试`;
  if (pendingQuickAttachmentBatches.value.length) return store.online ? "任务已创建，附件等待上传" : "任务已创建；当前离线，附件待联网后重试";
  if (quickAttachments.value.length) return store.online ? "附件将在任务创建并同步后上传" : "当前离线，附件暂存于本页；任务创建后联网上传";
  return "";
});
const quickDraft = ref(null);
const quickDraftDue = ref("");
const quickDraftStart = ref("");
const quickDraftAllDay = ref(false);
const quickDraftReminder = ref(null);
const quickParseError = ref("");
const quickParsing = ref(false);
const quickSaving = ref(false);
const quickAddExpanded = ref(false);
const quickAction = ref("");
const taskTemplatesOpen = ref(false);
const taskTemplateQuery = ref("");
const quickTemplateDraft = ref(null);
const boardQuickAddOpen = ref(false);
const boardQuickAddInput = ref(null);
const boardQuickAddGroup = ref(null);
const boardQuickAddTitle = ref("");
const boardQuickAttachmentInput = ref(null);
const boardQuickAddAttachments = ref([]);
const boardQuickAddError = ref("");
const boardQuickAddSaving = ref(false);
const quickInputMode = ref("DETAILED");
const quickPriorityToggleRef = ref(null);
const quickPriorityMenuRef = ref(null);
const quickPriorityMenuStyle = ref({});
const quickListToggleRef = ref(null);
const quickListMenuRef = ref(null);
const quickListMenuStyle = ref({});
const quickMoreToggleRef = ref(null);
const quickMoreMenuRef = ref(null);
const quickMoreMenuStyle = ref({});
const quickTagsMenuRef = ref(null);
const quickTagsMenuStyle = ref({});
const quickListQuery = ref("");
const quickListFolderExpanded = reactive({});
const quickTagsQuery = ref("");
const quickTagsDraft = ref([]);
const displaySettingsOpen = ref(false);
const showTags = ref(true);
const showListLink = ref(true);
const quickEntry = reactive({ dueAt: "", startAt: "", allDay: false, dateSelected: false, priority: "NONE", prioritySelected: false, listId: "", listSelected: false, tagIds: [] });
const quickRepeat = reactive(createQuickRepeatState());
const detailRepeat = reactive(createQuickRepeatState());
const quickAddSettings = ref({ defaultListId: "", defaultDueDate: "TODAY", defaultDueTime: "NONE", defaultReminderMinutes: null });
const quickRepeatError = ref("");
const detailRepeatError = ref("");
const quickDateMode = ref("DATE");
const quickCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const quickRangeStart = ref("");
const quickRangeEnd = ref("");
const quickSelectedDate = ref("");
const quickRangeStartTime = ref("09:00");
const quickRangeEndTime = ref("10:00");
const quickDateTime = ref("");
const quickAddReminder = reactive({ enabled: false, presets: [], amount: 1, unit: "DAY", time: "09:00", dailyUntilDone: false });
const quickWeekdays = ["一", "二", "三", "四", "五", "六", "日"];
const quickCalendarDays = computed(() => calendarMonthDays(quickCalendarMonth.value));
const quickCalendarMeta = ref({ holidays: {}, lunar: new Map() });
const quickCalendarMetaRequest = ref(0);
async function loadQuickCalendarMeta() {
  const request = ++quickCalendarMetaRequest.value;
  const days = calendarMonthDays(quickCalendarMonth.value);
  const from = localDateKey(days[0].date);
  const lastDay = days[days.length - 1].date;
  const to = localDateKey(new Date(lastDay.getFullYear(), lastDay.getMonth(), lastDay.getDate() + 1));
  try {
    const result = await apiGetTaskCalendar({ from, to, layers: "holiday", lunar: 1 });
    if (request !== quickCalendarMetaRequest.value) return;
    quickCalendarMeta.value = {
      holidays: Object.fromEntries((result?.holidays || []).map(item => [item.date, { off: Boolean(item.off), name: item.name }])),
      lunar: new Map((result?.lunar || []).map(item => {
        const parsed = parseLunarLabel(item.lunarDate);
        return parsed ? [item.date, { ...parsed, label: item.festival || item.solarTerm || lunarDayLabel(item.lunarDate) || "" }] : null;
      }).filter(Boolean)),
    };
  } catch {
    /* 月历元数据获取失败时保持现有数据，仅缺少节假日/农历高亮 */
  }
}
watch(quickCalendarMonth, () => { void loadQuickCalendarMeta(); });
const quickRepeatRuleActive = computed(() => buildQuickRepeatRule(quickRepeat));
const quickRepeatMatchContext = computed(() => ({ holidays: quickCalendarMeta.value.holidays, lunar: quickCalendarMeta.value.lunar, anchor: quickRepeatAnchorDate.value }));
const quickTodayKey = localDateKey(new Date());
const quickDateTimeCalendarDays = computed(() => quickCalendarDays.value.map((day) => ({ ...day, otherMonth: !day.inMonth, selected: isQuickDateSelected(day.key), today: day.key === quickTodayKey, lunar: quickCalendarMeta.value.lunar.get(day.key)?.label || "", repeat: Boolean(quickRepeatRuleActive.value) && day.key >= quickTodayKey && quickRepeatMatchesDate(quickRepeat, day.date, quickRepeatMatchContext.value) })));
const quickRepeatCalendarDays = computed(() => {
  const dueDate = quickEntry.dueAt ? new Date(quickEntry.dueAt) : new Date();
  const year = dueDate.getFullYear();
  const monthIndex = quickRepeat.frequency === "YEARLY" ? Number(quickRepeat.month || 1) - 1 : dueDate.getMonth();
  const first = new Date(year, monthIndex, 1);
  const offset = (first.getDay() + 6) % 7;
  return [...Array(offset).fill(null), ...Array.from({ length: new Date(year, monthIndex + 1, 0).getDate() }, (_, index) => index + 1)];
});
const detailRepeatCalendarDays = computed(() => {
  const anchor = (editorOpen.value ? draft.dueAt : selectedTask.value?.dueAt) || new Date();
  const date = new Date(anchor);
  const year = date.getFullYear();
  const monthIndex = detailRepeat.frequency === "YEARLY" ? Number(detailRepeat.month || 1) - 1 : date.getMonth();
  const first = new Date(year, monthIndex, 1);
  const offset = (first.getDay() + 6) % 7;
  return [...Array(offset).fill(null), ...Array.from({ length: new Date(year, monthIndex + 1, 0).getDate() }, (_, index) => index + 1)];
});
const quickCalendarTitle = computed(() => new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long" }).format(quickCalendarMonth.value));
const rowDateCalendarDays = computed(() => quickCalendarDays.value.map((day) => ({ ...day, otherMonth: !day.inMonth, selected: day.key === rowDatePanel.selectedDate || day.key === rowDatePanel.rangeStart || day.key === rowDatePanel.rangeEnd, today: day.key === quickTodayKey, lunar: quickCalendarMeta.value.lunar.get(day.key)?.label || "" })));
const quickReminderOptions = computed(() => getQuickReminderOptions(quickEntry.allDay));
const quickReminderTime = computed(() => quickAddReminder.time || QUICK_ALL_DAY_REMINDER_TIME);
const quickReminderUnitText = { MINUTE: "分钟", HOUR: "小时", DAY: "天", WEEK: "周" };
const quickReminderSummary = computed(() => quickAddReminder.presets.map(value => value === "CUSTOM" ? `自定义 ${Math.max(1, Number(quickAddReminder.amount) || 1)} ${quickReminderUnitText[quickAddReminder.unit] || ""}` : quickReminderPresetLabel(value, quickEntry.allDay, quickReminderTime.value)).join("、"));
const quickReminderMenuOptions = computed(() => quickReminderOptions.value.map(option => ({ ...option, label: quickEntry.allDay && option.value !== "CUSTOM" ? `${option.label} (${quickReminderTime.value})` : option.label })));
const QUICK_ALL_DAY_REMINDER_VALID = ["TODAY", "1_DAY", "2_DAY", "3_DAY", "1_WEEK"];
const quickReminderRecentOptions = computed(() => {
  const valid = value => {
    if (/^CUSTOM:(\d+):(MINUTE|HOUR|DAY|WEEK)$/.test(value)) return quickEntry.allDay ? !value.endsWith(":MINUTE") && !value.endsWith(":HOUR") : true;
    return quickEntry.allDay ? QUICK_ALL_DAY_REMINDER_VALID.includes(value) : ["ON_TIME", "5_MIN", "30_MIN", "1_HOUR", "1_DAY"].includes(value);
  };
  return quickReminderRecent.value
    .filter(value => value !== "CUSTOM" && valid(value) && !quickReminderOptions.value.some(option => option.value === value))
    .map(value => ({ value, label: quickReminderPresetLabel(value, quickEntry.allDay, quickReminderTime.value) }));
});
const quickReminderCustomPreview = computed(() => {
  const key = quickSelectedDate.value || (quickEntry.dueAt ? localDateKey(new Date(quickEntry.dueAt)) : localDateKey(new Date()));
  if (!key) return "";
  const base = quickEntry.allDay
    ? new Date(`${key}T00:00:00`)
    : new Date(`${key}T${quickDateTime.value || "09:00"}`);
  if (Number.isNaN(base.getTime())) return "";
  const amount = Math.max(1, Number(quickAddReminder.amount) || 1);
  if (quickEntry.allDay) {
    base.setDate(base.getDate() - amount * (quickAddReminder.unit === "WEEK" ? 7 : 1));
    const [hours, minutes] = String(quickReminderTime.value).split(":").map(Number);
    base.setHours(hours || 0, minutes || 0, 0, 0);
  } else {
    const offset = amount * ({ MINUTE: 1, HOUR: 60, DAY: 1440, WEEK: 10080 }[quickAddReminder.unit] || 1);
    base.setMinutes(base.getMinutes() - offset);
  }
  return `将于 ${new Intl.DateTimeFormat("zh-CN", { month: "long", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false }).format(base)} 提醒`;
});
const quickReminderOptionsForDraft = computed(() => (quickDraftReminder.value?.presets || []).map(value => quickReminderPresetLabel(value, quickDraftAllDay.value, quickDraftReminder.value?.time || QUICK_ALL_DAY_REMINDER_TIME)));
const quickDateContext = computed(() => {
  if (route.name === "tasks-today") return calendarDate.value || new Date();
  if (route.name === "tasks-tomorrow") return addDays(new Date(), 1);
  return null;
});
const quickSelectedList = computed(() => store.lists.find(item => item.publicId === quickEntry.listId) || null);
const quickListSummary = computed(() => quickSelectedList.value ? (quickSelectedList.value.systemKey === "INBOX" ? "收集箱" : quickSelectedList.value.name) : "清单");
const quickListMenuGroups = computed(() => {
  const query = quickListQuery.value.trim().toLowerCase();
  return sortedListGroups.value.filter(group => {
    const children = store.lists.filter(list => list.groupId === group.publicId && !list.archived);
    return children.length > 0 && (!query || group.name.toLowerCase().includes(query) || children.some(list => list.name.toLowerCase().includes(query)));
  });
});
function quickListMenuListsInGroup(groupId) {
  const query = quickListQuery.value.trim().toLowerCase();
  return store.lists.filter(list => list.groupId === groupId && !list.archived && list.publicId !== store.inbox?.publicId && (!query || list.name.toLowerCase().includes(query)));
}
const quickListMenuUngrouped = computed(() => {
  const query = quickListQuery.value.trim().toLowerCase();
  return store.lists.filter(list => !list.archived && !list.groupId && list.publicId !== store.inbox?.publicId && (!query || list.name.toLowerCase().includes(query)));
});
const quickTagsDraftTags = computed(() => quickTagsDraft.value.map(id => store.tags.find(tag => tag.publicId === id)).filter(Boolean));
const quickTagsMenuOptions = computed(() => {
  const query = quickTagsQuery.value.trim().toLowerCase();
  return store.tags.filter(tag => !query || tag.name.toLowerCase().includes(query));
});
const quickDateSummary = computed(() => {
  if (quickEntry.dueAt) return quickEntry.startAt ? `${quickDateLabel(quickEntry.startAt, false)} - ${quickDateLabel(quickEntry.dueAt, quickEntry.allDay)}` : quickDateLabel(quickEntry.dueAt, quickEntry.allDay);
  if (quickDateContext.value) return quickDateLabel(quickDateContext.value, true);
  return quickEntry.dateSelected ? "日期" : quickAddSettings.value.defaultDueDate === "TODAY" ? "今天" : "日期";
});
const checklistDragIndex = ref(null);
const checklistDropTarget = ref(null);
const sidebarHidden = ref(false);
const sidebarWidth = ref(248);
const sidebarEditing = ref(false);
const isMobileSidebar = ref(typeof window !== "undefined" && window.matchMedia("(max-width: 900px)").matches);
let sidebarTouchStart = null;
let sidebarResizeState = null;
let sidebarPreviousUserSelect = "";
const miniCalendarExpanded = ref(false);
const miniCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const miniCalendarResponse = ref({ tasks: [], lunar: [], holidays: [] });
const miniCalendarError = ref("");
let miniCalendarRequest = 0;
const sidebarPreferenceScope = ref("");
const defaultDetailWidth = Math.round(Math.min(680, Math.max(420, window.innerWidth * 0.35)));
const detailWidth = ref(defaultDetailWidth);
const detailPreferenceScope = ref("");
let detailResizeState = null;
let detailPreviousUserSelect = "";
const moreOpen = ref(false);
const listOptionsOpen = ref(false);
const workspaceMenuSection = ref("");
const workspaceMenuStyle = ref({});
const datePopoverOpen = ref(false);
const repeatPopoverOpen = ref(false);
const reminderPopoverOpen = ref(false);
const popoverViewportRevision = ref(0);
const datePopoverAnchor = ref(null);
const priorityPopoverAnchor = ref(null);
const datePopoverDue = ref("");
const datePopoverStart = ref("");
const datePopoverAllDay = ref(false);
const datePickerMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const datePickerMode = ref("DATE");
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
const selectedIds = reactive(new Set());
const deleteTaskConfirmOpen = ref(false);
const deleteTaskBusy = ref(false);
const deleteTaskTargets = ref([]);
const deleteTaskMode = ref("single");
const batchMode = computed(() => selectedIds.size > 0);
const batchPriority = ref("NONE");
const batchDueAt = ref("");
const batchDueClear = ref(false);
const batchListId = ref("");
const batchTagIds = ref([]);
const selectedTasks = computed(() => [...selectedIds].map(id => store.tasks.find(task => task.publicId === id)).filter(Boolean));
const batchParentCandidates = computed(() => {
  const targets = selectedTasks.value;
  if (!targets.length || targets.some(task => task.parentId) || new Set(targets.map(task => task.listId)).size !== 1) return [];
  const excluded = new Set(targets.map(task => task.publicId));
  let changed = true;
  while (changed) {
    changed = false;
    for (const task of store.tasks) {
      if (task.parentId && excluded.has(task.parentId) && !excluded.has(task.publicId)) {
        excluded.add(task.publicId);
        changed = true;
      }
    }
  }
  return store.tasks.filter(task => !task.deleted && task.status !== "COMPLETED" && !task.parentId && task.listId === targets[0].listId && !excluded.has(task.publicId));
});
const printOptionsOpen = ref(false);
const printMode = ref("TITLE");
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
const resolvingTaskConflict = ref(false);
async function resolveTaskConflict(conflict, strategy) {
  resolvingTaskConflict.value = true;
  try {
    await store.resolveConflict(conflict.operation?.opId || conflict.id, strategy);
    await store.syncNow();
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "冲突处理失败，本地数据已保留";
  } finally {
    resolvingTaskConflict.value = false;
  }
}
const organizationDropTarget = ref("");
const organizationDropPlacement = ref("");
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
// 笔记与任务共用一条记录：形态由所在清单类型决定（后续支持条目级类型）
const isNoteSelected = computed(() => Boolean(selectedTask.value && isNoteTask(selectedTask.value)));
const detailParentPickerOpen = ref(false);
const detailParentQuery = ref("");
const trashClearOpen = ref(false);
const trashClearBusy = ref(false);
const completionFilterMenu = ref("");
const completionFilters = reactive({ dateRange: "ALL", customStart: "", customEnd: "", listIds: null, scope: "ALL" });
const completedListDraft = ref(null);
const completedFolderExpanded = reactive({});
const completionDateOptions = [
  { value: "ALL", label: "所有日期" },
  { value: "THIS_WEEK", label: "本周" },
  { value: "LAST_WEEK", label: "上周" },
  { value: "THIS_MONTH", label: "本月" },
  { value: "CUSTOM", label: "自定义" },
];
const isCompletionRoute = computed(() => ["tasks-completed", "tasks-abandoned"].includes(route.name));
const isAbandonedRoute = computed(() => route.name === "tasks-abandoned");
const isTrashRoute = computed(() => route.name === "tasks-trash");
const completionDateLabel = computed(() => completionDateOptions.find(option => option.value === completionFilters.dateRange)?.label || "所有日期");
const completionListLabel = computed(() => completionFilters.listIds === null ? "所有清单" : completionFilters.listIds.length ? `已选 ${completionFilters.listIds.length} 项` : "未选择清单");
const completionScopeOptions = computed(() => isAbandonedRoute.value
  ? [{ value: "MINE", label: "我放弃的" }, { value: "ALL", label: "所有放弃" }]
  : [{ value: "MINE", label: "我完成的" }, { value: "ALL", label: "所有完成" }]);
const completionScopeLabel = computed(() => completionScopeOptions.value.find(option => option.value === completionFilters.scope)?.label || completionScopeOptions.value[1].label);
const completionUngroupedLists = computed(() => store.lists.filter(list => list.systemKey !== "INBOX" && !list.groupId));
function completionListsInGroup(groupId) { return store.lists.filter(list => list.groupId === groupId); }
function completionAvailableListIds() { return [...new Set([store.inbox?.publicId, ...store.lists.map(list => list.publicId)].filter(Boolean))]; }
function toggleCompletionFilterMenu(menu) {
  if (completionFilterMenu.value === menu) completionFilterMenu.value = "";
  else {
    if (menu === "list") completedListDraft.value = completionFilters.listIds === null ? null : [...completionFilters.listIds];
    completionFilterMenu.value = menu;
  }
}
function selectCompletionDateRange(value) {
  completionFilters.dateRange = value;
  if (value !== "CUSTOM") completionFilterMenu.value = "";
}
function toggleCompletedList(id, checked) {
  completedListDraft.value = toggleCompletionListSelection(completedListDraft.value, completionAvailableListIds(), id, checked);
}
function completedFolderSelected(groupId) {
  const ids = completionListsInGroup(groupId).map(list => list.publicId);
  return ids.length > 0 && (completedListDraft.value === null || ids.every(id => completedListDraft.value.includes(id)));
}
function toggleCompletedFolder(groupId, checked) {
  const ids = completionListsInGroup(groupId).map(list => list.publicId);
  const selected = new Set(completedListDraft.value === null ? completionAvailableListIds() : completedListDraft.value);
  for (const id of ids) checked ? selected.add(id) : selected.delete(id);
  completedListDraft.value = normalizeCompletionListSelection([...selected], completionAvailableListIds());
}
function cancelCompletedListSelection() { completedListDraft.value = completionFilters.listIds === null ? null : [...completionFilters.listIds]; completionFilterMenu.value = ""; }
function applyCompletedListSelection() {
  completionFilters.listIds = normalizeCompletionListSelection(completedListDraft.value, completionAvailableListIds());
  completionFilterMenu.value = "";
}
const listActivityOpen = ref(false);
const listActivityListId = ref("");
const listActivityTitle = ref("收集箱");
const listActivityItems = ref([]);
const listActivityLoading = ref(false);
const listActivityError = ref("");
const listActivityPage = ref(0);
const listActivityTotal = ref(0);
const listActivityGroups = computed(() => {
  const groups = new Map();
  for (const item of listActivityItems.value) {
    const date = new Date(item.createdAt);
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    if (!groups.has(key)) groups.set(key, { key, label: listActivityGroupLabel(date), items: [] });
    groups.get(key).items.push(item);
  }
  return [...groups.values()];
});
const editorOpen = ref(false);
const editing = ref(null);
const inlineTitleEditing = ref(false);
const inlineTitleDraft = ref("");
const inlineTitleSaving = ref(false);
const inlineRowTitleTaskId = ref("");
const inlineRowTitleDraft = ref("");
const rowDateTaskId = ref("");
const rowDatePanel = reactive({ mode: "DATE", selectedDate: "", time: "", allDay: false, rangeStart: "", rangeEnd: "", rangeStartTime: "09:00", rangeEndTime: "10:00" });
const rowDateAnchor = ref(null);
const rowDateStyle = ref({});
const rowDatePanelRef = ref(null);
const rowTimeListOpen = ref(false);
let rowDateObserver = null;
const inlineDescriptionDraft = ref("");
const inlineDescriptionError = ref("");
const detailRichToolbarOpen = ref(false);
const detailChecklistEditingIndex = ref(-1);
const detailChecklistDraft = ref("");
let inlineDescriptionTimer = null;
let inlineDescriptionSavePromise = null;
let inlineDescriptionQueued = null;
let inlineDescriptionVersion = 0;
let inlineDescriptionSyncHold = null;
const dragged = ref(null);
const dragOverTask = ref(null);
const dragOverPlacement = ref("before");
const dragOverGroupKey = ref("");
const groupBy = ref("time");
const sort = ref("time");
const sortDirection = ref("asc");
const hideCompleted = ref(false);
const showDescription = ref(true);
const showChecklist = ref(true);
const workspaceViews = [
  { value: "LIST", label: "列表", icon: List },
  { value: "KANBAN", label: "看板", icon: Columns3 },
  { value: "TIMELINE", label: "时间线", icon: GanttChart },
];
const selectedWorkspaceView = ref(route.query.workspaceView === "KANBAN" ? "KANBAN" : route.query.workspaceView === "TIMELINE" ? "TIMELINE" : "LIST");
const activeWorkspaceView = computed(() => selectedWorkspaceView.value);
const boardDetailAnchor = ref(null);
const detailPaneStyle = computed(() => {
  if (activeWorkspaceView.value === "LIST" || !boardDetailAnchor.value || window.matchMedia("(max-width: 900px)").matches) return undefined;
  const rect = boardDetailAnchor.value;
  const paneWidth = Math.min(480, Math.max(320, window.innerWidth - 48));
  const paneMaxHeight = Math.min(window.innerHeight - 32, 720);
  const gap = 14;
  const fitsRight = rect.right + gap + paneWidth <= window.innerWidth - 16;
  const fitsLeft = rect.left - gap - paneWidth >= 16;
  const left = fitsRight
    ? rect.right + gap
    : fitsLeft
      ? rect.left - paneWidth - gap
      : rect.clickX + gap + paneWidth <= window.innerWidth - 16
        ? rect.clickX + gap
        : rect.clickX - paneWidth - gap >= 16
          ? rect.clickX - paneWidth - gap
          : Math.min(Math.max(16, rect.centerX - paneWidth / 2), window.innerWidth - paneWidth - 16);
  const requestedTop = rect.clickY > 0 ? rect.clickY - 24 : rect.top;
  const top = Math.min(Math.max(16, requestedTop), Math.max(16, window.innerHeight - Math.min(320, paneMaxHeight) - 16));
  const availableHeight = window.innerHeight - top - 16;
  return { position: "fixed", top: `${top}px`, left: `${left}px`, right: "auto", width: `${paneWidth}px`, maxHeight: `${Math.min(paneMaxHeight, availableHeight)}px`, transform: "none" };
});
const noteLists = computed(() => store.lists.filter(item => !item.archived && item.objectType === "NOTE"));
const detailParentCandidates = computed(() => {
  const task = selectedTask.value;
  if (!task?.publicId) return [];
  const blocked = new Set([task.publicId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const item of store.tasks) {
      if (item.parentId && blocked.has(item.parentId) && !blocked.has(item.publicId)) {
        blocked.add(item.publicId);
        changed = true;
      }
    }
  }
  const query = detailParentQuery.value.trim().toLocaleLowerCase();
  return store.tasks.filter(item => item.publicId && !blocked.has(item.publicId) && item.status !== "DELETED"
    && (!query || `${item.title} ${listName(item.listId)}`.toLocaleLowerCase().includes(query)));
});
const parentPickerCandidates = computed(() => {
  const query = detailParentQuery.value.trim().toLocaleLowerCase();
  const candidates = selectedIds.size ? batchParentCandidates.value : detailParentCandidates.value;
  return candidates.filter(item => !query || `${item.title} ${listName(item.listId)}`.toLocaleLowerCase().includes(query));
});
const quickAddIsNote = computed(() => noteLists.value.some(item => item.publicId === quickEntry.listId)
  || (route.name === "tasks-list" && noteLists.value.some(item => item.publicId === route.params.publicId)));
const quickTaskTemplates = [
  { id: "travel-packing", title: "旅行必备物品", description: "", checklist: ["身份证 / 护照 / 学生证", "充电器 / 数据线", "晴雨伞", "易于携带的小背包", "衣物：上衣 / 下装", "衣物：换洗内衣裤", "鞋袜"] },
  { id: "work-start", title: "每天工作前要做的几件事", description: "", checklist: ["花几分钟了解昨天的情况", "充点时间处理邮件和未读消息", "查看智能清单“今天”中的任务", "确定今天最重要的 1~3 件事，排好优先级", "确定今天最难的事情，把它拆分成检查事项"] },
  { id: "daily-journal", title: "每日记录", description: "今天完成了什么？\n\n今天发生了哪些美好或值得关注的事？\n\n今天遇到了哪些突发问题？\n\n今天心情如何？", checklist: [] },
];
const filteredQuickTaskTemplates = computed(() => {
  const query = taskTemplateQuery.value.trim().toLocaleLowerCase();
  const templates = [...quickTaskTemplates, ...savedTaskTemplates.value];
  if (!query) return templates;
  return templates.filter(template => `${template.title} ${template.description} ${template.checklist.join(" ")}`.toLocaleLowerCase().includes(query));
});
const quickSuggestionLabel = computed(() => ({ priority: "优先级候选", tag: "标签候选", list: "清单候选" })[quickSuggestion.value?.kind] || "候选");
const quickSuggestionOptions = computed(() => {
  const current = quickSuggestion.value;
  if (!current) return [];
  const query = current.query.toLocaleLowerCase();
  const matches = item => String(item.label).toLocaleLowerCase().includes(query);
  if (current.kind === "priority") return priorityOptions.map(item => ({ id: item.value, label: item.label, value: item.value })).filter(matches);
  if (current.kind === "tag") return store.tags.map(item => ({ id: item.publicId, label: item.name, value: item.publicId, icon: item.icon })).filter(matches);
  return store.lists.filter(item => !item.archived).map(item => ({ id: item.publicId, label: item.name, value: item.publicId, icon: item.icon, meta: item.objectType === "NOTE" ? "笔记" : "清单" })).filter(matches);
});
const groupOptions = [
  { value: "list", label: "清单" },
  { value: "time", label: "时间" },
  { value: "created", label: "创建时间" },
  { value: "tag", label: "标签" },
  { value: "priority", label: "优先级" },
  { value: "none", label: "无" },
];
const sortOptions = [
  { value: "time", label: "时间" },
  { value: "created", label: "创建时间" },
  { value: "updated", label: "修改时间" },
  { value: "title", label: "标题" },
  { value: "tag", label: "标签" },
  { value: "priority", label: "优先级" },
  { value: "manual", label: "手动" },
];
const groupOptionLabel = computed(() => groupOptions.find(option => option.value === groupBy.value)?.label || "时间");
const sortOptionLabel = computed(() => sortOptions.find(option => option.value === sort.value)?.label || "时间");
const sortDirectionOptions = computed(() => sort.value === "title" || sort.value === "tag"
  ? [{ value: "asc", label: "升序" }, { value: "desc", label: "降序" }]
  : sort.value === "priority"
    ? [{ value: "asc", label: "从高到低" }, { value: "desc", label: "从低到高" }]
    : [{ value: "asc", label: "从旧到新" }, { value: "desc", label: "从新到旧" }]);
const workspaceMenuOptions = computed(() => workspaceMenuSection.value === "group" ? groupOptions : workspaceMenuSection.value === "sort" ? sortOptions : sortDirectionOptions.value);
const workspaceMenuValue = computed(() => workspaceMenuSection.value === "group" ? groupBy.value : workspaceMenuSection.value === "sort" ? sort.value : sortDirection.value);
const workspaceMenuHeading = computed(() => ({ group: "分组", sort: "排序", direction: "顺序" })[workspaceMenuSection.value] || "返回");
const sortDirectionLabel = computed(() => {
  if (sort.value === "title" || sort.value === "tag") return sortDirection.value === "asc" ? "升序" : "降序";
  if (sort.value === "priority") return sortDirection.value === "asc" ? "从高到低" : "从低到高";
  return sortDirection.value === "asc" ? "从旧到新" : "从新到旧";
});
const viewPreferencesScope = ref("");
// 分组/排序/顺序/视图选项按「当前集合」独立保存：清单、标签、过滤器、智能集合各存一份
function currentViewScope() {
  const name = route.name;
  if (name === "tasks-list") return `list:${route.params.publicId}`;
  if (name === "tasks-tag") return `tag:${route.params.publicId}`;
  if (name === "tasks-filter") return `filter:${route.params.publicId}`;
  if (name === "tasks-group") return `group:${route.params.publicId}`;
  if (name === "tasks-shortcut") return `shortcut:${route.params.shortcut}`;
  return String(name || "tasks");
}
function viewPreferencesKey() {
  return `task_view_preferences_v2:${store.accountScope || "anonymous"}`;
}
function legacyViewPreferencesKey() {
  return `task_view_preferences:${store.accountScope || "anonymous"}`;
}
function readViewPreferenceMap() {
  try {
    const raw = JSON.parse(localStorage.getItem(viewPreferencesKey()) || "{}");
    return raw && typeof raw === "object" ? raw : {};
  } catch {
    return {};
  }
}
function readLegacyViewPreferences() {
  try {
    const raw = JSON.parse(localStorage.getItem(legacyViewPreferencesKey()) || "null");
    return raw && typeof raw === "object" ? raw : null;
  } catch {
    return null;
  }
}
let applyingViewPreferences = false;
function currentViewPreferences() {
  return {
    groupBy: groupBy.value,
    sort: sort.value,
    sortDirection: sortDirection.value,
    hideCompleted: hideCompleted.value,
    showDescription: showDescription.value,
    showChecklist: showChecklist.value,
    showTags: showTags.value,
    showListLink: showListLink.value,
  };
}
function persistViewPreferences() {
  if (applyingViewPreferences) return;
  if (!viewPreferencesScope.value || viewPreferencesScope.value !== store.accountScope) return;
  try {
    const map = readViewPreferenceMap();
    map[currentViewScope()] = currentViewPreferences();
    localStorage.setItem(viewPreferencesKey(), JSON.stringify(map));
  } catch {}
}
function loadViewPreferences() {
  const scope = store.accountScope || "anonymous";
  viewPreferencesScope.value = scope;
  const map = readViewPreferenceMap();
  const scoped = map[currentViewScope()];
  // 没单独设置过的集合先沿用旧版全局偏好，避免升级后所有人被重置成默认值
  const saved = scoped || readLegacyViewPreferences() || {};
  applyingViewPreferences = true;
  try {
    if (groupOptions.some((item) => item.value === saved.groupBy)) groupBy.value = saved.groupBy;
    if (sortOptions.some((item) => item.value === saved.sort)) sort.value = saved.sort;
    if (["asc", "desc"].includes(saved.sortDirection)) sortDirection.value = saved.sortDirection;
    if (typeof saved.hideCompleted === "boolean") hideCompleted.value = saved.hideCompleted;
    if (typeof saved.showDescription === "boolean") showDescription.value = saved.showDescription;
    if (typeof saved.showChecklist === "boolean") showChecklist.value = saved.showChecklist;
    if (typeof saved.showTags === "boolean") showTags.value = saved.showTags;
    if (typeof saved.showListLink === "boolean") showListLink.value = saved.showListLink;
    quickInputMode.value = localStorage.getItem(`task_quick_input_mode:${scope}`) === "SIMPLE" ? "SIMPLE" : "DETAILED";
  } catch {
    /* 忽略损坏的偏好 */
  } finally {
    applyingViewPreferences = false;
  }
}
watch([groupBy, sort, sortDirection, hideCompleted, showDescription, showChecklist, showTags, showListLink], persistViewPreferences);
watch(quickInputMode, value => {
  if (!viewPreferencesScope.value || viewPreferencesScope.value !== store.accountScope) return;
  try { localStorage.setItem(`task_quick_input_mode:${store.accountScope || "anonymous"}`, value); } catch {}
});
watch(() => store.accountScope, (scope) => {
  if (scope !== viewPreferencesScope.value) loadViewPreferences();
});
// 切换清单/标签/过滤器时重建各自的设置
watch(() => currentViewScope(), () => {
  if (viewPreferencesScope.value === store.accountScope) loadViewPreferences();
});
const context = reactive({ open: false, x: 0, y: 0, task: null });
const contextSubmenu = ref("");
const activities = ref([]);
const activityLoading = ref(false);
const comments = ref([]);
const attachments = ref([]);
const detailSecondaryOpen = ref(false);
const detailFieldsOpen = ref(false);
const detailFieldsSaving = ref(false);
const detailFieldsDraft = reactive({ listId: "", parentId: "", startAt: "", durationMinutes: 0, allDay: false });
const detailParentOptions = computed(() => store.tasks.filter(task => !task.deleted && task.status !== "COMPLETED" && task.publicId !== selectedTask.value?.publicId && task.listId === selectedTask.value?.listId));
const detailSubtasks = computed(() => selectedTask.value ? store.tasks.filter(task => !task.deleted && task.parentId === selectedTask.value.publicId).sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)) : []);
const detailSubtaskDraftOpen = ref(false);
const detailSubtaskDraft = ref("");
const detailSubtaskSaving = ref(false);
const detailSubtaskInput = ref(null);
const inlineSubtaskTitleId = ref("");
const inlineSubtaskTitleDraft = ref("");
const detailMoreOpen = ref(false);
const detailMoreSection = ref("");
const detailAttachmentInput = ref(null);
const savedTaskTemplates = ref([]);
const commentComposerOpen = ref(false);
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
const allSidebarSystemViews = computed(() => [...primaryViews, completedView, abandonedView, trashView, archivedView]);
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
const ungroupedLists = computed(() => previewListItems(""));
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
const recurrenceSpecialOptions = [
  { value: "FREQ=WEEKLY;BYDAY=SA,SU", label: "每周末" },
  { value: "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;X-WORKDAY=LEGAL", label: "法定工作日" },
  { value: "FREQ=YEARLY;X-HOLIDAY=LEGAL", label: "法定节假日" },
  { value: "FREQ=MONTHLY;X-LUNAR=DAY", label: "每月农历日" },
  { value: "FREQ=YEARLY;X-LUNAR=DAY", label: "每年农历日" },
];
const priorityOptions = [
  { value: "NONE", label: "无优先级" },
  { value: "LOW", label: "低优先级" },
  { value: "MEDIUM", label: "中优先级" },
  { value: "HIGH", label: "高优先级" },
];
const quickPriorityOptions = ["HIGH", "MEDIUM", "LOW", "NONE"].map(value => priorityOptions.find(option => option.value === value));
const datePickerWeekdays = ["一", "二", "三", "四", "五", "六", "日"];
const datePickerMonthLabel = computed(() => new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long" }).format(datePickerMonth.value));
const datePickerDays = computed(() => {
  const first = new Date(datePickerMonth.value.getFullYear(), datePickerMonth.value.getMonth(), 1);
  const mondayOffset = (first.getDay() + 6) % 7;
  const start = addDays(first, -mondayOffset);
  const currentValue = editorOpen.value ? draft.dueAt : datePopoverDue.value;
  const selectedKey = currentValue ? dateKey(new Date(currentValue)) : "";
  const todayKey = dateKey(new Date());
  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(start, index);
    return { date, key: dateKey(date), otherMonth: date.getMonth() !== first.getMonth(), selected: dateKey(date) === selectedKey, today: dateKey(date) === todayKey };
  });
});
const detailDateTimeValue = computed(() => {
  const value = editorOpen.value ? draft.dueAt : datePopoverDue.value;
  if (!value || (editorOpen.value ? draft.allDay : datePopoverAllDay.value)) return "";
  return toLocal(value).slice(11, 16);
});
const detailDateAllDay = computed(() => editorOpen.value ? Boolean(draft.allDay) : datePopoverAllDay.value);
const filterPriorityOptions = [
  { value: "HIGH", label: "高" },
  { value: "MEDIUM", label: "中" },
  { value: "LOW", label: "低" },
  { value: "NONE", label: "无" },
];
const title = computed(() =>
  route.name === "tasks-trash"
    ? "垃圾桶"
    : route.name === "tasks-today"
    ? calendarDate.value && dateKey(calendarDate.value) !== dateKey(new Date())
      ? new Intl.DateTimeFormat("zh-CN", { month: "long", day: "numeric" }).format(calendarDate.value)
      : "今天"
    : route.name === "tasks-tomorrow"
      ? "明天"
      : route.name === "tasks-next7"
        ? "最近 7 天"
        : route.name === "tasks-abandoned"
          ? "已放弃"
        : route.name === "tasks-completed"
          ? "已完成"
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
                      assigned: "指派给我",
                      summary: "摘要",
                      archived: "归档",
                    }[route.params.shortcut] || "任务"
                  : "所有",
);
async function clearTrashPermanently() {
  if (trashClearBusy.value || !trashedTaskCount.value || !store.online) return;
  trashClearBusy.value = true;
  try {
    const items = [...store.tasks.filter(task => task.deleted)];
    const failures = [];
    // 逐条请求但只在最后一次刷新：原先每条 purge 都会全量 hydrate（8700+ 任务），清空几百条会长时间卡住
    for (const item of items) {
      try {
        await store.purge(item, { deferHydrate: true });
      } catch (error) {
        failures.push({ title: item.title, detail: error?.response?.data?.detail || error?.message || "清除失败" });
      }
    }
    await store.hydrate();
    if (failures.length) {
      store.error = `已清除 ${items.length - failures.length} 项，${failures.length} 项未能清除：${failures[0].detail}`;
      return;
    }
    trashClearOpen.value = false;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "清空垃圾桶失败";
  } finally {
    trashClearBusy.value = false;
  }
}
const currentTaskList = computed(() => route.name === "tasks-list"
  ? store.lists.find(item => item.publicId === route.params.publicId) || null
  : null);
const isOrdinaryTaskListView = computed(() => Boolean(currentTaskList.value && currentTaskList.value.objectType !== "NOTE"));
const showChecklistMenuItem = computed(() => route.name === "tasks-filter");
const calendarDate = computed(() => {
  const match = String(route.query.date || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;
  const value = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return dateKey(value) === match[0] ? value : null;
});
const selectedDate = computed(() => calendarDate.value || startOfDay(new Date()));
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
  else if (route.name === "tasks-today") {
    const dayStart = calendarDate.value ? startOfDay(calendarDate.value) : today;
    values = values.filter((task) => inRange(task.dueAt, dayStart, addDays(dayStart, 1)));
  }
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
          : false);
  }
  if (route.name === "tasks-completed" || route.name === "tasks-abandoned") {
    values = filterCompletionTasks(values, {
      status: route.name === "tasks-completed" ? "COMPLETED" : "ABANDONED",
      dateRange: completionFilters.dateRange,
      customStart: completionFilters.customStart,
      customEnd: completionFilters.customEnd,
      listIds: completionFilters.listIds,
    });
  }
  return sortTaskItems(values, { sortBy: sort.value, direction: sortDirection.value });
});
const openTasks = computed(() =>
  visibleTasks.value.filter((task) => !["COMPLETED", "ABANDONED"].includes(task.status)),
);
const completedTasks = computed(() =>
  visibleTasks.value.filter((task) => task.status === "COMPLETED"),
);
const completedPreviewTasks = computed(() => completedTasks.value.slice(0, completedLimit.value));
const collapsedSubtaskIds = ref(new Set());
function toggleSubtaskBranch(publicId) {
  const next = new Set(collapsedSubtaskIds.value);
  if (next.has(publicId)) next.delete(publicId);
  else next.add(publicId);
  collapsedSubtaskIds.value = next;
}
const groups = computed(() => {
  if (groupBy.value !== "time" && groupBy.value !== "none") return groupTaskItems(openTasks.value, groupBy.value, store.lists, new Date(), store.tags, { includeEmptyLists: route.name === "tasks-all" });
  if (groupBy.value === "none") return [{ key: "all", label: "所有任务", tasks: openTasks.value }];
  if (route.name === "tasks-shortcut" && route.params.shortcut === "archived")
    return [{ key: "open", label: "归档任务", tasks: openTasks.value }];
  const result = groupTaskItems(openTasks.value, "time", store.lists);
  if (["tasks-today", "tasks-tomorrow", "tasks-next7"].includes(route.name)) {
    const currentDay = result.find((group) => group.key === "today");
    if (currentDay) currentDay.label = title.value;
  }
  return result;
});
const listGroups = computed(() => buildTaskListGroups(groups.value, openTasks.value, collapsedSubtaskIds.value));
const todayCountdowns = computed(() => route.name === "tasks-today" && !route.query.date ? store.countdowns.filter((item) => !item.archived && !item.deleted) : []);
const todayHabits = computed(() => route.name === "tasks-today" && !route.query.date ? store.habits.filter((item) => !item.archived && !item.deleted) : []);
const completedLimit = ref(5);
function showMoreCompleted() { completedLimit.value = Math.min(completedTasks.value.length, completedLimit.value + 5); }
function loadMoreCompletedOnScroll(event) {
  const eventTarget = event.currentTarget;
  const target = eventTarget === window || eventTarget === document
    ? document.scrollingElement || document.documentElement
    : eventTarget;
  if (completedPreviewTasks.value.length < completedTasks.value.length && target.scrollTop + target.clientHeight >= target.scrollHeight - 120) showMoreCompleted();
}
function groupColor(task) {
  return taskListColor(task);
}
function taskList(task) {
  return store.lists.find((item) => item.publicId === task.listId);
}
function isNoteTask(task) {
  if (!task) return false;
  // 条目级类型优先（转换后仍可留在任意类型清单），旧数据回退到清单类型
  if (task.itemKind === "NOTE") return true;
  if (task.itemKind === "TASK") return false;
  return taskList(task)?.objectType === "NOTE";
}
function taskListColor(task) {
  return taskList(task)?.color || "var(--line2)";
}
function taskTag(task, tagId) {
  return store.tags.find((item) => item.publicId === tagId);
}
function taskTagStyle(tagId) {
  const color = taskTag(null, tagId)?.color;
  return color ? { "--task-tag-color": color } : {};
}
// 紧凑场景（看板卡片/子任务行/已完成行）最多显示 2 个标签；主任务行渲染全量标签由容器查询收起
function visibleTaskTagIds(task) {
  return (task.tagIds || []).slice(0, 2);
}
async function removeTaskTag(task, tagId) {
  try {
    const updated = await store.update(task, { tagIds: (task.tagIds || []).filter(id => id !== tagId) });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "移除标签失败");
  }
}
function taskTagSummary(task) {
  return (task.tagIds || []).map(tagName).join("、");
}
// 标签 +N 悬浮浮层：teleport 到 body + fixed 定位（按触发元素锚定、视口钳制、滚动跟随），
// 替代纯 CSS absolute——顶部/左侧行在视口边缘时浮层会被裁掉或超出可视范围
const tagFlyout = ref({ taskId: "", tagIds: [], style: { visibility: "hidden" } });
const tagFlyoutAnchor = ref(null);
let tagFlyoutHideTimer = null;
function showTagFlyout(task, event) {
  cancelTagFlyoutHide();
  tagFlyoutAnchor.value = event.currentTarget;
  tagFlyout.value = { taskId: task.publicId, tagIds: task.tagIds || [], style: { visibility: "hidden" } };
  nextTick(positionTagFlyout);
}
function positionTagFlyout() {
  const anchor = tagFlyoutAnchor.value;
  if (!anchor || !tagFlyout.value.taskId) return;
  const el = document.querySelector(".clone-tag-flyout");
  if (!el) return;
  const rect = anchor.getBoundingClientRect();
  const GAP = 7;
  const top = rect.top - el.offsetHeight - GAP >= 8 ? rect.top - el.offsetHeight - GAP : rect.bottom + GAP;
  const clampedTop = Math.max(8, Math.min(top, window.innerHeight - el.offsetHeight - 8));
  const left = Math.max(8, Math.min(rect.right - el.offsetWidth, window.innerWidth - el.offsetWidth - 8));
  tagFlyout.value = { ...tagFlyout.value, style: { top: `${Math.round(clampedTop)}px`, left: `${Math.round(left)}px`, visibility: "visible" } };
}
function hideTagFlyout() {
  cancelTagFlyoutHide();
  tagFlyoutAnchor.value = null;
  tagFlyout.value = { taskId: "", tagIds: [], style: { visibility: "hidden" } };
}
function hideTagFlyoutSoon() {
  if (tagFlyoutHideTimer) clearTimeout(tagFlyoutHideTimer);
  tagFlyoutHideTimer = setTimeout(hideTagFlyout, 120);
}
function cancelTagFlyoutHide() {
  if (tagFlyoutHideTimer) {
    clearTimeout(tagFlyoutHideTimer);
    tagFlyoutHideTimer = null;
  }
}
function isTaskDueToday(task) {
  if (!task.dueAt) return false;
  return new Date(task.dueAt).toDateString() === new Date().toDateString();
}
function isTaskOverdue(task) {
  return Boolean(task.dueAt && new Date(task.dueAt) < new Date() && !isTaskDueToday(task) && task.status !== "COMPLETED");
}
function taskDateLabel(task) {
  if (isTaskDueToday(task)) return "今天";
  const date = new Date(task.dueAt);
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  if (date.toDateString() === tomorrow.toDateString()) return "明天";
  const sameYear = date.getFullYear() === new Date().getFullYear();
  const label = new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", ...(sameYear ? {} : { year: "numeric" }) }).format(date);
  return date.getHours() || date.getMinutes() ? `${label} ${new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit" }).format(date)}` : label;
}
function navigateToTaskList(task) {
  const list = taskList(task);
  router.push(list?.systemKey === "INBOX" ? "/tasks/inbox" : `/tasks/list/${task.listId}`);
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
function quickDateLabel(value, allDay = false) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "日期";
  const today = new Date();
  const tomorrow = addDays(new Date(today.getFullYear(), today.getMonth(), today.getDate()), 1);
  const key = localDateKey(date);
  const prefix = key === localDateKey(today)
    ? "今天"
    : key === localDateKey(tomorrow)
      ? "明天"
      : new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric" }).format(date);
  if (allDay || (!date.getHours() && !date.getMinutes())) return prefix;
  return `${prefix} ${new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit" }).format(date)}`;
}
function formatCreatedAt(value) {
  if (!value) return "未知";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
function listName(id) {
  const list = store.lists.find((item) => item.publicId === id);
  return list?.systemKey === "INBOX" ? "收集箱" : list?.name || "收集箱";
}

function configuredQuickTime() {
  const configured = quickAddSettings.value.defaultDueTime;
  if (!configured || configured === "NONE") return "";
  if (configured === "NEXT_HOUR") return nextWholeHour().slice(11, 16);
  return /^\d{2}:\d{2}$/.test(configured) ? configured : "";
}

function quickDefaultListId() {
  if (route.name === "tasks-list") return route.params.publicId || store.inbox?.publicId || "";
  if (quickAddSettings.value.defaultListId && store.lists.some(item => item.publicId === quickAddSettings.value.defaultListId && !item.archived)) return quickAddSettings.value.defaultListId;
  return store.inbox?.publicId || "";
}

function quickDefaultTaskFields(dateOverride = null) {
  const contextDate = dateOverride || quickDateContext.value || (quickAddSettings.value.defaultDueDate === "TODAY" ? new Date() : null);
  if (!contextDate) return { dueAt: "", allDay: false };
  return { dueAt: `${localDateKey(contextDate)}T00:00`, allDay: true };
}

function applyQuickAddDefaults() {
  const defaults = quickDefaultTaskFields();
  Object.assign(quickEntry, {
    dueAt: defaults.dueAt,
    startAt: "",
    allDay: defaults.allDay,
    dateSelected: Boolean(defaults.dueAt),
    priority: "NONE",
    prioritySelected: false,
    listId: quickDefaultListId(),
    listSelected: Boolean(quickDefaultListId()),
    tagIds: [],
  });
  quickSelectedDate.value = defaults.dueAt ? defaults.dueAt.slice(0, 10) : "";
  quickDateTime.value = "";
}

function openBoardQuickAdd(group) {
  quickAddExpanded.value = false;
  quickAction.value = "";
  quickFlyoutAnchor.value = null;
  boardQuickAddGroup.value = group;
  boardQuickAddTitle.value = "";
  applyQuickAddDefaults();
  Object.assign(quickAddReminder, { enabled: false, presets: [], amount: 1, unit: "DAY", time: "09:00", dailyUntilDone: false });
  Object.assign(quickRepeat, createQuickRepeatState());
  quickRepeatError.value = "";
  quickRangeStart.value = "";
  quickRangeEnd.value = "";
  if (groupBy.value === "list" && String(group.key).startsWith("list:")) {
    const listId = String(group.key).slice("list:".length);
    quickEntry.listId = listId === "no-list" ? store.inbox?.publicId || "" : listId;
    quickEntry.listSelected = true;
  } else if (groupBy.value === "priority") {
    quickEntry.priority = String(group.key).slice("priority:".length) || "NONE";
    quickEntry.prioritySelected = true;
  } else if (groupBy.value === "tag") {
    const tagId = String(group.key).slice("tag:".length);
    if (tagId && tagId !== "no-tag") quickEntry.tagIds = [tagId];
  } else if (groupBy.value === "time") {
    const dayOffset = { overdue: -1, today: 0, later: 1 }[group.key];
    if (dayOffset !== undefined) {
      const groupDate = addDays(new Date(), dayOffset);
      const groupDefaults = quickDefaultTaskFields(groupDate);
      quickEntry.dueAt = groupDefaults.dueAt;
      quickEntry.allDay = groupDefaults.allDay;
      quickEntry.dateSelected = Boolean(groupDefaults.dueAt);
      quickSelectedDate.value = groupDefaults.dueAt ? groupDefaults.dueAt.slice(0, 10) : "";
    }
  }
  boardQuickAddOpen.value = true;
  nextTick(() => {
    const input = Array.isArray(boardQuickAddInput.value) ? boardQuickAddInput.value[0] : boardQuickAddInput.value;
    input?.focus();
  });
}

function closeBoardQuickAdd() {
  boardQuickAddOpen.value = false;
  boardQuickAddError.value = "";
  boardQuickAddAttachments.value = [];
  quickFlyoutAnchor.value = null;
  cancelQuickDraft();
}

function openBoardAttachmentPicker() {
  boardQuickAttachmentInput.value?.click();
}

function selectBoardQuickAttachments(event) {
  const files = Array.from(event.target.files || []);
  event.target.value = "";
  const { accepted } = validateQuickAttachmentFiles(files);
  boardQuickAddAttachments.value.push(...accepted.map(file => ({ id: crypto.randomUUID(), file, name: file.name, size: file.size })));
}

function closeBoardQuickAddOnOutsidePointer(event) {
  if (boardQuickAddOpen.value && !event.target.closest?.(".clone-board-quick-inline, .clone-inline-board-column-head, .clone-quick-action-panel, .clone-quick-flyout")) closeBoardQuickAdd();
}

async function saveBoardQuickAdd() {
  const titleText = boardQuickAddTitle.value.trim();
  if (!titleText || boardQuickAddSaving.value) return;
  boardQuickAddSaving.value = true;
  boardQuickAddError.value = "";
  try {
    const dueAt = quickEntry.dueAt ? new Date(quickEntry.dueAt).toISOString() : "";
    const reminderConfig = { ...quickAddReminder, presets: [...quickAddReminder.presets] };
    const reminderPayloads = quickReminderPayloads({ dueAt, allDay: quickEntry.allDay, config: reminderConfig });
    if (quickAddReminder.enabled && !dueAt) throw new Error("提醒需要先设置任务日期");
    const saved = await store.create({
      title: titleText,
      listId: quickEntry.listId || store.inbox?.publicId || "",
      priority: quickEntry.prioritySelected ? quickEntry.priority : "NONE",
      dueAt,
      allDay: quickEntry.allDay,
      tagIds: [...quickEntry.tagIds],
      rrule: buildQuickRepeatRule(quickRepeat) || "",
    });
    if (saved?.publicId && boardQuickAddAttachments.value.length) {
      const result = await uploadQuickAttachments(saved.publicId, boardQuickAddAttachments.value, apiUploadTaskAttachment);
      if (result.error) message.warning("任务已添加，但部分附件上传失败");
    }
    for (const reminderPayload of reminderPayloads) await apiCreateTaskReminder(saved.publicId, reminderPayload);
    closeBoardQuickAdd();
    message.success("任务已添加");
  } catch (error) {
    boardQuickAddError.value = error?.response?.data?.detail || error?.message || "任务新增失败";
  } finally {
    boardQuickAddSaving.value = false;
  }
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
function sidebarWidthKey() { return `task_sidebar_width:${store.accountScope || "anonymous"}`; }
function loadSidebarWidth() {
  sidebarWidth.value = 248;
  try {
    const value = Number(localStorage.getItem(sidebarWidthKey()));
    if (Number.isFinite(value) && value >= 184 && value <= 420) sidebarWidth.value = value;
  } catch {}
}
function persistSidebarWidth() {
  try { localStorage.setItem(sidebarWidthKey(), String(sidebarWidth.value)); } catch {}
}
function startSidebarResize(event) {
  if (isMobileSidebar.value || event.button !== 0) return;
  sidebarResizeState = { startX: event.clientX, startWidth: sidebarWidth.value };
  sidebarPreviousUserSelect = document.body.style.userSelect;
  event.currentTarget.setPointerCapture?.(event.pointerId);
  document.body.style.userSelect = "none";
  window.addEventListener("pointermove", updateSidebarResize);
  window.addEventListener("pointerup", stopSidebarResize, { once: true });
  window.addEventListener("pointercancel", stopSidebarResize, { once: true });
  event.preventDefault();
}
function updateSidebarResize(event) {
  if (!sidebarResizeState) return;
  const maxWidth = Math.min(420, Math.max(184, window.innerWidth - 620));
  const width = Math.min(maxWidth, Math.max(160, sidebarResizeState.startWidth + event.clientX - sidebarResizeState.startX));
  if (width < 184) {
    sidebarWidth.value = 184;
    sidebarHidden.value = true;
    stopSidebarResize();
    return;
  }
  sidebarWidth.value = width;
}
function stopSidebarResize() {
  if (!sidebarResizeState) return;
  sidebarResizeState = null;
  document.body.style.userSelect = sidebarPreviousUserSelect;
  window.removeEventListener("pointermove", updateSidebarResize);
  window.removeEventListener("pointercancel", stopSidebarResize);
  persistSidebarWidth();
}
function resizeSidebarByKey(event) {
  if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
  event.preventDefault();
  const delta = event.key === "ArrowLeft" ? -16 : 16;
  const width = sidebarWidth.value + delta;
  if (width < 184) sidebarHidden.value = true;
  else sidebarWidth.value = Math.min(420, width);
  persistSidebarWidth();
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
  if (datePopoverOpen.value) {
    datePopoverDue.value = toLocal(editorOpen.value ? draft.dueAt : selectedTask.value?.dueAt);
    datePopoverStart.value = toLocal(editorOpen.value ? draft.startAt : selectedTask.value?.startAt);
    datePopoverAllDay.value = Boolean(editorOpen.value ? draft.allDay : selectedTask.value?.allDay);
    detailTimeRowOpen.value = false;
    datePickerMode.value = "DATE";
    const currentValue = editorOpen.value ? draft.dueAt : selectedTask.value?.dueAt;
    const currentDate = currentValue ? new Date(currentValue) : new Date();
    datePickerMonth.value = new Date(currentDate.getFullYear(), currentDate.getMonth(), 1);
  }
}
const detailTimeRowOpen = ref(false);
/** 浮层的日期输入在编辑态写回草稿，浏览态写回临时值，两边共用同一套控件 */
const detailStartModel = computed({
  get: () => (editorOpen.value ? draft.startAt : datePopoverStart.value),
  set: value => { if (editorOpen.value) draft.startAt = value; else datePopoverStart.value = value; }
});
const detailDueModel = computed({
  get: () => (editorOpen.value ? draft.dueAt : datePopoverDue.value),
  set: value => { if (editorOpen.value) draft.dueAt = value; else datePopoverDue.value = value; }
});
const detailDateSelectedKey = computed(() => {
  const value = detailDueModel.value;
  return value ? dateKey(new Date(value)) : "";
});
/** 复用 TaskDateTimePopover 时按 378px 宽度自行定位，不再套用旧的 560px 固定浮层 */
const detailDatePopoverStyle = computed(() => {
  void popoverViewportRevision.value;
  const anchor = datePopoverAnchor.value;
  if (!anchor || typeof window === "undefined") return { position: "fixed", zIndex: 1000, top: "80px", left: "24px" };
  const rect = anchor.getBoundingClientRect();
  const width = Math.min(378, window.innerWidth - 24);
  const height = Math.min(660, window.innerHeight - 24);
  const left = Math.max(12, Math.min(rect.left, window.innerWidth - width - 12));
  let top = rect.bottom + 8;
  if (top + height > window.innerHeight - 12) top = Math.max(12, rect.top - height - 8);
  if (top + height > window.innerHeight - 12) top = Math.max(12, window.innerHeight - height - 12);
  return { position: "fixed", zIndex: 1000, top: `${top}px`, left: `${left}px`, width: `${width}px` };
});
function selectDateKey(key) {
  const [year, month, day] = String(key || "").split("-").map(Number);
  if (!year) return;
  selectDatePickerDay(new Date(year, month - 1, day));
}
function applyDetailQuickDate(kind) {
  if (kind === "NEXT_MONTH") { chooseDetailDatePreset("NEXT_MONTH"); return; }
  setDatePopoverOffset(kind === "TOMORROW" ? 1 : kind === "NEXT_WEEK" ? 7 : 0);
}
function applyDetailClearDate() {
  setDatePopoverOffset(null);
  setDetailAllDay(false);
}
function refreshAttributePopoverLayout() {
  popoverViewportRevision.value += 1;
  positionTagFlyout();
}
function attributePopoverStyle(kind) {
  void popoverViewportRevision.value;
  const anchor = ({ date: datePopoverAnchor, priority: priorityPopoverAnchor })[kind]?.value;
  if (!anchor || typeof window === "undefined") return {};
  const rect = anchor.getBoundingClientRect();
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  const pane = anchor.closest(".clone-detail-pane");
  const paneRect = pane?.getBoundingClientRect();
  const preferredWidth = kind === "date" ? 560 : kind === "repeat" ? 560 : 190;
  const paneWidth = paneRect?.width > 0 ? paneRect.width - 16 : viewportWidth - 24;
  const width = Math.max(1, Math.min(preferredWidth, paneWidth, viewportWidth - 24));
  const height = kind === "date" ? 620 : kind === "repeat" ? 650 : 270;
  const left = paneRect?.width > 0
    ? (kind === "priority" ? paneRect.right - width - 8 : paneRect.left + 8)
    : (kind === "priority" ? rect.right - width : rect.left);
  const clampedLeft = Math.max(12, Math.min(left, viewportWidth - width - 12));
  const usableHeight = Math.min(height, viewportHeight - 24);
  const below = rect.bottom + 8;
  const top = below + usableHeight > viewportHeight - 12 ? Math.max(12, rect.top - usableHeight - 8) : below;
  return { position: "fixed", zIndex: 1000, top: `${top}px`, left: `${clampedLeft}px`, width: `${width}px`, maxHeight: `calc(100dvh - 24px)` };
}
function selectDatePickerDay(date) {
  const currentValue = editorOpen.value ? draft.dueAt : datePopoverDue.value;
  const current = currentValue ? new Date(currentValue) : new Date();
  date.setHours(current.getHours(), current.getMinutes(), 0, 0);
  if (editorOpen.value) draft.dueAt = toLocal(date);
  else datePopoverDue.value = toLocal(date);
}
function setDatePopoverOffset(offset) {
  if (offset === null) {
    if (editorOpen.value) { draft.startAt = ""; draft.dueAt = ""; }
    else { datePopoverStart.value = ""; datePopoverDue.value = ""; }
    return;
  }
  const baseValue = editorOpen.value ? draft.dueAt : datePopoverDue.value;
  const baseDate = baseValue ? new Date(baseValue) : new Date();
  const date = startOfDay(addDays(new Date(), offset));
  date.setHours(baseDate.getHours(), baseDate.getMinutes(), 0, 0);
  if (editorOpen.value) draft.dueAt = toLocal(date);
  else datePopoverDue.value = toLocal(date);
  datePickerMonth.value = new Date(date.getFullYear(), date.getMonth(), 1);
}
function chooseDetailDatePreset(preset) {
  if (preset === "NEXT_MONTH") {
    const current = editorOpen.value ? draft.dueAt : datePopoverDue.value;
    const base = current ? new Date(current) : new Date();
    const date = new Date(base.getFullYear(), base.getMonth() + 1, 1);
    selectDatePickerDay(date);
    datePickerMonth.value = new Date(date.getFullYear(), date.getMonth(), 1);
    return;
  }
  const offsets = { TODAY: 0, TOMORROW: 1, NEXT_WEEK: 7 };
  setDatePopoverOffset(Object.prototype.hasOwnProperty.call(offsets, preset) ? offsets[preset] : 0);
}
function moveDetailMonth(offset) {
  datePickerMonth.value = new Date(datePickerMonth.value.getFullYear(), datePickerMonth.value.getMonth() + offset, 1);
}
function selectDetailDateKey(key) {
  const date = new Date(`${key}T00:00:00`);
  selectDatePickerDay(date);
}
function selectDetailTime(time) {
  const current = editorOpen.value ? draft.dueAt : datePopoverDue.value;
  const date = current ? new Date(current) : new Date();
  const [hours, minutes] = time.split(":").map(Number);
  date.setHours(hours, minutes, 0, 0);
  if (editorOpen.value) {
    draft.dueAt = toLocal(date);
    draft.allDay = false;
  } else {
    datePopoverDue.value = toLocal(date);
    datePopoverAllDay.value = false;
  }
}
function clearDetailTime() {
  const current = editorOpen.value ? draft.dueAt : datePopoverDue.value;
  if (current) {
    const date = new Date(current);
    date.setHours(0, 0, 0, 0);
    if (editorOpen.value) draft.dueAt = toLocal(date);
    else datePopoverDue.value = toLocal(date);
  }
  setDetailAllDay(true);
}
function clearDetailDate() {
  if (editorOpen.value) {
    draft.startAt = "";
    draft.dueAt = "";
    draft.allDay = true;
  } else {
    datePopoverStart.value = "";
    datePopoverDue.value = "";
    datePopoverAllDay.value = true;
  }
}
function setDetailAllDay(value) {
  if (editorOpen.value) draft.allDay = Boolean(value);
  else datePopoverAllDay.value = Boolean(value);
  if (value) clearDetailTime();
}
async function confirmTaskDueDate() {
  const start = editorOpen.value ? draft.startAt : datePopoverStart.value;
  const due = editorOpen.value ? draft.dueAt : datePopoverDue.value;
  if (datePickerMode.value === "RANGE" && start && due && new Date(due) <= new Date(start)) {
    store.error = "时间段结尾必须晚于开始";
    return;
  }
  if (editorOpen.value) {
    datePopoverOpen.value = false;
    store.error = "";
    return;
  }
  await saveTaskDueDate();
}
function toggleRepeatPopover() {
  repeatPopoverOpen.value = !repeatPopoverOpen.value;
  reminderPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  if (repeatPopoverOpen.value) syncDetailRepeatFromRule(editorOpen.value ? (draft.rrule === "CUSTOM" ? draft.customRrule : draft.rrule) : selectedTask.value?.rrule, editorOpen.value ? draft.recurrenceAnchor : selectedTask.value?.recurrenceAnchor);
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
    selectedTask.value = await store.update(selectedTask.value, {
      dueAt: datePopoverDue.value ? new Date(datePopoverDue.value).toISOString() : "",
      startAt: datePickerMode.value === "RANGE" && datePopoverStart.value ? new Date(datePopoverStart.value).toISOString() : "",
    });
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
function syncQuickRepeatFromRule(target, rule = "", anchor = "DUE_DATE") {
  Object.assign(target, createQuickRepeatState());
  const value = String(rule || "");
  const parts = Object.fromEntries(value.split(";").map(item => item.split("=")).filter(item => item.length === 2));
  if (!value) return;
  target.mode = anchor === "COMPLETION_DATE" ? "COMPLETION_DATE" : "DUE_DATE";
  if (value === "FREQ=DAILY") target.preset = "DAILY";
  else if (value === "FREQ=WEEKLY") target.preset = "WEEKLY";
  else if (value === "FREQ=MONTHLY") target.preset = "MONTHLY";
  else if (value === "FREQ=YEARLY") target.preset = "YEARLY";
  else if (value === "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR") target.preset = "WORKDAY";
  else if (value === "FREQ=WEEKLY;BYDAY=SA,SU") target.preset = "WEEKEND";
  else if (parts["X-TASK-KIND"] === "LEGAL_WORKDAY") target.preset = "LEGAL_WORKDAY";
  else if (parts["X-TASK-KIND"] === "LEGAL_HOLIDAY") target.preset = "LEGAL_HOLIDAY";
  else if (parts["X-TASK-KIND"] === "LUNAR_MONTHLY") { target.preset = "LUNAR_MONTHLY"; target.lunarDay = Number(parts["X-LUNAR-DAY"]) || 1; }
  else if (parts["X-TASK-KIND"] === "LUNAR_YEARLY") { target.preset = "LUNAR_YEARLY"; target.lunarMonth = Number(parts["X-LUNAR-MONTH"]) || 1; target.lunarDay = Number(parts["X-LUNAR-DAY"]) || 1; }
  else {
    target.preset = "CUSTOM";
    target.frequency = parts.FREQ || "WEEKLY";
    target.interval = Number(parts.INTERVAL) || 1;
    const byDays = parts.BYDAY ? parts.BYDAY.split(",") : [];
    const ordinalDay = byDays.find(day => /^-?\d+(MO|TU|WE|TH|FR|SA|SU)$/.test(day));
    target.weekdays = byDays.map(day => day.replace(/^-?\d+/, "")).filter(day => DAY_CODES.includes(day));
    if (ordinalDay) {
      target.monthMode = "WEEKDAY";
      target.ordinal = Number(ordinalDay.match(/^-?\d+/)?.[0]) || 1;
      target.weekday = ordinalDay.replace(/^-?\d+/, "");
    } else if (parts.BYSETPOS) {
      target.monthMode = "WORKDAY";
      target.workdayPosition = Number(parts.BYSETPOS) < 0 ? -1 : 1;
    }
    target.month = Number(parts.BYMONTH) || 1;
    target.lastDay = Number(parts.BYMONTHDAY) === -1;
    const byMonthDays = String(parts.BYMONTHDAY || "").split(",").map(Number).filter(value => Number.isFinite(value) && value >= 1 && value <= 31);
    target.monthDays = byMonthDays.length ? byMonthDays : [1];
    if (target.frequency === "DAILY" && byDays.join(",") === "MO,TU,WE,TH,FR") target.skipWeekends = true;
    target.endMode = parts.COUNT ? "COUNT" : parts.UNTIL ? "UNTIL" : "NEVER";
    target.count = Number(parts.COUNT) || 10;
    target.until = parts.UNTIL ? `${parts.UNTIL.slice(0, 4)}-${parts.UNTIL.slice(4, 6)}-${parts.UNTIL.slice(6, 8)}` : "";
    target.skipLegalHolidays = parts["X-SKIP-HOLIDAYS"] === "TRUE";
    target.skipWeekends = parts["X-SKIP-WEEKENDS"] === "TRUE";
  }
  if (parts.COUNT) { target.endMode = "COUNT"; target.count = Number(parts.COUNT) || 1; }
  if (parts.UNTIL) { target.endMode = "UNTIL"; target.until = `${parts.UNTIL.slice(0, 4)}-${parts.UNTIL.slice(4, 6)}-${parts.UNTIL.slice(6, 8)}`; }
}
function syncDetailRepeatFromRule(rule = "", anchor = "DUE_DATE") {
  syncQuickRepeatFromRule(detailRepeat, rule, anchor);
  detailRepeatError.value = "";
}
function selectDetailRepeatPreset(value) {
  detailRepeat.preset = value;
  detailRepeatError.value = "";
  if (value === "CUSTOM" && detailRepeat.frequency === "YEARLY" && detailRepeat.monthMode === "WORKDAY") detailRepeat.monthMode = "DATE";
}
function setDetailRepeatFrequency(event) {
  detailRepeat.frequency = event.target.value;
  if (detailRepeat.frequency === "YEARLY" && detailRepeat.monthMode === "WORKDAY") detailRepeat.monthMode = "DATE";
}
async function saveDetailRepeat() {
  detailRepeatError.value = quickRepeatValidation(detailRepeat);
  if (detailRepeatError.value) return;
  const rule = buildQuickRepeatRule(detailRepeat);
  if (editorOpen.value) {
    draft.rrule = rule ? "CUSTOM" : "";
    draft.customRrule = rule;
    draft.recurrenceAnchor = detailRepeat.mode === "COMPLETION_DATE" ? "COMPLETION_DATE" : "DUE_DATE";
    repeatPopoverOpen.value = false;
    return;
  }
  if (!selectedTask.value) return;
  try {
    selectedTask.value = await store.update(selectedTask.value, { rrule: rule, recurrenceAnchor: rule ? (detailRepeat.mode === "COMPLETION_DATE" ? "COMPLETION_DATE" : "DUE_DATE") : "" });
    repeatPopoverOpen.value = false;
  } catch (error) { detailRepeatError.value = error?.response?.data?.detail || error?.message || "重复规则保存失败"; }
}
function resetDetailRepeat() {
  Object.assign(detailRepeat, createQuickRepeatState());
  detailRepeatError.value = "";
}
async function toggleReminderPopover() {
  reminderPopoverOpen.value = !reminderPopoverOpen.value;
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
  detailWidth.value = defaultDetailWidth;
  try {
    const value = Number(localStorage.getItem(detailWidthKey()));
    if (Number.isFinite(value) && value >= 320 && value <= 720 && value !== 390) detailWidth.value = value;
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
  detailWidth.value = Math.min(720, Math.max(320, detailResizeState.startWidth - (event.clientX - detailResizeState.startX)));
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
  detailWidth.value = defaultDetailWidth;
  persistDetailWidth();
}
function resizeDetailByKey(event) {
  if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
  event.preventDefault();
  const step = event.shiftKey ? 48 : 16;
  detailWidth.value = Math.min(720, Math.max(320, detailWidth.value + (event.key === 'ArrowLeft' ? step : -step)));
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
watch(() => store.accountScope, () => loadSidebarWidth());
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
function closeWorkspaceMenus() {
  moreOpen.value = false;
  listOptionsOpen.value = false;
  printOptionsOpen.value = false;
  workspaceMenuSection.value = "";
}
function toggleWorkspaceMenu(menu) {
  const wasOpen = menu === "group" ? moreOpen.value : listOptionsOpen.value;
  closeWorkspaceMenus();
  if (wasOpen) return;
  if (menu === "group") moreOpen.value = true;
  else listOptionsOpen.value = true;
  positionWorkspaceMenu(menu);
}
// 菜单锚定触发按钮（窗口右下对齐按钮、向下展开）：固定贴窗口右上会在窄窗口 + 详情面板时飘出中栏
function positionWorkspaceMenu(menu) {
  const trigger = document.querySelector(`[data-workspace-menu-trigger="${menu}"]`);
  if (!trigger) return;
  const rect = trigger.getBoundingClientRect();
  const MENU_WIDTH = 212;
  const left = Math.max(8, Math.min(rect.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - 8));
  workspaceMenuStyle.value = { top: `${Math.round(rect.bottom + 8)}px`, left: `${Math.round(left)}px` };
}
function selectWorkspaceMenuOption(value) {
  if (workspaceMenuSection.value === "group") groupBy.value = value;
  else if (workspaceMenuSection.value === "sort") sort.value = value;
  else sortDirection.value = value;
  closeWorkspaceMenus();
}
function closeWorkspaceMenusOnOutsidePointer(event) {
  if (!moreOpen.value && !listOptionsOpen.value) return;
  if (event.target.closest?.("[data-workspace-menu-trigger], .clone-floating-menu")) return;
  closeWorkspaceMenus();
}
function closeCompletionFilterOnOutsidePointer(event) {
  if (!completionFilterMenu.value) return;
  if (event.target.closest?.(".clone-completion-filters")) return;
  completionFilterMenu.value = "";
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
async function toggleDescriptionChecklist(task) {
  if (!task) return;
  if (selectedTask.value?.publicId === task.publicId && inlineDescriptionDraft.value !== (task.description || "")) {
    await saveInlineDescription();
    task = store.tasks.find(item => item.publicId === task.publicId) || task;
  }
  const checklist = task.checklist?.length
    ? task.checklist.map(item => `${item.completed ? '- [x] ' : '- [ ] '}${item.title}`).join('\n')
    : String(task.description || '').split(/\r?\n/).map(line => line.replace(/^\s*[-*+]\s*(?:\[[ xX]\]\s*)?/, '').trim()).filter(Boolean).map((title, sortOrder) => ({ title, completed: false, sortOrder }));
  if (task.checklist?.length) {
    selectedTask.value = await store.update(task, { description: checklist, checklist: [] });
    inlineDescriptionDraft.value = selectedTask.value.description || "";
  } else if (checklist.length) {
    selectedTask.value = await store.update(task, { description: '', checklist });
    inlineDescriptionDraft.value = selectedTask.value.description || "";
  }
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
function closeDetailMoreOnOutsidePointer(event) {
  if (detailMoreOpen.value && !event.target.closest?.(".clone-detail-footer")) {
    detailMoreOpen.value = false;
    detailMoreSection.value = "";
  }
}
function closeDetailMore() {
  detailMoreOpen.value = false;
  detailMoreSection.value = "";
}
async function associateTaskParent() {
  closeDetailMore();
  if (!selectedTask.value && !selectedIds.size) return;
  detailParentQuery.value = "";
  detailParentPickerOpen.value = true;
}
function openBatchParentPicker() {
  if (!selectedIds.size || !batchParentCandidates.value.length) return;
  detailParentQuery.value = "";
  detailParentPickerOpen.value = true;
}
async function setDetailParent(parent) {
  if (selectedIds.size) {
    const targets = [...selectedTasks.value];
    if (!parent || !batchParentCandidates.value.some(item => item.publicId === parent.publicId)) return;
    try {
      for (const task of targets) await store.update(task, { parentId: parent.publicId });
      detailParentPickerOpen.value = false;
      message.success(`已将 ${targets.length} 项关联到主任务“${parent.title}”`);
    } catch (error) {
      message.error(error?.response?.data?.detail || error?.message || "批量关联主任务失败");
    }
    return;
  }
  const task = selectedTask.value;
  if (!task) return;
  try {
    selectedTask.value = await store.update(task, { parentId: parent?.publicId || null });
    detailParentPickerOpen.value = false;
    message.success(parent ? `已关联主任务“${parent.title}”` : "已取消主任务关联");
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "关联主任务失败");
  }
}
async function pinDetailTask() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  try {
    selectedTask.value = await store.sortTask(task, 0);
    message.success("任务已置顶到当前清单和状态的首位");
  } catch (error) {
    message.error(error?.message || "任务置顶失败");
  }
}
async function abandonDetailTask() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task || task.status === "ABANDONED") return;
  try {
    await store.abandon(task);
    selectedTask.value = store.tasks.find(item => item.publicId === task.publicId) || null;
  } catch (error) {
    message.error(error?.message || "放弃任务失败");
  }
}
async function toggleDetailTaskTag(tag) {
  const task = selectedTask.value;
  if (!task || !tag) return;
  const tagIds = task.tagIds || [];
  try {
    const updated = await store.update(task, {
      tagIds: tagIds.includes(tag.publicId)
        ? tagIds.filter(id => id !== tag.publicId)
        : [...tagIds, tag.publicId],
    });
    selectedTask.value = updated;
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "更新标签失败");
  }
}
function chooseDetailAttachment() {
  closeDetailMore();
  detailAttachmentInput.value?.click();
}
async function startDetailFocus(mode = "POMODORO", plannedMinutes = 0) {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  try {
    const payload = { taskId: task.publicId };
    // 正计时：plannedMinutes=0，后端不设计划结束时间，由用户手动完成
    if (mode === "COUNT_UP") payload.plannedMinutes = 0;
    else if (plannedMinutes > 0) payload.plannedMinutes = plannedMinutes;
    await focusStore.start(payload);
    await router.push("/tasks/focus");
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "无法开始专注");
  }
}
// 预计番茄时长：输入 1-180 分钟后开始番茄专注
const focusEstimateOpen = ref(false);
const focusEstimateMinutes = ref(25);
function startEstimatedFocus() {
  closeDetailMore();
  focusEstimateMinutes.value = Number(focusStore.settings?.focusMinutes) || 25;
  focusEstimateOpen.value = true;
}
async function confirmEstimatedFocus() {
  const minutes = Math.min(180, Math.max(1, Number(focusEstimateMinutes.value) || 25));
  focusEstimateOpen.value = false;
  await startDetailFocus("POMODORO", minutes);
}
async function moveDetailTaskFromMenu(list) {
  await moveDetailTaskToList(list);
  closeDetailMore();
}
// 导出弹窗：Markdown / 纯文本预览 + 复制 + 下载
const exportDialogOpen = ref(false);
const exportFormat = ref("markdown");
const exportContent = computed(() => {
  const task = selectedTask.value;
  if (!task) return "";
  const checklist = (task.checklist || []).map(item => `${item.completed ? "[x]" : "[ ]"} ${item.title}`).join("\n");
  if (exportFormat.value === "text") {
    return [task.title, task.description || "", checklist].filter(Boolean).join("\n\n");
  }
  const markdownChecklist = (task.checklist || []).map(item => `- [${item.completed ? "x" : " "}] ${item.title}`).join("\n");
  return `# ${task.title}\n\n${task.description || ""}${markdownChecklist ? `\n\n${markdownChecklist}` : ""}\n`;
});
function openExportDialog() {
  closeDetailMore();
  exportDialogOpen.value = true;
}
async function copyExportContent() {
  try {
    await navigator.clipboard.writeText(exportContent.value);
    message.success("已复制导出内容");
  } catch {
    message.error("复制失败，请手动选择内容复制");
  }
}
function downloadExportContent() {
  const task = selectedTask.value;
  if (!task) return;
  const extension = exportFormat.value === "text" ? "txt" : "md";
  const mimeType = exportFormat.value === "text" ? "text/plain;charset=utf-8" : "text/markdown;charset=utf-8";
  const url = URL.createObjectURL(new Blob([exportContent.value], { type: mimeType }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `${String(task.title || "task").replace(/[\\/:*?"<>|]/g, "-")}.${extension}`;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
// 保存为模板：应用内弹窗输入名称
const taskTemplateDialogOpen = ref(false);
const taskTemplateName = ref("");
function openTaskTemplateDialog() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  taskTemplateName.value = task.title || "模板";
  taskTemplateDialogOpen.value = true;
}
function saveTaskTemplateFromDialog() {
  const task = selectedTask.value;
  const name = taskTemplateName.value.trim();
  if (!task || !name) return;
  const template = { id: crypto.randomUUID(), title: task.title, templateName: name, description: task.description || "", checklist: (task.checklist || []).map(item => item.title) };
  try {
    const templates = [...savedTaskTemplates.value, template];
    localStorage.setItem(taskTemplateStorageKey(), JSON.stringify(templates));
    savedTaskTemplates.value = templates;
    taskTemplateDialogOpen.value = false;
    message.success("已保存为模板");
  } catch {
    message.error("模板保存失败");
  }
}
// 任务/笔记动态：由更多菜单（或右键菜单）触发的浮层，不再展开详情下半区
const activityPanelOpen = ref(false);
const activityPanelTask = ref(null);
const contextActivityTask = ref(null);
async function openActivityPanel() {
  closeDetailMore();
  const target = contextActivityTask.value || selectedTask.value;
  contextActivityTask.value = null;
  if (!target) return;
  activityPanelTask.value = target;
  activityPanelOpen.value = true;
  await loadActivity(target);
}
async function showDetailActivity() {
  await openActivityPanel();
}
function taskTemplateStorageKey() {
  return `task-detail-templates:${store.accountScope || "anonymous"}`;
}
function loadSavedTaskTemplates() {
  try {
    const templates = JSON.parse(localStorage.getItem(taskTemplateStorageKey()) || "[]");
    savedTaskTemplates.value = Array.isArray(templates) ? templates : [];
  } catch {
    savedTaskTemplates.value = [];
  }
}
async function duplicateDetailTask() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  try {
    const copy = await store.create({
      ...task,
      title: `${task.title} 副本`,
      status: "OPEN",
      completedAt: null,
      abandonedAt: null,
      publicId: undefined,
    });
    selectedTask.value = copy;
    openDetail(copy);
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "创建副本失败");
  }
}
async function copyDetailTaskLink() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  try {
    const url = new URL("/tasks/all", window.location.origin);
    url.searchParams.set("task", task.publicId);
    await navigator.clipboard.writeText(url.href);
    message.success("任务链接已复制");
  } catch {
    message.warning("复制失败，请检查浏览器剪贴板权限");
  }
}
// 转换为笔记/任务：只切换条目级类型（itemKind），条目可留在任意类型清单
async function convertDetailTaskToNoteNow() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  try {
    const updated = await store.update(task, { itemKind: "NOTE" });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
    message.success("已转换为笔记");
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "转换失败";
  }
}
async function convertDetailNoteToTask() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  try {
    const updated = await store.update(task, { itemKind: "TASK" });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
    message.success("已转换为任务");
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "转换失败";
  }
}
async function convertDetailTaskToNote(list) {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task || !list) return;
  try {
    selectedTask.value = await store.update(task, { listId: list.publicId, priority: "NONE" });
    message.success(`已转换并移动到笔记清单“${list.name}”`);
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || "转换为笔记失败");
  }
}
function exportDetailTask(format = "markdown") {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  const checklist = (task.checklist || []).map(item => `- [${item.completed ? "x" : " "}] ${item.title}`).join("\n");
  const markdown = `# ${task.title}\n\n${task.description || ""}${checklist ? `\n\n${checklist}` : ""}\n`;
  const content = format === "text"
    ? [task.title, task.description || "", (task.checklist || []).map(item => `${item.completed ? "[x]" : "[ ]"} ${item.title}`).join("\n")].filter(Boolean).join("\n\n")
    : markdown;
  const extension = format === "text" ? "txt" : "md";
  const mimeType = format === "text" ? "text/plain;charset=utf-8" : "text/markdown;charset=utf-8";
  const url = URL.createObjectURL(new Blob([content], { type: mimeType }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `${task.title.replace(/[\\/:*?"<>|]/g, "-") || "task"}.${extension}`;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function openDetailTaskNote() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  const popup = window.open("", `task-note-${task.publicId}`, "popup,width=420,height=620,resizable=yes,scrollbars=yes");
  if (!popup) return message.warning("浏览器阻止了便签窗口，请允许弹窗后重试");
  popup.document.title = `${task.title} - 便签`;
  const style = popup.document.createElement("style");
  style.textContent = "body{margin:0;padding:24px;background:#f7f7f5;color:#242424;font:14px/1.65 system-ui,sans-serif}main{max-width:620px;margin:0 auto}h1{margin:0 0 18px;font-size:21px;line-height:1.35}article{white-space:pre-wrap;overflow-wrap:anywhere}ul{padding-left:22px}li{margin:6px 0}.meta{margin-top:22px;color:#777;font-size:12px}";
  const main = popup.document.createElement("main");
  const title = popup.document.createElement("h1");
  title.textContent = task.title;
  const body = popup.document.createElement("article");
  body.textContent = task.description || "";
  main.append(title, body);
  if (task.checklist?.length) {
    const list = popup.document.createElement("ul");
    for (const item of task.checklist) {
      const row = popup.document.createElement("li");
      row.textContent = `${item.completed ? "☑" : "☐"} ${item.title}`;
      list.append(row);
    }
    main.append(list);
  }
  const meta = popup.document.createElement("p");
  meta.className = "meta";
  meta.textContent = listName(task.listId);
  main.append(meta);
  popup.document.head.append(style);
  popup.document.body.replaceChildren(main);
  popup.focus();
}
function printDetailTask() {
  const task = selectedTask.value;
  closeDetailMore();
  if (!task) return;
  const printWindow = window.open("", "_blank");
  if (!printWindow) return message.warning("浏览器阻止了打印窗口，请允许弹窗后重试");
  printWindow.document.title = task.title;
  const heading = printWindow.document.createElement("h1");
  heading.textContent = task.title;
  const description = printWindow.document.createElement("pre");
  description.textContent = task.description || "";
  printWindow.document.body.replaceChildren(heading, description);
  printWindow.document.head.insertAdjacentHTML("beforeend", "<style>body{font:16px/1.6 system-ui,sans-serif;margin:40px}pre{font:inherit;white-space:pre-wrap}</style>");
  printWindow.document.close();
  window.setTimeout(() => {
    if (!printWindow.closed) {
      printWindow.focus();
      printWindow.print();
    }
  }, 180);
}
function deleteDetailTask() {
  const task = selectedTask.value;
  closeDetailMore();
  if (task) void deleteTask(task);
}
function openDetail(task) {
  if (!task) return;
  if (selectedTask.value && inlineDescriptionDraft.value !== (selectedTask.value.description || "")) {
    void saveInlineDescription().catch(() => {});
  }
  listActivityOpen.value = false;
  reminderLoadSequence += 1;
  closeAttributePopovers();
  Object.assign(quickReminder, { enabled: false, kind: "RELATIVE", offsetMinutes: 30, remindLocal: "", dailyUntilDone: false, reminder: null });
  selectedTask.value = task;
  closeDetailMore();
  inlineDescriptionDraft.value = task.description || "";
  inlineDescriptionError.value = "";
  editorOpen.value = false;
  inlineTitleEditing.value = false;
  detailChecklistEditingIndex.value = -1;
  detailRichToolbarOpen.value = false;
  detailSecondaryOpen.value = false;
  detailFieldsOpen.value = false;
  cancelDetailSubtask();
  commentComposerOpen.value = false;
  commentDraft.value = "";
  void loadActivity(task);
  void loadCollaboration(task);
}
async function openTaskIndicator(task, kind) {
  openDetail(task);
  await nextTick();
  if (kind === "repeat") {
    toggleRepeatPopover();
    return;
  }
  if (kind === "focus") {
    document.querySelector('[aria-label="专注记录"].clone-detail-focus-summary')?.scrollIntoView({ block: "nearest", behavior: "smooth" });
    return;
  }
  if (kind === "attachments") {
    detailSecondaryOpen.value = true;
    await nextTick();
    const target = document.querySelector(".clone-collaboration-section[aria-label='附件']");
    target?.scrollIntoView({ block: "nearest", behavior: "smooth" });
    return;
  }
  const selector = kind === "checklist"
    ? '[aria-label="检查项"].clone-checklist-read'
    : kind === "subtasks"
      ? '[aria-label="子任务"].clone-detail-subtasks'
      : ".clone-detail-description-block";
  document.querySelector(selector)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
}
function openTaskRow(task, event) {
  const anchorElement = event?.currentTarget;
  const rect = anchorElement?.getBoundingClientRect?.();
  const clickX = Number(event?.clientX);
  const clickY = Number(event?.clientY);
  boardDetailAnchor.value = rect ? {
    top: rect.top,
    right: rect.right,
    left: rect.left,
    clickX: clickX > 0 ? clickX : (rect.left + rect.right) / 2,
    clickY: clickY > 0 ? clickY : 0,
    centerX: clickX > 0 ? clickX : (rect.left + rect.right) / 2,
  } : null;
  openDetail(task);
  inlineRowTitleTaskId.value = task.publicId;
  inlineRowTitleDraft.value = task.title;
  void nextTick(() => {
    document.querySelector(".clone-task-content input")?.focus();
    window.requestAnimationFrame(() => {
      if (!anchorElement?.isConnected) return;
      const updatedRect = anchorElement.getBoundingClientRect();
      boardDetailAnchor.value = {
        top: updatedRect.top,
        right: updatedRect.right,
        left: updatedRect.left,
        clickX: clickX > 0 ? clickX : (updatedRect.left + updatedRect.right) / 2,
        clickY: clickY > 0 ? clickY : 0,
        centerX: clickX > 0 ? clickX : (updatedRect.left + updatedRect.right) / 2,
      };
    });
  });
}
function openListActivity() {
  const list = route.name === "tasks-inbox" ? store.inbox : currentTaskList.value;
  const listId = list?.publicId;
  if (!listId) return;
  listOptionsOpen.value = false;
  printOptionsOpen.value = false;
  clearBatchSelection();
  closeDetail();
  listActivityListId.value = listId;
  listActivityTitle.value = list.name || "清单";
  listActivityOpen.value = true;
  listActivityItems.value = [];
  listActivityPage.value = 0;
  listActivityTotal.value = 0;
  listActivityError.value = "";
  void loadListActivityPage(true);
}
function closeListActivity() {
  listActivityOpen.value = false;
  listActivityRequest += 1;
  listActivityLoading.value = false;
}
let listActivityRequest = 0;
async function loadListActivityPage(reset = false) {
  const listId = listActivityListId.value;
  if (!listId || listActivityLoading.value) return;
  if (!store.online) {
    listActivityError.value = "联网后才能查看清单动态";
    return;
  }
  const requestId = ++listActivityRequest;
  const page = reset ? 0 : listActivityPage.value + 1;
  if (reset) {
    listActivityItems.value = [];
    listActivityError.value = "";
  }
  listActivityLoading.value = true;
  try {
    const result = await apiListTaskListActivity(listId, { page, size: 50 });
    if (requestId !== listActivityRequest) return;
    listActivityItems.value = reset ? result?.items || [] : [...listActivityItems.value, ...(result?.items || [])];
    listActivityPage.value = page;
    listActivityTotal.value = result?.total || 0;
    listActivityError.value = "";
  } catch (error) {
    if (requestId === listActivityRequest) listActivityError.value = error?.response?.data?.detail || error?.message || "清单动态加载失败";
  } finally {
    if (requestId === listActivityRequest) listActivityLoading.value = false;
  }
}
function listActivityGroupLabel(date) {
  const today = new Date();
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const current = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const dayName = new Intl.DateTimeFormat("zh-CN", { weekday: "long" }).format(date);
  if (day.getTime() === current.getTime()) return `今天 ${dayName}`;
  return new Intl.DateTimeFormat("zh-CN", { month: "long", day: "numeric", weekday: "long" }).format(date);
}
function formatListActivityTime(value) {
  const date = new Date(value);
  const elapsed = Math.max(0, Date.now() - date.getTime());
  if (elapsed < 60 * 60 * 1000) return `${Math.max(1, Math.floor(elapsed / 60000))}分钟前`;
  if (elapsed < 24 * 60 * 60 * 1000) return `${Math.floor(elapsed / (60 * 60 * 1000))}小时前`;
  return new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(date);
}
function activityIcon(item) {
  if (item.summary?.startsWith("移出") || ["DELETED", "ATTACHMENT_DELETED"].includes(item.eventType)) return item.summary?.startsWith("移出") ? CircleMinus : Trash2;
  if (["CREATED", "RESTORED", "ATTACHMENT_ADDED"].includes(item.eventType)) return Plus;
  return Pencil;
}
function activityTone(item) {
  if (item.summary?.startsWith("移出") || ["DELETED", "ATTACHMENT_DELETED"].includes(item.eventType)) return "is-destructive";
  return ["CREATED", "RESTORED", "ATTACHMENT_ADDED"].includes(item.eventType) ? "is-created" : "is-updated";
}
function activityTags(item) {
  return (item.tagIds || []).map(id => store.tags.find(tag => tag.publicId === id)).filter(Boolean);
}
function openActivityTask(item) {
  closeListActivity();
  void router.push({ path: "/tasks/all", query: { task: item.taskId } });
}
function cancelInlineRowTitle() {
  inlineRowTitleTaskId.value = "";
  inlineRowTitleDraft.value = "";
}
function beginInlineSubtaskTitleEdit(task) {
  inlineSubtaskTitleId.value = task.publicId;
  inlineSubtaskTitleDraft.value = task.title || "";
  void nextTick(() => document.querySelector(".clone-detail-subtask-title-input")?.focus());
}
function cancelInlineSubtaskTitle() {
  inlineSubtaskTitleId.value = "";
  inlineSubtaskTitleDraft.value = "";
}
async function saveInlineSubtaskTitle(task) {
  if (inlineSubtaskTitleId.value !== task.publicId) return;
  const title = inlineSubtaskTitleDraft.value.trim();
  if (!title || title === task.title) return cancelInlineSubtaskTitle();
  try {
    await store.update(task, { title });
    cancelInlineSubtaskTitle();
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "子任务标题保存失败";
  }
}
async function saveInlineRowTitle(task) {
  if (inlineRowTitleTaskId.value !== task.publicId) return;
  const title = inlineRowTitleDraft.value.trim();
  if (!title || title === task.title) return cancelInlineRowTitle();
  try {
    const updated = await store.update(task, { title });
    if (selectedTask.value?.publicId === updated.publicId) selectedTask.value = updated;
    cancelInlineRowTitle();
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "标题保存失败";
  }
}
function openRowDateEditor(task, anchorEl = null) {
  if (rowDateTaskId.value === task.publicId) {
    closeRowDateEditor();
    return;
  }
  rowDateTaskId.value = task.publicId;
  rowDateAnchor.value = anchorEl;
  rowTimeListOpen.value = false;
  const due = task.dueAt ? new Date(task.dueAt) : null;
  const allDay = Boolean(task.allDay);
  rowDatePanel.mode = task.startAt ? "RANGE" : "DATE";
  rowDatePanel.selectedDate = due ? localDateKey(due) : "";
  rowDatePanel.time = due && !allDay ? toLocal(task.dueAt).slice(11, 16) : "";
  rowDatePanel.allDay = allDay || !due;
  rowDatePanel.rangeStart = task.startAt ? localDateKey(new Date(task.startAt)) : "";
  rowDatePanel.rangeEnd = due ? localDateKey(due) : "";
  if (task.startAt && due) {
    rowDatePanel.rangeStartTime = toLocal(task.startAt).slice(11, 16);
    rowDatePanel.rangeEndTime = toLocal(task.dueAt).slice(11, 16);
  }
  const monthBase = due || new Date();
  quickCalendarMonth.value = new Date(monthBase.getFullYear(), monthBase.getMonth(), 1);
  syncQuickRepeatFromRule(rowRepeat, task.rrule || "", task.recurrenceAnchor || "DUE_DATE");
  rowRepeatEditorOpen.value = false;
  resetRowDateMenu();
  void loadRowReminders(task);
  nextTick(positionRowDatePanel);
}
function closeRowDateEditor() {
  rowDateTaskId.value = "";
  rowDateAnchor.value = null;
  rowDateObserver?.disconnect();
  rowDateObserver = null;
  resetRowDateMenu();
}
function positionRowDatePanel() {
  rowDateObserver?.disconnect();
  rowDateObserver = null;
  const panel = rowDatePanelRef.value;
  if (!panel) return;
  const width = 378;
  const height = panel.offsetHeight || 560;
  const rect = rowDateAnchor.value?.getBoundingClientRect();
  const left = rect ? Math.max(12, Math.min(rect.left, window.innerWidth - width - 12)) : Math.max(12, (window.innerWidth - width) / 2);
  const below = rect ? rect.bottom + 6 : 120;
  const top = below + height <= window.innerHeight - 12 ? below : Math.max(12, Math.min((rect?.top || below) - height - 6, window.innerHeight - height - 12));
  rowDateStyle.value = { position: "fixed", top: `${top}px`, left: `${left}px`, width: `${width}px` };
  rowDateObserver = new ResizeObserver(positionRowDatePanel);
  rowDateObserver.observe(panel);
}
function setRowDateMode(mode) {
  rowDatePanel.mode = mode;
  if (mode === "RANGE") rowDatePanel.allDay = false;
}
function chooseRowDatePreset(preset) {
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (preset === "TOMORROW") date.setDate(date.getDate() + 1);
  if (preset === "NEXT_WEEK") {
    const daysUntilNextMonday = ((8 - date.getDay()) % 7) || 7;
    date.setDate(date.getDate() + daysUntilNextMonday);
  }
  if (preset === "NEXT_MONTH") date.setMonth(date.getMonth() + 1, 1);
  const key = localDateKey(date);
  quickCalendarMonth.value = new Date(date.getFullYear(), date.getMonth(), 1);
  if (rowDatePanel.mode === "RANGE") {
    rowDatePanel.rangeStart = key;
    rowDatePanel.rangeEnd = "";
  } else rowDatePanel.selectedDate = key;
}
function selectRowDate(key) {
  if (rowDatePanel.mode !== "RANGE") {
    rowDatePanel.selectedDate = key;
    return;
  }
  if (!rowDatePanel.rangeStart || rowDatePanel.rangeEnd) {
    rowDatePanel.rangeStart = key;
    rowDatePanel.rangeEnd = "";
    return;
  }
  if (key < rowDatePanel.rangeStart) {
    rowDatePanel.rangeStart = key;
    rowDatePanel.rangeEnd = "";
  } else rowDatePanel.rangeEnd = key;
}
function selectRowTime(time) {
  rowDatePanel.time = time;
  rowDatePanel.allDay = false;
  rowTimeListOpen.value = false;
}
function clearRowTime() {
  rowDatePanel.time = "";
  rowDatePanel.allDay = true;
}
function clearRowDateFromPanel() {
  const task = store.tasks.find(item => item.publicId === rowDateTaskId.value);
  closeRowDateEditor();
  if (task) void saveRowDate(task, "", { startAt: "", allDay: false });
}
async function applyRowDatePanel() {
  const task = store.tasks.find(item => item.publicId === rowDateTaskId.value);
  if (!task) {
    closeRowDateEditor();
    return;
  }
  const rule = buildQuickRepeatRule(rowRepeat);
  const anchor = rule ? (rowRepeat.mode === "COMPLETION_DATE" ? "COMPLETION_DATE" : "DUE_DATE") : "";
  let newDueAt = "";
  if (rowDatePanel.mode === "RANGE") {
    if (!rowDatePanel.rangeStart || !rowDatePanel.rangeEnd) {
      store.error = "请选择有效的开始和结束日期";
      return;
    }
    const startAt = new Date(`${rowDatePanel.rangeStart}T${rowDatePanel.allDay ? "00:00" : (rowDatePanel.rangeStartTime || "09:00")}`).toISOString();
    newDueAt = new Date(`${rowDatePanel.rangeEnd}T${rowDatePanel.allDay ? "00:00" : (rowDatePanel.rangeEndTime || "10:00")}`).toISOString();
    if (newDueAt <= startAt) {
      store.error = "请选择有效的开始和结束日期";
      return;
    }
    await saveRowDate(task, newDueAt, { startAt, allDay: rowDatePanel.allDay, rrule: rule, recurrenceAnchor: anchor });
  } else {
    if (!rowDatePanel.selectedDate) {
      store.error = "请选择日期";
      return;
    }
    newDueAt = new Date(`${rowDatePanel.selectedDate}T${rowDatePanel.time || "00:00"}`).toISOString();
    await saveRowDate(task, newDueAt, { allDay: !rowDatePanel.time, rrule: rule, recurrenceAnchor: anchor });
  }
  await syncRowReminders(task.publicId, newDueAt, rowDatePanel.mode === "RANGE" ? rowDatePanel.allDay : !rowDatePanel.time);
  closeRowDateEditor();
}
async function saveRowDate(task, dueAt = "", extra = {}) {
  try {
    await store.update(task, { dueAt, allDay: extra.allDay ?? false, ...extra });
    rowDateTaskId.value = "";
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "日期更新失败";
  }
}
function closeRowDatePanelOnOutsidePointer(event) {
  if (!rowDateTaskId.value) return;
  if (event.target.closest?.(".clone-row-date-panel, .clone-quick-flyout, [data-row-date-trigger]")) return;
  closeRowDateEditor();
}
function closeRowDateMenuOnOutsidePointer(event) {
  if (!rowDateMenu.value) return;
  if (event.target.closest?.(".clone-quick-flyout, .clone-date-prop-trigger, .clone-date-prop-clear, .clone-date-time-list")) return;
  resetRowDateMenu();
}

/* ---- 行内日期弹层：提醒（复用快添预设浮层与任务提醒 API） ---- */
const rowDateMenu = ref("");
const rowReminderToggleRef = ref(null);
const rowReminderMenuRef = ref(null);
const rowReminderMenuStyle = ref({});
const rowRepeatToggleRef = ref(null);
const rowRepeatMenuRef = ref(null);
const rowRepeatMenuStyle = ref({});
const rowEndToggleRef = ref(null);
const rowEndMenuRef = ref(null);
const rowEndMenuStyle = ref({});
const rowRepeatGroupOpen = ref("");
const rowEndPanel = ref("");
const rowEndCountDraft = ref(10);
const rowEndCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const rowRepeatEditorOpen = ref(false);
const rowRepeatError = ref("");
const rowRepeat = reactive(createQuickRepeatState());
const rowReminderLoading = ref(false);
const rowReminderDirty = ref(false);
const rowReminderExisting = ref([]);
const rowReminderConfig = reactive({ presets: [], amount: 1, unit: "DAY", time: "09:00" });
let rowFlyoutObserver = null;
function resetRowDateMenu() {
  rowDateMenu.value = "";
  rowTimeListOpen.value = false;
  rowEndPanel.value = "";
  stopRowFlyoutObserver();
}
function reminderPresetFromTask(reminder, dueAt) {
  if (reminder?.kind === "ABSOLUTE" && reminder.remindAt) {
    if (!dueAt) return "";
    const dayStart = date => { const copy = new Date(date); copy.setHours(0, 0, 0, 0); return copy; };
    const days = Math.round((dayStart(new Date(reminder.remindAt)) - dayStart(new Date(dueAt))) / 86400000);
    if (days === 0) return "TODAY";
    if (days === 1) return "1_DAY";
    if (days === 2) return "2_DAY";
    if (days === 3) return "3_DAY";
    if (days === 7) return "1_WEEK";
    return days >= 1 ? `CUSTOM:${days}:DAY` : "";
  }
  const offset = Number(reminder?.offsetMinutes);
  if (!Number.isFinite(offset) || offset < 0) return "";
  return { 0: "ON_TIME", 5: "5_MIN", 30: "30_MIN", 60: "1_HOUR", 1440: "1_DAY" }[offset] ?? `CUSTOM:${offset}:MINUTE`;
}
async function loadRowReminders(task) {
  rowReminderExisting.value = [];
  rowReminderConfig.presets = [];
  rowReminderDirty.value = false;
  if (!store.online || !task?.publicId) return;
  rowReminderLoading.value = true;
  try {
    const reminders = await apiListTaskReminders(task.publicId);
    rowReminderExisting.value = reminders || [];
    rowReminderConfig.presets = (reminders || []).map(reminder => reminderPresetFromTask(reminder, task.dueAt)).filter(Boolean);
  } catch { /* 读取失败时按无提醒展示，保存日期不受影响 */ }
  finally { rowReminderLoading.value = false; }
}
async function syncRowReminders(taskPublicId, dueAt, allDay) {
  if (!rowReminderDirty.value) return;
  if (!store.online) { store.error = "提醒需要联网保存"; return; }
  for (const reminder of rowReminderExisting.value) {
    try { await apiDeleteTaskReminder(taskPublicId, reminder.publicId, reminder.revision); }
    catch (error) { store.error = error?.response?.data?.detail || error?.message || "提醒更新失败"; return; }
  }
  rowReminderExisting.value = [];
  if (rowReminderConfig.presets.length && dueAt) {
    for (const preset of rowReminderConfig.presets) {
      let payloads = [];
      try {
        payloads = quickReminderPayloads({ dueAt, allDay, config: { enabled: true, presets: [preset], amount: rowReminderConfig.amount, unit: rowReminderConfig.unit, time: rowReminderConfig.time } });
      } catch { store.error = "部分提醒与新日期不兼容，已跳过该提醒"; continue; }
      for (const payload of payloads) {
        try { rowReminderExisting.value.push(await apiCreateTaskReminder(taskPublicId, payload)); }
        catch (error) { store.error = error?.response?.data?.detail || error?.message || "提醒保存失败"; return; }
      }
    }
  }
}
function selectRowReminder(preset) {
  rowReminderDirty.value = true;
  const selected = rowReminderConfig.presets.includes(preset);
  rowReminderConfig.presets = selected
    ? rowReminderConfig.presets.filter(value => value !== preset)
    : [...rowReminderConfig.presets, preset];
  if (!selected) rememberQuickReminderPreset(preset);
  if (preset === "CUSTOM" && !selected) {
    rowReminderConfig.amount = 1;
    rowReminderConfig.unit = rowDatePanel.allDay ? "DAY" : "MINUTE";
  }
}
function clearRowReminders() {
  rowReminderDirty.value = true;
  rowReminderConfig.presets = [];
}
function applyRowReminderCustom() {
  rowReminderDirty.value = true;
  const amount = Math.max(1, Math.round(Number(rowReminderConfig.amount) || 1));
  rowReminderConfig.amount = amount;
  const encoded = `CUSTOM:${amount}:${rowReminderConfig.unit}`;
  rowReminderConfig.presets = rowReminderConfig.presets.map(value => value === "CUSTOM" ? encoded : value);
  if (!rowReminderConfig.presets.includes(encoded)) rowReminderConfig.presets = [...rowReminderConfig.presets, encoded];
  rememberQuickReminderPreset(encoded);
}
function cancelRowReminderCustom() {
  rowReminderConfig.presets = rowReminderConfig.presets.filter(value => value !== "CUSTOM");
}
const rowReminderMenuOptions = computed(() => getQuickReminderOptions(rowDatePanel.allDay).map(option => ({ ...option, label: rowDatePanel.allDay && option.value !== "CUSTOM" ? `${option.label} (${rowReminderConfig.time})` : option.label })));
const rowReminderSummary = computed(() => rowReminderConfig.presets.map(value => quickReminderPresetLabel(value, rowDatePanel.allDay, rowReminderConfig.time)).join("、"));
const rowReminderValidPreset = value => {
  if (/^CUSTOM:(\d+):(MINUTE|HOUR|DAY|WEEK)$/.test(value)) return rowDatePanel.allDay ? !value.endsWith(":MINUTE") && !value.endsWith(":HOUR") : true;
  return rowDatePanel.allDay ? ["TODAY", "1_DAY", "2_DAY", "3_DAY", "1_WEEK"].includes(value) : ["ON_TIME", "5_MIN", "30_MIN", "1_HOUR", "1_DAY"].includes(value);
};
const rowReminderRecentOptions = computed(() => quickReminderRecent.value
  .filter(value => value !== "CUSTOM" && rowReminderValidPreset(value) && !rowReminderMenuOptions.value.some(option => option.value === value))
  .map(value => ({ value, label: quickReminderPresetLabel(value, rowDatePanel.allDay, rowReminderConfig.time) })));
const rowReminderCustomPreview = computed(() => {
  const key = rowDatePanel.selectedDate || rowDatePanel.rangeEnd;
  if (!key) return "";
  const base = rowDatePanel.allDay ? new Date(`${key}T00:00:00`) : new Date(`${key}T${rowDatePanel.time || "09:00"}`);
  if (Number.isNaN(base.getTime())) return "";
  const amount = Math.max(1, Number(rowReminderConfig.amount) || 1);
  if (rowDatePanel.allDay) {
    base.setDate(base.getDate() - amount * (rowReminderConfig.unit === "WEEK" ? 7 : 1));
    const [hours, minutes] = String(rowReminderConfig.time).split(":").map(Number);
    base.setHours(hours || 0, minutes || 0, 0, 0);
  } else {
    base.setMinutes(base.getMinutes() - amount * ({ MINUTE: 1, HOUR: 60, DAY: 1440, WEEK: 10080 }[rowReminderConfig.unit] || 1));
  }
  return `将于 ${new Intl.DateTimeFormat("zh-CN", { month: "long", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false }).format(base)} 提醒`;
});

/* ---- 行内日期弹层：重复（复用快添重复菜单/自定义面板/结束重复） ---- */
const rowRepeatAnchorDate = computed(() => {
  const task = store.tasks.find(item => item.publicId === rowDateTaskId.value);
  const key = rowDatePanel.selectedDate || rowDatePanel.rangeEnd || (task?.dueAt ? localDateKey(new Date(task.dueAt)) : "");
  const parsed = key ? new Date(`${key}T00:00:00`) : new Date();
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
});
const rowRepeatMenuOptions = computed(() => {
  const preset = rowRepeat.preset;
  const date = rowRepeatAnchorDate.value;
  const weekdayText = QUICK_WEEKDAY_TEXT[date.getDay()];
  const monthDay = date.getDate();
  const month = date.getMonth() + 1;
  return [
    { key: "none", label: "不重复", kind: "preset", value: "", active: !preset },
    { key: "DAILY", label: "每天", kind: "preset", value: "DAILY", active: preset === "DAILY" },
    { key: "WEEKLY", label: `每周（${weekdayText}）`, kind: "preset", value: "WEEKLY", active: preset === "WEEKLY" },
    { key: "MONTHLY", label: `每月（${monthDay}日）`, kind: "preset", value: "MONTHLY", active: preset === "MONTHLY" },
    { key: "YEARLY", label: `每年（${month}月${monthDay}日）`, kind: "preset", value: "YEARLY", active: preset === "YEARLY" },
    { key: "group-workday", label: "工作日", isGroup: true },
    { key: "WORKDAY", label: "每周工作日（周一至五）", kind: "preset", value: "WORKDAY", active: preset === "WORKDAY", child: true, groupKey: "group-workday" },
    { key: "LEGAL_WORKDAY", label: "法定工作日", kind: "preset", value: "LEGAL_WORKDAY", active: preset === "LEGAL_WORKDAY", child: true, groupKey: "group-workday" },
    { key: "group-holiday", label: "节假日", isGroup: true },
    { key: "WEEKEND", label: "每周末", kind: "preset", value: "WEEKEND", active: preset === "WEEKEND", child: true, groupKey: "group-holiday" },
    { key: "LEGAL_HOLIDAY", label: "法定节假日", kind: "preset", value: "LEGAL_HOLIDAY", active: preset === "LEGAL_HOLIDAY", child: true, groupKey: "group-holiday" },
    { key: "group-lunar", label: "农历重复", isGroup: true },
    { key: "LUNAR_MONTHLY", label: `农历每月（${monthDay}日）`, kind: "preset", value: "LUNAR_MONTHLY", active: preset === "LUNAR_MONTHLY", child: true, groupKey: "group-lunar" },
    { key: "LUNAR_YEARLY", label: `农历每年（${month}月${monthDay}日）`, kind: "preset", value: "LUNAR_YEARLY", active: preset === "LUNAR_YEARLY", child: true, groupKey: "group-lunar" },
    { key: "separator-2", separator: true },
    { key: "CUSTOM", label: "自定义", kind: "custom", active: preset === "CUSTOM" },
  ];
});
function onRowRepeatMenuSelect(option) {
  if (!option || option.separator || option.isGroup) return;
  if (option.kind === "custom") {
    resetRowDateMenu();
    if (!["CUSTOM", "LUNAR_MONTHLY", "LUNAR_YEARLY"].includes(rowRepeat.preset)) rowRepeat.preset = "CUSTOM";
    rowRepeatEditorOpen.value = true;
    return;
  }
  const value = option.value;
  if (!value) {
    Object.assign(rowRepeat, createQuickRepeatState());
  } else {
    rowRepeat.preset = value;
    const anchor = rowRepeatAnchorDate.value;
    if (value === "WEEKLY") rowRepeat.weekdays = [DAY_CODES[(anchor.getDay() + 6) % 7]];
    if (value === "MONTHLY") {
      rowRepeat.monthMode = "DATE";
      rowRepeat.monthDays = [anchor.getDate()];
      rowRepeat.lastDay = false;
    }
    if (value === "YEARLY") {
      rowRepeat.monthMode = "DATE";
      rowRepeat.month = anchor.getMonth() + 1;
      rowRepeat.monthDays = [anchor.getDate()];
      rowRepeat.lastDay = false;
    }
    if (value === "LUNAR_MONTHLY") rowRepeat.lunarDay = Math.min(30, anchor.getDate());
    if (value === "LUNAR_YEARLY") {
      rowRepeat.lunarMonth = Math.min(12, anchor.getMonth() + 1);
      rowRepeat.lunarDay = Math.min(30, anchor.getDate());
    }
  }
  rowRepeatError.value = "";
  resetRowDateMenu();
}
function applyRowRepeat() {
  rowRepeatError.value = quickRepeatValidation(rowRepeat);
  if (rowRepeatError.value) return;
  rowRepeatEditorOpen.value = false;
}
function cancelRowRepeat() {
  const task = store.tasks.find(item => item.publicId === rowDateTaskId.value);
  syncQuickRepeatFromRule(rowRepeat, task?.rrule || "", task?.recurrenceAnchor || "DUE_DATE");
  rowRepeatError.value = "";
  rowRepeatEditorOpen.value = false;
}
function setRowRepeatFrequency(event) {
  rowRepeat.frequency = event.target.value;
  if (rowRepeat.frequency === "YEARLY" && rowRepeat.monthMode === "WORKDAY") rowRepeat.monthMode = "DATE";
}
function toggleRowRepeatWeekday(day) {
  const selected = rowRepeat.weekdays.includes(day) ? rowRepeat.weekdays.filter(item => item !== day) : [...rowRepeat.weekdays, day];
  rowRepeat.weekdays = DAY_CODES.filter(code => selected.includes(code));
}
function toggleRowRepeatMonthDay(day) {
  const selected = rowRepeat.monthDays.includes(day) ? rowRepeat.monthDays.filter(value => value !== day) : [...rowRepeat.monthDays, day];
  rowRepeat.monthDays = selected.sort((a, b) => a - b);
  rowRepeat.lastDay = false;
}
const rowRepeatCalendarDays = computed(() => {
  const date = rowRepeatAnchorDate.value;
  const year = date.getFullYear();
  const monthIndex = rowRepeat.frequency === "YEARLY" ? Number(rowRepeat.month || 1) - 1 : date.getMonth();
  const first = new Date(year, monthIndex, 1);
  const offset = (first.getDay() + 6) % 7;
  return [...Array(offset).fill(null), ...Array.from({ length: new Date(year, monthIndex + 1, 0).getDate() }, (_, index) => index + 1)];
});
const rowShowEndRow = computed(() => Boolean(buildQuickRepeatRule(rowRepeat)));
const rowEndLabel = computed(() => {
  if (rowRepeat.endMode === "UNTIL" && rowRepeat.until) return `直到 ${rowRepeat.until}`;
  if (rowRepeat.endMode === "COUNT") return `${Math.max(1, Number(rowRepeat.count) || 1)} 次重复`;
  return "永不结束";
});
const rowEndCalendarTitle = computed(() => new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long" }).format(rowEndCalendarMonth.value));
const rowEndCalendarDays = computed(() => calendarMonthDays(rowEndCalendarMonth.value).map(day => ({ ...day, selected: rowRepeat.endMode === "UNTIL" && rowRepeat.until === day.key })));
function moveRowEndMonth(offset) {
  rowEndCalendarMonth.value = new Date(rowEndCalendarMonth.value.getFullYear(), rowEndCalendarMonth.value.getMonth() + offset, 1);
}
function toggleRowEndPanel(panel) {
  rowEndPanel.value = rowEndPanel.value === panel ? "" : panel;
}
function selectRowEndMode(mode) {
  rowRepeat.endMode = mode;
  resetRowDateMenu();
}
function applyRowEndCount() {
  rowRepeat.endMode = "COUNT";
  rowRepeat.count = Math.max(1, Number(rowEndCountDraft.value) || 1);
  resetRowDateMenu();
}
function cancelRowEndCount() {
  rowEndCountDraft.value = Math.max(1, Number(rowRepeat.count) || 10);
  rowEndPanel.value = "";
}
function pickRowEndDate(dayKey) {
  rowRepeat.endMode = "UNTIL";
  rowRepeat.until = dayKey;
  resetRowDateMenu();
}
function toggleRowDateMenu(menu) {
  rowDateMenu.value = rowDateMenu.value === menu ? "" : menu;
  if (!rowDateMenu.value) {
    stopRowFlyoutObserver();
    return;
  }
  rowTimeListOpen.value = false;
  rowEndPanel.value = "";
  if (menu === "repeat") rowRepeatGroupOpen.value = QUICK_REPEAT_GROUPS[rowRepeat.preset] || "";
  if (menu === "end") {
    const anchor = rowRepeat.until ? new Date(`${rowRepeat.until}T00:00:00`) : rowRepeatAnchorDate.value;
    rowEndCalendarMonth.value = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
    rowEndCountDraft.value = Math.max(1, Number(rowRepeat.count) || 10);
  }
  nextTick(() => positionRowDateMenu(menu));
}
function rowFlyoutToggleFor(menu) {
  if (menu === "reminder") return rowReminderToggleRef.value;
  if (menu === "repeat") return rowRepeatToggleRef.value;
  if (menu === "end") return rowEndToggleRef.value;
  return null;
}
function rowFlyoutMenuFor(menu) {
  if (menu === "reminder") return rowReminderMenuRef.value;
  if (menu === "repeat") return rowRepeatMenuRef.value;
  if (menu === "end") return rowEndMenuRef.value;
  return null;
}
function applyRowFlyoutPosition(menu) {
  const style = quickFlyoutStyle(rowFlyoutToggleFor(menu), rowFlyoutMenuFor(menu));
  if (menu === "reminder") rowReminderMenuStyle.value = style;
  else if (menu === "repeat") rowRepeatMenuStyle.value = style;
  else if (menu === "end") rowEndMenuStyle.value = style;
}
function positionRowDateMenu(menu) {
  rowFlyoutObserver?.disconnect();
  rowFlyoutObserver = null;
  const target = rowFlyoutMenuFor(menu);
  if (!target) return;
  rowFlyoutObserver = new ResizeObserver(() => applyRowFlyoutPosition(menu));
  rowFlyoutObserver.observe(target);
  applyRowFlyoutPosition(menu);
}
function stopRowFlyoutObserver() {
  rowFlyoutObserver?.disconnect();
  rowFlyoutObserver = null;
}
function switchWorkspaceView(view) {
  closeWorkspaceMenus();
  selectedWorkspaceView.value = view;
  const { workspaceView, ...query } = route.query;
  if (view !== "LIST") query.workspaceView = view;
  router.replace({ path: route.path, query });
}
function openDisplaySettings() {
  closeWorkspaceMenus();
  displaySettingsOpen.value = true;
}
function printTaskList(mode = "TITLE") {
  printMode.value = mode;
  printOptionsOpen.value = false;
  closeWorkspaceMenus();
  void nextTick(() => window.print());
}
function addGroupForCurrentList() {
  const list = currentTaskList.value;
  if (!list) return;
  closeWorkspaceMenus();
  openOrganizationDialog("group", null, { groupListIds: [list.publicId] });
}
async function shareCurrentList() {
  const list = currentTaskList.value;
  if (!list) return;
  closeWorkspaceMenus();
  const url = new URL(`/tasks/list/${list.publicId}`, window.location.origin).href;
  try { await navigator.clipboard.writeText(url); message.success("清单链接已复制"); }
  catch { window.prompt("复制清单链接", url); }
}
function printDescription(value) {
  return String(value || "").replace(/<[^>]*>/g, " ").replace(/[\n\r]+/g, " ").replace(/[#*_`~>-]/g, " ").replace(/\s+/g, " ").trim();
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
function scheduleInlineDescriptionSave(value) {
  inlineDescriptionError.value = "";
  inlineDescriptionVersion += 1;
  if (inlineDescriptionTimer) clearTimeout(inlineDescriptionTimer);
  const task = selectedTask.value;
  inlineDescriptionTimer = setTimeout(() => {
    inlineDescriptionTimer = null;
    void persistInlineDescription(task, inlineDescriptionDraft.value).catch(() => {});
  }, 650);
}
async function persistInlineDescription(task, value) {
  if (!task) return;
  inlineDescriptionQueued = { taskId: task.publicId, value, version: inlineDescriptionVersion, fallback: task };
  if (inlineDescriptionSavePromise) return inlineDescriptionSavePromise;
  const promise = (async () => {
    while (inlineDescriptionQueued) {
      const queued = inlineDescriptionQueued;
      inlineDescriptionQueued = null;
      const latest = store.tasks.find(item => item.publicId === queued.taskId) || queued.fallback;
      if (queued.value === (latest.description || "")) continue;
      try {
        const updated = await store.update(latest, { description: queued.value });
        if (selectedTask.value?.publicId === queued.taskId && inlineDescriptionVersion === queued.version) selectedTask.value = updated;
        if (selectedTask.value?.publicId === queued.taskId && inlineDescriptionVersion !== queued.version) {
          inlineDescriptionQueued = { taskId: queued.taskId, value: inlineDescriptionDraft.value, version: inlineDescriptionVersion, fallback: selectedTask.value };
        }
      } catch (error) {
        inlineDescriptionError.value = error?.response?.data?.detail || error?.message || "描述保存失败";
        throw error;
      }
    }
  })();
  inlineDescriptionSavePromise = promise;
  try {
    await promise;
    inlineDescriptionError.value = "";
  } finally {
    if (inlineDescriptionSavePromise === promise) inlineDescriptionSavePromise = null;
  }
}
function saveInlineDescription() {
  if (inlineDescriptionTimer) clearTimeout(inlineDescriptionTimer);
  inlineDescriptionTimer = null;
  return persistInlineDescription(selectedTask.value, inlineDescriptionDraft.value);
}
function holdInlineDescriptionSync() {
  if (!inlineDescriptionSyncHold) inlineDescriptionSyncHold = store.holdSync();
}
async function releaseInlineDescriptionSync() {
  try {
    await saveInlineDescription();
  } catch {
    return;
  }
  if (!inlineDescriptionSyncHold) return;
  store.releaseSyncHold(inlineDescriptionSyncHold);
  inlineDescriptionSyncHold = null;
}
function handleManualSync(event) {
  event.preventDefault();
  void (async () => {
    try {
      await saveInlineDescription();
      await store.syncNow();
    } catch (error) {
      inlineDescriptionError.value = error?.response?.data?.detail || error?.message || "描述保存失败，同步已暂停";
    }
  })().catch(() => {});
}
// 描述区「+」插入菜单：格式项调用编辑器命令，资源项触发附件/子任务/标签/关联任务
const detailBlockMenuOpen = ref(false);
const detailDescriptionEditor = ref(null);
const detailLinkPickerOpen = ref(false);
const detailLinkQuery = ref("");
const detailLinkOptions = computed(() => {
  const keyword = detailLinkQuery.value.trim().toLowerCase();
  const candidates = store.tasks.filter(task => !task.deleted && task.publicId !== selectedTask.value?.publicId);
  const matched = keyword ? candidates.filter(task => String(task.title || "").toLowerCase().includes(keyword)) : candidates;
  return matched.slice(0, 20);
});
function toggleDetailBlockMenu() {
  detailBlockMenuOpen.value = !detailBlockMenuOpen.value;
  if (detailBlockMenuOpen.value) {
    detailLinkPickerOpen.value = false;
    detailLinkQuery.value = "";
  }
}
function insertDetailBlock(kind) {
  detailBlockMenuOpen.value = false;
  if (["heading1", "heading2", "heading3", "list", "ordered", "check", "quote", "divider"].includes(kind)) {
    // 先让编辑器获得焦点，否则块级命令在无选区时不生效（描述为空时点「+」插入标题会没反应）
    detailDescriptionEditor.value?.focusEditor();
    detailDescriptionEditor.value?.runToolbar(kind);
    return;
  }
  if (kind === "attachment") {
    detailAttachmentInput.value?.click();
    return;
  }
  if (kind === "subtask") {
    createSubtask(selectedTask.value);
    return;
  }
  if (kind === "tag") {
    if (!detailTagPickerOpen.value) toggleDetailTagPicker();
    return;
  }
  if (kind === "linkTask") {
    detailLinkQuery.value = "";
    detailLinkPickerOpen.value = true;
  }
}
function chooseDetailLinkTask(task) {
  const url = new URL("/tasks/all", window.location.origin);
  url.searchParams.set("task", task.publicId);
  detailDescriptionEditor.value?.insertLink(task.title || "未命名任务", url.href);
  detailLinkPickerOpen.value = false;
}

// 底部清单选择：搜索 + 点击移动
const detailListPickerOpen = ref(false);
const detailListQuery = ref("");
const detailListOptions = computed(() => {
  const keyword = detailListQuery.value.trim().toLowerCase();
  const lists = activeLists.value;
  return keyword ? lists.filter(list => String(list.name || "").toLowerCase().includes(keyword)) : lists;
});
function toggleDetailListPicker() {
  detailListPickerOpen.value = !detailListPickerOpen.value;
  if (detailListPickerOpen.value) {
    detailListQuery.value = "";
    detailTagPickerOpen.value = false;
    detailBlockMenuOpen.value = false;
  }
}
async function moveDetailTaskToList(list) {
  const task = selectedTask.value;
  detailListPickerOpen.value = false;
  if (!task || list.publicId === task.listId) return;
  try {
    const updated = await store.update(task, { listId: list.publicId });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "移动清单失败";
  }
}
const detailTagPickerOpen = ref(false);
const draftDetailTagIds = ref([]);
const detailTagSaving = ref(false);
function toggleDetailTagPicker() {
  detailTagPickerOpen.value = !detailTagPickerOpen.value;
  draftDetailTagIds.value = [...(selectedTask.value?.tagIds || [])];
}
function toggleDraftDetailTag(id, checked) {
  draftDetailTagIds.value = checked ? [...new Set([...draftDetailTagIds.value, id])] : draftDetailTagIds.value.filter(value => value !== id);
}
async function applyDetailTags() {
  const task = selectedTask.value;
  if (!task || detailTagSaving.value) return;
  detailTagSaving.value = true;
  try {
    const updated = await store.update(task, { tagIds: draftDetailTagIds.value });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
    detailTagPickerOpen.value = false;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "标签保存失败";
  } finally {
    detailTagSaving.value = false;
  }
}
async function removeDetailTag(tagId) {
  const task = selectedTask.value;
  if (!task) return;
  try {
    const updated = await store.update(task, { tagIds: (task.tagIds || []).filter(id => id !== tagId) });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
    draftDetailTagIds.value = draftDetailTagIds.value.filter(id => id !== tagId);
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "移除标签失败";
  }
}
function beginDetailChecklistEdit(item, index) {
  detailChecklistEditingIndex.value = index;
  detailChecklistDraft.value = item.title || "";
  void nextTick(() => document.querySelector(`[data-checklist-index="${index}"] .clone-detail-checklist-title-input`)?.focus());
}
// 检查项：拖动排序 / 回车续建 / 删除
const detailChecklistDraftOpen = ref(false);
const detailChecklistNewDraft = ref("");
const detailChecklistDraftInput = ref(null);
const detailChecklistDragIndex = ref(null);
const detailChecklistDropTarget = ref(null);
async function applyDetailChecklist(checklist) {
  const task = selectedTask.value;
  if (!task) return;
  try {
    const updated = await store.update(task, { checklist: checklist.map((item, index) => ({ ...item, sortOrder: index })) });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "检查项保存失败";
  }
}
function startDetailChecklistDrag(index, event) {
  detailChecklistDragIndex.value = index;
  event.dataTransfer?.setData("text/plain", String(index));
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
}
function dropDetailChecklist(target) {
  const from = detailChecklistDragIndex.value;
  detailChecklistDragIndex.value = null;
  detailChecklistDropTarget.value = null;
  const task = selectedTask.value;
  if (from === null || from === target || !task?.checklist) return;
  const next = [...task.checklist];
  const [moved] = next.splice(from, 1);
  next.splice(target, 0, moved);
  void applyDetailChecklist(next);
}
async function removeDetailChecklistItem(task, index) {
  if (!task?.checklist) return;
  const next = task.checklist.filter((_, itemIndex) => itemIndex !== index);
  if (selectedTask.value?.publicId !== task.publicId) return;
  await applyDetailChecklist(next);
}
// 检查项提醒：行内闹钟按钮 → 小面板设置时间（检查项级提醒，后端定时扫描推送站内消息）
const checklistReminderIndex = ref(-1);
const checklistReminderDraft = ref("");
const checklistReminderSaving = ref(false);
function checklistReminderLocalValue(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = number => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
function formatChecklistReminder(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(date);
}
function openChecklistReminder(item, index) {
  checklistReminderIndex.value = checklistReminderIndex.value === index ? -1 : index;
  checklistReminderDraft.value = checklistReminderLocalValue(item.remindAt);
}
function setChecklistReminderOffset(days) {
  const date = new Date();
  if (days === 0) date.setHours(date.getHours() + 1, 0, 0, 0);
  else { date.setDate(date.getDate() + 1); date.setHours(9, 0, 0, 0); }
  checklistReminderDraft.value = checklistReminderLocalValue(date.toISOString());
}
async function updateChecklistReminder(index, isoValue) {
  const task = selectedTask.value;
  if (!task?.checklist) return;
  checklistReminderSaving.value = true;
  try {
    const checklist = task.checklist.map((entry, entryIndex) => entryIndex === index ? { ...entry, remindAt: isoValue } : entry);
    await applyDetailChecklist(checklist);
    checklistReminderIndex.value = -1;
  } finally {
    checklistReminderSaving.value = false;
  }
}
async function saveChecklistReminder(item, index) {
  const value = checklistReminderDraft.value ? new Date(checklistReminderDraft.value) : null;
  if (!value || Number.isNaN(value.getTime())) {
    store.error = "请选择提醒时间";
    return;
  }
  await updateChecklistReminder(index, value.toISOString());
}
async function clearChecklistReminder(item, index) {
  checklistReminderDraft.value = "";
  await updateChecklistReminder(index, null);
}
function startDetailChecklistDraft() {
  detailChecklistDraftOpen.value = true;
  void nextTick(() => detailChecklistDraftInput.value?.focus());
}
function cancelDetailChecklistDraft() {
  detailChecklistNewDraft.value = "";
  detailChecklistDraftOpen.value = false;
}
async function submitDetailChecklistDraft() {
  const title = detailChecklistNewDraft.value.trim();
  const task = selectedTask.value;
  if (!task) return;
  if (!title) {
    detailChecklistDraftOpen.value = false;
    return;
  }
  detailChecklistNewDraft.value = "";
  await applyDetailChecklist([...(task.checklist || []), { title, completed: false, sortOrder: (task.checklist || []).length }]);
  // 回车续建：保持输入行打开并聚焦，连续录入
  detailChecklistDraftOpen.value = true;
  void nextTick(() => detailChecklistDraftInput.value?.focus());
}
// 编辑已有项按回车：保存后聚焦底部输入行，直接录入下一条
async function saveDetailChecklistTitleAndNext(task, index) {
  await saveDetailChecklistTitle(task, index);
  startDetailChecklistDraft();
}
function cancelDetailChecklistEdit(item) {
  detailChecklistDraft.value = item.title || "";
  detailChecklistEditingIndex.value = -1;
}
async function saveDetailChecklistTitle(task, index) {
  if (detailChecklistEditingIndex.value !== index) return;
  task = store.tasks.find(item => item.publicId === task?.publicId) || task;
  const current = task?.checklist?.[index];
  const title = detailChecklistDraft.value.trim();
  detailChecklistEditingIndex.value = -1;
  if (!current || !title || title === current.title) {
    detailChecklistDraft.value = current?.title || "";
    return;
  }
  const checklist = task.checklist.map((item, itemIndex) => itemIndex === index ? { ...item, title } : item);
  try {
    const updated = await store.update(task, { checklist });
    if (selectedTask.value?.publicId === task.publicId) selectedTask.value = updated;
  } catch (error) {
    detailChecklistDraft.value = current.title || "";
    store.error = error?.response?.data?.detail || error?.message || "检查项保存失败";
  }
}
async function openEdit(task) {
  if (!task) return;
  if (selectedTask.value?.publicId === task.publicId && inlineDescriptionDraft.value !== (task.description || "")) {
    await saveInlineDescription();
  }
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
  detailSecondaryOpen.value = false;
  commentComposerOpen.value = false;
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
// 子任务：拖动排序 / 右键菜单 / 点击进入子任务详情
const detailSubtaskDragIndex = ref(null);
const detailSubtaskDropTarget = ref(null);
const detailSubtaskOpenAfterCreate = ref(false);
function startDetailSubtaskDrag(index, event) {
  detailSubtaskDragIndex.value = index;
  event.dataTransfer?.setData("text/plain", String(index));
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
}
async function dropDetailSubtask(target) {
  const from = detailSubtaskDragIndex.value;
  detailSubtaskDragIndex.value = null;
  detailSubtaskDropTarget.value = null;
  if (from === null || from === target) return;
  const children = [...detailSubtasks.value];
  if (!children[from]) return;
  const [moved] = children.splice(from, 1);
  children.splice(target, 0, moved);
  try {
    // 子任务是独立任务：按新顺序写回各自的 sortOrder（仅对变化的写库）
    for (let index = 0; index < children.length; index += 1) {
      if (Number(children[index].sortOrder || 0) !== index) await store.update(children[index], { sortOrder: index });
    }
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "子任务排序失败";
  }
}
function createSubtask(task, options = {}) {
  if (!task) return;
  if (selectedTask.value?.publicId !== task.publicId) openDetail(task);
  detailMoreOpen.value = false;
  detailMoreSection.value = "";
  detailSubtaskDraft.value = "";
  detailSubtaskDraftOpen.value = true;
  detailSubtaskOpenAfterCreate.value = Boolean(options.openDetail);
  void nextTick(() => detailSubtaskInput.value?.focus());
}
function toggleDetailFields() {
  if (detailFieldsOpen.value) { detailFieldsOpen.value = false; return; }
  const task = selectedTask.value;
  if (!task) return;
  Object.assign(detailFieldsDraft, { listId: task.listId || store.inbox?.publicId || "", parentId: task.parentId || "", startAt: toLocal(task.startAt), durationMinutes: Number(task.durationMinutes) || 0, allDay: Boolean(task.allDay) });
  detailFieldsOpen.value = true;
}
async function saveDetailFields() {
  const task = selectedTask.value;
  if (!task || detailFieldsSaving.value) return;
  detailFieldsSaving.value = true;
  try {
    selectedTask.value = await store.update(task, { listId: detailFieldsDraft.listId, parentId: detailFieldsDraft.parentId || null, startAt: detailFieldsDraft.startAt ? new Date(detailFieldsDraft.startAt).toISOString() : "", durationMinutes: Number(detailFieldsDraft.durationMinutes) || 0, allDay: Boolean(detailFieldsDraft.allDay) });
    detailFieldsOpen.value = false;
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "任务字段保存失败"; }
  finally { detailFieldsSaving.value = false; }
}
function cancelDetailSubtask() { detailSubtaskDraftOpen.value = false; detailSubtaskDraft.value = ""; }
async function saveDetailSubtask() {
  const parent = selectedTask.value;
  const title = detailSubtaskDraft.value.trim();
  if (!parent || !title || detailSubtaskSaving.value) return;
  detailSubtaskSaving.value = true;
  try {
    const created = await store.create({ title, listId: parent.listId || store.inbox?.publicId || "", parentId: parent.publicId });
    detailSubtaskDraftOpen.value = false;
    detailSubtaskDraft.value = "";
    message.success("子任务已添加");
    // 从中间栏右键「添加子任务」进入时，创建后直接打开该子任务的详情
    if (detailSubtaskOpenAfterCreate.value && created?.publicId) {
      detailSubtaskOpenAfterCreate.value = false;
      const fresh = store.tasks.find(item => item.publicId === created.publicId) || created;
      openDetail(fresh);
    }
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "子任务添加失败"; }
  finally { detailSubtaskSaving.value = false; }
}
function closeDetail() {
  if (selectedTask.value && inlineDescriptionDraft.value !== (selectedTask.value.description || "")) {
    void saveInlineDescription();
  }
  reminderLoadSequence += 1;
  listActivityOpen.value = false;
  listActivityRequest += 1;
  listActivityLoading.value = false;
  selectedTask.value = null;
  boardDetailAnchor.value = null;
  detailParentPickerOpen.value = false;
  editing.value = null;
  editorOpen.value = false;
  inlineTitleEditing.value = false;
  detailChecklistEditingIndex.value = -1;
  detailSecondaryOpen.value = false;
  commentComposerOpen.value = false;
  commentDraft.value = "";
  resetCollaboration();
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
}
// 详情栏/工具栏的浮层互斥：日期、重复、提醒、优先级、插入菜单、父任务选择、页面菜单、清单选项
useExclusivePanels("workspace", [
  ["date", () => datePopoverOpen.value, value => (datePopoverOpen.value = value)],
  ["repeat", () => repeatPopoverOpen.value, value => (repeatPopoverOpen.value = value)],
  ["reminder", () => reminderPopoverOpen.value, value => (reminderPopoverOpen.value = value)],
  ["priority", () => priorityPopoverOpen.value, value => (priorityPopoverOpen.value = value)],
  ["detailBlock", () => detailBlockMenuOpen.value, value => (detailBlockMenuOpen.value = value)],
  ["detailParent", () => detailParentPickerOpen.value, value => (detailParentPickerOpen.value = value)],
  ["pageMore", () => moreOpen.value, value => (moreOpen.value = value)],
  ["listOptions", () => listOptionsOpen.value, value => (listOptionsOpen.value = value)]
]);

function closeAttributePopovers() {
  datePopoverOpen.value = false;
  repeatPopoverOpen.value = false;
  reminderPopoverOpen.value = false;
  priorityPopoverOpen.value = false;
}
function closeAttributePopoversOnOutsidePointer(event) {
  if (event.target.closest?.(".clone-toolbar-popover, .clone-detail-tool[aria-label='设置日期'], .clone-detail-repeat-tool, .clone-detail-priority-tool")) return;
  closeAttributePopovers();
}
function toLocal(value) {
  if (!value) return "";
  const date = new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
function toggleQuickAction(action, anchorEl = null) {
  quickFlyoutAnchor.value = anchorEl;
  const nextAction = quickAction.value === action ? "" : action;
  quickAction.value = nextAction;
  if (nextAction !== "date" && nextAction !== "repeat") {
    stopQuickPanelObserver();
    return;
  }
  if (nextAction === "date") {
    const selected = quickEntry.dueAt ? new Date(quickEntry.dueAt) : new Date();
    quickSelectedDate.value = quickEntry.dueAt ? localDateKey(selected)
      : quickEntry.dateSelected ? "" : quickDateContext.value ? localDateKey(quickDateContext.value) : quickAddSettings.value.defaultDueDate === "TODAY" ? localDateKey(selected) : "";
    quickCalendarMonth.value = new Date(selected.getFullYear(), selected.getMonth(), 1);
    quickDateTime.value = quickEntry.dueAt && !quickEntry.allDay ? toLocal(quickEntry.dueAt).slice(11, 16) : "";
    quickRangeStart.value = quickEntry.startAt ? localDateKey(new Date(quickEntry.startAt)) : "";
    quickRangeEnd.value = quickEntry.startAt && quickEntry.dueAt ? localDateKey(new Date(quickEntry.dueAt)) : "";
    if (quickEntry.startAt) {
      quickDateMode.value = "RANGE";
      quickRangeStartTime.value = toLocal(quickEntry.startAt).slice(11, 16);
      quickRangeEndTime.value = toLocal(quickEntry.dueAt).slice(11, 16);
    }
  }
  nextTick(positionQuickPanel);
}

function openQuickAttachmentPicker() {
  quickAction.value = "";
  quickAttachmentInput.value?.click();
}

function selectQuickPriority(value) {
  quickEntry.priority = value;
  quickEntry.prioritySelected = true;
  quickAction.value = "";
}
function selectQuickList(publicId) {
  quickEntry.listId = publicId;
  quickEntry.listSelected = true;
  quickListQuery.value = "";
  quickAction.value = "";
}
function toggleQuickTagsDraft(publicId) {
  const list = quickTagsDraft.value;
  const index = list.indexOf(publicId);
  if (index >= 0) list.splice(index, 1);
  else list.push(publicId);
}
function cancelQuickTags() {
  quickTagsDraft.value = [];
  quickTagsQuery.value = "";
  quickAction.value = "";
}
function applyQuickTags() {
  quickEntry.tagIds = [...quickTagsDraft.value];
  quickTagsDraft.value = [];
  quickTagsQuery.value = "";
  quickAction.value = "";
}
let quickBarObserver = null;
const quickFlyoutAnchor = ref(null);
const quickDateToggleRef = ref(null);
const quickPanelMenuRef = ref(null);
const quickPanelStyle = ref({});
let quickPanelObserver = null;
function quickFlyoutToggleFallback(name) {
  if (name === "priority") return quickPriorityToggleRef.value;
  if (name === "list") return quickListToggleRef.value;
  if (name === "more" || name === "tags") return quickMoreToggleRef.value;
  return null;
}
function positionQuickPanel() {
  quickPanelObserver?.disconnect();
  quickPanelObserver = null;
  const menu = quickPanelMenuRef.value;
  if (!menu) return;
  const toggle = quickFlyoutAnchor.value || quickDateToggleRef.value;
  quickPanelStyle.value = quickFlyoutStyle(toggle, menu, 378);
  quickPanelObserver = new ResizeObserver(() => {
    quickPanelStyle.value = quickFlyoutStyle(quickFlyoutAnchor.value || quickDateToggleRef.value, quickPanelMenuRef.value, 378);
  });
  quickPanelObserver.observe(menu);
}
function stopQuickPanelObserver() {
  quickPanelObserver?.disconnect();
  quickPanelObserver = null;
}
function quickBarMenuRefs(name) {
  const toggle = quickFlyoutAnchor.value || quickFlyoutToggleFallback(name);
  if (name === "priority") return { toggle, menu: quickPriorityMenuRef.value, style: quickPriorityMenuStyle };
  if (name === "list") return { toggle, menu: quickListMenuRef.value, style: quickListMenuStyle };
  if (name === "more") return { toggle, menu: quickMoreMenuRef.value, style: quickMoreMenuStyle };
  if (name === "tags") return { toggle, menu: quickTagsMenuRef.value, style: quickTagsMenuStyle };
  return null;
}
function applyQuickBarPosition(name) {
  const target = quickBarMenuRefs(name);
  if (!target) return;
  target.style.value = quickFlyoutStyle(target.toggle, target.menu, name === "list" ? 264 : name === "tags" ? 300 : 224);
}
function positionQuickBarFlyout(name) {
  quickBarObserver?.disconnect();
  quickBarObserver = null;
  const menu = quickBarMenuRefs(name)?.menu;
  if (!menu) return;
  quickBarObserver = new ResizeObserver(() => applyQuickBarPosition(name));
  quickBarObserver.observe(menu);
  applyQuickBarPosition(name);
}
function stopQuickBarObserver() {
  quickBarObserver?.disconnect();
  quickBarObserver = null;
}

function updateQuickSuggestion(event) {
  quickSuggestion.value = quickAddTokenAtCaret(quickTitle.value, event.target.selectionStart);
  quickSuggestionIndex.value = 0;
}
function handleQuickSuggestionKeydown(event) {
  if (!quickSuggestion.value || !quickSuggestionOptions.value.length) return;
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    const delta = event.key === "ArrowDown" ? 1 : -1;
    quickSuggestionIndex.value = (quickSuggestionIndex.value + delta + quickSuggestionOptions.value.length) % quickSuggestionOptions.value.length;
  } else if (event.key === "Enter") {
    event.preventDefault();
    selectQuickSuggestion(quickSuggestionOptions.value[quickSuggestionIndex.value]);
  } else if (event.key === "Escape") {
    event.preventDefault();
    quickSuggestion.value = null;
  }
}
function selectQuickSuggestion(option) {
  const token = quickSuggestion.value;
  if (!token || !option) return;
  if (token.kind === "priority") {
    quickEntry.priority = option.value;
    quickEntry.prioritySelected = true;
  } else if (token.kind === "tag") {
    if (!quickEntry.tagIds.includes(option.value)) quickEntry.tagIds.push(option.value);
  } else if (token.kind === "list") {
    quickEntry.listId = option.value;
    quickEntry.listSelected = true;
  }
  quickTitle.value = replaceQuickAddToken(quickTitle.value, token);
  quickSuggestion.value = null;
  nextTick(() => quickTitleInput.value?.focus());
}
function convertQuickAddToNote() {
  const noteList = noteLists.value[0];
  if (!noteList) return message.warning("请先创建笔记清单，再转换为笔记");
  quickEntry.listId = noteList.publicId;
  quickEntry.listSelected = true;
  quickEntry.priority = "NONE";
  quickEntry.prioritySelected = true;
  Object.assign(quickRepeat, createQuickRepeatState());
  quickRepeatError.value = "";
  quickAction.value = "";
  message.success(`新内容将添加至笔记清单“${noteList.name}”`);
}
function openTaskTemplates() {
  quickAction.value = "";
  taskTemplateQuery.value = "";
  taskTemplatesOpen.value = true;
}
function applyQuickTaskTemplate(template) {
  quickTemplateDraft.value = {
    description: template.description || "",
    checklist: template.checklist.map((title, sortOrder) => ({ title, completed: false, sortOrder })),
  };
  const titleText = template.templateName || template.title;
  if (quickFlyoutAnchor.value) {
    boardQuickAddTitle.value = titleText;
  } else {
    quickTitle.value = titleText;
    quickAddExpanded.value = true;
    nextTick(() => quickTitleInput.value?.focus());
  }
  taskTemplatesOpen.value = false;
}

function selectQuickAttachments(event) {
  const files = Array.from(event.target.files || []);
  event.target.value = "";
  const { accepted, rejected } = validateQuickAttachmentFiles(files);
  quickAttachments.value.push(...accepted.map(file => ({ id: crypto.randomUUID(), file, name: file.name, size: file.size })));
  quickAttachmentSelectionError.value = rejected.map(item => `${item.file.name}：${item.reason}`).join("；");
}

function removeQuickAttachment(id) {
  quickAttachments.value = quickAttachments.value.filter(item => item.id !== id);
  if (!quickAttachments.value.length) quickAttachmentSelectionError.value = "";
}

function removePendingQuickAttachment(batchId, attachmentId) {
  pendingQuickAttachmentBatches.value = pendingQuickAttachmentBatches.value
    .map(batch => batch.id === batchId ? { ...batch, attachments: batch.attachments.filter(item => item.id !== attachmentId) } : batch)
    .filter(batch => batch.attachments.length);
  if (!pendingQuickAttachmentBatches.value.length) quickAttachmentError.value = "";
}

async function retryQuickAttachments() {
  if (!pendingQuickAttachmentBatches.value.length || quickAttachmentUploading.value) return;
  if (!store.online) {
    quickAttachmentError.value = "当前离线，附件待联网后重试";
    return;
  }
  quickAttachmentUploading.value = true;
  quickAttachmentError.value = "";
  quickAttachmentProgress.value = 0;
  try {
    for (const batch of [...pendingQuickAttachmentBatches.value]) {
      await store.syncNow();
      const syncedTask = store.tasks.find(item => item.publicId === batch.taskId);
      if (!syncedTask || Number(syncedTask.revision || 0) <= 0) throw new Error("任务尚未同步到服务端，请稍后重试");
      const total = batch.attachments.length;
      const result = await uploadQuickAttachments(batch.taskId, batch.attachments, apiUploadTaskAttachment, (index, percent) => {
        quickAttachmentProgress.value = Math.round(((index + percent / 100) / total) * 100);
      });
      if (result.remaining.length) {
        pendingQuickAttachmentBatches.value = pendingQuickAttachmentBatches.value.map(value => value.id === batch.id ? { ...value, attachments: result.remaining } : value);
      } else {
        pendingQuickAttachmentBatches.value = pendingQuickAttachmentBatches.value.filter(value => value.id !== batch.id);
      }
      if (result.error) throw result.error;
    }
    quickAttachmentError.value = "";
  } catch (error) {
    quickAttachmentError.value = error?.response?.data?.detail || error?.message || "附件上传失败";
  } finally {
    quickAttachmentUploading.value = false;
    if (!pendingQuickAttachmentBatches.value.length) quickAttachmentProgress.value = 0;
  }
}

function moveQuickMonth(offset) {
  quickCalendarMonth.value = new Date(quickCalendarMonth.value.getFullYear(), quickCalendarMonth.value.getMonth() + offset, 1);
}

function isQuickDateSelected(key) {
  if (quickDateMode.value === "DATE") return key === quickSelectedDate.value;
  return key === quickRangeStart.value || key === quickRangeEnd.value;
}

function isQuickRangeEndpoint(key) {
  return quickDateMode.value === "RANGE" && Boolean(quickRangeStart.value && quickRangeEnd.value && key >= quickRangeStart.value && key <= quickRangeEnd.value);
}

function selectQuickCalendarDate(key) {
  if (quickDateMode.value === "DATE") {
    quickSelectedDate.value = key;
    return;
  }
  if (!quickRangeStart.value || quickRangeEnd.value) {
    quickRangeStart.value = key;
    quickRangeEnd.value = "";
    return;
  }
  if (key < quickRangeStart.value) {
    quickRangeStart.value = key;
    quickRangeEnd.value = "";
  } else quickRangeEnd.value = key;
}

function chooseQuickDatePreset(preset) {
  const now = new Date();
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (preset === "TOMORROW") date.setDate(date.getDate() + 1);
  if (preset === "NEXT_WEEK") {
    const daysUntilNextMonday = ((8 - date.getDay()) % 7) || 7;
    date.setDate(date.getDate() + daysUntilNextMonday);
  }
  if (preset === "NEXT_MONTH") date.setMonth(date.getMonth() + 1, 1);
  const key = localDateKey(date);
  quickCalendarMonth.value = new Date(date.getFullYear(), date.getMonth(), 1);
  if (quickDateMode.value === "RANGE") {
    quickRangeStart.value = key;
    quickRangeEnd.value = "";
  } else quickSelectedDate.value = key;
}

function setQuickAllDay(allDay) {
  quickEntry.allDay = allDay;
  if (allDay) {
    quickAddReminder.unit = "DAY";
  }
  if (quickAddReminder.presets.length) {
    const portableCustoms = quickAddReminder.presets.filter(value => /^CUSTOM:\d+:(DAY|WEEK)$/.test(value));
    quickAddReminder.presets = [allDay ? "TODAY" : "30_MIN", ...portableCustoms];
  }
}

function setQuickDateMode(mode) {
  quickDateMode.value = mode;
  if (mode === "RANGE") setQuickAllDay(false);
}

function selectQuickTime(time) {
  quickEntry.allDay = false;
  quickDateTime.value = time;
  applyQuickDateTime();
  quickDateMenu.value = "";
  stopQuickFlyoutObserver();
}

function clearQuickTime() {
  quickDateTime.value = "";
  quickEntry.allDay = true;
  if (quickEntry.dueAt) {
    const date = new Date(quickEntry.dueAt);
    date.setHours(0, 0, 0, 0);
    quickEntry.dueAt = toLocal(date);
  }
}

function applyQuickDateTime() {
  const key = quickSelectedDate.value || (quickEntry.dueAt ? localDateKey(new Date(quickEntry.dueAt)) : localDateKey(new Date()));
  const time = quickEntry.allDay ? "00:00" : quickDateTime.value;
  if (!time) quickEntry.allDay = true;
  quickEntry.dueAt = `${key}T${time || "00:00"}`;
  quickEntry.dateSelected = true;
}

function defaultQuickTime() {
  return configuredQuickTime() || "00:00";
}

function applyQuickDate() {
  if (quickDateMode.value === "RANGE") {
    const rangeStart = `${quickRangeStart.value}T${quickEntry.allDay ? "00:00" : (quickRangeStartTime.value || "09:00")}`;
    const rangeEnd = `${quickRangeEnd.value}T${quickEntry.allDay ? "00:00" : (quickRangeEndTime.value || "10:00")}`;
    if (!quickRangeStart.value || !quickRangeEnd.value || rangeEnd <= rangeStart) {
      quickParseError.value = "请选择有效的开始和结束日期";
      return;
    }
    quickEntry.startAt = rangeStart;
    quickEntry.dueAt = rangeEnd;
    if (!quickEntry.allDay) quickEntry.allDay = false;
  } else {
    if (!quickSelectedDate.value && !quickEntry.dueAt) {
      quickParseError.value = "请选择日期";
      return;
    }
    applyQuickDateTime();
    quickEntry.startAt = "";
  }
  quickEntry.dateSelected = true;
  quickParseError.value = "";
  quickAction.value = "";
}

function applyQuickRepeat() {
  quickRepeatError.value = quickRepeatValidation(quickRepeat);
  if (quickRepeatError.value) return;
  quickAction.value = "";
}

function setQuickRepeatFrequency(event) {
  quickRepeat.frequency = event.target.value;
  if (quickRepeat.frequency === "YEARLY" && quickRepeat.monthMode === "WORKDAY") quickRepeat.monthMode = "DATE";
}

function cancelQuickRepeat() {
  if (quickRepeatSnapshot.value) Object.assign(quickRepeat, quickRepeatSnapshot.value);
  quickRepeatError.value = "";
  quickAction.value = "";
}

function toggleQuickRepeatWeekday(day) {
  const selected = quickRepeat.weekdays.includes(day) ? quickRepeat.weekdays.filter(item => item !== day) : [...quickRepeat.weekdays, day];
  quickRepeat.weekdays = DAY_CODES.filter(code => selected.includes(code));
}

function clearQuickDate() {
  Object.assign(quickEntry, { dueAt: "", startAt: "", allDay: false, dateSelected: true });
  quickSelectedDate.value = "";
  quickRangeStart.value = "";
  quickRangeEnd.value = "";
  quickAddReminder.enabled = false;
  quickAction.value = "";
}

const QUICK_WEEKDAY_TEXT = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
const QUICK_REPEAT_GROUPS = { WORKDAY: "group-workday", LEGAL_WORKDAY: "group-workday", WEEKEND: "group-holiday", LEGAL_HOLIDAY: "group-holiday", LUNAR_MONTHLY: "group-lunar", LUNAR_YEARLY: "group-lunar" };
const QUICK_REMINDER_RECENT_KEY = "salary-sync.quickReminderRecent";
const quickDateMenu = ref("");
const quickTimeToggleRef = ref(null);
const quickReminderToggleRef = ref(null);
const quickReminderMenuRef = ref(null);
const quickReminderMenuStyle = ref({});
const quickRepeatToggleRef = ref(null);
const quickRepeatMenuRef = ref(null);
const quickRepeatMenuStyle = ref({});
const quickEndToggleRef = ref(null);
const quickEndMenuRef = ref(null);
const quickEndMenuStyle = ref({});
const quickRepeatGroupOpen = ref("");
const quickRepeatSnapshot = ref(null);
const quickEndPanel = ref("");
const quickEndCountDraft = ref(10);
const quickEndCalendarMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
const quickReminderRecent = ref((() => {
  try {
    const raw = JSON.parse(localStorage.getItem(QUICK_REMINDER_RECENT_KEY) || "[]");
    return Array.isArray(raw) ? raw.filter(value => typeof value === "string") : [];
  } catch {
    return [];
  }
})());
function rememberQuickReminderPreset(preset) {
  if (!preset || preset === "CUSTOM") return;
  quickReminderRecent.value = [preset, ...quickReminderRecent.value.filter(value => value !== preset)].slice(0, 5);
  try { localStorage.setItem(QUICK_REMINDER_RECENT_KEY, JSON.stringify(quickReminderRecent.value)); } catch { /* 忽略存储失败 */ }
}
const quickShowEndRow = computed(() => Boolean(buildQuickRepeatRule(quickRepeat)));
const quickEndLabel = computed(() => {
  if (quickRepeat.endMode === "UNTIL" && quickRepeat.until) return `直到 ${quickRepeat.until}`;
  if (quickRepeat.endMode === "COUNT") return `${Math.max(1, Number(quickRepeat.count) || 1)} 次重复`;
  return "永不结束";
});
const quickEndCalendarTitle = computed(() => new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long" }).format(quickEndCalendarMonth.value));
const quickEndCalendarDays = computed(() => calendarMonthDays(quickEndCalendarMonth.value).map(day => ({ ...day, selected: quickRepeat.endMode === "UNTIL" && quickRepeat.until === day.key })));
const quickTimeOptions = computed(() => Array.from({ length: 48 }, (_, index) => `${String(Math.floor(index / 2)).padStart(2, "0")}:${index % 2 ? "30" : "00"}`));
function quickFlyoutStyle(toggle, menu, minWidth = 224) {
  if (!toggle || !menu) return {};
  const rect = toggle.getBoundingClientRect();
  const menuHeight = Math.min(menu.scrollHeight || 240, 320);
  const popover = toggle.closest(".task-date-time-popover");
  const popRect = popover?.getBoundingClientRect();
  const menuWidth = Math.max(rect.width, minWidth);
  const spaceRight = popRect ? window.innerWidth - popRect.right - 12 : 0;
  if (popRect && spaceRight >= menuWidth + 8) {
    const top = Math.max(12, Math.min(rect.top - 6, window.innerHeight - menuHeight - 12));
    return { position: "fixed", top: `${top}px`, left: `${popRect.right + 8}px`, width: `${menuWidth}px` };
  }
  const spaceBelow = window.innerHeight - rect.bottom - 12;
  const top = spaceBelow >= menuHeight ? rect.bottom + 4 : Math.max(12, rect.top - Math.min(menuHeight, spaceBelow) - 4);
  return { position: "fixed", top: `${Math.max(12, Math.min(top, window.innerHeight - menuHeight - 12))}px`, left: `${rect.left}px`, width: `${menuWidth}px` };
}
let quickFlyoutObserver = null;
function quickFlyoutToggleFor(menu) {
  if (quickFlyoutAnchor.value) return quickFlyoutAnchor.value;
  if (menu === "reminder") return quickReminderToggleRef.value;
  if (menu === "repeat") return quickRepeatToggleRef.value;
  if (menu === "end") return quickEndToggleRef.value;
  return null;
}
function quickFlyoutMenuFor(menu) {
  if (menu === "reminder") return quickReminderMenuRef.value;
  if (menu === "repeat") return quickRepeatMenuRef.value;
  if (menu === "end") return quickEndMenuRef.value;
  return null;
}
function applyQuickFlyoutPosition(menu) {
  const style = quickFlyoutStyle(quickFlyoutToggleFor(menu), quickFlyoutMenuFor(menu));
  if (menu === "reminder") quickReminderMenuStyle.value = style;
  else if (menu === "repeat") quickRepeatMenuStyle.value = style;
  else if (menu === "end") quickEndMenuStyle.value = style;
}
function positionQuickDateMenu(menu) {
  quickFlyoutObserver?.disconnect();
  quickFlyoutObserver = null;
  const target = quickFlyoutMenuFor(menu);
  if (!target) return;
  quickFlyoutObserver = new ResizeObserver(() => applyQuickFlyoutPosition(menu));
  quickFlyoutObserver.observe(target);
  applyQuickFlyoutPosition(menu);
}
function stopQuickFlyoutObserver() {
  quickFlyoutObserver?.disconnect();
  quickFlyoutObserver = null;
}
function toggleQuickDateMenu(menu) {
  quickDateMenu.value = quickDateMenu.value === menu ? "" : menu;
  if (!quickDateMenu.value) {
    stopQuickFlyoutObserver();
    return;
  }
  quickEndPanel.value = "";
  if (menu === "reminder" && !quickAddReminder.presets.length) applyQuickReminderDefaults();
  if (menu === "repeat") quickRepeatGroupOpen.value = QUICK_REPEAT_GROUPS[quickRepeat.preset] || "";
  if (menu === "end") {
    const anchor = quickRepeat.until ? new Date(`${quickRepeat.until}T00:00:00`) : quickRepeatAnchorDate.value;
    quickEndCalendarMonth.value = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
    quickEndCountDraft.value = Math.max(1, Number(quickRepeat.count) || 10);
  }
  nextTick(() => positionQuickDateMenu(menu));
}
watch(quickAction, (value) => {
  quickDateMenu.value = "";
  quickEndPanel.value = "";
  stopQuickFlyoutObserver();
  if (["priority", "list", "more", "tags"].includes(value)) {
    if (value === "tags") quickTagsDraft.value = [...quickEntry.tagIds];
    nextTick(() => positionQuickBarFlyout(value));
  } else {
    stopQuickBarObserver();
  }
  if (!["date", "repeat"].includes(value)) stopQuickPanelObserver();
  if (value === "repeat") {
    quickRepeatSnapshot.value = JSON.parse(JSON.stringify(quickRepeat));
    if (!["CUSTOM", "LUNAR_MONTHLY", "LUNAR_YEARLY"].includes(quickRepeat.preset)) quickRepeat.preset = "CUSTOM";
  }
});
function toggleQuickRepeatGroup(key) {
  quickRepeatGroupOpen.value = quickRepeatGroupOpen.value === key ? "" : key;
}
const quickRepeatAnchorDate = computed(() => {
  const key = quickSelectedDate.value || (quickEntry.dueAt ? localDateKey(new Date(quickEntry.dueAt)) : "");
  const parsed = key ? new Date(`${key}T00:00:00`) : new Date();
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed;
});
const quickRepeatMenuOptions = computed(() => {
  const preset = quickRepeat.preset;
  const date = quickRepeatAnchorDate.value;
  const weekdayText = QUICK_WEEKDAY_TEXT[date.getDay()];
  const monthDay = date.getDate();
  const month = date.getMonth() + 1;
  return [
    { key: "none", label: "不重复", kind: "preset", value: "", active: !preset },
    { key: "DAILY", label: "每天", kind: "preset", value: "DAILY", active: preset === "DAILY" },
    { key: "WEEKLY", label: `每周（${weekdayText}）`, kind: "preset", value: "WEEKLY", active: preset === "WEEKLY" },
    { key: "MONTHLY", label: `每月（${monthDay}日）`, kind: "preset", value: "MONTHLY", active: preset === "MONTHLY" },
    { key: "YEARLY", label: `每年（${month}月${monthDay}日）`, kind: "preset", value: "YEARLY", active: preset === "YEARLY" },
    { key: "group-workday", label: "工作日", isGroup: true },
    { key: "WORKDAY", label: "每周工作日（周一至五）", kind: "preset", value: "WORKDAY", active: preset === "WORKDAY", child: true, groupKey: "group-workday" },
    { key: "LEGAL_WORKDAY", label: "法定工作日", kind: "preset", value: "LEGAL_WORKDAY", active: preset === "LEGAL_WORKDAY", child: true, groupKey: "group-workday" },
    { key: "group-holiday", label: "节假日", isGroup: true },
    { key: "WEEKEND", label: "每周末", kind: "preset", value: "WEEKEND", active: preset === "WEEKEND", child: true, groupKey: "group-holiday" },
    { key: "LEGAL_HOLIDAY", label: "法定节假日", kind: "preset", value: "LEGAL_HOLIDAY", active: preset === "LEGAL_HOLIDAY", child: true, groupKey: "group-holiday" },
    { key: "group-lunar", label: "农历重复", isGroup: true },
    { key: "LUNAR_MONTHLY", label: `农历每月（${monthDay}日）`, kind: "preset", value: "LUNAR_MONTHLY", active: preset === "LUNAR_MONTHLY", child: true, groupKey: "group-lunar" },
    { key: "LUNAR_YEARLY", label: `农历每年（${month}月${monthDay}日）`, kind: "preset", value: "LUNAR_YEARLY", active: preset === "LUNAR_YEARLY", child: true, groupKey: "group-lunar" },
    { key: "separator-2", separator: true },
    { key: "CUSTOM", label: "自定义", kind: "custom", active: preset === "CUSTOM" },
  ];
});
function onQuickRepeatMenuSelect(option) {
  if (!option || option.separator || option.isGroup) return;
  if (option.kind === "custom") {
    quickDateMenu.value = "";
    if (!["CUSTOM", "LUNAR_MONTHLY", "LUNAR_YEARLY"].includes(quickRepeat.preset)) quickRepeat.preset = "CUSTOM";
    quickAction.value = "repeat";
    nextTick(positionQuickPanel);
    return;
  }
  const value = option.value;
  if (!value) {
    Object.assign(quickRepeat, createQuickRepeatState());
  } else {
    quickRepeat.preset = value;
    const anchor = quickRepeatAnchorDate.value;
    if (value === "WEEKLY") quickRepeat.weekdays = [DAY_CODES[(anchor.getDay() + 6) % 7]];
    if (value === "MONTHLY") {
      quickRepeat.monthMode = "DATE";
      quickRepeat.monthDays = [anchor.getDate()];
      quickRepeat.lastDay = false;
    }
    if (value === "YEARLY") {
      quickRepeat.monthMode = "DATE";
      quickRepeat.month = anchor.getMonth() + 1;
      quickRepeat.monthDays = [anchor.getDate()];
      quickRepeat.lastDay = false;
    }
    if (value === "LUNAR_MONTHLY") quickRepeat.lunarDay = Math.min(30, anchor.getDate());
    if (value === "LUNAR_YEARLY") {
      quickRepeat.lunarMonth = Math.min(12, anchor.getMonth() + 1);
      quickRepeat.lunarDay = Math.min(30, anchor.getDate());
    }
  }
  quickRepeatError.value = "";
  quickDateMenu.value = "";
}
function toggleQuickRepeatMonthDay(day) {
  const selected = quickRepeat.monthDays.includes(day) ? quickRepeat.monthDays.filter(value => value !== day) : [...quickRepeat.monthDays, day];
  quickRepeat.monthDays = selected.sort((a, b) => a - b);
  quickRepeat.lastDay = false;
}
function toggleDetailRepeatMonthDay(day) {
  const selected = detailRepeat.monthDays.includes(day) ? detailRepeat.monthDays.filter(value => value !== day) : [...detailRepeat.monthDays, day];
  detailRepeat.monthDays = selected.sort((a, b) => a - b);
  detailRepeat.lastDay = false;
}
function selectQuickEndMode(mode) {
  quickRepeat.endMode = mode;
  if (mode === "NEVER") quickRepeat.until = "";
  quickEndPanel.value = "";
  quickDateMenu.value = "";
}
function toggleQuickEndPanel(panel) {
  quickEndPanel.value = quickEndPanel.value === panel ? "" : panel;
}
function moveQuickEndMonth(offset) {
  quickEndCalendarMonth.value = new Date(quickEndCalendarMonth.value.getFullYear(), quickEndCalendarMonth.value.getMonth() + offset, 1);
}
function pickQuickEndDate(key) {
  quickRepeat.endMode = "UNTIL";
  quickRepeat.until = key;
  quickEndPanel.value = "";
  quickDateMenu.value = "";
}
function applyQuickEndCount() {
  const count = Number(quickEndCountDraft.value);
  if (!Number.isInteger(count) || count < 1) return;
  quickRepeat.endMode = "COUNT";
  quickRepeat.count = count;
  quickEndPanel.value = "";
  quickDateMenu.value = "";
}
function cancelQuickEndCount() {
  quickEndCountDraft.value = Math.max(1, Number(quickRepeat.count) || 10);
  quickEndPanel.value = "";
}

function applyQuickReminderDefaults() {
  const minutes = quickAddSettings.value.defaultReminderMinutes;
  const timedPreset = ({ 0: "ON_TIME", 5: "5_MIN", 30: "30_MIN", 60: "1_HOUR", 1440: "1_DAY" })[minutes];
  const dayPreset = ({ 0: "TODAY", 1440: "1_DAY", 2880: "2_DAY", 4320: "3_DAY", 10080: "1_WEEK" })[minutes];
  quickAddReminder.enabled = true;
  quickAddReminder.presets = [quickEntry.allDay ? dayPreset || "TODAY" : timedPreset || "30_MIN"];
  if (!quickEntry.allDay && !timedPreset && minutes != null) {
    quickAddReminder.amount = Math.max(1, minutes);
    quickAddReminder.unit = "MINUTE";
  }
  rememberQuickReminderPreset(quickAddReminder.presets[0]);
}

function selectQuickReminder(preset) {
  const selected = quickAddReminder.presets.includes(preset);
  quickAddReminder.presets = selected
    ? quickAddReminder.presets.filter(value => value !== preset)
    : [...quickAddReminder.presets, preset];
  quickAddReminder.enabled = quickAddReminder.presets.length > 0;
  if (!selected) rememberQuickReminderPreset(preset);
  if (preset === "CUSTOM" && !selected) {
    quickAddReminder.amount = 1;
    quickAddReminder.unit = quickEntry.allDay ? "DAY" : "MINUTE";
  }
}

function applyQuickReminderCustom() {
  const amount = Math.max(1, Math.round(Number(quickAddReminder.amount) || 1));
  quickAddReminder.amount = amount;
  const encoded = `CUSTOM:${amount}:${quickAddReminder.unit}`;
  quickAddReminder.presets = quickAddReminder.presets.map(value => value === "CUSTOM" ? encoded : value);
  if (!quickAddReminder.presets.includes(encoded)) quickAddReminder.presets = [...quickAddReminder.presets, encoded];
  quickAddReminder.enabled = true;
  rememberQuickReminderPreset(encoded);
}

function cancelQuickReminderCustom() {
  quickAddReminder.presets = quickAddReminder.presets.filter(value => value !== "CUSTOM");
  quickAddReminder.enabled = quickAddReminder.presets.length > 0;
}

function clearQuickReminders() {
  quickAddReminder.enabled = false;
  quickAddReminder.presets = [];
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
      description: quickTemplateDraft.value?.description || "",
      checklist: quickTemplateDraft.value?.checklist?.map(item => ({ ...item })) || [],
      rrule: buildQuickRepeatRule(quickRepeat) || parsed.rrule || "",
      repeatState: { ...quickRepeat, weekdays: [...quickRepeat.weekdays] },
      priority: quickEntry.prioritySelected ? quickEntry.priority : parsed.priority || "NONE",
      listId: quickEntry.listSelected ? quickEntry.listId || store.inbox?.publicId || "" : list?.publicId || fallbackListId,
      tagIds: [...new Set([
        ...store.tags.filter((tag) => parsed.tagNames?.some((name) => name.toLocaleLowerCase() === tag.name.toLocaleLowerCase())).map((tag) => tag.publicId),
        ...quickEntry.tagIds,
      ])],
    };
    const fallbackDefaults = quickDefaultTaskFields();
    quickDraftDue.value = quickEntry.dateSelected ? quickEntry.dueAt : parsed.dueAt ? toLocal(parsed.dueAt) : fallbackDefaults.dueAt;
    quickDraftStart.value = quickEntry.dateSelected ? quickEntry.startAt : "";
    quickDraftAllDay.value = quickEntry.dateSelected ? quickEntry.allDay : fallbackDefaults.allDay;
    quickDraftReminder.value = { ...quickAddReminder, presets: [...quickAddReminder.presets] };
    quickAction.value = "";
    await createQuickAddTask();
  } catch (error) {
    quickParseError.value = error?.response?.data?.detail || error?.message || "解析快速添加内容失败";
  } finally {
    quickParsing.value = false;
  }
}
function cancelQuickDraft() {
  quickDraft.value = null;
  quickDraftDue.value = "";
  quickDraftStart.value = "";
  quickDraftAllDay.value = false;
  quickDraftReminder.value = null;
  quickTemplateDraft.value = null;
  taskTemplatesOpen.value = false;
  taskTemplateQuery.value = "";
  quickAction.value = "";
  Object.assign(quickRepeat, createQuickRepeatState());
  quickRepeatError.value = "";
  Object.assign(quickEntry, { dueAt: "", startAt: "", allDay: false, dateSelected: false, priority: "NONE", prioritySelected: false, listId: "", listSelected: false, tagIds: [] });
  Object.assign(quickAddReminder, { enabled: false, presets: [], amount: 1, unit: "DAY", time: "09:00", dailyUntilDone: false });
  quickDateMode.value = "DATE";
  quickSelectedDate.value = "";
  quickRangeStart.value = "";
  quickRangeEnd.value = "";
}
async function createQuickAddTask() {
  if (!quickDraft.value?.title?.trim() || quickSaving.value) return;
  quickSaving.value = true;
  quickParseError.value = "";
  try {
    const dueAt = quickDraftDue.value ? new Date(quickDraftDue.value).toISOString() : "";
    const reminderPayloads = quickReminderPayloads({ dueAt, allDay: quickDraftAllDay.value, config: quickDraftReminder.value });
    if (quickDraftReminder.value?.enabled && !dueAt) throw new Error("提醒需要先设置任务日期");
    const saved = await store.create({
      title: quickDraft.value.title.trim(),
      description: quickDraft.value.description || "",
      listId: quickDraft.value.listId || store.inbox?.publicId || "",
      priority: quickDraft.value.priority || "NONE",
      startAt: quickDraftStart.value ? new Date(quickDraftStart.value).toISOString() : "",
      dueAt,
      durationMinutes: 0,
      allDay: quickDraftAllDay.value,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      rrule: quickDraft.value.rrule || "",
      recurrenceAnchor: quickDraft.value.rrule ? (quickDraft.value.repeatState?.mode === "COMPLETION_DATE" ? "COMPLETION_DATE" : "DUE_DATE") : "",
      tagIds: [...quickDraft.value.tagIds],
      checklist: quickDraft.value.checklist?.map((item, sortOrder) => ({ ...item, sortOrder })) || [],
    });
    if (saved?.publicId && quickAttachments.value.length) {
      pendingQuickAttachmentBatches.value.push({ id: crypto.randomUUID(), taskId: saved.publicId, attachments: quickAttachments.value });
      quickAttachments.value = [];
      quickAttachmentSelectionError.value = "";
      if (store.online) await retryQuickAttachments();
      else quickAttachmentError.value = "当前离线，附件待联网后重试";
    }
    let reminderError = "";
    if (saved?.publicId && reminderPayloads.length) {
      try {
        for (const reminderPayload of reminderPayloads) await apiCreateTaskReminder(saved.publicId, reminderPayload);
      } catch (error) {
        reminderError = error?.response?.data?.detail || error?.message || "提醒保存失败";
      }
    }
    quickTitle.value = "";
    cancelQuickDraft();
    quickAddExpanded.value = false;
    if (reminderError) quickParseError.value = `任务已添加，但${reminderError}；可在任务详情中重试`;
  } catch (error) {
    quickParseError.value = error?.response?.data?.detail || error?.message || "新增任务失败";
  } finally {
    quickSaving.value = false;
  }
}
function expandQuickAdd() {
  quickAddExpanded.value = true;
  applyQuickAddDefaults();
  nextTick(() => quickTitleInput.value?.focus());
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
  const task = editing.value || selectedTask.value;
  if (!task || !commentDraft.value.trim()) return;
  if (!store.online) {
    commentError.value = "评论需要联网保存";
    return;
  }
  commentError.value = "";
  try {
    await apiCreateTaskComment(task.publicId, { bodyMarkdown: commentDraft.value.trim() });
    commentDraft.value = "";
    await Promise.all([loadCollaboration(task), loadActivity(task)]);
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
  if (!file || !(editing.value || selectedTask.value)?.publicId) return;
  pendingAttachment.value = file;
  await uploadAttachmentFile(file);
}
async function uploadAttachmentFile(file) {
  const task = editing.value || selectedTask.value;
  if (!task?.publicId || !file) return;
  if (!store.online) {
    attachmentError.value = "当前离线，附件未上传；联网后可重试";
    return;
  }
  attachmentError.value = "";
  attachmentProgress.value = 1;
  try {
    await apiUploadTaskAttachment(task.publicId, file, (value) => { attachmentProgress.value = value; });
    pendingAttachment.value = null;
    await Promise.all([loadCollaboration(task), loadActivity(task)]);
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
  if (!task) return;
  closeContext();
  closeWorkspaceMenus();
  detailMoreOpen.value = false;
  detailMoreSection.value = "";
  deleteTaskMode.value = "single";
  deleteTaskTargets.value = [task];
  deleteTaskConfirmOpen.value = true;
}
function cancelDeleteTasks() {
  if (deleteTaskBusy.value) return;
  deleteTaskConfirmOpen.value = false;
  deleteTaskTargets.value = [];
}
async function confirmDeleteTasks() {
  const targets = [...deleteTaskTargets.value];
  if (!targets.length || deleteTaskBusy.value) return;
  deleteTaskBusy.value = true;
  try {
    if (deleteTaskMode.value === "batch") {
      await applyBatch("DELETE");
    } else {
      await store.remove(targets[0]);
      if (selectedTask.value?.publicId === targets[0].publicId) closeDetail();
    }
    deleteTaskConfirmOpen.value = false;
    deleteTaskTargets.value = [];
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "任务删除失败";
  } finally {
    deleteTaskBusy.value = false;
  }
}
function toggleSelected(task) {
  selectedIds.has(task.publicId)
    ? selectedIds.delete(task.publicId)
    : selectedIds.add(task.publicId);
  if (!selectedIds.size) resetBatchFields();
}
function resetBatchFields() {
  batchDueAt.value = "";
  batchDueClear.value = false;
  batchPriority.value = "NONE";
  batchListId.value = "";
  batchTagIds.value = [];
}
function clearBatchSelection() {
  selectedIds.clear();
  resetBatchFields();
}
function handleTaskContentClick(task, event) {
  if (event.metaKey || event.ctrlKey) {
    event.preventDefault();
    toggleSelected(task);
    return;
  }
  openTaskRow(task);
}
function handleTaskCheckClick(task, event) {
  if (event.metaKey || event.ctrlKey) {
    event.preventDefault();
    toggleSelected(task);
    return;
  }
  toggleComplete(task);
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
  await applyBatch("COMPLETE");
}
async function batchDelete() {
  if (!selectedTasks.value.length) return;
  deleteTaskMode.value = "batch";
  deleteTaskTargets.value = [...selectedTasks.value];
  deleteTaskConfirmOpen.value = true;
}
async function batchPin() {
  const targets = [...selectedTasks.value];
  if (!targets.length) return;
  try {
    const groups = new Map();
    for (const task of targets) {
      const key = `${task.listId}:${task.status}`;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(task);
    }
    for (const group of groups.values()) {
      for (const task of [...group].reverse()) await store.sortTask(task, 0);
    }
    message.success(`已将 ${targets.length} 项置顶到所属清单`);
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "批量置顶失败";
  }
}
async function batchDuplicate() {
  const targets = [...selectedTasks.value];
  if (!targets.length) return;
  try {
    for (const task of targets) {
      await store.create({ ...task, title: `${task.title} 副本`, parentId: null, status: "OPEN", completedAt: null, abandonedAt: null, deleted: false, publicId: undefined });
    }
    message.success(`已创建 ${targets.length} 项副本`);
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "批量创建副本失败";
  }
}
function openBatchStickyNote() {
  const targets = [...selectedTasks.value];
  if (!targets.length) return;
  const popup = window.open("", "task-batch-note", "popup,width=520,height=680,resizable=yes,scrollbars=yes");
  if (!popup) return message.warning("浏览器阻止了便签窗口，请允许弹窗后重试");
  popup.document.title = `已选 ${targets.length} 项任务 - 便签`;
  const style = popup.document.createElement("style");
  style.textContent = "body{margin:0;padding:24px;background:#f7f7f5;color:#242424;font:14px/1.65 system-ui,sans-serif}main{max-width:720px;margin:0 auto}article{padding:0 0 18px;margin:0 0 18px;border-bottom:1px solid #ddd}h1{margin:0 0 8px;font-size:20px}p{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;color:#555}";
  const main = popup.document.createElement("main");
  for (const task of targets) {
    const article = popup.document.createElement("article");
    const heading = popup.document.createElement("h1");
    heading.textContent = task.title;
    const description = popup.document.createElement("p");
    description.textContent = task.description || "";
    article.append(heading, description);
    main.append(article);
  }
  popup.document.head.append(style);
  popup.document.body.replaceChildren(main);
  popup.focus();
}
async function copyBatchTaskText() {
  const text = selectedTasks.value.map(task => task.title).filter(Boolean).join("\n");
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    message.success(`已复制 ${selectedTasks.value.length} 项任务文本`);
  } catch {
    message.error("复制失败，请检查浏览器剪贴板权限");
  }
}
async function applyBatch(action, values = {}) {
  const targets = selectedTasks.value;
  if (!targets.length) return;
  try {
    await store.batchTasks(targets, action, values);
    if (action === "COMPLETE" || action === "DELETE") clearBatchSelection();
    message.success(action === "COMPLETE" ? `已完成 ${targets.length} 项` : action === "DELETE" ? `已删除 ${targets.length} 项` : "批量修改已应用");
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "批量操作失败";
  }
}
function startTaskDrag(task, event) {
  if (batchMode.value || (sort.value !== "manual" && !["list", "tag", "priority", "time"].includes(groupBy.value))) return;
  dragged.value = task;
  dragOverTask.value = null;
  dragOverGroupKey.value = "";
  dragOverPlacement.value = "before";
  event.dataTransfer?.setData("text/plain", task.publicId);
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
}
function setTaskDragTarget(task, event) {
  dragOverTask.value = task;
  dragOverGroupKey.value = "";
  const bounds = event.currentTarget.getBoundingClientRect();
  dragOverPlacement.value = event.clientY >= bounds.top + bounds.height / 2 ? "after" : "before";
}
function setTaskGroupDragTarget(group) {
  dragOverTask.value = null;
  dragOverGroupKey.value = group.key;
}
function manualTaskSortIndex(groupKey, movingTask, targetTask = null, after = false) {
  const siblings = store.tasks.filter(task => task.listId === movingTask.listId && task.status === movingTask.status && !task.deleted)
    .sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0))
    .filter(task => task.publicId !== movingTask.publicId);
  if (targetTask) {
    const targetIndex = siblings.findIndex(task => task.publicId === targetTask.publicId);
    if (targetIndex >= 0) return targetIndex + (after ? 1 : 0);
  }
  const targetGroup = groups.value.find(group => group.key === groupKey);
  const lastTarget = targetGroup?.tasks?.filter(task => task.publicId !== movingTask.publicId).at(-1);
  const lastIndex = lastTarget ? siblings.findIndex(task => task.publicId === lastTarget.publicId) : -1;
  return lastIndex >= 0 ? lastIndex + 1 : siblings.length;
}
async function dropTaskIntoGroup(group) {
  if (!dragged.value || batchMode.value) return endTaskDrag();
  try {
    const patch = taskGroupDropPatch(dragged.value, groupBy.value, group.key);
    if (patch) {
      const updated = await store.update(dragged.value, patch);
      if (sort.value === "manual") await store.sortTask(updated, manualTaskSortIndex(group.key, updated));
    }
    else if (sort.value === "manual") await store.sortTask(dragged.value, manualTaskSortIndex(group.key, dragged.value));
  } finally {
    endTaskDrag();
  }
}
async function dropTask(target, index = 0, groupKey = "") {
  if (!dragged.value || dragged.value.publicId === target.publicId || batchMode.value) return endTaskDrag();
  try {
    const patch = taskGroupDropPatch(dragged.value, groupBy.value, groupKey);
    if (patch) {
      const updated = await store.update(dragged.value, patch);
      if (sort.value === "manual") await store.sortTask(updated, manualTaskSortIndex(groupKey, updated, target, dragOverPlacement.value === "after"));
    } else {
      // 同组拖动排序：非手动排序下自动切换为手动，让拖动顺序持久生效（对齐滴答）
      if (sort.value !== "manual") sort.value = "manual";
      await store.sortTask(dragged.value, manualTaskSortIndex(groupKey, dragged.value, target, dragOverPlacement.value === "after"));
    }
  } finally {
    endTaskDrag();
  }
}
function endTaskDrag() {
  dragged.value = null;
  dragOverTask.value = null;
  dragOverPlacement.value = "before";
  dragOverGroupKey.value = "";
}
async function createList(groupId = "") {
  openOrganizationDialog("list", null, { groupId });
}
async function createTag() {
  openOrganizationDialog("tag");
}
function previewListItems(groupId) {
  // Keep row geometry fixed during drag; the target marker previews placement without moving hit areas.
  return sidebarLists.value
    .filter(item => (item.groupId || "") === (groupId || "") && !isHiddenObject("list", item) && !isPinnedObject("list", item))
    .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0));
}
function listsInGroup(groupId) { return previewListItems(groupId); }
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
    const copiedList = await store.createList({ name: listCopy.name.trim(), color: source.color || "", icon: source.icon || "", archived: false, sortOrder: store.lists.reduce((max, list) => Math.max(max, Number(list.sortOrder) || 0), 0) + 1024, groupId: source.groupId || "" });
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
      void store.refreshServer();
    } catch (error) { store.error = error?.response?.data?.detail || error?.message || "保存收集箱颜色失败"; }
    return;
  }
  if (!name) return;
  try {
    if (kind === "list") {
      const payload = { name, color: organizationDialog.color, icon: organizationDialog.icon || "", archived: item?.archived || false, sortOrder: item?.sortOrder ?? (store.lists.reduce((max, list) => Math.max(max, Number(list.sortOrder) || 0), 0) + 1024), viewType: organizationDialog.viewType, objectType: organizationDialog.objectType, showInSmartLists: organizationDialog.showInSmartLists };
      let groupId = organizationDialog.groupId;
      if (groupId === "__CREATE_GROUP__") {
        const folderName = organizationDialog.newGroupName.trim();
        if (!folderName) return;
        const created = await apiCreateTaskListGroup({ name: folderName, color: organizationDialog.newGroupColor, icon: organizationDialog.newGroupIcon, sortOrder: store.listGroups.length, pinned: false });
        groupId = created?.publicId || store.listGroups.find(group => group.name === folderName)?.publicId || "";
        if (!groupId) throw new Error("文件夹已创建，请刷新后重新选择");
        organizationDialog.groupId = groupId;
      }
      const saved = item ? await store.updateList(item, payload) : await store.createList({ ...payload, groupId });
      const target = store.lists.find(value => value.publicId === (item?.publicId || saved?.id || saved?.publicId));
      if (item && target && (target.groupId || "") !== (groupId || "")) {
        await store.syncNow();
        if (store.conflicts.length || store.rejected.length || store.syncError) throw new Error("清单编辑尚未同步成功，请同步后再修改文件夹");
        await store.assignListGroup(target, groupId);
      }
    } else if (kind === "tag") {
      const payload = { name, color: organizationDialog.color, icon: organizationDialog.icon || null, parentId: organizationDialog.parentId || null, sortOrder: item?.sortOrder ?? store.tags.length };
      if (item) await store.updateTag(item, payload); else await store.createTag(payload);
      void store.refreshServer();
    } else if (kind === "filter") {
      const payload = filterPayload(item, { name, sortOrder: item?.sortOrder ?? filters.value.length }, true);
      if (item) await apiUpdateTaskFilter(item.publicId, payload, item.revision);
      else await apiCreateTaskFilter(payload);
      await loadFilters();
    } else if (kind === "group") {
      if (item) await store.updateListGroup(item, { name, color: organizationDialog.color, icon: organizationDialog.icon || "", sortOrder: item.sortOrder, pinned: organizationDialog.pinned });
      else {
        const group = await apiCreateTaskListGroup({ name, color: organizationDialog.color, icon: organizationDialog.icon || "", sortOrder: store.listGroups.length, pinned: organizationDialog.pinned });
        if (group?.publicId) store.listGroups = [...store.listGroups.filter(value => value.publicId !== group.publicId), group];
        const createdGroup = group?.publicId ? group : store.listGroups.find(value => value.name === name);
        const ids = organizationDialog.groupListIds.length ? organizationDialog.groupListIds : organizationDialog.groupId ? [organizationDialog.groupId] : [];
        if (createdGroup) for (const id of ids) {
          const list = store.lists.find(value => value.publicId === id);
          if (list) await store.assignListGroup(list, createdGroup.publicId);
        }
      }
    }
    organizationDialogOpen.value = false;
    void store.refreshServer();
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
    } else if (action === "delete-list") {
      await store.deleteList(item);
    } else if (action === "dissolve-group") {
      const members = activeLists.value.filter(list => list.groupId === item.publicId);
      for (const list of members) await store.assignListGroup(list, "");
      await store.deleteListGroup(item);
      if (route.name === "tasks-group" && route.params.publicId === item.publicId) await router.push("/tasks/all");
    }
    organizationConfirmOpen.value = false;
  } catch (error) {
    store.error = error?.response?.data?.detail || error?.message || "操作失败";
    void store.refreshServer();
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
function startOrganizationDrag(kind, item, event) {
  endOrganizationDrag();
  organizationDrag.value = { kind, item: { ...item } };
  if (event?.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", `${kind}:${item.publicId}`);
  }
}
function setOrganizationDropPosition(targetKey, event) {
  const rect = event.currentTarget.getBoundingClientRect();
  organizationDropTarget.value = targetKey;
  organizationDropPlacement.value = event.clientY < rect.top + rect.height / 2 ? "before" : "after";
}
function isDropBefore(targetKey) { return organizationDropTarget.value === targetKey && organizationDropPlacement.value === "before"; }
function isDropAfter(targetKey) { return organizationDropTarget.value === targetKey && organizationDropPlacement.value === "after"; }
function handleSortableDragOver(targetKey, event) { setOrganizationDropPosition(targetKey, event); }
function handleGroupDragOver(group, event) {
  if (event.target.closest(".clone-sortable-item")) return;
  if (organizationDrag.value?.kind === "list") {
    organizationDropTarget.value = `group:${group.publicId}`;
    organizationDropPlacement.value = "merge";
    event.currentTarget.classList.add("drag-over");
    event.currentTarget.classList.remove("drop-before", "drop-after");
  } else {
    setOrganizationDropPosition(`group:${group.publicId}`, event);
    event.currentTarget.classList.remove("drag-over", "drop-before", "drop-after");
    event.currentTarget.classList.add(`drop-${organizationDropPlacement.value}`);
  }
}
function handleListRootDragOver(event) {
  if (organizationDrag.value?.kind !== "list" || event.target.closest(".clone-sortable-item, .clone-sortable-group")) return;
  organizationDropTarget.value = "list-root";
  organizationDropPlacement.value = "after";
  clearDropMarker();
}
function handleListDragOver(target, event) {
  const source = organizationDrag.value;
  if (!source || source.kind !== "list" || source.item.publicId === target.publicId) return;
  const placement = getListDropPlacement(event.clientY, event.currentTarget.getBoundingClientRect());
  organizationDropTarget.value = placement === "merge" ? `new-group:${target.publicId}` : target.publicId;
  organizationDropPlacement.value = placement;
  updateDropMarker(event.currentTarget, placement);
}
let organizationDropMarkerElement = null;
function updateDropMarker(element, placement) {
  if (organizationDropMarkerElement && organizationDropMarkerElement !== element) {
    organizationDropMarkerElement.classList.remove("drop-before", "drop-after", "drop-merge");
  }
  organizationDropMarkerElement = element;
  element.classList.remove("drop-before", "drop-after", "drop-merge");
  element.classList.add(placement === "merge" ? "drop-merge" : `drop-${placement}`);
}
function clearDropMarker() {
  organizationDropMarkerElement?.classList.remove("drop-before", "drop-after", "drop-merge");
  organizationDropMarkerElement = null;
}
function handleOrganizationDragOver(event) {
  if (!organizationDrag.value) return;
  if (organizationDrag.value.kind === "list") return;
  const row = event.target.closest(".clone-sortable-item");
  if (row) {
    const href = row.getAttribute("href") || "";
    const id = href.split("?")[0].split("/").filter(Boolean).at(-1);
    if (!id) return;
    const rect = row.getBoundingClientRect();
    const offset = (event.clientY - rect.top) / Math.max(rect.height, 1);
    if (organizationDrag.value.kind === "tag" && id !== organizationDrag.value.item.publicId && offset >= 0.3 && offset <= 0.7) {
      organizationDropTarget.value = `tag-merge:${id}`;
      organizationDropPlacement.value = "merge";
      updateDropMarker(row, "merge");
      return;
    }
    const edge = offset < 0.5 ? "before" : "after";
    organizationDropTarget.value = id;
    organizationDropPlacement.value = edge;
    updateDropMarker(row, edge);
    return;
  }
  const groupRow = event.target.closest(".clone-sortable-group");
  if (!groupRow) { clearDropMarker(); return; }
  clearDropMarker();
  const href = groupRow.querySelector(".clone-group-navigation")?.getAttribute("href") || "";
  const id = href.split("?")[0].split("/").filter(Boolean).at(-1);
  if (!id) return;
  if (organizationDrag.value.kind === "list") {
    organizationDropTarget.value = `group:${id}`;
    organizationDropPlacement.value = "merge";
    groupRow.classList.add("drag-over");
    groupRow.classList.remove("drop-before", "drop-after");
    return;
  }
  const heading = groupRow.querySelector(".clone-list-group-heading") || groupRow;
  const rect = heading.getBoundingClientRect();
  const edge = event.clientY < rect.top + rect.height / 2 ? "before" : "after";
  organizationDropTarget.value = `group:${id}`;
  organizationDropPlacement.value = edge;
  groupRow.classList.remove("drag-over");
  groupRow.classList.add(`drop-${edge}`);
}
function handleListDragLeave(target) {
  // 子节点之间的 dragleave 也会冒泡；落点只由下一次 dragover 或 dragend 改变。
  // 保留最后一个有效落点，避免释放鼠标时先清空 placement 而丢失排序意图。
}
function dropListOnList(target) {
  if (organizationDropTarget.value === `new-group:${target.publicId}` && organizationDrag.value?.kind === "list") {
    const source = organizationDrag.value.item;
    endOrganizationDrag();
    openOrganizationDialog("group", null, { groupListIds: [source.publicId, target.publicId], name: `${source.name}、${target.name}` });
    return;
  }
  dropOrganization("list", target);
}
function handleSidebarDrop() {
  const source = organizationDrag.value;
  const targetKey = organizationDropTarget.value;
  if (!source || !targetKey) return endOrganizationDrag();
  if (source.kind === "tag" && targetKey.startsWith("tag-merge:")) {
    const target = store.tags.find(item => item.publicId === targetKey.slice("tag-merge:".length));
    return target ? mergeTagPair(source.item, target) : endOrganizationDrag();
  }
  if (source.kind === "list") {
    if (targetKey === "list-root") return dropOrganizationRoot("list");
    if (targetKey.startsWith("group:")) {
      const group = sortedListGroups.value.find(item => item.publicId === targetKey.slice(6));
      return group ? dropGroup(group) : endOrganizationDrag();
    }
    const listId = targetKey.startsWith("new-group:") ? targetKey.slice(10) : targetKey;
    const target = sidebarLists.value.find(item => item.publicId === listId);
    return target ? dropListOnList(target) : endOrganizationDrag();
  }
  if (source.kind === "group") {
    const groupId = targetKey.startsWith("group:") ? targetKey.slice(6) : targetKey;
    const group = sortedListGroups.value.find(item => item.publicId === groupId);
    return group ? dropGroup(group) : endOrganizationDrag();
  }
  const collection = source.kind === "tag" ? store.tags : filters.value;
  const target = collection.find(item => item.publicId === targetKey);
  return target ? dropOrganization(source.kind, target) : endOrganizationDrag();
}
async function dropOrganization(kind, target) {
  const source = organizationDrag.value;
  if (!source || source.kind !== kind || source.item.publicId === target.publicId) return endOrganizationDrag();
  const placement = organizationDropPlacement.value;
  if (kind === "tag" && placement === "merge") return mergeTagPair(source.item, target);
  endOrganizationDrag();
  try {
    if (kind === "list") {
      await commitListDrop(source.item, target.groupId || "", target.publicId, placement);
    } else {
      const collection = kind === "tag" ? store.tags.filter(value => (value.parentId || "") === (target.parentId || "")) : filters.value;
      const ordered = moveItem(collection, source.item.publicId, target.publicId, placement);
      if (kind === "tag") for (const value of ordered) await apiUpdateTaskTag(value.publicId, { name: value.name, color: value.color || "", parentId: value.parentId || "", sortOrder: value.sortOrder }, value.revision);
      else for (const value of ordered) await apiUpdateTaskFilter(value.publicId, { ...value, sortOrder: value.sortOrder }, value.revision);
      await store.refreshServer();
      await loadFilters();
    }
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "排序保存失败"; }
}
async function mergeTagPair(source, target) {
  endOrganizationDrag();
  if (!store.online) { store.error = "合并标签需要联网执行"; return; }
  if (source.publicId === target.publicId) return;
  tagMerge.source = source;
  if (!mergeTargets.value.some(item => item.publicId === target.publicId)) {
    store.error = "该标签不能作为合并目标";
    return;
  }
  try {
    await store.syncNow();
    const currentSource = store.tags.find(item => item.publicId === source.publicId) || source;
    const currentTarget = store.tags.find(item => item.publicId === target.publicId) || target;
    const result = await apiMergeTaskTag(currentSource.publicId, currentTarget.publicId, currentTarget.revision, currentSource.revision);
    await store.refreshServer();
    message.success(`已合并标签，迁移 ${result.movedTasks} 条任务和 ${result.movedChildren} 个子标签`);
  } catch (error) {
    store.error = `${error?.response?.data?.detail || error?.message || "合并失败"}；源标签已保留，可刷新后重试`;
  }
}
async function commitListDrop(source, targetGroupId, targetId = "", placement = "after") {
  if (targetId === source.publicId) return;
  const sourceGroupId = source.groupId || "";
  const allLists = sidebarLists.value.slice().sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0));
  const destinationLists = allLists.filter(item => (item.groupId || "") === (targetGroupId || "") && item.publicId !== source.publicId);
  const orderedDestination = reorderListDrop(destinationLists, { ...source, groupId: targetGroupId || null }, targetId, placement);
  const assignment = sourceGroupId !== (targetGroupId || "")
    ? { listId: source.publicId, groupId: targetGroupId || null }
    : null;
  await store.reorderLists(orderedDestination, assignment);
}
async function dropOrganizationRoot(kind) {
  const source = organizationDrag.value;
  endOrganizationDrag();
  if (kind === "list" && source?.kind === "list") {
    try { await commitListDrop(source.item, "", "", "after"); }
    catch (error) { store.error = error?.message || "移动清单失败"; }
  }
}
async function dropGroup(target) {
  const source = organizationDrag.value;
  if (!source || source.item.publicId === target.publicId) return endOrganizationDrag();
  const placement = organizationDropPlacement.value;
  endOrganizationDrag();
  try {
    if (source.kind === "list") {
      await commitListDrop(source.item, target.publicId, "", "after");
    } else if (source.kind === "group") {
      const ordered = moveItem(sortedListGroups.value, source.item.publicId, target.publicId, placement);
      for (const value of ordered) await apiUpdateTaskListGroup(value.publicId, { name: value.name, sortOrder: value.sortOrder }, value.revision);
      void store.refreshServer();
    }
  } catch (error) { store.error = error?.response?.data?.detail || error?.message || "移动失败"; }
}
function endOrganizationDrag() {
  organizationDrag.value = null;
  organizationDropTarget.value = "";
  organizationDropPlacement.value = "";
  organizationDropMarkerElement?.classList.remove("drop-before", "drop-after", "drop-merge", "drag-over");
  clearDropMarker();
  document.querySelectorAll(".clone-sortable-group.drop-before, .clone-sortable-group.drop-after, .clone-sortable-group.drag-over").forEach(element => element.classList.remove("drop-before", "drop-after", "drag-over"));
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
    if (tagFlyout.value.taskId) {
      hideTagFlyout();
      return;
    }
    if (boardQuickAddOpen.value) {
      closeBoardQuickAdd();
      return;
    }
    if (detailMoreOpen.value) {
      closeDetailMore();
      return;
    }
    if (rowDateTaskId.value) {
      if (rowDateMenu.value || rowTimeListOpen.value || rowRepeatEditorOpen.value) {
        resetRowDateMenu();
        rowRepeatEditorOpen.value = false;
        return;
      }
      closeRowDateEditor();
      return;
    }
    if (quickAction.value) {
      quickAction.value = "";
      return;
    }
    closeWorkspaceMenus();
    closeContext();
    organizationMenuOpen.value = false;
    clearBatchSelection();
    if (window.matchMedia("(max-width: 900px)").matches) sidebarHidden.value = true;
    if (editorOpen.value || (activeWorkspaceView.value !== "LIST" && selectedTask.value)) closeDetail();
  }
  if (
    event.key.toLowerCase() === "n" &&
    !["INPUT", "TEXTAREA", "SELECT"].includes(event.target?.tagName)
  ) {
    event.preventDefault();
    document.querySelector(".clone-quick-add input")?.focus();
  }
}
function closeQuickAction(event) {
  if (quickAction.value && !event.target.closest?.(".clone-quick-add, .clone-quick-action-panel, .clone-quick-flyout")) quickAction.value = "";
}
function closeQuickDateMenuOnOutsidePointer(event) {
  if (!quickDateMenu.value) return;
  if (event.target.closest?.(".clone-quick-flyout, .clone-date-prop-trigger, .clone-date-prop-clear, .clone-date-time-list")) return;
  quickDateMenu.value = "";
  stopQuickFlyoutObserver();
}
// 通过链接直接打开任务：需在首屏任务尚未加载完时也生效（watch tasks 长度），否则复制出的链接点开是空详情
watch(
  () => [route.query.task, store.tasks.length],
  () => {
    const target = route.query.task;
    if (!target) return;
    const task = store.tasks.find(item => item.publicId === target);
    if (task && selectedTask.value?.publicId !== target && !editorOpen.value) openDetail(task);
  },
  { immediate: true },
);
watch(
  () => route.fullPath,
  () => {
    completedLimit.value = 5;
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
onMounted(() => {
  loadSidebarVisibility();
  loadSidebarWidth();
  loadSidebarObjectPrefs();
  loadDetailWidth();
  loadViewPreferences();
  loadSidebarSections();
  loadSectionDisplayModes();
  loadListGroupState();
  const filtersLoad = loadFilters();
  const sidebarPreferencesLoad = loadSidebarPreferences(filtersLoad);
  miniCalendarMonth.value = new Date(selectedDate.value.getFullYear(), selectedDate.value.getMonth(), 1);
  const storeInit = store.init({ waitForRemote: false });
  watch(() => store.accountScope, loadSavedTaskTemplates, { immediate: true });
  const taskSettingsLoad = apiGetTaskSettings().then(settings => { if (settings) quickAddSettings.value = settings; }).catch(() => {});
  const miniCalendarLoad = loadMiniCalendar();
  void Promise.allSettled([storeInit, taskSettingsLoad, filtersLoad, sidebarPreferencesLoad, miniCalendarLoad]).then(results => {
    const failed = results.find(result => result.status === "rejected");
    if (failed) store.error = failed.reason?.message || "任务工作区加载失败";
  });
  document.addEventListener("pointerdown", closeContext);
  document.addEventListener("pointerdown", closeDetailMoreOnOutsidePointer);
  document.addEventListener("pointerdown", closeQuickAction);
  document.addEventListener("pointerdown", closeQuickDateMenuOnOutsidePointer);
  document.addEventListener("pointerdown", closeRowDatePanelOnOutsidePointer);
  document.addEventListener("pointerdown", closeRowDateMenuOnOutsidePointer);
  document.addEventListener("pointerdown", closeBoardQuickAddOnOutsidePointer);
  document.addEventListener("pointerdown", closeWorkspaceMenusOnOutsidePointer);
  document.addEventListener("pointerdown", closeCompletionFilterOnOutsidePointer);
  document.addEventListener("pointerdown", closeAttributePopoversOnOutsidePointer);
  window.addEventListener("resize", refreshAttributePopoverLayout, { passive: true });
  window.addEventListener("scroll", refreshAttributePopoverLayout, { passive: true, capture: true });
  window.addEventListener("scroll", loadMoreCompletedOnScroll, { passive: true });
  window.addEventListener("keydown", onKeydown);
  window.addEventListener("task:manual-sync", handleManualSync);
  window.matchMedia("(max-width: 900px)").addEventListener("change", handleSidebarViewportChange);
});
watch(() => store.online, online => {
  if (online && pendingQuickAttachmentBatches.value.length) void retryQuickAttachments();
});
onBeforeUnmount(() => {
  stopDetailResize();
  stopSidebarResize();
  stopQuickFlyoutObserver();
  stopQuickBarObserver();
  stopQuickPanelObserver();
  if (tagFlyoutHideTimer) {
    clearTimeout(tagFlyoutHideTimer);
    tagFlyoutHideTimer = null;
  }
  document.removeEventListener("pointerdown", closeContext);
  document.removeEventListener("pointerdown", closeDetailMoreOnOutsidePointer);
  document.removeEventListener("pointerdown", closeQuickAction);
  document.removeEventListener("pointerdown", closeQuickDateMenuOnOutsidePointer);
  document.removeEventListener("pointerdown", closeRowDatePanelOnOutsidePointer);
  document.removeEventListener("pointerdown", closeRowDateMenuOnOutsidePointer);
  document.removeEventListener("pointerdown", closeBoardQuickAddOnOutsidePointer);
  document.removeEventListener("pointerdown", closeWorkspaceMenusOnOutsidePointer);
  document.removeEventListener("pointerdown", closeCompletionFilterOnOutsidePointer);
  document.removeEventListener("pointerdown", closeAttributePopoversOnOutsidePointer);
  window.removeEventListener("resize", refreshAttributePopoverLayout);
  window.removeEventListener("scroll", refreshAttributePopoverLayout, true);
  window.removeEventListener("scroll", loadMoreCompletedOnScroll);
  window.removeEventListener("keydown", onKeydown);
  window.removeEventListener("task:manual-sync", handleManualSync);
  if (inlineDescriptionSyncHold) {
    const token = inlineDescriptionSyncHold;
    inlineDescriptionSyncHold = null;
    void saveInlineDescription().catch(() => {}).finally(() => store.releaseSyncHold(token));
  }
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
  grid-template-columns: var(--clone-sidebar-width, 248px) minmax(420px, 1fr) 5px var(--clone-detail-width, 680px);
  height: 100vh;
  margin: 0;
  background: var(--card);
  color: var(--ink);
  overflow: hidden;
  transition: grid-template-columns 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}
.clone-task-shell.workspace-overlay-view {
  grid-template-columns: var(--clone-sidebar-width, 248px) minmax(0, 1fr);
}
.clone-task-shell.is-summary { grid-template-columns: var(--clone-sidebar-width, 248px) minmax(0, 1fr); }
.clone-task-shell.is-summary.workspace-overlay-view { grid-template-columns: var(--clone-sidebar-width, 248px) minmax(0, 1fr); }
.clone-task-shell.is-summary.sidebar-hidden { grid-template-columns: 0 minmax(0, 1fr); }
.clone-task-shell.is-summary.workspace-overlay-view.sidebar-hidden { grid-template-columns: 0 minmax(0, 1fr); }
.clone-task-shell.is-summary > .clone-detail-divider,
.clone-task-shell.is-summary > .clone-detail-pane { display: none !important; }
.clone-task-shell.is-summary > .clone-task-main { min-width: 0; padding: 0; }
.clone-task-shell.workspace-overlay-view > .clone-detail-divider { display: none; }
.clone-task-shell.workspace-overlay-view .clone-detail-pane.is-empty { display: none; }
.clone-task-shell.workspace-overlay-view.workspace-detail-open .clone-detail-pane {
  position: fixed;
  z-index: 301;
  top: 50%;
  right: 24px;
  width: min(480px, calc(100vw - 48px));
  max-height: min(82vh, 720px);
  transform: translateY(-50%);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: 0 18px 52px rgb(15 23 42 / 25%);
}
.clone-workspace-detail-backdrop {
  position: fixed;
  z-index: 300;
  inset: 0;
  border: 0;
  background: transparent;
  cursor: default;
}
.clone-task-shell.sidebar-hidden { grid-template-columns: 0 minmax(420px, 1fr) 5px var(--clone-detail-width, 680px); }
.clone-task-shell.workspace-overlay-view.sidebar-hidden { grid-template-columns: 0 minmax(0, 1fr); }
.clone-task-shell.sidebar-hidden .clone-task-sidebar { align-items: center; gap: 0; padding: 0; border-right: 0; overflow: hidden; pointer-events: none; }
.clone-task-shell.sidebar-hidden .clone-sidebar-head { display: flex; justify-content: center; width: 100%; padding: 0; }
.clone-task-shell.sidebar-hidden .clone-sidebar-head strong { opacity: 0; }
.clone-task-shell.sidebar-hidden .clone-task-sidebar > :not(.clone-sidebar-head) { opacity: 0; transform: translateX(-7px); visibility: hidden; pointer-events: none; transition-delay: 0s, 0s, 0.2s; }
.clone-task-shell.sidebar-hidden .clone-sidebar-head button { width: 34px; height: 34px; }
.clone-task-sidebar {
  position: relative;
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
.clone-sidebar-resize { position: absolute; z-index: 5; top: 0; right: 0; bottom: 0; width: 8px; padding: 0; border: 0; background: transparent; cursor: ew-resize; touch-action: none; }
.clone-sidebar-resize::after { position: absolute; inset: 0 3px; background: transparent; content: ""; transition: background-color 120ms ease; }
.clone-sidebar-resize:hover::after, .clone-sidebar-resize:focus-visible::after { background: color-mix(in srgb, var(--accent) 40%, transparent); }
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
.clone-list-color-dot { flex: 0 0 7px; width: 7px; height: 7px; margin: 0 3px; border: 1px solid color-mix(in srgb, var(--muted) 48%, transparent); border-radius: 50%; }
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
.clone-sortable-group.drag-over:not(.drop-before):not(.drop-after) { outline: 1px dashed var(--accent); outline-offset: 2px; border-radius: 6px; background: color-mix(in srgb, var(--accent) 8%, transparent); }
.clone-sortable-group.drop-before > .clone-list-group-heading::before,
.clone-sortable-group.drop-after > .clone-list-group-heading::before { position: absolute; left: 4px; right: 4px; height: 2px; border-radius: 2px; background: var(--accent); content: ""; pointer-events: none; }
.clone-sortable-group.drop-before > .clone-list-group-heading::before { top: 0; }
.clone-sortable-group.drop-after > .clone-list-group-heading::before { bottom: 0; }
.clone-sortable-group.drop-before > .clone-list-group-heading::after,
.clone-sortable-group.drop-after > .clone-list-group-heading::after { position: absolute; left: 3px; width: 7px; height: 7px; border: 1px solid var(--card); border-radius: 50%; background: var(--accent); content: ""; pointer-events: none; }
.clone-sortable-group.drop-before > .clone-list-group-heading::after { top: -2px; }
.clone-sortable-group.drop-after > .clone-list-group-heading::after { bottom: -2px; }
.clone-sortable-item.sorting-dragging,
.clone-sortable-group.sorting-dragging { opacity: 0.48; transform: scale(0.985); }
.clone-drag-placeholder { min-height: 28px; border: 1px dashed color-mix(in srgb, var(--accent) 62%, var(--line)); background: color-mix(in srgb, var(--accent) 7%, var(--card)); color: var(--muted); pointer-events: none; }
.clone-list-group { display: grid; min-width: 0; gap: 2px; }
.clone-list-group-heading { position: relative; display: flex; align-items: center; gap: 0; min-height: 28px; padding: 0 8px 0 10px; color: var(--muted); font-size: 10px; font-weight: 600; }
.clone-group-navigation > span:not(.clone-group-toggle) { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-list-group-heading button { display: grid; width: 22px; height: 22px; place-items: center; border: 0; border-radius: 0; background: transparent; color: var(--muted); cursor: pointer; }
.clone-list-group-heading button:hover, .clone-item-menu-trigger:hover, .clone-sidebar-object-row > .clone-item-menu-trigger:hover { background: transparent; color: var(--ink); }
.clone-list-group-heading .clone-list-group-icon { display: inline-grid; flex: none; width: 16px; height: 16px; place-items: center; font-size: 14px; line-height: 1; }
.clone-group-toggle { display: inline-grid; flex: 0 0 16px; width: 16px; height: 16px; place-items: center; margin-left: -18px; color: var(--muted); cursor: pointer; }
.clone-group-toggle:hover, .clone-group-toggle:focus-visible { color: var(--ink); outline: none; }
.clone-group-navigation { display: flex; min-width: 0; min-height: 28px; flex: 1; align-items: center; gap: 7px; color: var(--muted); text-decoration: none; }
.clone-group-navigation:hover, .clone-group-navigation.active { color: var(--ink); }
.clone-group-navigation .clone-list-group-icon { margin-left: -5px; color: inherit; }
.clone-group-navigation small { flex: none; margin-left: auto; color: var(--muted); font-size: 10px; }
.clone-group-menu { flex: 0 0 16px; padding: 0; }
.clone-group-lists { padding-left: 16px; }
.clone-sidebar-heading-actions { position: relative; display: flex; flex: none; align-items: center; gap: 2px; white-space: nowrap; opacity: 0; visibility: hidden; transition: opacity 120ms ease, visibility 120ms ease; }
.clone-sidebar-section > header:hover .clone-sidebar-heading-actions,
.clone-sidebar-section > header:focus-within .clone-sidebar-heading-actions { opacity: 1; visibility: visible; }
.clone-section-display-menu { position: absolute; z-index: 90; top: calc(100% + 5px); right: 0; display: grid; min-width: 168px; padding: 5px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 8px 24px rgb(15 23 42 / 16%); }
.clone-section-display-menu button { display: flex; min-height: 34px; align-items: center; justify-content: space-between; gap: 12px; padding: 0 9px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; text-align: left; cursor: pointer; }
.clone-section-display-menu button:hover, .clone-section-display-menu button:focus-visible { background: var(--paper); color: var(--ink); }
.clone-sortable-item { position: relative; }
.clone-sortable-item { isolation: isolate; }
.clone-sortable-item.drag-over:not(.drop-merge) { outline: 0; background: transparent; }
.clone-sortable-item.drop-merge { outline: 1px solid color-mix(in srgb, var(--accent) 42%, var(--line)); background: color-mix(in srgb, var(--accent) 10%, var(--card)); }
.clone-sortable-item.drop-before::before,
.clone-sortable-item.drop-after::before { position: absolute; z-index: 2; left: 5px; right: 5px; height: 2px; border-radius: 2px; background: var(--accent); content: ""; pointer-events: none; }
.clone-sortable-item.drop-before::before { top: 1px; }
.clone-sortable-item.drop-after::before { bottom: 1px; }
.clone-sortable-item.drop-before::after,
.clone-sortable-item.drop-after::after { position: absolute; z-index: 3; left: 4px; width: 6px; height: 6px; border: 1px solid var(--card); border-radius: 50%; background: var(--accent); content: ""; pointer-events: none; }
.clone-sortable-item.drop-before::after { top: -1px; }
.clone-sortable-item.drop-after::after { bottom: -1px; }
.clone-item-menu-trigger { display: grid; flex: none; width: 16px; height: 24px; place-items: center; padding: 0; border: 0; border-radius: 0; background: transparent; color: var(--muted); opacity: 0; cursor: pointer; }
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
.clone-organization-form select { font-size: 12px; }
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
.clone-filter-field { display: grid !important; grid-template-columns: 68px minmax(0, 1fr); align-items: center; gap: 8px !important; min-width: 0; color: var(--ink2) !important; font-size: 12px; }
.clone-filter-field > span { color: var(--ink2); font-size: 12px; }
.clone-filter-field > input,
.clone-filter-field > select { width: 100%; min-width: 0; font-size: 13px !important; }
.clone-filter-select { position: relative; min-width: 0; }
.clone-filter-select > summary { display: flex; align-items: center; justify-content: space-between; min-height: 36px; padding: 6px 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink); font-size: 12px; cursor: pointer; list-style: none; }
.clone-filter-select > summary::-webkit-details-marker { display: none; }
.clone-filter-select[open] > summary { border-color: var(--accent); }
.clone-filter-select-menu { position: absolute; z-index: 25; top: calc(100% + 4px); right: 0; left: 0; display: grid; gap: 2px; max-height: min(250px, 36vh); overflow: auto; padding: 7px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 12px 28px rgb(0 0 0 / 20%); }
.clone-filter-select-menu label { display: flex !important; align-items: center; gap: 8px; min-height: 30px; margin: 0; padding: 3px 5px; border-radius: 4px; color: var(--ink) !important; font-size: 12px; cursor: pointer; }
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
:global(.task-filter-dialog) { width: min(540px, calc(100vw - 32px)); max-width: 540px; overflow: visible; }
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
.clone-sidebar-section > header:focus-within { background: transparent; }
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
.clone-mini-calendar-toggle svg { transform: rotate(180deg); }
.clone-mini-calendar.is-expanded .clone-mini-calendar-toggle svg { transform: rotate(0deg); }
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
  max-width: none;
}
.clone-title-block,
.clone-header-actions {
  display: flex;
  align-items: center;
  gap: 9px;
}
.clone-title-block h1 {
  margin: 0;
  font-size: 20px;
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
.clone-completion-filters { position: relative; z-index: 8; display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 2px 0 18px 43px; }
.clone-completion-filter { position: relative; }
.clone-completion-filter-trigger { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-width: 126px; min-height: 34px; padding: 0 11px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.clone-completion-filter-trigger:hover,
.clone-completion-filter-trigger[aria-expanded="true"] { border-color: var(--accent); color: var(--ink); }
.clone-completion-filter-menu { position: absolute; z-index: 40; top: calc(100% + 6px); left: 0; display: grid; gap: 2px; width: max-content; min-width: 190px; max-width: min(330px, calc(100vw - 32px)); max-height: min(420px, 64vh); overflow: auto; padding: 7px; border: 1px solid var(--line); border-radius: 9px; background: var(--card); box-shadow: 0 14px 36px rgb(0 0 0 / 28%); }
.clone-completion-filter-menu > button { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 36px; padding: 0 10px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); text-align: left; font: inherit; font-size: 13px; cursor: pointer; }
.clone-completion-filter-menu > button:hover,
.clone-completion-filter-menu > button.selected { color: var(--accent); background: var(--accent-soft); }
.clone-completion-custom-range { display: grid; gap: 8px; padding: 10px 7px 6px; border-top: 1px solid var(--line); }
.clone-completion-custom-range label { display: grid; grid-template-columns: 42px minmax(0, 1fr); align-items: center; gap: 8px; color: var(--muted); font-size: 11px; }
.clone-completion-custom-range input { min-width: 0; height: 32px; padding: 0 7px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12px; }
.clone-completion-list-menu { width: 280px; padding: 8px; }
.clone-trash-header-actions { justify-content: flex-end; }
.clone-completion-header-actions { justify-content: flex-end; }
.clone-completion-list-menu > .clone-completion-list-option:first-child { margin: -8px -8px 4px; padding: 0 16px; border-bottom: 1px solid var(--line); border-radius: 0; color: var(--accent); }
.clone-completion-list-option { display: flex; align-items: center; gap: 9px; min-height: 35px; padding: 0 8px; border-radius: 5px; color: var(--ink2); font-size: 13px; cursor: pointer; }
.clone-completion-list-option:hover { background: var(--accent-soft); }
.clone-completion-list-option input { width: 14px; height: 14px; margin: 0; accent-color: var(--accent); }
.clone-completion-folder-option { display: flex; align-items: center; min-height: 35px; }
.clone-completion-folder-toggle { display: grid; width: 24px; height: 28px; flex: none; place-items: center; border: 0; background: transparent; color: var(--muted); cursor: pointer; }
.clone-completion-folder-toggle svg { transition: transform 150ms ease; }
.clone-completion-folder-toggle svg.expanded { transform: rotate(90deg); }
.clone-completion-folder-option .clone-completion-list-option { flex: 1; }
.clone-completion-list-option.is-child { padding-left: 36px; }
.clone-completion-list-dot { width: 8px; height: 8px; flex: none; border: 1px solid var(--line); border-radius: 50%; }
.clone-completion-list-menu footer { display: flex; justify-content: flex-end; gap: 8px; padding: 8px 4px 2px; border-top: 1px solid var(--line); }
.clone-completion-list-menu footer button { min-width: 68px; height: 30px; border: 1px solid var(--line); border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.clone-completion-list-menu footer .clone-completion-confirm { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast, #fff); }
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
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin: 16px 0 12px;
  padding: 8px 14px;
  min-height: 46px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--card);
  color: var(--muted);
  box-shadow: 0 1px 2px rgba(20, 27, 38, 0.035);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.clone-quick-add-anchor { position: relative; z-index: 20; }
.clone-quick-add.is-collapsed { padding: 0; border-color: transparent; background: color-mix(in srgb, var(--ink) 4%, var(--card)); box-shadow: none; }
.clone-quick-add-collapsed { display: flex; width: 100%; min-height: 44px; align-items: center; gap: 9px; padding: 0 14px; border: 0; border-radius: inherit; background: transparent; color: var(--muted); font: inherit; text-align: left; cursor: text; }
.clone-quick-add-collapsed:hover { color: var(--ink2); background: color-mix(in srgb, var(--ink) 3%, transparent); }
.clone-quick-add-collapsed svg { flex: none; }
.clone-quick-add.is-simple form .clone-quick-action:not([aria-label="快速设置日期和提醒"]),
.clone-quick-add.is-simple form .clone-quick-icon-action { display: none; }
.clone-quick-add.is-simple form .clone-quick-action[aria-label="快速设置日期和提醒"] span { display: none; }
.clone-quick-suggestions { position: absolute; z-index: 90; top: calc(100% + 5px); left: 28px; display: grid; gap: 2px; width: min(300px, calc(100vw - 64px)); max-height: min(320px, 48vh); overflow: auto; padding: 5px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 8px 24px rgba(20, 27, 38, .18); }
.clone-quick-suggestions button { display: flex; align-items: center; gap: 9px; min-width: 0; min-height: 36px; padding: 0 9px; border: 0; border-radius: 5px; background: transparent; color: var(--ink); text-align: left; cursor: pointer; }
.clone-quick-suggestions button.active,
.clone-quick-suggestions button:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); }
.clone-quick-suggestions button > span:nth-last-child(2) { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-quick-suggestions small { color: var(--muted); font-size: 10px; }
.clone-quick-suggestion-icon { width: 18px; flex: none; text-align: center; }
.clone-modal-backdrop { position: fixed; z-index: 1000; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(0, 0, 0, .42); }
.clone-display-settings { display: grid; gap: 14px; width: min(430px, 100%); padding: 20px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); color: var(--ink); box-shadow: 0 16px 48px rgba(0, 0, 0, .22); }
.clone-display-settings > header { display: flex; align-items: center; justify-content: space-between; }
.clone-display-settings h2 { margin: 0; font-size: 16px; }
.clone-display-settings > header button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-display-settings > label { display: flex; align-items: center; gap: 9px; min-height: 26px; color: var(--ink2); font-size: 13px; }
.clone-display-settings input { accent-color: var(--accent); }
.clone-display-settings > footer { display: flex; justify-content: flex-end; margin-top: 4px; }
.clone-task-template-dialog { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; gap: 18px; width: min(860px, 100%); max-height: min(570px, calc(100dvh - 40px)); padding: 20px; border: 1px solid var(--line); border-radius: 10px; background: var(--card); color: var(--ink); box-shadow: 0 18px 55px rgba(0, 0, 0, .28); }
.clone-task-template-dialog > header { display: flex; align-items: center; gap: 16px; }
.clone-task-template-dialog > header h2 { flex: 1; margin: 0; font-size: 16px; }
.clone-task-template-dialog > header label { display: flex; width: min(220px, 35vw); align-items: center; gap: 7px; padding: 0 9px; border: 1px solid var(--line); border-radius: 6px; color: var(--muted); }
.clone-task-template-dialog > header label input { width: 100%; min-width: 0; height: 30px; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 12px; }
.clone-task-template-dialog > header > button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-task-template-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-content: start; gap: 10px; overflow: auto; }
.clone-task-template-card { display: flex; min-width: 0; min-height: 182px; flex-direction: column; gap: 10px; overflow: hidden; padding: 13px; border: 1px solid transparent; border-radius: 8px; background: color-mix(in srgb, var(--ink) 4%, var(--card)); color: var(--ink2); text-align: left; cursor: pointer; }
.clone-task-template-card:hover { border-color: color-mix(in srgb, var(--accent) 38%, var(--line)); background: color-mix(in srgb, var(--accent) 7%, var(--card)); }
.clone-task-template-card > strong { color: var(--ink); font-size: 13px; }
.clone-task-template-card ul { display: grid; gap: 5px; overflow: auto; margin: 0; padding: 0; list-style: none; }
.clone-task-template-card li { display: flex; min-width: 0; align-items: flex-start; gap: 6px; font-size: 11px; line-height: 1.45; }
.clone-task-template-card li > span { width: 12px; height: 12px; flex: none; margin-top: 1px; border: 1px solid var(--muted); border-radius: 3px; }
.clone-task-template-card p { margin: 0; color: var(--ink2); font-size: 12px; line-height: 1.65; white-space: pre-line; }
.clone-task-template-empty { display: grid; min-height: 180px; margin: 0; place-items: center; color: var(--muted); font-size: 12px; }
.clone-task-template-dialog > footer { display: flex; justify-content: center; }
.clone-task-template-dialog > footer button { border: 0; background: transparent; color: var(--accent); font-size: 12px; text-decoration: underline; cursor: pointer; }
@media (max-width: 700px) {
  .clone-task-template-backdrop { align-items: end; padding: 0; }
  .clone-task-template-dialog { width: 100%; height: min(78dvh, 620px); max-height: min(78dvh, 620px); gap: 14px; padding: 16px 14px calc(16px + env(safe-area-inset-bottom)); border-radius: 12px 12px 0 0; }
  .clone-task-template-dialog > header { flex-wrap: wrap; gap: 10px; }
  .clone-task-template-dialog > header h2 { min-width: 0; }
  .clone-task-template-dialog > header label { order: 3; width: 100%; }
  .clone-task-template-grid { grid-template-columns: minmax(0, 1fr); gap: 8px; }
  .clone-task-template-card { min-height: 112px; max-height: 190px; }
  .clone-quick-input-settings { max-height: calc(100dvh - 32px); overflow-y: auto; }
  .clone-quick-input-previews { grid-template-columns: minmax(0, 1fr); }
}
:global(body:has(.clone-task-template-backdrop) .floating-agent) { visibility: hidden; pointer-events: none; }
.clone-quick-add form {
  display: grid;
  align-items: stretch;
  flex: 1 1 100%;
  gap: 3px 8px;
}
.clone-quick-add > svg { display: none; }
.clone-quick-add input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
}
.clone-quick-add input:focus-visible { outline: none; box-shadow: none; }
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
.clone-quick-add form button.clone-quick-action,
.clone-quick-add form button.clone-quick-icon-action { display: inline-flex; }
.clone-quick-add form button.clone-quick-submit { display: inline-flex; align-items: center; justify-content: center; }
.clone-quick-add-form { min-width: 0; }
.clone-quick-title-row { display: flex; min-width: 0; align-items: center; min-height: 28px; }
.clone-quick-actions-row { display: flex; min-width: 0; align-items: center; gap: 4px; }
.clone-quick-actions-row .clone-quick-submit { margin-left: auto; }
.clone-quick-file-input { display: none; }
.clone-quick-attachments { display: flex; flex-wrap: wrap; gap: 6px; margin: -4px 0 10px 24px; }
.clone-quick-attachments > span { display: inline-flex; min-width: 0; align-items: center; gap: 6px; max-width: 100%; padding: 5px 7px; border: 1px solid var(--line); border-radius: 5px; background: var(--card); color: var(--ink2); font-size: 11px; }
.clone-quick-attachments > span > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-quick-attachments small { flex: 0 0 auto; color: var(--muted); font-size: 10px; }
.clone-quick-attachments button { display: grid; width: 20px; height: 20px; flex: 0 0 20px; place-items: center; border: 0; border-radius: 50%; background: transparent; color: var(--muted); cursor: pointer; }
.clone-quick-attachments button:hover { color: var(--ink); background: color-mix(in srgb, var(--ink) 8%, transparent); }
.clone-quick-attachment-state { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin: -5px 0 10px 24px; color: var(--muted); font-size: 11px; }
.clone-quick-attachment-state[role="alert"] { color: var(--danger, #b42318); }
.clone-quick-attachment-state button { padding: 3px 7px; border: 1px solid var(--line); border-radius: 4px; background: var(--card); color: var(--accent); font-size: 11px; cursor: pointer; }
.clone-quick-attachment-state button:disabled { cursor: progress; opacity: .65; }
.clone-quick-add form .clone-quick-action,
.clone-quick-add form .clone-quick-icon-action { align-items: center; justify-content: center; gap: 5px; min-height: 30px; padding: 0 6px; border-radius: 5px; background: transparent; color: var(--muted); font-size: 11px; white-space: nowrap; }
.clone-quick-add form .clone-quick-action:hover,
.clone-quick-add form .clone-quick-icon-action:hover,
.clone-quick-add form .clone-quick-action[aria-expanded="true"],
.clone-quick-add form .clone-quick-icon-action[aria-expanded="true"] { background: color-mix(in srgb, var(--accent) 9%, transparent); color: var(--accent); }
.clone-quick-add form .clone-quick-icon-action { position: relative; width: 30px; padding: 0; }
.clone-quick-add form .clone-quick-icon-action span { position: absolute; top: -2px; right: -1px; min-width: 13px; height: 13px; padding: 0 3px; border-radius: 8px; background: var(--accent); color: var(--accent-contrast, #fff); font-size: 9px; line-height: 13px; }
.clone-quick-add form .clone-quick-submit { min-height: 30px; padding: 0 10px; font-size: 12px; white-space: nowrap; }
.clone-quick-add form .clone-quick-action.active,
.clone-quick-add form .clone-quick-icon-action.active { color: var(--accent); }
.clone-quick-add form .clone-quick-priority-action[data-priority="HIGH"] { color: var(--priority-high); }
.clone-quick-add form .clone-quick-priority-action[data-priority="MEDIUM"] { color: var(--priority-medium); }
.clone-quick-add form .clone-quick-priority-action[data-priority="LOW"] { color: var(--priority-low); }
.clone-quick-add form .clone-quick-priority-action[data-priority="NONE"] { color: var(--muted); }
.clone-quick-list-dot { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: var(--line2); }
.clone-quick-action-panel { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; width: fit-content; max-width: 100%; margin: -8px 0 12px 24px; padding: 8px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); box-shadow: 0 5px 18px rgba(20, 27, 38, .12); }
.clone-quick-action-panel strong { width: 100%; color: var(--muted); font-size: 11px; font-weight: 600; }
.clone-quick-action-panel label { display: flex; align-items: center; gap: 7px; color: var(--muted); font-size: 11px; }
.clone-quick-action-panel input:not([type="checkbox"]),
.clone-quick-action-panel select { min-width: 0; height: 30px; padding: 0 7px; border: 1px solid var(--line); border-radius: 4px; background: var(--paper); color: var(--ink); font: inherit; }
.clone-quick-action-panel button { min-height: 28px; padding: 0 8px; border: 0; border-radius: 4px; background: transparent; color: var(--ink); font-size: 11px; cursor: pointer; }
.clone-quick-action-panel button:hover,
.clone-quick-action-panel button.active { background: color-mix(in srgb, var(--accent) 10%, transparent); color: var(--accent); }
.clone-quick-action-panel .clone-quick-priority-option { display: flex; width: 100%; align-items: center; gap: 9px; text-align: left; }
.clone-quick-priority-option span { flex: 1; }
.clone-quick-priority-option > svg:first-child,
.clone-board-quick-priority-option > svg:first-child { flex: none; color: var(--muted); }
.clone-quick-priority-option[data-priority="HIGH"] > svg:first-child,
.clone-board-quick-priority-option[data-priority="HIGH"] > svg:first-child { color: var(--priority-high); }
.clone-quick-priority-option[data-priority="MEDIUM"] > svg:first-child,
.clone-board-quick-priority-option[data-priority="MEDIUM"] > svg:first-child { color: var(--priority-medium); }
.clone-quick-priority-option[data-priority="LOW"] > svg:first-child,
.clone-board-quick-priority-option[data-priority="LOW"] > svg:first-child { color: var(--priority-low); }
.clone-quick-priority-option[data-priority="NONE"] > svg:first-child,
.clone-board-quick-priority-option[data-priority="NONE"] > svg:first-child { color: var(--muted); }
.clone-quick-action-panel label:has(input[type="checkbox"]) { padding: 3px 5px; border-radius: 4px; color: var(--ink); }
.clone-quick-action-panel.is-date,
.clone-quick-action-panel.is-repeat { position: fixed; z-index: 320; top: 0; left: 0; margin: 0; box-sizing: border-box; }
.clone-quick-action-panel.is-date { display: block; width: min(378px, calc(100vw - 36px)); max-height: min(690px, calc(100dvh - 120px)); overflow: visible; padding: 0; border: 0; background: transparent; box-shadow: none; }
.clone-quick-action-panel.is-date > .task-date-time-popover { width: 100%; }
.clone-quick-action-panel.is-repeat { display: block; width: min(410px, calc(100vw - 36px)); max-width: calc(100vw - 36px); max-height: min(690px, calc(100dvh - 120px)); overflow: auto; }
.clone-quick-date-panel { display: grid; gap: 10px; padding: 12px; }
.clone-quick-date-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 3px; padding: 3px; border-radius: 6px; background: var(--paper); }
.clone-quick-action-panel .clone-quick-date-tabs button { min-height: 29px; color: var(--muted); }
.clone-quick-action-panel .clone-quick-date-tabs button.active { background: var(--card); color: var(--accent); box-shadow: 0 1px 3px rgba(0, 0, 0, .12); }
.clone-quick-date-presets { display: flex; justify-content: space-between; gap: 3px; }
.clone-quick-action-panel .clone-quick-date-presets button { padding-inline: 6px; color: var(--accent); }
.clone-quick-calendar-heading { display: grid; grid-template-columns: 32px 1fr 32px; align-items: center; text-align: center; }
.clone-quick-action-panel .clone-quick-calendar-heading button { display: grid; width: 30px; padding: 0; place-items: center; }
.clone-quick-calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; text-align: center; }
.clone-quick-calendar-grid > span { display: grid; min-height: 22px; place-items: center; color: var(--muted); font-size: 10px; }
.clone-quick-action-panel .clone-quick-calendar-grid button { display: grid; width: 32px; max-width: 100%; aspect-ratio: 1; min-height: 0; margin: auto; padding: 0; place-items: center; border-radius: 50%; font-size: 12px; }
.clone-quick-action-panel .clone-quick-calendar-grid button.outside-month { color: var(--muted); opacity: .55; }
.clone-quick-action-panel .clone-quick-calendar-grid button.selected { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-date-time-row,
.clone-quick-range-fields { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 14px; padding-top: 8px; border-top: 1px solid var(--line); }
.clone-quick-action-panel .clone-quick-date-time-row label,
.clone-quick-action-panel .clone-quick-range-fields label { display: inline-flex; color: var(--ink2); }
.clone-quick-action-panel .clone-quick-date-time-row input[type="checkbox"] { margin: 0; }
.clone-quick-action-panel .clone-quick-date-time-row input[type="time"],
.clone-quick-action-panel .clone-quick-range-fields input[type="time"] { width: 105px; }
.clone-quick-range-fields > span { flex-basis: 100%; color: var(--muted); font-size: 11px; }
.clone-quick-reminder-section { display: grid; gap: 6px; padding-top: 8px; border-top: 1px solid var(--line); }
.clone-quick-repeat-section { position: relative; display: grid; gap: 6px; padding-top: 8px; border-top: 1px solid var(--line); }
.clone-quick-action-panel .clone-quick-repeat-toggle { display: flex; width: 100%; align-items: center; gap: 8px; padding-inline: 2px; text-align: left; }
.clone-quick-action-panel .clone-quick-repeat-toggle span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-quick-action-panel .clone-quick-repeat-toggle svg:last-child { margin-left: auto; }
.clone-quick-action-panel .clone-quick-repeat-toggle.active { color: var(--accent); }
.clone-quick-repeat-menu { z-index: 320; display: grid; gap: 1px; max-height: 240px; overflow: auto; padding: 4px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); box-shadow: 0 10px 24px rgb(15 23 42 / 16%); overscroll-behavior: contain; }
.clone-quick-repeat-menu > button { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px; text-align: left; }
.clone-quick-repeat-menu > button.active { color: var(--accent); }
.clone-quick-repeat-menu-separator { height: 1px; margin: 4px 0; background: var(--line); }
.clone-quick-action-panel .clone-quick-reminder-toggle { display: flex; width: 100%; align-items: center; justify-content: flex-start; gap: 8px; padding-inline: 2px; text-align: left; }
.clone-quick-action-panel .clone-quick-reminder-toggle svg:last-child { margin-left: auto; }
.clone-quick-action-panel .clone-quick-reminder-toggle[aria-pressed="true"] { color: var(--accent); }
.clone-quick-action-panel .clone-quick-reminder-toggle span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-quick-reminder-options { display: grid; max-height: 180px; overflow: auto; }
.clone-quick-action-panel .clone-quick-reminder-options button { display: flex; align-items: center; justify-content: space-between; text-align: left; }
.clone-quick-reminder-custom { display: grid; gap: 7px; padding: 7px; border-radius: 5px; background: var(--paper); }
.clone-quick-action-panel .clone-quick-reminder-custom label { display: flex; justify-content: space-between; color: var(--ink2); }
.clone-quick-action-panel .clone-quick-reminder-custom input { width: 72px; }
.clone-quick-action-panel .clone-quick-reminder-custom select { min-width: 88px; }
.clone-quick-action-panel .clone-quick-reminder-repeat { display: flex; justify-content: flex-start; color: var(--ink2); }
.clone-date-prop-rows { display: grid; gap: 2px; padding-top: 8px; border-top: 1px solid var(--line); }
.clone-date-prop-row { display: flex; align-items: center; gap: 6px; }
.clone-date-prop-trigger { display: inline-flex; flex: 1; min-width: 0; min-height: 32px; align-items: center; gap: 8px; padding: 4px 10px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-date-prop-trigger span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-date-prop-trigger:hover { background: var(--accent-soft); color: var(--accent); }
.clone-date-prop-trigger.active { color: var(--accent); }
.clone-date-prop-trigger svg.is-open { transform: rotate(90deg); transition: transform .15s; }
.clone-date-prop-clear { display: grid; width: 26px; min-height: 26px; flex: none; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-date-prop-clear:hover { background: var(--accent-soft); color: var(--accent); }
.clone-date-time-list { display: grid; max-height: 216px; gap: 1px; overflow: auto; overscroll-behavior: contain; }
.clone-date-time-list button { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 10px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-date-time-list button:hover { background: var(--accent-soft); }
.clone-date-time-list button.active { color: var(--accent); }
.clone-quick-repeat-badge { flex: 0 0 auto; color: var(--accent); }
.clone-quick-priority-menu { gap: 2px; }
.clone-quick-list-menu { gap: 1px; }
.clone-quick-list-search { display: flex; align-items: center; gap: 7px; margin-bottom: 4px; padding: 5px 8px; border-bottom: 1px solid var(--line); color: var(--muted); }
.clone-quick-list-search input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 13px; outline: none; }
.clone-quick-list-menu > button span { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-quick-list-folder { color: var(--ink); font-weight: 500; }
.clone-quick-list-empty { margin: 4px 8px; color: var(--muted); font-size: 12px; }
.clone-quick-tags-menu { gap: 6px; }
.clone-quick-tags-selected { display: flex; flex-wrap: wrap; gap: 6px; }
.clone-quick-tag-chip { display: inline-flex; align-items: center; gap: 3px; padding: 2px 4px 2px 8px; border-radius: 4px; background: var(--accent-soft); color: var(--accent); font-size: 12px; }
.clone-quick-tag-chip button { display: grid; place-items: center; width: 16px; height: 16px; border: 0; border-radius: 3px; background: transparent; color: inherit; cursor: pointer; }
.clone-quick-tags-options { display: grid; align-content: start; gap: 1px; max-height: 240px; overflow: auto; }
.clone-quick-tags-options > button { display: flex; min-height: 30px; align-items: center; gap: 8px; padding: 0 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-quick-tags-options > button:hover { background: var(--accent-soft); }
.clone-quick-tags-options > button.active { color: var(--accent); }
.clone-quick-tags-options > button span { flex: 1; }
.clone-quick-more-menu { gap: 2px; }
.clone-quick-more-menu > button { justify-content: flex-start; }
.clone-quick-more-menu > button span { flex: 1; }
.clone-quick-more-menu > button small { color: var(--muted); font-size: 10px; }
.clone-quick-more-menu > button > svg:last-child { margin-left: auto; }
.clone-quick-tags-menu { max-height: 400px; }
.clone-quick-tags-options { max-height: 220px; }
.clone-quick-flyout { z-index: 320; display: grid; align-content: start; gap: 1px; max-height: 320px; overflow: auto; padding: 4px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink); box-shadow: 0 10px 24px rgb(15 23 42 / 16%); overscroll-behavior: contain; }
.clone-quick-flyout > button { display: flex; min-height: 30px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.clone-quick-flyout > button:hover { background: var(--accent-soft); }
.clone-quick-flyout > button.active { color: var(--accent); }
.clone-quick-flyout > button svg.is-open { transform: rotate(90deg); transition: transform .15s; }
.clone-quick-flyout-separator { height: 1px; margin: 4px 0; background: var(--line); }
.clone-quick-flyout-title { padding: 2px 8px; color: var(--muted); font-size: 11px; }
.clone-quick-flyout-actions { display: flex; justify-content: flex-end; gap: 8px; }
.clone-quick-flyout-actions button { min-height: 26px; padding: 3px 10px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); font: inherit; font-size: 12px; cursor: pointer; }
.clone-quick-flyout-actions button.primary { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-flyout .clone-quick-reminder-custom { display: grid; gap: 7px; padding: 7px; border-radius: 5px; background: var(--paper); }
.clone-quick-flyout .clone-quick-reminder-custom label { display: flex; justify-content: space-between; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-quick-flyout .clone-quick-reminder-custom input { width: 72px; }
.clone-quick-flyout .clone-quick-reminder-custom select { min-width: 88px; }
.clone-quick-reminder-preview { margin: 0; color: var(--muted); font-size: 11px; }
.clone-quick-end-calendar { display: grid; gap: 4px; padding: 6px 4px; border-radius: 5px; background: var(--paper); }
.clone-quick-end-cal-head { display: flex; align-items: center; justify-content: space-between; }
.clone-quick-end-cal-head strong { font-size: 12px; }
.clone-quick-end-cal-head button { display: grid; width: 24px; min-height: 24px; place-items: center; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-quick-end-cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 2px; text-align: center; }
.clone-quick-end-cal-grid > span { display: grid; min-height: 20px; place-items: center; color: var(--muted); font-size: 10px; }
.clone-quick-end-cal-grid > button { display: grid; min-height: 24px; border: 0; border-radius: 50%; background: transparent; color: var(--ink2); font-size: 11px; cursor: pointer; }
.clone-quick-end-cal-grid > button:hover { background: var(--accent-soft); }
.clone-quick-end-cal-grid > button.other-month { color: var(--muted); opacity: .48; }
.clone-quick-end-cal-grid > button.selected { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-end-count { display: grid; gap: 7px; padding: 6px 4px; border-radius: 5px; background: var(--paper); }
.clone-quick-end-count label { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-quick-end-count input { width: 64px; }
.clone-quick-repeat-panel { display: grid; gap: 10px; width: 100%; min-width: 0; box-sizing: border-box; }
.clone-quick-repeat-panel > header { display: flex; align-items: center; justify-content: space-between; }
.clone-quick-repeat-panel > header button { width: 26px; min-height: 26px; padding: 0; }
.clone-quick-repeat-mode > select { width: 100%; }
.clone-quick-repeat-interval { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-quick-repeat-interval input { width: 64px; }
.clone-quick-repeat-interval select { flex: 1; min-width: 0; }
.clone-quick-repeat-circles { display: flex; justify-content: space-between; gap: 4px; }
.clone-quick-repeat-circles button { display: grid; width: 30px; aspect-ratio: 1; min-height: 0; place-items: center; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--ink2); font-size: 12px; cursor: pointer; }
.clone-quick-repeat-circles button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-quick-repeat-circles button.active { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-quick-repeat-presets { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 4px; }
.clone-quick-repeat-presets button { border: 1px solid var(--line); }
.clone-quick-repeat-date-calendar { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 3px; text-align: center; }
.clone-quick-repeat-date-calendar > span { display: grid; min-height: 20px; place-items: center; color: var(--muted); font-size: 10px; }
.clone-quick-repeat-date-calendar > span.clone-quick-repeat-date-spacer { min-height: 0; }
.clone-quick-repeat-date-calendar button { min-width: 0; min-height: 27px; padding-inline: 0; border: 1px solid transparent; }
.clone-quick-repeat-date-calendar button.active { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); }
.clone-quick-repeat-date-calendar button.clone-quick-repeat-last-day { grid-column: span 2; }
.clone-quick-repeat-checks { display: flex; flex-wrap: wrap; gap: 10px; }
.clone-quick-repeat-checks .clone-quick-repeat-check { display: inline-flex; align-items: center; gap: 5px; }
.clone-quick-repeat-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 7px; }
.clone-quick-repeat-grid label { display: grid; min-width: 0; gap: 4px; }
.clone-quick-repeat-grid input, .clone-quick-repeat-grid select, .clone-quick-repeat-end input[type="date"], .clone-quick-repeat-end input[type="number"] { width: 100%; min-width: 0; max-width: 100%; box-sizing: border-box; }
.clone-quick-repeat-days, .clone-quick-repeat-end { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; color: var(--ink2); font-size: 11px; }
.clone-quick-repeat-days label, .clone-quick-repeat-check, .clone-quick-repeat-end label { display: inline-flex; align-items: center; gap: 4px; }
.clone-quick-repeat-check { color: var(--ink2); }
.clone-quick-repeat-end > span { flex-basis: 100%; color: var(--muted); }
.clone-quick-repeat-end input[type="date"] { max-width: 135px; }
.clone-quick-repeat-end input[type="number"] { max-width: 70px; }
.clone-quick-repeat-panel > footer { position: sticky; bottom: 0; z-index: 1; padding-block: 4px; background: var(--card); }
.clone-quick-action-panel .clone-quick-date-footer { display: flex; justify-content: space-between; padding-top: 4px; border-top: 1px solid var(--line); }
.clone-quick-action-panel .clone-quick-date-footer .clone-quick-date-apply { padding-inline: 14px; background: var(--accent); color: var(--accent-contrast, #fff); }
@media (max-width: 600px) {
  .clone-quick-action-panel.is-date,
  .clone-quick-action-panel.is-repeat { position: fixed; z-index: 260; top: 72px; right: 8px; bottom: auto; left: 8px; width: auto; max-width: none; max-height: calc(100dvh - 96px); margin: 0; }
  .clone-quick-action-panel.is-date > .task-date-time-popover { width: 100%; max-height: calc(100dvh - 96px); }
  .clone-quick-repeat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.clone-quick-date-footer { position: sticky; bottom: 0; z-index: 1; background: var(--card); }
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
.clone-quick-draft-reminder { display: flex; align-items: center; gap: 5px; margin: 0; color: var(--muted); font-size: 11px; }
.clone-quick-draft-tags { display: flex; flex-wrap: wrap; gap: 8px 12px; margin: 0; padding: 7px 8px; border: 1px solid var(--line); border-radius: 5px; }
.clone-quick-draft-tags legend { padding: 0 4px; color: var(--muted); font-size: 11px; }
.clone-quick-draft-tags label { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; }
.clone-quick-draft > footer button:not(.clone-primary-button) { border: 0; background: transparent; color: var(--muted); cursor: pointer; }
.clone-quick-draft > footer .clone-primary-button { min-height: 30px; }
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
.clone-error:has(.clone-conflict-actions) { align-items: stretch; flex-direction: column; }
.clone-conflict-actions { display: flex; align-items: center; gap: 7px; color: var(--ink); }
.clone-conflict-actions span { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-conflict-actions button { min-height: 28px; padding: 0 9px; border: 1px solid color-mix(in srgb, var(--down) 24%, var(--line)); border-radius: 5px; background: var(--card); color: inherit; cursor: pointer; }
.clone-conflict-actions button:disabled { opacity: .55; cursor: wait; }
.clone-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 0;
  max-width: none;
  color: var(--muted);
  font-size: 12px;
}
.clone-list-toolbar { display: flex; }
.clone-task-groups {
  display: grid;
  max-width: none;
  gap: 20px;
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
.clone-task-group.drag-target > .clone-group-heading { border-radius: 4px; background: color-mix(in srgb, var(--accent) 9%, transparent); color: var(--accent); }
.clone-group-heading span {
  color: var(--muted);
  font-size: 12px;
}
.clone-completed-group { border-top: 1px solid var(--line); padding-top: 0; }
.clone-completed-more { display: block; margin: 8px auto 4px; padding: 6px 12px; border: 0; background: transparent; color: var(--accent); font: inherit; font-size: 12px; cursor: pointer; }
.clone-group-heading svg {
  transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1);
}
.clone-task-rows {
  border-top: 0;
}
.clone-task-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 48px;
  /* 行宽即中间栏可用宽度：标签分档收起的容器查询以此为准（对齐滴答按中栏收窄） */
  container: task-meta / inline-size;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 74%, transparent);
  padding: 0 10px 0 13px;
  border-left: 3px solid var(--task-group-color, transparent);
  transition: background 0.12s ease, box-shadow 0.12s ease, opacity 0.12s ease, transform 0.12s ease;
}
.clone-task-row[data-depth]:not([data-depth="0"]) { width: calc(100% - 40px * var(--task-depth)); margin-left: calc(40px * var(--task-depth)); }
.clone-subtask-toggle { position: absolute; z-index: 2; top: 50%; left: -3px; display: grid; width: 14px; height: 18px; place-items: center; padding: 0; transform: translateY(-50%); border: 0; background: transparent; color: var(--muted); cursor: pointer; }
.clone-task-row.has-children.is-note .clone-drag-handle { margin-left: 7px; }
.clone-subtask-toggle:hover { color: var(--ink); }
.clone-subtask-toggle svg { transition: transform 0.16s ease; }
.clone-subtask-toggle svg.rotated { transform: rotate(-90deg); }
.clone-task-row.dragging { opacity: .42; }
.clone-task-row.drag-over { box-shadow: inset 0 3px 0 var(--accent), inset 3px 0 0 var(--task-group-color, var(--accent)); }
.clone-task-row.drop-before::after,
.clone-task-row.drop-after::after { position: absolute; z-index: 4; left: 7px; width: 7px; height: 7px; border: 1px solid var(--card); border-radius: 50%; background: var(--accent); content: ""; pointer-events: none; }
.clone-task-row.drop-before::before,
.clone-task-row.drop-after::before { position: absolute; z-index: 3; left: 10px; right: 7px; height: 2px; background: var(--accent); content: ""; pointer-events: none; }
.clone-task-row.drop-before::before { top: -1px; }
.clone-task-row.drop-after::before { bottom: -1px; }
.clone-task-row.drop-before::after { top: -4px; }
.clone-task-row.drop-after::after { bottom: -4px; }
.clone-drag-handle { display: grid; flex: none; width: 17px; height: 24px; place-items: center; margin-left: -9px; padding: 0; border: 0; background: transparent; color: var(--muted); opacity: 0; cursor: grab; }
.clone-task-row:hover .clone-drag-handle,
.clone-drag-handle:focus-visible { opacity: .8; }
.clone-drag-handle:active { cursor: grabbing; }
.clone-select-box { width: 20px; height: 20px; border-radius: 5px; }
.clone-task-row:hover,
.clone-task-row.selected {
  background: color-mix(in srgb, var(--ink) 5%, transparent);
  box-shadow: none;
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
.clone-select-box {
  display: grid;
  flex: none;
  width: 21px;
  height: 21px;
  place-items: center;
  border: 1.5px solid var(--line2);
  border-radius: 6px;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}
.clone-select-box { border-radius: 6px; }
.clone-check {
  position: relative;
  display: grid;
  flex: none;
  width: 26px;
  height: 28px;
  min-width: 26px;
  min-height: 28px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}
.clone-check::before {
  width: 14px;
  height: 14px;
  border: 1.5px solid var(--line2);
  border-radius: 4px;
  background: transparent;
  content: "";
}
.clone-check svg { position: absolute; width: 10px; height: 10px; }
.clone-task-content {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  gap: 5px;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}
.clone-print-task-copy { display: grid; min-width: 0; gap: 3px; }
.clone-print-description { display: none; overflow: hidden; color: var(--muted); font-size: 11px; font-weight: 400; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.clone-task-content strong {
  overflow: hidden;
  font-size: 14px;
  font-weight: 450;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clone-task-metadata { display: flex; align-items: center; justify-content: flex-end; gap: 7px; flex: 0 1 auto; min-width: 0; overflow: hidden; color: var(--muted); font-size: 12px; white-space: nowrap; }
.clone-task-tags { position: relative; display: flex; align-items: center; gap: 5px; min-width: 0; overflow: visible; }
.clone-task-tag-wrap { position: relative; display: inline-flex; align-items: center; flex: none; min-width: 0; }
.clone-task-tag { display: inline-flex; align-items: center; max-width: 92px; overflow: hidden; padding: 2px 8px; border-radius: 12px; background: color-mix(in srgb, var(--task-tag-color, var(--accent)) 22%, var(--card)); color: color-mix(in srgb, var(--task-tag-color, var(--accent)) 70%, var(--ink)); text-overflow: ellipsis; }
.clone-task-tag-remove { position: absolute; z-index: 2; top: -4px; right: -4px; display: grid; width: 14px; height: 14px; place-items: center; padding: 0; border: 1px solid var(--card); border-radius: 50%; background: var(--ink2); color: var(--card); opacity: 0; cursor: pointer; }
.clone-task-tag-wrap:hover .clone-task-tag-remove,
.clone-task-tag-remove:focus-visible { opacity: 1; }
.clone-task-tag-count { position: relative; display: none; align-items: center; flex: none; padding: 2px 7px; border-radius: 12px; background: color-mix(in srgb, var(--accent) 18%, var(--card)); color: var(--ink2); cursor: default; }
/* 标签溢出计数 chip 由容器查询分档驱动：宽档最多 4 个标签，超出才显示 +N；数字随档位取对应 data-h* 属性 */
/* 档位判定与隐藏只作用于标签行的直接子元素——+N 悬浮浮层里的同名 chips 不参与分档，避免被隐藏 */
.clone-task-tags:has(> .clone-task-tag-wrap:nth-child(5)) .clone-task-tag-count { display: inline-flex; font-size: 0; }
.clone-task-tags:has(> .clone-task-tag-wrap:nth-child(5)) .clone-task-tag-count::before { content: "+" attr(data-h4); font-size: 11px; }
.clone-tag-flyout { position: fixed; z-index: 90; display: flex; flex-wrap: wrap; gap: 5px; width: max-content; max-width: min(260px, 70vw); padding: 6px 9px; border: 1px solid var(--line); border-radius: 5px; background: var(--card); color: var(--ink); font-size: 12px; box-shadow: 0 4px 14px rgba(0,0,0,.18); }
/* metadata 默认 overflow hidden 防行溢出，悬浮标签时临时放开，避免行尾标签的移除按钮被裁剪 */
.clone-task-metadata:has(.clone-task-tag-wrap:hover),
.clone-task-metadata:has(.clone-task-tag-remove:focus-visible) { overflow: visible; }
.clone-checklist-progress { width: 14px; height: 14px; flex: none; border-radius: 50%; background: conic-gradient(var(--accent) var(--check-progress), color-mix(in srgb, var(--line2) 60%, transparent) 0); }
.clone-task-indicator { display: inline-flex; align-items: center; gap: 2px; flex: none; color: var(--muted); }
.clone-task-indicator-action { min-height: 22px; padding: 0; border: 0; background: transparent; font: inherit; cursor: pointer; }
.clone-task-indicator-action:hover { color: var(--accent); }
.clone-task-indicator-action:focus-visible { outline: 2px solid color-mix(in srgb, var(--accent) 65%, transparent); outline-offset: 2px; border-radius: 3px; }
.clone-task-indicator small { font-size: 10px; }
.clone-task-date { display: inline-flex; align-items: center; gap: 4px; flex: none; color: var(--accent); }
.clone-task-date { padding: 0; border: 0; background: transparent; font: inherit; cursor: pointer; }
.clone-task-date.overdue { color: var(--down); }
.clone-task-date.today { color: var(--accent); }
.clone-task-row.is-note { min-height: 54px; }
.clone-task-row.is-note .clone-task-content { min-height: 44px; }
.clone-task-content input { min-width: 0; width: 100%; padding: 4px 6px; border: 1px solid var(--accent); border-radius: 4px; outline: 0; background: var(--card); color: var(--ink); font: inherit; }
.clone-row-date-panel { position: fixed; z-index: 60; }

.clone-task-list-link { display: inline-flex; align-items: center; gap: 5px; min-width: 0; max-width: 120px; overflow: hidden; padding: 0; border: 0; background: transparent; color: var(--ink2); font: inherit; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.clone-task-list-link:hover { color: var(--accent); }
.clone-task-list-dot { width: 7px; height: 7px; flex: none; border-radius: 50%; }
.clone-task-main:has(.clone-inline-board) { display: flex; flex-direction: column; overflow: hidden; padding-bottom: 16px; }
.clone-inline-board { display: grid; grid-auto-columns: 280px; grid-auto-flow: column; grid-template-rows: minmax(0, 1fr); gap: 18px; min-width: 0; flex: 1 1 auto; min-height: 0; overflow-x: auto; overflow-y: hidden; padding: 8px 18px 12px; background: color-mix(in srgb, var(--paper) 78%, var(--card)); }
.clone-inline-board-column { display: grid; align-content: start; gap: 10px; min-width: 0; height: 100%; min-height: 0; overflow-x: hidden; overflow-y: auto; padding: 0 2px 12px; scrollbar-width: thin; overscroll-behavior: contain; }
.clone-inline-board-column-head { position: sticky; top: 0; z-index: 1; display: flex; align-items: center; justify-content: space-between; min-height: 38px; padding: 0 4px; background: color-mix(in srgb, var(--paper) 78%, var(--card)); }
.clone-inline-board-column-head > div { display: flex; align-items: baseline; gap: 7px; min-width: 0; }
.clone-inline-board-column-head strong { overflow: hidden; color: var(--ink2); font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.clone-inline-board-column-head small { color: var(--muted); font-size: 12px; }
.clone-inline-board-column-head button { display: grid; flex: 0 0 28px; width: 28px; height: 28px; place-items: center; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-inline-board-column-head button:hover { background: color-mix(in srgb, var(--accent) 11%, transparent); color: var(--accent); }
.clone-board-quick-inline { position: relative; z-index: 4; margin: -2px 0 2px; }
.clone-board-quick-inline .clone-board-quick-entry { min-height: 0; padding: 10px 11px; border: 1px solid var(--line); border-radius: 8px; box-shadow: none; }
.clone-board-quick-entry > input:focus-visible { outline: none; box-shadow: none; }
.clone-board-quick-inline .clone-board-quick-entry > input { min-height: 30px; font-size: 14px; }
.clone-board-quick-inline .clone-board-quick-actions { gap: 3px; }
.clone-board-quick-inline .clone-board-quick-actions > button { width: auto; height: 28px; min-width: 28px; flex-basis: auto; padding: 0 6px; }
.clone-board-quick-inline .clone-board-quick-actions > .clone-board-quick-submit { width: 32px; height: 28px; flex-basis: 32px; }
.clone-board-quick-inline .clone-board-quick-popover { top: calc(100% + 7px); bottom: auto; }
.clone-inline-board-card { display: grid; gap: 8px; width: 100%; min-height: 92px; padding: 13px 13px 11px; border: 1px solid color-mix(in srgb, var(--line) 78%, transparent); border-radius: 9px; background: var(--surface); color: var(--ink); text-align: left; cursor: pointer; box-shadow: 0 2px 9px rgb(15 23 42 / 6%); transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease, background .15s ease; }
.clone-inline-board-card:hover, .clone-inline-board-card:focus-visible, .clone-inline-board-card.selected { border-color: color-mix(in srgb, var(--accent) 65%, var(--line)); background: color-mix(in srgb, var(--accent) 4%, var(--surface)); box-shadow: 0 7px 18px rgb(15 23 42 / 11%); outline: none; transform: translateY(-1px); }
.clone-inline-board-card.checked { opacity: .72; }
.clone-inline-board-card-title { display: flex; align-items: flex-start; gap: 8px; min-width: 0; }
.clone-inline-board-card-title strong { min-width: 0; overflow-wrap: anywhere; font-size: 13px; font-weight: 600; line-height: 1.45; }
.clone-inline-board-card.checked .clone-inline-board-card-title strong { text-decoration: line-through; }
.clone-inline-board-check { display: grid; width: 17px; height: 17px; flex: 0 0 17px; place-items: center; margin-top: 1px; padding: 0; border: 1.5px solid var(--muted); border-radius: 5px; background: transparent; color: var(--accent-contrast, #fff); cursor: pointer; }
.clone-inline-board-check:hover { border-color: var(--accent); }
.clone-inline-board-check.completed { border-color: var(--accent); background: var(--accent); }
.clone-inline-board-card[data-priority="HIGH"] .clone-inline-board-check { border-color: var(--priority-high); }
.clone-inline-board-card[data-priority="MEDIUM"] .clone-inline-board-check { border-color: var(--priority-medium); }
.clone-inline-board-note-icon { flex: 0 0 auto; margin-top: 2px; color: var(--accent); }
.clone-inline-board-tags { display: flex; flex-wrap: wrap; gap: 4px; padding-left: 25px; }
.clone-inline-board-tag, .clone-inline-board-tag-more { display: inline-flex; max-width: 120px; align-items: center; min-height: 20px; padding: 1px 7px; overflow: hidden; border-radius: 10px; background: color-mix(in srgb, var(--task-tag-color, var(--accent)) 14%, transparent); color: color-mix(in srgb, var(--task-tag-color, var(--accent)) 74%, var(--ink)); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.clone-inline-board-tag-more { position: relative; overflow: visible; background: color-mix(in srgb, var(--muted) 13%, transparent); color: var(--muted); cursor: default; }
.clone-inline-board-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; min-width: 0; padding-left: 25px; color: var(--muted); font-size: 10px; }
.clone-inline-board-date, .clone-inline-board-list { display: inline-flex; min-width: 0; align-items: center; gap: 3px; padding: 0; border: 0; background: transparent; color: var(--muted); font: inherit; font-size: 10px; cursor: pointer; }
.clone-inline-board-date:hover, .clone-inline-board-list:hover { color: var(--accent); }
.clone-inline-board-date.today { color: var(--accent); }
.clone-inline-board-date.overdue { color: var(--down); }
.clone-inline-board-indicator { display: inline-flex; align-items: center; gap: 3px; color: var(--muted); }
.clone-inline-board-checklist-ring { display: inline-block; width: 11px; height: 11px; border: 1px solid var(--line2); border-radius: 50%; background: conic-gradient(var(--accent) var(--check-progress), transparent 0); position: relative; }
.clone-inline-board-checklist-ring::after { position: absolute; inset: 2px; border-radius: 50%; background: var(--surface); content: ""; }
.clone-inline-board-list { max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-inline-board-list-dot { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: var(--line2); }
.clone-inline-board-empty { margin: 0; padding: 12px 8px; border: 1px dashed var(--line); border-radius: 8px; color: var(--muted); font-size: 11px; text-align: center; }
.clone-board-quick-backdrop { z-index: 1100; }
.clone-board-quick-dialog { display: grid; gap: 18px; width: min(720px, 100%); padding: 24px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); color: var(--ink); box-shadow: 0 24px 70px rgb(0 0 0 / 28%); }
.clone-board-quick-dialog > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.clone-board-quick-dialog > header > div { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.clone-board-quick-dialog > header strong { overflow: hidden; font-size: 21px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.clone-board-quick-dialog > header small { color: var(--muted); font-size: 15px; }
.clone-board-quick-dialog > header button { display: grid; width: 32px; height: 32px; flex: 0 0 32px; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-board-quick-dialog > header button:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.clone-board-quick-entry { position: relative; display: grid; gap: 3px; min-height: 126px; padding: 18px; border: 2px solid var(--accent); border-radius: 12px; background: var(--surface); }
.clone-board-quick-entry > input { width: 100%; min-width: 0; min-height: 45px; padding: 0; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 20px; }
.clone-board-quick-entry > input::placeholder { color: var(--muted); }
.clone-board-quick-entry.has-error { border-color: var(--danger, #ef4444); }
.clone-board-quick-actions { display: flex; align-items: center; gap: 8px; min-width: 0; }
.clone-board-quick-actions > button { position: relative; display: grid; width: 38px; height: 38px; flex: 0 0 38px; place-items: center; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-board-quick-actions > button:hover, .clone-board-quick-actions > button[aria-expanded="true"] { background: color-mix(in srgb, var(--accent) 10%, transparent); color: var(--accent); }
.clone-board-quick-actions > button.clone-board-quick-action { display: inline-flex; align-items: center; justify-content: center; gap: 5px; width: auto; flex: 0 1 auto; padding: 0 6px; font-size: 11px; white-space: nowrap; }
.clone-board-quick-actions > button.clone-board-quick-action.active { color: var(--accent); }
.clone-board-quick-actions > button.clone-board-quick-priority-action[data-priority="HIGH"] { color: var(--down); }
.clone-board-quick-actions > button.clone-board-quick-priority-action[data-priority="MEDIUM"] { color: var(--warn); }
.clone-board-quick-actions > button.clone-board-quick-priority-action[data-priority="LOW"] { color: #4f82dd; }
.clone-board-quick-actions > button.clone-board-quick-priority-action[data-priority="NONE"] { color: var(--muted); }
.clone-board-quick-actions > button > small { position: absolute; top: 0; right: 0; min-width: 14px; border-radius: 8px; background: var(--accent); color: var(--accent-contrast, white); font-size: 9px; line-height: 14px; }
.clone-board-quick-actions > .clone-board-quick-submit { width: 48px; height: 40px; flex-basis: 48px; margin-left: auto; background: var(--accent); color: var(--accent-contrast, white); }
.clone-board-quick-actions > .clone-board-quick-submit:hover { background: var(--accent); color: var(--accent-contrast, white); filter: brightness(1.08); }
.clone-board-quick-actions > .clone-board-quick-submit:disabled { opacity: .45; cursor: not-allowed; }
.clone-board-quick-popover { position: absolute; z-index: 2; bottom: calc(100% + 9px); left: 0; display: grid; gap: 8px; min-width: 220px; max-width: min(340px, calc(100vw - 56px)); max-height: min(320px, 45vh); overflow: auto; padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); color: var(--ink); box-shadow: 0 12px 32px rgb(0 0 0 / 22%); }
.clone-board-quick-popover strong { color: var(--ink2); font-size: 12px; }
.clone-board-quick-popover label { display: grid; gap: 5px; color: var(--muted); font-size: 12px; }
.clone-board-quick-popover input:not([type="checkbox"]), .clone-board-quick-popover select, .clone-board-quick-popover textarea { min-width: 0; min-height: 34px; padding: 6px 8px; border: 1px solid var(--line); border-radius: 5px; background: var(--surface); color: var(--ink); font: inherit; font-size: 12px; }
.clone-board-quick-popover > button { min-height: 30px; padding: 5px 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); text-align: left; cursor: pointer; }
.clone-board-quick-popover > button:hover, .clone-board-quick-popover > button.active { background: var(--accent-soft); color: var(--accent); }
.clone-board-quick-popover > button.clone-board-quick-priority-option { display: flex; align-items: center; gap: 9px; text-align: left; }
.clone-board-quick-priority-option span { flex: 1; }
.clone-board-quick-tags label { display: flex; align-items: center; gap: 7px; min-height: 28px; color: var(--ink2); }
.clone-board-quick-tags input { width: 15px; height: 15px; accent-color: var(--accent); }
.clone-board-quick-more > button { display: inline-flex; align-items: center; gap: 7px; width: 100%; }
.clone-board-quick-more > button small { margin-left: auto; color: var(--muted); }
.clone-board-quick-all-day { display: flex !important; grid-template-columns: none !important; align-items: center; gap: 7px !important; }
.clone-board-quick-attachments { display: flex; flex-wrap: wrap; gap: 5px; padding-top: 4px; }
.clone-board-quick-attachments > span { display: inline-flex; max-width: 100%; align-items: center; gap: 5px; padding: 3px 6px; border: 1px solid var(--line); border-radius: 4px; color: var(--ink2); font-size: 10px; }
.clone-board-quick-attachments > span > span { max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-board-quick-attachments button { display: grid; width: 18px; height: 18px; place-items: center; padding: 0; border: 0; background: transparent; color: var(--muted); cursor: pointer; }
.clone-board-quick-error { margin: -8px 0 0; color: var(--danger, #ef4444); font-size: 12px; }
@media (max-width: 600px) {
  .clone-board-quick-dialog { gap: 14px; padding: 16px; }
  .clone-board-quick-dialog > header strong { font-size: 18px; }
  .clone-board-quick-entry { min-height: 118px; padding: 13px; }
  .clone-board-quick-entry > input { font-size: 17px; }
  .clone-board-quick-actions { gap: 3px; }
  .clone-board-quick-actions > button { width: 34px; height: 36px; flex-basis: 34px; }
  .clone-board-quick-actions > .clone-board-quick-submit { width: 42px; flex-basis: 42px; }
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
.clone-task-row[data-priority="HIGH"] .clone-check::before { border-color: var(--priority-high); }
.clone-task-row[data-priority="MEDIUM"] .clone-check::before { border-color: var(--priority-medium); }
.clone-task-row[data-priority="LOW"] .clone-check::before { border-color: var(--priority-low); }
.clone-row-more {
  flex: none;
}
.clone-empty-state {
  display: grid;
  justify-items: center;
  gap: 9px;
  width: 100%;
  min-height: 210px;
  align-content: center;
  color: var(--muted);
  border-top: 1px solid var(--line);
}
.clone-empty-state strong { color: var(--ink2); font-size: 15px; font-weight: 600; }
.clone-empty-state p { max-width: 340px; margin: 0; font-size: 12px; line-height: 1.6; text-align: center; }
.clone-countdown-group { margin-top: 20px; }
.clone-habit-group { margin-top: 0; }
.clone-efficiency-rows { border-top: 1px solid var(--line); }
.clone-efficiency-row { display: flex; align-items: center; gap: 9px; min-height: 48px; padding: 0 10px; border-bottom: 1px solid var(--line); border-left: 3px solid var(--efficiency-color, #22c55e); }
.clone-efficiency-row strong { min-width: 0; flex: 1; overflow: hidden; font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.clone-efficiency-icon { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; background: var(--accent-soft); font-size: 13px; }
.clone-efficiency-meta { color: var(--muted); font-size: 11px; }
.clone-efficiency-check { display: grid; width: 21px; height: 21px; place-items: center; border: 1.5px solid var(--line2); border-radius: 6px; background: transparent; color: var(--accent); cursor: pointer; }
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
.clone-list-activity-pane { display: grid; height: 100%; min-height: 0; grid-template-rows: 56px minmax(0, 1fr); }
.clone-list-activity-header { display: flex; align-items: center; justify-content: space-between; min-height: 56px; padding: 0 18px; border-bottom: 1px solid var(--line); }
.clone-list-activity-header strong { color: var(--ink); font-size: 16px; font-weight: 650; }
.clone-list-activity-header button { display: grid; width: 32px; height: 32px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-list-activity-header button:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.clone-list-activity-scroll { min-height: 0; overflow: auto; padding: 18px 16px 28px; }
.clone-list-activity-group { margin: 0 0 24px; }
.clone-list-activity-group h3 { margin: 0 0 12px; color: var(--ink2); font-size: 14px; font-weight: 550; }
.clone-list-activity-group ol { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.clone-list-activity-group li { position: relative; display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: 12px; min-height: 74px; }
.clone-list-activity-group li:not(:last-child)::after { position: absolute; top: 32px; bottom: 0; left: 15px; width: 2px; background: var(--line); content: ""; }
.clone-list-activity-icon { z-index: 1; display: grid; width: 28px; height: 28px; place-items: center; border-radius: 50%; background: #4c75ed; color: #fff; }
.clone-list-activity-icon.is-destructive { background: #dc4548; }
.clone-list-activity-icon.is-updated { background: #707986; }
.clone-list-activity-copy { min-width: 0; padding: 2px 0 20px; }
.clone-list-activity-copy p { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 7px; margin: 0 0 5px; color: var(--ink2); font-size: 14px; line-height: 1.5; }
.clone-list-activity-copy p > strong { color: var(--ink); font-weight: 600; }
.clone-list-activity-task { max-width: 100%; padding: 0; border: 0; background: transparent; color: var(--ink); font: inherit; text-align: left; cursor: pointer; }
.clone-list-activity-task:hover, .clone-list-activity-tag:hover { text-decoration: underline; }
.clone-list-activity-tag { color: var(--activity-tag-color); font-size: 13px; text-decoration: none; }
.clone-list-activity-copy time { color: var(--muted); font-size: 12px; }
.clone-list-activity-more { display: block; min-height: 34px; margin: 0 auto; padding: 0 14px; border: 1px solid var(--line); border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.clone-list-activity-more:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.clone-list-activity-more:disabled { opacity: .6; cursor: wait; }
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
/* 位置由 positionWorkspaceMenu 按触发按钮实测坐标注入（top/left 内联样式） */
.clone-view-menu { width: 212px; max-height: min(74vh, 620px); overflow: visible; padding: 8px; }
.clone-view-menu > strong { padding: 8px 9px 4px; color: var(--muted); font-size: 11px; }
.clone-view-menu > button { min-height: 32px; }
.clone-view-menu .clone-menu-submenu-trigger { min-height: 48px; padding: 6px 9px; gap: 12px; }
.clone-menu-submenu-trigger > span { flex: 1; }
.clone-menu-submenu-trigger > small { color: var(--muted); font-size: 12px; white-space: nowrap; }
.clone-menu-submenu-trigger > svg:last-child { color: var(--muted); }
.clone-view-menu .clone-menu-back { color: var(--ink2); font-weight: 600; }
.clone-view-menu .clone-menu-reset { justify-content: center; color: var(--muted); }
.clone-view-segmented { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-items: center; padding: 7px 9px 9px; }
.clone-view-segmented button { display: grid; justify-self: center; width: 40px; height: 40px; min-height: 40px; place-items: center; padding: 0; border: 1px solid transparent; border-radius: 6px; color: var(--ink); }
.clone-view-segmented button[aria-pressed="true"] { color: var(--accent); background: var(--accent-soft); }
.clone-print-submenu { position: relative; }
.clone-view-menu .clone-print-submenu-trigger { display: flex; width: 100%; align-items: center; justify-content: flex-start; gap: 8px; min-height: 34px; padding: 7px 9px; border: 0; border-radius: 6px; background: transparent; color: var(--ink); text-align: left; cursor: pointer; }
.clone-print-submenu-trigger span { flex: 1; }
.clone-print-submenu-trigger > svg:last-child { color: var(--muted); }
.clone-view-menu .clone-print-submenu-trigger:hover,
.clone-view-menu .clone-print-submenu-trigger[aria-expanded="true"] { background: var(--paper); }
.clone-print-submenu-options { position: absolute; z-index: 2; top: -4px; right: 100%; display: grid; width: 190px; gap: 2px; padding: 5px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 10px 28px rgb(15 23 42 / 16%); }
.clone-view-menu .clone-print-submenu-options button { min-height: 38px; justify-content: flex-start; padding: 7px 10px; font-size: 13px; }
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
.clone-task-row-menu { width: min(260px, calc(100vw - 16px)); max-height: min(80vh, 560px); overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; padding: 5px; }
.clone-task-row-menu > strong { padding: 6px 8px; font-size: 11px; line-height: 16px; }
.clone-task-row-menu > button { box-sizing: border-box; width: 100%; min-width: 0; min-height: 32px; justify-content: flex-start; gap: 9px; padding: 5px 8px; font-size: 13px; line-height: 18px; }
.clone-task-row-menu > button > svg:first-child { width: 16px; height: 16px; flex: 0 0 16px; }
.clone-task-row-menu > button > svg:last-child:not(:first-child) { width: 14px; height: 14px; flex: 0 0 14px; margin-left: auto; }
.clone-task-row-menu .clone-context-submenu { margin-left: 18px; }
.clone-task-row-menu .clone-context-submenu button { box-sizing: border-box; min-height: 28px; padding: 4px 7px; font-size: 12px; line-height: 18px; }
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
.clone-batch-backdrop { display: none; }
.clone-batch-panel { min-height: 100%; padding: 0 20px 24px; }
.clone-batch-panel-head { display: flex; min-height: 68px; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid var(--line); }
.clone-batch-panel-head > div { display: flex; align-items: center; gap: 11px; min-width: 0; }
.clone-batch-panel-head strong { color: var(--ink2); font-size: 16px; font-weight: 600; }
.clone-batch-panel-head strong b { color: var(--accent); }
.clone-batch-close, .clone-batch-cancel { display: inline-grid; min-width: 32px; height: 32px; place-items: center; padding: 0 6px; border: 0; border-radius: 5px; background: transparent; color: var(--accent); font: inherit; cursor: pointer; }
.clone-batch-close { color: var(--muted); }
.clone-batch-close:hover, .clone-batch-cancel:hover { background: var(--accent-soft); }
.clone-batch-fields { display: grid; gap: 8px; margin-top: 12px; padding: 14px; border: 1px solid transparent; border-radius: 7px; background: color-mix(in srgb, var(--paper) 70%, var(--card)); }
.clone-batch-field { display: flex; min-width: 0; min-height: 42px; align-items: center; gap: 10px; color: var(--muted); }
.clone-batch-field > svg, .clone-batch-tags summary > svg { flex: none; color: var(--accent); }
.clone-batch-field input, .clone-batch-field select { min-width: 0; min-height: 34px; flex: 1; padding: 4px 7px; border: 0; border-radius: 4px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; }
.clone-batch-field select { cursor: pointer; }
.clone-batch-field > button:not(.clone-batch-apply) { display: grid; width: 26px; height: 26px; flex: none; place-items: center; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-batch-apply { flex: none; padding: 5px 8px; border: 0; border-radius: 4px; background: transparent; color: var(--accent); font: inherit; font-size: 12px; cursor: pointer; }
.clone-batch-apply:hover { background: var(--accent-soft); }
.clone-batch-apply:disabled { color: var(--muted); cursor: default; opacity: .55; }
.clone-batch-tags { color: var(--ink2); }
.clone-batch-tags summary { display: flex; min-height: 42px; align-items: center; gap: 10px; cursor: pointer; list-style: none; }
.clone-batch-tags summary::-webkit-details-marker { display: none; }
.clone-batch-tags summary span { flex: 1; font-size: 13px; }
.clone-batch-tags summary small { color: var(--muted); }
.clone-batch-tags > div { display: grid; max-height: 190px; gap: 2px; overflow: auto; padding: 4px 0 4px 27px; }
.clone-batch-tags label { display: flex; min-height: 30px; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-batch-tags input { accent-color: var(--accent); }
.clone-batch-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 14px; }
.clone-batch-actions button { display: grid; min-width: 0; min-height: 78px; align-content: center; justify-items: center; gap: 7px; border: 1px solid transparent; border-radius: 6px; background: color-mix(in srgb, var(--paper) 72%, var(--card)); color: var(--accent); font: inherit; cursor: pointer; }
.clone-batch-actions button:hover { border-color: color-mix(in srgb, var(--accent) 38%, var(--line)); background: var(--accent-soft); }
.clone-batch-actions button span { color: var(--ink2); font-size: 13px; }
.clone-batch-actions button.danger { color: var(--down); }
.clone-batch-actions button:disabled { color: var(--muted); opacity: .5; cursor: not-allowed; }
.clone-batch-actions .batch-action-pin { color: #eab308; }
.clone-batch-actions .batch-action-parent { color: #10b981; }
.clone-batch-actions .batch-action-merge { color: #06b6d4; }
.clone-batch-actions .batch-action-duplicate { color: #34d399; }
.clone-batch-actions .batch-action-note { color: #9ca3af; }
.clone-batch-actions .batch-action-sticky { color: #eab308; }
.clone-batch-actions .batch-action-copy { color: #06b6d4; }
.clone-detail-pane {
  position: relative;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
  flex: 0 0 56px;
  min-height: 56px !important;
  padding: 0 14px !important;
  color: var(--ink2) !important;
}
.clone-detail-toolbar-main,
.clone-detail-toolbar-actions { display: flex; align-items: center; gap: 3px; min-width: 0; }
.clone-detail-toolbar-main { flex: 1; overflow: visible; }
.clone-detail-toolbar-actions { margin-left: auto; }
.clone-detail-status-tool {
  display: inline-grid;
  width: 19px;
  height: 19px;
  flex: 0 0 19px;
  place-items: center;
  margin: 0 5px 0 1px;
  padding: 0;
  border: 1.5px solid var(--muted);
  border-radius: 5px;
  background: transparent;
  color: var(--accent-contrast, #fff);
  cursor: pointer;
}
.clone-detail-status-tool:hover { border-color: var(--accent); background: var(--accent-soft); }
.clone-detail-status-tool.completed { border-color: var(--accent); background: var(--accent); }
.clone-detail-status-tool.abandoned { border-color: var(--down); }
.clone-detail-status-tool svg { stroke-width: 2.5; }
.clone-detail-pane > header.clone-detail-toolbar .clone-detail-status-tool {
  display: inline-grid;
  width: 20px;
  height: 20px;
  min-width: 20px;
  max-width: 20px;
  flex: 0 0 20px;
  padding: 0;
  border: 1.5px solid var(--muted);
  border-radius: 5px;
}
.clone-detail-pane > header.clone-detail-toolbar .clone-detail-status-tool.completed { border-color: var(--accent); background: var(--accent); }
.clone-detail-pane > header.clone-detail-toolbar .clone-toolbar-popover button {
  width: auto;
  height: auto;
  min-width: 0;
  max-width: none;
  flex: initial;
  border: 0;
  border-radius: 5px;
  font: inherit;
}
.clone-detail-pane > header.clone-detail-toolbar .clone-date-popover-tabs button,
.clone-detail-pane > header.clone-detail-toolbar .clone-date-quick-actions button { width: 100%; }
.clone-detail-pane > header.clone-detail-toolbar .clone-date-calendar-head button { width: 30px; height: 28px; min-height: 28px; }
.clone-detail-pane > header.clone-detail-toolbar .clone-date-calendar-grid > button { width: 100%; height: 30px; min-height: 30px; }
.clone-detail-header-progress { position: absolute; right: 0; bottom: 0; left: 0; height: 2px; overflow: hidden; background: color-mix(in srgb, var(--line) 55%, transparent); pointer-events: none; }
.clone-detail-header-progress span { display: block; width: 100%; height: 100%; transform-origin: left center; background: var(--accent); transition: transform 180ms ease; }
.clone-detail-tool,
.clone-detail-priority-tool {
  display: inline-flex; align-items: center; gap: 5px; min-height: 32px; max-width: 132px;
  padding: 5px 7px; border: 0; border-radius: 5px; background: transparent; color: var(--muted);
  font: inherit; font-size: 11px; cursor: pointer; white-space: nowrap;
}
.clone-detail-tool span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-detail-tool:hover { background: var(--accent-soft); color: var(--accent); }
.clone-detail-toolbar-main > .clone-detail-tool[aria-label="设置日期"] { color: var(--down); }
.clone-detail-toolbar-main > .clone-detail-tool[aria-label="设置日期"]:hover { color: var(--down); background: color-mix(in srgb, var(--down) 9%, transparent); }
.clone-detail-list-tool { width: 28px; padding-inline: 6px; color: var(--ink2); font-weight: 600; }
.clone-detail-list-tool span { display: none; }
.clone-detail-toolbar-main > .clone-detail-tool[aria-label="设置日期"] { max-width: 150px; }
.clone-detail-repeat-indicator { display: grid; width: 28px; height: 28px; flex: 0 0 28px; place-items: center; color: var(--muted); pointer-events: none; }
.clone-detail-priority-tool { color: var(--muted); font-size: 20px; line-height: 1; }
.clone-detail-priority-tool[data-priority="HIGH"] { color: var(--priority-high); }
.clone-detail-priority-tool[data-priority="MEDIUM"] { color: var(--priority-medium); }
.clone-detail-priority-tool[data-priority="LOW"] { color: var(--priority-low); }
.clone-detail-content-toggle {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  margin-left: auto;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.clone-detail-content-toggle:hover { background: var(--accent-soft); color: var(--accent); }
.clone-toolbar-popover {
  position: absolute; z-index: 110; top: 46px; left: 12px; display: grid; gap: 7px; min-width: 170px;
  padding: 10px; border: 1px solid var(--line); border-radius: 7px; background: var(--card);
  box-shadow: 0 14px 30px rgba(15,23,42,.18);
}
.clone-date-toolbar-popover,
.clone-priority-toolbar-popover { position: fixed; inset: auto; box-sizing: border-box; min-width: 0; overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; }
.clone-date-popover-shell { position: fixed; z-index: 1000; box-sizing: border-box; }
.clone-date-popover-shell .task-date-time-popover { width: 100%; max-width: none; box-shadow: 0 18px 40px rgb(15 23 42 / 22%); }
.clone-date-rows { display: grid; gap: 2px; margin-top: 2px; }
.clone-date-rowline { display: flex; align-items: center; gap: 8px; min-height: 34px; padding: 0 2px; border-top: 1px solid var(--line); color: var(--ink2); font-size: 12.5px; }
.clone-date-rowlabel { flex: 0 0 auto; }
.clone-date-rowvalue { display: inline-flex; align-items: center; gap: 3px; margin-left: auto; padding: 0 4px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); font-size: 12px; cursor: pointer; }
.clone-date-rowvalue:hover { background: var(--accent-soft); color: var(--accent); }
.clone-date-rowvalue.active { color: var(--accent); }
.clone-date-rowvalue svg.rotated { transform: rotate(180deg); }
.clone-date-rowinputs { display: grid; gap: 6px; padding: 6px 2px 8px 22px; }
.clone-date-rowinputs label { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12px; }
.clone-date-rowinputs input[type="datetime-local"] { min-width: 0; flex: 1; min-height: 30px; padding: 0 7px; border: 1px solid var(--line); border-radius: 6px; background: var(--surface); color: var(--ink); font: inherit; font-size: 12px; }
.clone-detail-date-reminder-popover.is-inline { position: static; width: auto; margin: 2px 0 8px 22px; box-shadow: none; }
.clone-detail-repeat-inline.is-inline { margin: 2px 0 8px 22px; padding-top: 8px; }
.clone-date-toolbar-popover { width: min(560px, calc(100vw - 24px)); }
.clone-toolbar-popover strong { color: var(--ink2); font-size: 12px; }
.clone-toolbar-popover button { min-height: 29px; padding: 5px 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); text-align: left; cursor: pointer; }
.clone-toolbar-popover button:focus-visible,
.clone-toolbar-popover input:focus-visible,
.clone-toolbar-popover select:focus-visible { outline: 2px solid color-mix(in srgb, var(--accent) 60%, transparent); outline-offset: 1px; }
.clone-toolbar-popover button:hover, .clone-toolbar-popover button.active { background: var(--accent-soft); color: var(--accent); }
.clone-toolbar-popover input { width: 100%; box-sizing: border-box; }
.clone-date-popover-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; padding: 3px; border-radius: 7px; background: color-mix(in srgb, var(--line) 45%, var(--paper)); }
.clone-date-popover-tabs button { min-height: 30px; text-align: center; }
.clone-date-popover-tabs button.active { background: var(--card); color: var(--ink); box-shadow: 0 1px 4px rgb(15 23 42 / 10%); }
.clone-date-quick-actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; }
.clone-date-quick-actions button { min-width: 0; padding-inline: 4px; text-align: center; font-size: 11px; }
.clone-date-calendar-head { display: grid; grid-template-columns: 30px 1fr 30px; align-items: center; text-align: center; }
.clone-date-calendar-head button { display: grid; min-height: 28px; place-items: center; padding: 0; font-size: 21px; }
.clone-date-calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; text-align: center; }
.clone-date-calendar-grid > span { padding: 2px 0; color: var(--muted); font-size: 10px; }
.clone-date-calendar-grid > button { display: grid; min-width: 0; min-height: 30px; place-items: center; padding: 0; border-radius: 50%; font-size: 12px; text-align: center; }
.clone-date-calendar-grid > button.other-month { color: var(--muted); opacity: .5; }
.clone-date-calendar-grid > button.today { box-shadow: inset 0 0 0 1px var(--accent); }
.clone-date-calendar-grid > button.selected { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-date-time-field { display: grid; grid-template-columns: 38px minmax(0, 1fr); align-items: center; gap: 8px; min-width: 0; color: var(--muted); font-size: 11px; }
.clone-date-time-field input { min-width: 0; min-height: 31px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); }
.clone-priority-toolbar-popover { width: min(190px, calc(100vw - 24px)); }
.clone-detail-repeat-inline { display: grid; gap: 8px; min-width: 0; padding: 10px 0 0; border-top: 1px solid var(--line); }
.clone-repeat-popover-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.clone-repeat-popover-header .clone-popover-close { display: grid; width: 26px; min-height: 26px; place-items: center; padding: 0; }
.clone-detail-repeat-presets { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.clone-repeat-special-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; color: var(--ink2); font-size: 11px; }
.clone-repeat-special-grid label { display: flex; align-items: center; gap: 5px; }
.clone-repeat-special-grid input { width: 14px; min-height: 14px; }
.clone-detail-repeat-inline .clone-quick-repeat-grid input,
.clone-detail-repeat-inline .clone-quick-repeat-grid select,
.clone-detail-repeat-inline .clone-quick-repeat-end input[type="date"],
.clone-detail-repeat-inline .clone-quick-repeat-end input[type="number"] { min-height: 30px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); font-size: 12px; }
.clone-detail-repeat-inline .clone-quick-repeat-grid label { color: var(--muted); font-size: 11px; }
.clone-detail-repeat-inline .clone-quick-repeat-end { display: flex; flex-wrap: wrap; align-items: center; gap: 7px 12px; padding-top: 8px; border-top: 1px solid var(--line); }
.clone-detail-repeat-inline .clone-quick-repeat-end > span { flex-basis: 100%; }
.clone-detail-repeat-inline .clone-quick-repeat-end label { min-width: 0; white-space: nowrap; }
.clone-detail-repeat-inline .clone-quick-repeat-end input[type="radio"] { width: 14px; min-width: 14px; min-height: 14px; flex: 0 0 14px; margin: 0; }
.clone-detail-repeat-inline .clone-quick-repeat-end label input[type="date"],
.clone-detail-repeat-inline .clone-quick-repeat-end label input[type="number"] { width: auto; max-width: 112px; min-height: 26px; margin-left: 4px; }
.clone-detail-repeat-inline .clone-popover-actions { justify-content: flex-end; padding-top: 3px; border-top: 1px solid var(--line); }
.clone-detail-repeat-inline .clone-popover-actions button { min-width: 58px; text-align: center; }
.clone-detail-repeat-inline .clone-popover-actions .clone-popover-save { background: var(--accent); color: var(--accent-contrast, #fff); }
@media (max-width: 640px) {
  .clone-detail-repeat-presets { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .clone-date-toolbar-popover,
  .clone-date-toolbar-popover { left: 12px !important; }
}
.clone-reminder-popover { right: 48px; left: auto; width: min(250px, calc(100vw - 28px)); }
.clone-reminder-popover-field { display: grid; gap: 4px; color: var(--muted); font-size: 11px; }
.clone-reminder-popover-field select { min-height: 32px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); }
.clone-toolbar-popover .clone-inline-check { display: flex; align-items: center; gap: 7px; color: var(--ink2); font-size: 12px; }
.clone-toolbar-popover .clone-inline-check input { width: 15px; min-height: 15px; margin: 0; }
.clone-toolbar-popover .clone-popover-save { justify-self: end; background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-toolbar-popover .clone-popover-save:hover { background: var(--accent); color: var(--accent-contrast, #fff); }
.clone-reminder-tool.active { color: var(--accent); }
.clone-detail-date-reminder { position: relative; display: grid; }
.clone-detail-date-reminder-trigger { display: flex; align-items: center; gap: 7px; width: 100%; min-height: 30px; padding: 5px 8px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.clone-detail-date-reminder-trigger:hover, .clone-detail-date-reminder-trigger.active { background: var(--accent-soft); color: var(--accent); }
.clone-detail-date-reminder-trigger svg:last-child { margin-left: auto; }
.clone-detail-date-reminder-popover { position: absolute; z-index: 120; top: 0; left: calc(100% + 6px); display: grid; gap: 8px; width: min(248px, calc(100vw - 32px)); padding: 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 14px 30px rgb(15 23 42 / 18%); }
.clone-detail-date-reminder-popover strong { color: var(--ink2); font-size: 12px; }
.clone-detail-date-reminder-popover .clone-inline-check { min-height: 28px; }
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
.clone-detail-form > label:first-child input { min-height: 42px; padding: 9px 12px; border-color: var(--line); font-size: 18px; }
.clone-detail-form > label:nth-child(2) { gap: 8px; }
.clone-detail-form .task-milkdown-editor { border: 1px solid var(--line); border-radius: 9px; overflow: hidden; }
.clone-detail-form .task-milkdown-editor :deep([data-milkdown-root]) { min-height: 220px; background: var(--paper); }
.clone-detail-form .task-rich-toolbar { display: flex; flex-wrap: wrap; gap: 4px; padding: 8px 10px; border-bottom: 1px solid var(--line); background: var(--card); }
.clone-detail-form .task-rich-toolbar button { display: grid; width: 34px; height: 32px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); cursor: pointer; }
.clone-detail-form .task-rich-toolbar button:hover { background: var(--paper); color: var(--accent); }
.clone-detail-form .clone-form-grid { padding: 14px 0; border-top: 1px solid var(--line); }
.clone-detail-form .clone-form-grid select,
.clone-detail-form .clone-form-grid input:not([type="checkbox"]) { min-height: 42px; border-radius: 8px; }
.clone-detail-read { flex: 1 1 auto; min-height: 0; overflow: auto; align-content: start; padding: 16px 22px 108px; gap: 0; scrollbar-gutter: stable; }
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
.clone-detail-description-block { position: relative; min-width: 0; padding: 2px 0; }
.clone-detail-block-add { position: absolute; z-index: 7; top: 0; left: -24px; }
.clone-detail-block-add-trigger { display: grid; width: 20px; height: 20px; place-items: center; padding: 0; border: 0; border-radius: 4px; background: transparent; color: var(--muted); opacity: .55; cursor: pointer; }
.clone-detail-block-add-trigger:hover { background: var(--accent-soft); color: var(--accent); opacity: 1; }
.clone-detail-block-menu { position: absolute; z-index: 30; top: calc(100% + 4px); left: 0; display: grid; min-width: 190px; padding: 6px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 12px 30px rgb(15 23 42 / 18%); }
.clone-detail-block-menu button { display: flex; align-items: center; gap: 9px; min-height: 30px; padding: 5px 8px; border: 0; border-radius: 4px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.clone-detail-block-menu button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-detail-link-picker { position: absolute; z-index: 32; top: calc(100% + 4px); left: 0; display: grid; gap: 4px; width: 250px; max-height: 340px; padding: 8px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 12px 30px rgb(15 23 42 / 18%); }
.clone-detail-link-picker > input { min-height: 30px; padding: 0 8px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12px; }
.clone-detail-link-options { display: grid; gap: 1px; max-height: 220px; overflow: auto; }
.clone-detail-link-options button { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 30px; padding: 5px 7px; border: 0; border-radius: 4px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.clone-detail-link-options button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-detail-link-options small { color: var(--muted); font-size: 11px; }
.clone-detail-link-options span { padding: 4px 7px; color: var(--muted); font-size: 12px; }

.clone-detail-description-block :deep(.task-rich-editor), .clone-detail-description-block :deep(.task-milkdown-editor) { position: static; border: 0; border-radius: 0; background: transparent; overflow: visible; }
.clone-detail-description-block :deep(.task-rich-toolbar) { position: absolute; right: clamp(88px, calc(var(--clone-detail-width) * .2), 112px); bottom: 56px; z-index: 8; display: flex; flex-wrap: nowrap; width: max-content; max-width: calc(100% - 112px); max-height: 0; gap: 3px; overflow-x: auto; overflow-y: hidden; scrollbar-width: none; margin: 0; padding: 0 8px; border: 1px solid transparent; border-radius: 9px; background: var(--card); box-shadow: 0 5px 20px rgb(15 23 42 / 14%); opacity: 0; visibility: hidden; pointer-events: none; transition: max-height 160ms ease, opacity 120ms ease, visibility 0s linear 160ms; }
.clone-detail-description-block :deep(.task-rich-toolbar::-webkit-scrollbar) { display: none; }
.clone-detail-description-block.toolbar-open :deep(.task-rich-toolbar) { max-height: 42px; padding: 4px 8px; border-color: var(--line); opacity: 1; visibility: visible; pointer-events: auto; transition: max-height 160ms ease, opacity 120ms ease, visibility 0s; }
.clone-detail-description-block :deep(.task-rich-toolbar button), .clone-detail-description-block :deep(.task-rich-color) { flex: 0 0 30px; min-width: 30px; height: 30px; }
.clone-detail-description-block :deep(.task-rich-toolbar button:last-child) { display: none; }
.clone-detail-description-block :deep(.task-rich-input) { min-height: 28px; max-height: 48vh; padding: 0; border: 0; resize: none; outline: 0 !important; box-shadow: none; background: transparent; font-size: 13px; line-height: 1.7; field-sizing: content; }
.clone-detail-description-block :deep(.task-rich-input:focus) { border: 0; outline: 0 !important; box-shadow: none; }
.clone-detail-description-block :deep(.task-milkdown-editor [data-milkdown-root]) { min-height: 28px; max-height: 48vh; padding: 0; overflow-y: auto; color: var(--ink2); font-size: 13px; line-height: 1.7; }
.clone-detail-description-block :deep(.task-milkdown-editor .ProseMirror) { min-height: 28px; }
.clone-detail-description-block :deep(.task-rich-hint) { display: none; }
.clone-inline-save-status { display: block; min-height: 15px; padding-left: 1px; color: var(--muted); font-size: 10px; }
.clone-inline-save-status.error { color: var(--danger, #c2413b); }
.clone-detail-description-block :deep(.task-rich-preview) { min-height: 0; padding: 0; overflow: visible; color: var(--ink2); font-size: 13px; line-height: 1.7; }
.clone-detail-description { display: block; width: 100%; padding: 0; border: 0; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; line-height: 1.7; text-align: left; white-space: pre-wrap; cursor: text; }
.clone-detail-description.placeholder { color: var(--muted); }
.clone-detail-properties { display: grid; border-top: 1px solid var(--line); }
.clone-detail-properties label { display: grid; gap: 5px; padding: 8px 0; color: var(--muted); font-size: 12px; }
.clone-detail-properties select, .clone-detail-properties input:not([type="checkbox"]) { min-width: 0; min-height: 34px; padding: 0 9px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink); font: inherit; }
.clone-detail-field-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 10px; }
.clone-detail-field-actions button, .clone-detail-subtask-draft button { min-height: 32px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.clone-detail-field-actions .clone-primary-button { border-color: var(--accent); background: var(--accent); color: white; }
.clone-detail-subtasks { display: grid; gap: 0; padding: 8px 0 12px; }
.clone-detail-focus-summary { display: flex; align-items: center; gap: 7px; padding: 9px 0; color: var(--muted); font-size: 12px; }
.clone-detail-focus-summary svg { color: var(--accent); }
.clone-detail-focus-summary strong { margin-left: auto; color: var(--ink2); font-size: 12px; font-weight: 500; }
.clone-detail-subtasks > header { display: flex; align-items: center; gap: 7px; padding: 4px 0 6px; color: var(--muted); font-size: 12px; }
.clone-detail-subtasks > header span { color: var(--muted); font-variant-numeric: tabular-nums; }
.clone-detail-subtasks > header > button { display: grid; width: 26px; height: 26px; place-items: center; margin-left: auto; border: 0; border-radius: 50%; background: transparent; color: var(--accent); cursor: pointer; }
.clone-detail-subtasks > header > button:hover { background: var(--accent-soft); }
.clone-detail-activity-panel { position: absolute; z-index: 45; right: 10px; bottom: 58px; display: flex; width: min(360px, calc(100% - 20px)); max-height: min(62%, 470px); flex-direction: column; overflow: hidden; border: 1px solid var(--line); border-radius: 10px; background: var(--card); box-shadow: 0 16px 40px rgb(15 23 42 / 22%); }
.clone-detail-activity-panel > header { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 12px; border-bottom: 1px solid var(--line); }
.clone-detail-activity-panel > header strong { color: var(--ink); font-size: 13px; font-weight: 600; }
.clone-detail-activity-panel > header button { display: grid; width: 24px; height: 24px; place-items: center; padding: 0; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.clone-detail-activity-panel > header button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-detail-activity-panel-body { overflow: auto; padding: 9px 12px 12px; }
.clone-detail-activity-panel-body ol { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.clone-detail-activity-panel-body li { display: grid; grid-template-columns: 8px minmax(0, 1fr); gap: 8px; align-items: start; }
.clone-detail-activity-dot { width: 6px; height: 6px; margin-top: 6px; border-radius: 50%; background: color-mix(in srgb, var(--accent) 62%, transparent); }
.clone-detail-activity-panel-body li p { margin: 0; color: var(--ink2); font-size: 12px; line-height: 1.5; }
.clone-detail-activity-panel-body li time { color: var(--muted); font-size: 11px; }
.clone-detail-activity-panel-body .clone-muted { margin: 4px 0; font-size: 12px; }
.clone-export-body { display: grid; gap: 10px; min-width: min(560px, 80vw); }
.clone-export-tabs { display: inline-flex; justify-self: center; padding: 3px; border-radius: 8px; background: color-mix(in srgb, var(--ink) 7%, transparent); }
.clone-export-tabs button { min-height: 28px; padding: 0 18px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.clone-export-tabs button.active { background: var(--card); color: var(--ink); box-shadow: 0 1px 4px rgb(15 23 42 / 14%); }
.clone-export-preview { max-height: 46vh; margin: 0; overflow: auto; padding: 12px; border: 1px solid var(--line); border-radius: 7px; background: var(--paper); color: var(--ink2); font-size: 12px; line-height: 1.6; white-space: pre-wrap; }
.clone-export-body footer { display: flex; justify-content: flex-end; gap: 8px; }
.clone-template-form { display: grid; gap: 10px; }
.clone-template-form input { min-height: 34px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink); font: inherit; }
.clone-template-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.clone-focus-estimate-field { display: grid; gap: 6px; color: var(--ink2); font-size: 12px; }
.clone-focus-estimate-field input { min-height: 34px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink); font: inherit; }
.clone-detail-activity-dot { width: 6px; height: 6px; margin-top: 6px; border-radius: 50%; background: color-mix(in srgb, var(--accent) 62%, transparent); }
.clone-export-body { display: grid; gap: 10px; min-width: min(560px, 80vw); }
.clone-export-tabs { display: inline-flex; justify-self: center; padding: 3px; border-radius: 8px; background: color-mix(in srgb, var(--ink) 7%, transparent); }
.clone-export-tabs button { min-height: 28px; padding: 0 18px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.clone-export-tabs button.active { background: var(--card); color: var(--ink); box-shadow: 0 1px 4px rgb(15 23 42 / 14%); }
.clone-export-preview { max-height: 46vh; margin: 0; overflow: auto; padding: 12px; border: 1px solid var(--line); border-radius: 7px; background: var(--paper); color: var(--ink2); font-size: 12px; line-height: 1.6; white-space: pre-wrap; }
.clone-export-body footer { display: flex; justify-content: flex-end; gap: 8px; }
.clone-template-form { display: grid; gap: 10px; }
.clone-template-form input { min-height: 34px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink); font: inherit; }
.clone-template-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.clone-focus-estimate-field { display: grid; gap: 6px; color: var(--ink2); font-size: 12px; }
.clone-focus-estimate-field input { min-height: 34px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink); font: inherit; }
.clone-detail-subtask-row { position: relative; display: grid; grid-template-columns: 14px 18px minmax(0, 1fr) auto auto; align-items: center; gap: 8px; min-width: 0; min-height: 38px; padding: 0 7px; border-bottom: 1px solid var(--line); color: var(--ink2); }
.clone-detail-subtask-row.drop-target { box-shadow: inset 0 2px 0 var(--accent); }
.clone-detail-subtask-grip { display: grid; place-items: center; color: var(--muted); opacity: 0; cursor: grab; }
.clone-detail-subtask-row:hover .clone-detail-subtask-grip { opacity: .5; }
.clone-detail-subtask-priority { display: inline-flex; color: var(--muted); }
.clone-detail-subtask-priority[data-priority="HIGH"] { color: var(--priority-high); }
.clone-detail-subtask-priority[data-priority="MEDIUM"] { color: var(--priority-medium); }
.clone-detail-subtask-priority[data-priority="LOW"] { color: var(--priority-low); }
.clone-detail-subtask-check { display: grid; width: 17px; height: 17px; flex: 0 0 17px; place-items: center; padding: 0; border: 1.5px solid var(--line2); border-radius: 5px; background: transparent; color: var(--accent); cursor: pointer; }
.clone-detail-subtask-row.completed .clone-detail-subtask-check { border-color: var(--accent); background: var(--accent); color: white; }
.clone-detail-subtask-title { min-width: 0; flex: 1 1 auto; overflow: hidden; padding: 5px 0; border: 0; background: transparent; color: inherit; font: inherit; font-size: 13px; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: text; }
.clone-detail-subtask-row.completed .clone-detail-subtask-title { color: var(--muted); text-decoration: line-through; }
.clone-detail-subtask-title-input { min-width: 0; height: 30px; flex: 1 1 auto; padding: 0 5px; border: 1px solid var(--accent); border-radius: 4px; outline: 0; background: var(--card); color: var(--ink); font: inherit; font-size: 13px; }
.clone-detail-subtask-date { flex: none; padding: 4px 0 4px 6px; border: 0; background: transparent; color: var(--accent); font: inherit; font-size: 12px; cursor: pointer; }
.clone-detail-subtask-add { display: inline-flex; align-items: center; justify-self: start; gap: 6px; min-height: 36px; padding: 0 5px; border: 0; background: transparent; color: var(--accent); font: inherit; font-size: 13px; cursor: pointer; }
.clone-detail-subtask-add:hover { color: color-mix(in srgb, var(--accent) 75%, var(--ink)); }
.clone-detail-subtask-draft { display: flex; align-items: center; gap: 7px; min-width: 0; min-height: 40px; padding: 0 7px; border-bottom: 1px solid var(--line); background: transparent; }
.clone-detail-subtask-draft input { flex: 1 1 auto; min-width: 0; height: 32px; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 13px; }
.clone-detail-subtask-draft > button { display: grid; width: 28px; height: 28px; flex: none; place-items: center; border: 0; border-radius: 50%; background: transparent; color: var(--accent); cursor: pointer; }
.clone-detail-subtask-draft > button:disabled { opacity: .45; cursor: default; }
.clone-detail-properties button { display: flex; justify-content: space-between; gap: 12px; min-width: 0; padding: 9px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; color: var(--muted); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.clone-detail-property { display: flex; justify-content: space-between; gap: 12px; min-width: 0; padding: 9px 0; border-bottom: 1px solid var(--line); color: var(--muted); font-size: 12px; }
.clone-detail-properties button:hover strong { color: var(--accent); }
.clone-detail-properties strong, .clone-detail-property strong { min-width: 0; overflow: hidden; color: var(--ink2); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.clone-detail-edit-fields { display: inline-flex; align-items: center; justify-self: start; gap: 6px; min-height: 32px; padding: 0 2px; border: 0; background: transparent; color: var(--muted); font: inherit; font-size: 12px; cursor: pointer; }
.clone-detail-edit-fields:hover { color: var(--accent); }
.clone-checklist-editor,
.clone-checklist-read { display: grid; gap: 0; border-top: 1px solid var(--line); }
.clone-checklist-editor > header,
.clone-checklist-read > header { display: flex; align-items: center; justify-content: space-between; padding: 12px 0 5px; color: var(--ink2); font-size: 12px; }
.clone-checklist-editor > header button,
.clone-checklist-editor > header button:hover,
.clone-checklist-row { position: relative; display: grid; grid-template-columns: 16px 18px minmax(0, 1fr) 22px 22px; align-items: center; gap: 8px; min-height: 34px; border-bottom: 1px solid color-mix(in srgb, var(--line) 52%, transparent); }
.clone-checklist-remind { display: grid; place-items: center; width: 22px; height: 22px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: var(--muted); opacity: 0; cursor: pointer; }
.clone-checklist-row:hover .clone-checklist-remind { opacity: .7; }
.clone-checklist-remind.active { color: var(--accent); opacity: 1; }
.clone-checklist-remind:hover { background: var(--accent-soft); color: var(--accent); opacity: 1; }
.clone-checklist-reminder-popover { position: absolute; z-index: 30; top: calc(100% + 4px); right: 0; display: grid; gap: 8px; width: 230px; padding: 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 12px 30px rgb(15 23 42 / 18%); }
.clone-checklist-reminder-popover strong { color: var(--ink); font-size: 12px; }
.clone-checklist-reminder-quick { display: flex; gap: 6px; }
.clone-checklist-reminder-quick button { flex: 1; min-height: 26px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink2); font: inherit; font-size: 11px; cursor: pointer; }
.clone-checklist-reminder-quick button:hover { border-color: var(--accent); color: var(--accent); }
.clone-checklist-reminder-popover label { display: grid; gap: 4px; color: var(--ink2); font-size: 11px; }
.clone-checklist-reminder-popover input { min-height: 30px; padding: 0 8px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12px; }
.clone-checklist-reminder-popover .clone-muted { margin: 0; font-size: 11px; }
.clone-checklist-grip { display: grid; place-items: center; width: 16px; height: 20px; padding: 0; border: 0; background: transparent; color: var(--muted); opacity: 0; cursor: grab; }
.clone-checklist-row:hover .clone-checklist-grip { opacity: .55; }
.clone-checklist-remove { display: grid; place-items: center; width: 22px; height: 22px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: var(--muted); opacity: 0; cursor: pointer; }
.clone-checklist-row:hover .clone-checklist-remove { opacity: .7; }
.clone-checklist-row:hover .clone-checklist-remove:hover { background: color-mix(in srgb, var(--down) 12%, transparent); color: var(--down); opacity: 1; }
.clone-checklist-row.drop-target { box-shadow: inset 0 2px 0 var(--accent); }
.clone-checklist-draft { display: grid; grid-template-columns: 16px minmax(0, 1fr); align-items: center; gap: 8px; min-height: 34px; }
.clone-checklist-draft input { min-width: 0; padding: 4px 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 13px; outline: none; }
.clone-checklist-draft .is-placeholder { opacity: .25; }
.clone-checklist-row.drop-target { border-top: 2px solid var(--accent); }
.clone-checklist-row .clone-checklist-grip { cursor: grab; touch-action: none; }
.clone-checklist-row .clone-checklist-grip:active { cursor: grabbing; }
.clone-checklist-row input[type="checkbox"] { flex: 0 0 auto; width: 15px; min-height: 15px; margin: 0; accent-color: var(--accent); }
.clone-checklist-row input[type="text"], .clone-checklist-row > input:not([type]) { flex: 1; min-width: 0; min-height: 30px; padding: 4px 0; border: 0; background: transparent; }
.clone-checklist-row span { flex: 1; min-width: 0; padding: 4px 0; color: var(--ink2); font-size: 13px; cursor: text; }
.clone-checklist-row span:hover { color: var(--ink); }
.clone-checklist-row .clone-detail-checklist-title-input { flex: 1; min-width: 0; min-height: 28px; padding: 3px 0; border: 0; border-bottom: 1px solid color-mix(in srgb, var(--accent) 52%, transparent); outline: 0; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; }
.clone-checklist-row .completed, .clone-checklist-row input.completed { color: var(--muted); text-decoration: line-through; }
.clone-checklist-add { display: inline-flex; align-items: center; gap: 6px; justify-self: start; margin-top: 7px; padding: 5px 0; border: 0; background: transparent; color: var(--accent); font-size: 12px; cursor: pointer; }
.clone-checklist-add:hover { color: var(--accent); }
.clone-checklist-read > header span { color: var(--muted); font-size: 11px; }
.clone-checklist-progress-label { min-width: 30px; text-align: right; }
.clone-detail-checklist-progress {
  height: 4px;
  overflow: hidden;
  margin: 0 0 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--line) 72%, transparent);
}
.clone-detail-checklist-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--accent);
  transition: width 180ms ease;
}
.clone-detail-tags-row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; padding: 10px 0; border-top: 1px solid var(--line); color: var(--muted); }
.clone-detail-tag { position: relative; display: inline-flex; width: fit-content; max-width: 100%; align-items: center; padding: 2px 7px; border-radius: 8px; background: color-mix(in srgb, var(--task-tag-color, var(--accent)) 16%, transparent); color: color-mix(in srgb, var(--task-tag-color, var(--accent)) 76%, var(--ink)); font-size: 10px; line-height: 1.55; }
.clone-detail-tag-remove { position: absolute; top: -5px; right: -5px; display: grid; width: 13px; height: 13px; place-items: center; padding: 0; border: 1px solid color-mix(in srgb, var(--task-tag-color, var(--accent)) 42%, var(--line)); border-radius: 50%; background: var(--card); color: color-mix(in srgb, var(--task-tag-color, var(--accent)) 82%, var(--ink)); opacity: .7; cursor: pointer; transition: opacity 120ms ease; }
.clone-detail-tag:hover .clone-detail-tag-remove { opacity: 1; }
.clone-detail-tag-remove:hover { background: var(--down); border-color: var(--down); color: #fff; }
.clone-detail-tag-name { padding: 0; border: 0; background: transparent; color: inherit; font: inherit; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
.clone-detail-tag-name:hover { text-decoration: underline; }
.clone-detail-tag-remove { display: grid; place-items: center; width: 15px; height: 15px; padding: 0; border: 0; border-radius: 50%; background: color-mix(in srgb, var(--ink) 16%, transparent); color: var(--card); opacity: 0; cursor: pointer; }
.clone-detail-tag:hover .clone-detail-tag-remove { opacity: 1; }
.clone-detail-secondary { display: grid; gap: 16px; padding-top: 12px; }
.clone-detail-footer { position: absolute; right: 0; bottom: 0; left: 0; z-index: 4; display: flex; min-height: 48px; align-items: center; justify-content: space-between; gap: 12px; padding: 0 16px; border-top: 1px solid var(--line); background: color-mix(in srgb, var(--card) 98%, var(--paper)); }
.clone-detail-footer-actions { position: relative; }
.clone-detail-footer button { display: inline-flex; align-items: center; gap: 6px; min-width: 28px; min-height: 30px; padding: 5px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); font: inherit; font-size: 12px; cursor: pointer; }
.clone-detail-footer button:hover, .clone-detail-footer button[aria-pressed="true"] { background: var(--accent-soft); color: var(--accent); }
.clone-detail-footer-list { position: relative; min-width: 0 !important; max-width: 50%; overflow: hidden; }
.clone-detail-list-picker { position: absolute; z-index: 40; bottom: calc(100% + 8px); left: 0; display: grid; gap: 2px; min-width: 230px; max-height: 320px; overflow: auto; padding: 6px; border: 1px solid var(--line); border-radius: 7px; background: var(--card); box-shadow: 0 12px 30px rgb(15 23 42 / 18%); }
.clone-detail-list-picker input { min-height: 30px; margin-bottom: 4px; padding: 0 8px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12px; }
.clone-detail-list-picker button { display: flex; align-items: center; gap: 8px; min-height: 32px; padding: 5px 8px; border: 0; border-radius: 4px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; text-align: left; cursor: pointer; }
.clone-detail-list-picker button:hover { background: var(--accent-soft); color: var(--accent); }
.clone-detail-list-picker button[aria-selected="true"] { color: var(--accent); font-weight: 600; }
.clone-detail-list-picker span { padding: 4px 8px; color: var(--muted); font-size: 12px; }
.clone-detail-list-dot { width: 8px; height: 8px; flex: none; border-radius: 50%; }
.clone-detail-footer-list span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-detail-footer-actions { display: flex; align-items: center; gap: 8px; }
.clone-detail-footer-actions small { color: var(--accent); font-size: 10px; }
.clone-detail-more-menu { position: absolute; right: 8px; bottom: calc(100% + 8px); z-index: 20; width: 246px; max-height: min(72vh, 680px); overflow: auto; padding: 7px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); box-shadow: 0 14px 40px rgb(15 23 42 / 20%); }
.clone-detail-more-menu > button { display: flex; width: 100%; min-height: 38px; align-items: center; gap: 12px; padding: 7px 10px; border-radius: 6px; color: var(--ink2); text-align: left; font-size: 13px; }
.clone-detail-more-menu > button svg:first-child { flex: none; color: var(--ink2); }
.clone-detail-more-menu > button svg:last-child:not(:first-child) { margin-left: auto; color: var(--muted); }
.clone-detail-more-menu > button:hover { background: var(--paper); color: var(--ink); }
.clone-detail-more-menu > button.danger { color: var(--down); }
.clone-detail-more-separator { height: 1px; margin: 5px 0; background: var(--line); }
.clone-detail-more-submenu { display: grid; max-height: 160px; gap: 2px; overflow: auto; padding: 4px 8px 8px 40px; color: var(--muted); font-size: 12px; }
.clone-detail-more-submenu label { display: flex; min-height: 28px; align-items: center; gap: 7px; color: var(--ink2); cursor: pointer; }
.clone-detail-more-submenu input { accent-color: var(--accent); }
.clone-detail-more-submenu > button { min-height: 30px; padding: 5px 6px; border: 0; background: transparent; color: var(--ink2); text-align: left; cursor: pointer; }
.clone-detail-more-submenu > button:hover { color: var(--accent); }
.clone-detail-attachment-input { display: none; }
:global(.clone-parent-picker-dialog) { width: min(460px, calc(100vw - 32px)); }
.clone-parent-picker { display: grid; gap: 8px; min-width: 0; }
.clone-parent-picker-search { display: flex; min-height: 36px; align-items: center; gap: 8px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; color: var(--muted); }
.clone-parent-picker-search input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 12px; }
.clone-parent-picker-none { min-height: 32px; padding: 0 9px; border: 0; border-radius: 5px; background: transparent; color: var(--muted); text-align: left; cursor: pointer; }
.clone-parent-picker-none:hover, .clone-parent-picker-list > button:hover { background: var(--paper); color: var(--ink); }
.clone-parent-picker-list { display: grid; max-height: min(42vh, 320px); gap: 2px; overflow: auto; }
.clone-parent-picker-list > button { display: grid; min-height: 42px; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 5px 9px; border: 0; border-radius: 5px; background: transparent; color: var(--ink2); text-align: left; cursor: pointer; }
.clone-parent-picker-list > button span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clone-parent-picker-list > button small { color: var(--muted); font-size: 11px; }
.clone-parent-picker-list > p { margin: 0; padding: 14px 9px; color: var(--muted); font-size: 12px; text-align: center; }
.clone-detail-comment-composer { position: absolute; right: 12px; bottom: 48px; left: 12px; z-index: 4; display: flex; min-height: 46px; align-items: center; gap: 8px; padding: 6px 8px 6px 12px; border: 1px solid color-mix(in srgb, var(--accent) 58%, var(--line)); border-radius: 7px; background: var(--card); box-shadow: 0 3px 12px rgb(15 23 42 / 8%); }
.clone-detail-comment-composer textarea { width: 100%; min-width: 0; min-height: 30px; max-height: 96px; padding: 6px 0; border: 0; outline: 0; resize: vertical; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; }
.clone-detail-comment-composer button { display: grid; width: 30px; height: 30px; flex: 0 0 30px; place-items: center; border: 0; border-radius: 5px; background: var(--accent); color: var(--accent-contrast, #fff); cursor: pointer; }
.clone-detail-comment-composer button:disabled { opacity: .5; cursor: default; }
.clone-detail-comment-composer .clone-error { position: absolute; right: 0; bottom: calc(100% + 4px); left: 0; margin: 0; padding: 5px 8px; border-radius: 5px; background: var(--card); }
.clone-detail-description {
  margin: 0;
  white-space: pre-wrap;
  color: var(--ink2);
  font-size: 13px;
  line-height: 1.7;
}
.clone-detail-description.placeholder { color: var(--muted); }
.clone-priority-toolbar-popover { right: 12px; left: auto; width: 168px; min-width: 168px; }
.clone-priority-toolbar-popover .clone-priority-option { display: flex; width: 100%; align-items: center; gap: 9px; white-space: nowrap; }
.clone-priority-option i { display: block; width: 9px; height: 9px; flex: 0 0 9px; border-radius: 50%; background: var(--muted); }
.clone-priority-option.priority-high i { background: var(--priority-high); }
.clone-priority-option.priority-medium i { background: var(--priority-medium); }
.clone-priority-option.priority-low i { background: var(--priority-low); }
.clone-priority-option.priority-none i { border: 1px solid var(--muted); background: transparent; }
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
    grid-template-columns: var(--clone-sidebar-width, 248px) minmax(420px, 1fr);
  }
  .clone-task-shell.sidebar-hidden { grid-template-columns: 0 minmax(420px, 1fr); }
  .clone-detail-pane {
    grid-column: 2;
    border-top: 1px solid var(--line);
  }
  .clone-task-shell.workspace-overlay-view { grid-template-columns: var(--clone-sidebar-width, 248px) minmax(0, 1fr); }
  .clone-task-shell.workspace-overlay-view.sidebar-hidden { grid-template-columns: 0 minmax(0, 1fr); }
  .clone-task-shell.detail-open {
    grid-template-rows: minmax(0, 1fr) minmax(320px, auto);
  }
  .clone-detail-pane {
    grid-row: 2;
  }
}
/* Tablet widths keep the desktop sidebar and task list, while details float above the list. */
@media (min-width: 901px) and (max-width: 1279px) {
  .clone-task-shell {
    grid-template-columns: var(--clone-sidebar-width, 248px) minmax(0, 1fr);
  }
  .clone-task-shell.sidebar-hidden {
    grid-template-columns: 0 minmax(0, 1fr);
  }
  .clone-detail-divider,
  .clone-detail-pane.is-empty {
    display: none;
  }
  .clone-detail-pane:not(.is-empty) {
    position: fixed;
    z-index: 280;
    top: 0;
    right: 0;
    bottom: 0;
    display: block;
    width: min(var(--clone-detail-width, 390px), 46vw);
    border-left: 1px solid var(--line);
    box-shadow: -12px 0 32px rgb(15 23 42 / 16%);
  }
}
@media (max-width: 900px) {
  .clone-quick-add { align-items: flex-start; min-height: 46px; padding-top: 8px; padding-bottom: 8px; }
  .clone-quick-add-form { row-gap: 6px; column-gap: 3px; }
  .clone-quick-add form input { flex-basis: 100%; height: 27px; }
  .clone-quick-add form .clone-quick-action { min-height: 28px; padding: 0 3px; }
  .clone-quick-add form .clone-quick-list-action { max-width: 78px; overflow: hidden; }
  .clone-quick-action-panel { margin-left: 0; }
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
  .clone-sidebar-resize { display: none; }
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
  .clone-task-main.quick-action-open { z-index: 250; overflow: visible; }
  .clone-task-header {
    align-items: flex-start;
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
  .clone-detail-pane,
  .clone-detail-divider { display: none !important; }
  .clone-batch-backdrop { position: fixed; z-index: 279; inset: 72px 0 0; display: block; border: 0; background: rgb(15 23 42 / 32%); }
  .clone-detail-pane.is-batch { position: fixed; z-index: 280; top: 72px; right: 0; bottom: 0; display: block !important; width: min(420px, 92vw); max-height: none; margin: 0; overflow: auto; border: 0; border-left: 1px solid var(--line); border-radius: 0; box-shadow: -12px 0 32px rgb(15 23 42 / 16%); animation: clone-batch-drawer-in .22s cubic-bezier(.22, 1, .36, 1); }
  .clone-activity-backdrop { position: fixed; z-index: 279; inset: 72px 0 0; display: block; border: 0; background: rgb(15 23 42 / 32%); }
  .clone-detail-pane.is-list-activity { position: fixed; z-index: 280; top: 72px; right: 0; bottom: 0; display: block !important; width: min(420px, 92vw); max-height: none; margin: 0; overflow: hidden; border: 0; border-left: 1px solid var(--line); border-radius: 0; box-shadow: -12px 0 32px rgb(15 23 42 / 16%); animation: clone-batch-drawer-in .22s cubic-bezier(.22, 1, .36, 1); }
  .clone-batch-panel { padding: 0 16px 24px; }
  .clone-batch-panel-head { position: sticky; top: 0; z-index: 1; min-height: 60px; background: var(--card); }
  .clone-batch-fields { margin-top: 12px; }
  .clone-batch-actions button { min-height: 68px; }
  .clone-task-shell.workspace-overlay-view.workspace-detail-open .clone-detail-pane {
    display: block !important;
    top: 8px;
    right: 12px;
    bottom: 8px;
    left: 12px;
    width: auto;
    max-height: none;
    margin: 0;
    border-radius: 10px;
    transform: none;
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
@keyframes clone-batch-drawer-in { from { transform: translateX(24px); opacity: .75; } to { transform: translateX(0); opacity: 1; } }
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
@media (max-width: 1100px) {
  .clone-task-main { padding-right: 20px; padding-left: 20px; }
  .clone-task-metadata { max-width: 46%; gap: 5px; font-size: 11px; }
  .clone-task-list-link { max-width: 82px; }
}
@container task-meta (max-width: 860px) {
  .clone-task-tags > .clone-task-tag-wrap:nth-child(n + 3) { display: none; }
  .clone-task-tags:has(> .clone-task-tag-wrap:nth-child(3)) .clone-task-tag-count { display: inline-flex; font-size: 0; }
  .clone-task-tags:has(> .clone-task-tag-wrap:nth-child(3)) .clone-task-tag-count::before { content: "+" attr(data-h2); font-size: 11px; }
}
@container task-meta (max-width: 720px) {
  .clone-task-tags > .clone-task-tag-wrap:nth-child(n + 2) { display: none; }
  .clone-task-tags:has(> .clone-task-tag-wrap:nth-child(2)) .clone-task-tag-count { display: inline-flex; font-size: 0; }
  .clone-task-tags:has(> .clone-task-tag-wrap:nth-child(2)) .clone-task-tag-count::before { content: "+" attr(data-h1); font-size: 11px; }
}
@container task-meta (max-width: 640px) {
  .clone-task-tags > .clone-task-tag-wrap { display: none !important; }
  .clone-task-tags:has(> .clone-task-tag-wrap:nth-child(2)) .clone-task-tag-count { display: inline-flex; font-size: 0; }
  .clone-task-tags:has(> .clone-task-tag-wrap:nth-child(2)) .clone-task-tag-count::before { content: attr(data-total); font-size: 11px; }
  .clone-task-tags:not(:has(> .clone-task-tag-wrap:nth-child(2))) .clone-task-tag-count { display: inline-flex; font-size: 0; }
  .clone-task-tags:not(:has(> .clone-task-tag-wrap:nth-child(2))) .clone-task-tag-count::before { content: attr(data-total); font-size: 11px; }
}
@media (max-width: 760px) {
  .clone-task-main { padding-right: 14px; padding-left: 14px; }
  .clone-completion-filters { gap: 6px; margin: 0 0 15px 0; }
  .clone-completion-filter-trigger { min-width: 0; min-height: 32px; gap: 4px; padding: 0 8px; font-size: 11px; }
  .clone-completion-filter-menu { max-width: calc(100vw - 28px); }
  .clone-completion-list-menu { width: min(280px, calc(100vw - 28px)); }
  .clone-check { width: 30px; height: 32px; min-width: 30px; min-height: 32px; }
  .clone-check::before { width: 12px; height: 12px; }
  .clone-check svg { width: 9px; height: 9px; }
  .clone-task-metadata { max-width: 38%; }
  .clone-task-metadata > .clone-task-indicator:nth-of-type(n + 2),
  .clone-task-metadata > .clone-task-list-link { display: none; }
  .clone-task-tag { max-width: 68px; padding: 2px 6px; }
  .clone-title-block h1 { font-size: 19px; }
}
@media print {
  .clone-task-shell { display: block !important; width: 100% !important; height: auto !important; overflow: visible !important; }
  .clone-task-sidebar, .clone-detail-pane, .clone-header-actions, .clone-quick-add, .clone-quick-attachments, .clone-quick-attachment-state, .clone-row-more, .clone-drag-handle, .clone-check, .clone-select-box, .clone-task-list-link { display: none !important; }
  .clone-task-main { display: block !important; width: 100% !important; max-width: none !important; height: auto !important; overflow: visible !important; padding: 0 !important; }
  .clone-task-metadata, .clone-task-row em { display: none !important; }
  .clone-task-row.print-content .clone-print-description { display: block; overflow: visible; white-space: normal; }
  .clone-task-row, .clone-task-group { break-inside: avoid; }
}
</style>
