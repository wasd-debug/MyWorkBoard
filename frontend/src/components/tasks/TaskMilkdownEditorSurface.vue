<template>
  <Milkdown />
</template>

<script setup>
import { Editor, defaultValueCtx, editorViewCtx, rootCtx } from '@milkdown/kit/core'
import { headingSchema, toggleEmphasisCommand, toggleLinkCommand, toggleStrongCommand, toggleInlineCodeCommand, wrapInHeadingCommand, wrapInBulletListCommand, wrapInOrderedListCommand, wrapInBlockquoteCommand, insertHrCommand } from '@milkdown/kit/preset/commonmark'
import { extendListItemSchemaForTask, toggleStrikethroughCommand } from '@milkdown/kit/preset/gfm'
import { history } from '@milkdown/kit/plugin/history'
import { listener, listenerCtx } from '@milkdown/kit/plugin/listener'
import { SlashProvider } from '@milkdown/kit/plugin/slash'
import { Plugin, TextSelection } from '@milkdown/kit/prose/state'
import { trailing } from '@milkdown/kit/plugin/trailing'
import { commonmark } from '@milkdown/kit/preset/commonmark'
import { gfm } from '@milkdown/kit/preset/gfm'
import { $prose, callCommand, getMarkdown, replaceAll } from '@milkdown/kit/utils'
import { Milkdown } from '@milkdown/vue'
import { useEditor } from '@milkdown/vue'
import { watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'focus', 'blur'])
let crepeInstance
let slashProvider
let slashRange
let slashItems = []
let slashActiveIndex = 0
let slashQuery = ''

const slashCommands = [
  { key: 'heading1', label: '标题 1', detail: '大标题', aliases: ['h1', '标题'], kind: 'heading', payload: 1 },
  { key: 'heading2', label: '标题 2', detail: '中标题', aliases: ['h2', '标题'], kind: 'heading', payload: 2 },
  { key: 'heading3', label: '标题 3', detail: '小标题', aliases: ['h3', '标题'], kind: 'heading', payload: 3 },
  { key: 'list', label: '无序列表', detail: '创建项目符号列表', aliases: ['列表', '项目符号'], command: wrapInBulletListCommand.key },
  { key: 'ordered', label: '有序列表', detail: '创建编号列表', aliases: ['编号', '列表'], command: wrapInOrderedListCommand.key },
  { key: 'check', label: '检查项', detail: '创建待办检查项', aliases: ['待办', '任务'], kind: 'check' },
  { key: 'quote', label: '引用', detail: '创建引用块', aliases: ['blockquote'], command: wrapInBlockquoteCommand.key },
  { key: 'bold', label: '加粗', detail: '强调选中文本或后续输入', aliases: ['strong'], command: toggleStrongCommand.key },
  { key: 'italic', label: '斜体', detail: '倾斜文本', aliases: ['em'], command: toggleEmphasisCommand.key },
  { key: 'strike', label: '删除线', detail: '删除线文本', aliases: ['strikethrough'], command: toggleStrikethroughCommand.key },
  { key: 'code', label: '行内代码', detail: '代码文本', aliases: ['inline code'], command: toggleInlineCodeCommand.key },
  { key: 'divider', label: '分割线', detail: '插入水平分割线', aliases: ['hr', 'horizontal rule'], command: insertHrCommand.key },
  { key: 'time', label: '增加时间', detail: '插入当前日期和时间', aliases: ['日期', '时间'], kind: 'time' },
  { key: 'link', label: '链接', detail: '插入一个链接文本', aliases: ['url'], kind: 'link' }
]

const { loading } = useEditor((root) => {
  const menu = document.createElement('div')
  menu.className = 'task-milkdown-slash-menu'
  menu.setAttribute('role', 'listbox')
  menu.setAttribute('aria-label', 'Markdown 插入菜单')
  menu.dataset.show = 'false'
  menu.addEventListener('pointerdown', event => event.preventDefault())
  menu.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target.closest('[data-slash-index]') : null
    if (!target) return
    const item = slashItems[Number(target.getAttribute('data-slash-index'))]
    if (item) applySlashCommand(item)
  })

  slashProvider = new SlashProvider({
    content: menu,
    root: document.body,
    debounce: 80,
    offset: 6,
    floatingUIOptions: { strategy: 'fixed' },
    shouldShow(view) {
      const text = slashProvider?.getContent(view) || ''
      const match = text.match(/(?:^|\s)\/([^\s/]*)$/u)
      if (!match) return false
      const query = match[1].toLocaleLowerCase()
      if (query !== slashQuery) slashActiveIndex = 0
      slashQuery = query
      const slashOffset = text.lastIndexOf('/')
      slashRange = { from: view.state.selection.from - (text.length - slashOffset), to: view.state.selection.from }
      slashItems = slashCommands.filter(item => `${item.key} ${item.label} ${item.detail} ${item.aliases.join(' ')}`.toLocaleLowerCase().includes(query))
      if (slashActiveIndex >= slashItems.length) slashActiveIndex = 0
      renderSlashMenu(menu, slashItems)
      return slashItems.length > 0
    }
  })

  crepeInstance = Editor.make()
    .config((ctx) => {
      ctx.set(rootCtx, root)
      ctx.set(defaultValueCtx, props.modelValue)
    })
    .use(commonmark)
    .use(gfm)
    .use($prose(() => new Plugin({
      view: () => ({
        update: (view, previousState) => slashProvider.update(view, previousState),
        destroy: () => {
          slashProvider.destroy()
          slashProvider.element.remove()
        }
      }),
      props: {
        handleDOMEvents: {
          blur: () => {
            slashProvider.hide()
            return false
          },
          click: (view, event) => {
            const target = event.target instanceof Element ? event.target.closest('li[data-item-type="task"]') : null
            if (!target) return false
            const itemRect = target.getBoundingClientRect()
            const textRect = target.querySelector(':scope > p')?.getBoundingClientRect() || itemRect
            const checkboxHit = event.clientX >= itemRect.left - 24
              && event.clientX < textRect.left
              && event.clientY >= textRect.top
              && event.clientY < textRect.top + 24
            if (!checkboxHit) return false

            const position = view.posAtDOM(target, 0)
            const resolved = view.state.doc.resolve(position)
            for (let depth = resolved.depth; depth > 0; depth -= 1) {
              const listItem = resolved.node(depth)
              if (listItem.type.name !== 'list_item') continue
              if (listItem.attrs.checked == null) return false
              const nodePosition = resolved.before(depth)
              const taskItemType = extendListItemSchemaForTask.type(crepeInstance.ctx)
              view.dispatch(view.state.tr.setNodeMarkup(nodePosition, taskItemType, {
                ...listItem.attrs,
                checked: !listItem.attrs.checked
              }))
              event.preventDefault()
              return true
            }
            return false
          }
        },
        handleKeyDown: (_view, event) => {
          if (slashProvider.element.dataset.show !== 'true' || !slashItems.length) return false
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault()
            const direction = event.key === 'ArrowDown' ? 1 : -1
            slashActiveIndex = (slashActiveIndex + direction + slashItems.length) % slashItems.length
            renderSlashMenu(menu, slashItems)
            return true
          }
          if (event.key === 'Enter' || event.key === 'Tab') {
            event.preventDefault()
            applySlashCommand(slashItems[slashActiveIndex])
            return true
          }
          if (event.key === 'Escape') {
            event.preventDefault()
            slashProvider.hide()
            return true
          }
          return false
        }
      }
    })))
    .use(listener)
    .use(history)
    .use(trailing)
    .config((ctx) => {
      const manager = ctx.get(listenerCtx)
      manager.markdownUpdated((_ctx, markdown) => emit('update:modelValue', markdown))
      manager.focus(() => emit('focus'))
      manager.blur(() => emit('blur'))
    })

  return crepeInstance
})

watch([() => props.modelValue, loading], ([markdown, isLoading]) => {
  if (isLoading || !crepeInstance || crepeInstance.action(getMarkdown()) === markdown) return
  crepeInstance.action(replaceAll(markdown, true))
})

function runToolbar(key) {
  if (!crepeInstance) return
  const commands = {
    heading: [wrapInHeadingCommand.key, 2],
    bold: [toggleStrongCommand.key],
    list: [wrapInBulletListCommand.key],
    ordered: [wrapInOrderedListCommand.key],
    italic: [toggleEmphasisCommand.key],
    strike: [toggleStrikethroughCommand.key],
    divider: [insertHrCommand.key],
    code: [toggleInlineCodeCommand.key],
    quote: [wrapInBlockquoteCommand.key]
  }
  const [command, payload] = commands[key] || []
  if (command) crepeInstance.action(callCommand(command, payload))
}

function renderSlashMenu(menu, items) {
  menu.replaceChildren(...items.map((item, index) => {
    const option = document.createElement('button')
    option.type = 'button'
    option.className = 'task-milkdown-slash-option'
    option.setAttribute('role', 'option')
    option.setAttribute('aria-selected', String(index === slashActiveIndex))
    option.dataset.slashIndex = String(index)
    option.innerHTML = `<span class="task-milkdown-slash-label"></span><small class="task-milkdown-slash-detail"></small>`
    option.querySelector('.task-milkdown-slash-label').textContent = item.label
    option.querySelector('.task-milkdown-slash-detail').textContent = item.detail
    return option
  }))
}

function applySlashCommand(item) {
  const view = crepeInstance?.ctx.get(editorViewCtx)
  if (!view || !slashRange || !item) return
  const { from, to } = slashRange
  let transaction = view.state.tr.delete(from, to)
  if (item.kind === 'time') {
    const value = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date())
    transaction = transaction.insertText(value, from)
    view.dispatch(transaction)
    view.focus()
  } else if (item.kind === 'link') {
    const text = '链接文本'
    transaction = transaction.insertText(text, from).setSelection(TextSelection.create(transaction.doc, from, from + text.length))
    view.dispatch(transaction)
    crepeInstance.action(callCommand(toggleLinkCommand.key, { href: 'https://' }))
    const current = view.state
    view.dispatch(current.tr.setSelection(TextSelection.create(current.doc, from + text.length)))
    view.focus()
  } else {
    view.dispatch(transaction)
    view.focus()
    if (item.kind === 'heading') {
      const state = view.state
      const selection = state.selection.$from
      for (let depth = selection.depth; depth > 0; depth -= 1) {
        if (!selection.node(depth).isTextblock) continue
        const position = selection.before(depth)
        const heading = headingSchema.type(crepeInstance.ctx)
        view.dispatch(state.tr.setNodeMarkup(position, heading, { level: item.payload, id: '' }))
        break
      }
    } else if (item.kind === 'check') {
      crepeInstance.action(callCommand(wrapInBulletListCommand.key))
      const state = view.state
      const resolved = state.selection.$from
      for (let depth = resolved.depth; depth > 0; depth -= 1) {
        if (resolved.node(depth).type.name !== 'list_item') continue
        const position = resolved.before(depth)
        const listItem = resolved.node(depth)
        const taskItemType = extendListItemSchemaForTask.type(crepeInstance.ctx)
        view.dispatch(state.tr.setNodeMarkup(position, taskItemType, { ...listItem.attrs, checked: false }))
        break
      }
    } else if (item.command) {
      crepeInstance.action(callCommand(item.command, item.payload))
    }
  }
  slashProvider.hide()
  slashRange = null
  slashQuery = ''
}

defineExpose({ runToolbar })
</script>

<style>
.task-milkdown-slash-menu {
  position: fixed;
  z-index: 10000;
  width: 248px;
  max-height: min(320px, calc(100vh - 24px));
  overflow: auto;
  padding: 5px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--card);
  box-shadow: 0 8px 28px rgb(15 23 42 / 18%);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 100ms ease, visibility 100ms ease;
}

.task-milkdown-slash-menu[data-show="true"] {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
}

.task-milkdown-slash-option {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  padding: 6px 9px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.task-milkdown-slash-option[aria-selected="true"],
.task-milkdown-slash-option:hover {
  background: var(--hover, rgb(100 116 139 / 10%));
}

.task-milkdown-slash-option small {
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}
</style>
