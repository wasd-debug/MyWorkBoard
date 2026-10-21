import test from 'node:test'
import assert from 'node:assert/strict'
import { mergeSidebarPreferenceChanges } from './taskSidebarPreferences.js'

test('sidebar preference startup changes merge onto the latest server snapshot', () => {
  const before = {
    hiddenListIds: ['local-hidden'], pinnedListIds: [], conditionalSystemViews: [],
    collapsedSections: { lists: false, tags: false },
  }
  const current = {
    hiddenListIds: [], pinnedListIds: ['new-pin'], conditionalSystemViews: ['today'],
    collapsedSections: { lists: true, tags: false },
  }
  const server = {
    hiddenListIds: ['local-hidden', 'remote-hidden'], pinnedListIds: ['remote-pin'], conditionalSystemViews: ['tomorrow'],
    collapsedSections: { lists: false, tags: true, filters: true },
  }

  assert.deepEqual(mergeSidebarPreferenceChanges(server, before, current), {
    hiddenListIds: ['remote-hidden'], pinnedListIds: ['remote-pin', 'new-pin'],
    conditionalSystemViews: ['tomorrow', 'today'],
    collapsedSections: { lists: true, tags: true, filters: true },
    hiddenSystemViews: [], pinnedSystemViews: [], hiddenTagIds: [], pinnedTagIds: [], hiddenFilterIds: [], pinnedFilterIds: [],
  })
})
