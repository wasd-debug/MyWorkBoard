<template>
  <Milkdown />
</template>

<script setup>
import { Editor, defaultValueCtx, rootCtx } from '@milkdown/kit/core'
import { toggleEmphasisCommand, toggleStrongCommand, toggleInlineCodeCommand, wrapInHeadingCommand, wrapInBulletListCommand, wrapInOrderedListCommand, wrapInBlockquoteCommand, insertHrCommand } from '@milkdown/kit/preset/commonmark'
import { toggleStrikethroughCommand } from '@milkdown/kit/preset/gfm'
import { history } from '@milkdown/kit/plugin/history'
import { listener, listenerCtx } from '@milkdown/kit/plugin/listener'
import { trailing } from '@milkdown/kit/plugin/trailing'
import { commonmark } from '@milkdown/kit/preset/commonmark'
import { gfm } from '@milkdown/kit/preset/gfm'
import { callCommand, getMarkdown, replaceAll } from '@milkdown/kit/utils'
import { Milkdown } from '@milkdown/vue'
import { useEditor } from '@milkdown/vue'
import { watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'blur'])
let crepeInstance

const { loading } = useEditor((root) => {
  crepeInstance = Editor.make()
    .config((ctx) => {
      ctx.set(rootCtx, root)
      ctx.set(defaultValueCtx, props.modelValue)
    })
    .use(commonmark)
    .use(gfm)
    .use(listener)
    .use(history)
    .use(trailing)
    .config((ctx) => {
      const manager = ctx.get(listenerCtx)
      manager.markdownUpdated((_ctx, markdown) => emit('update:modelValue', markdown))
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

defineExpose({ runToolbar })
</script>
