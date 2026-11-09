import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({ codes: new Set<string>() }))

vi.mock('@/stores/permission', () => ({
  usePermissionStore: () => ({
    has: (code: string) => state.codes.has(code),
  }),
}))

import { permission } from './permission'

function mountButton(value: string | string[]) {
  const Button = defineComponent({
    template: '<button v-permission="value">发货</button>',
    props: { value: { type: [String, Array], required: true } },
  })
  return mount(Button, {
    props: { value },
    global: { directives: { permission } },
  })
}

describe('v-permission', () => {
  it('shows the element when the store allows the code', () => {
    state.codes = new Set(['admin:order:ship'])
    const wrapper = mountButton('admin:order:ship')
    expect((wrapper.element as HTMLElement).style.display).toBe('')
    wrapper.unmount()
  })

  it('hides the element when the code is missing', () => {
    state.codes = new Set()
    const wrapper = mountButton('admin:order:ship')
    expect((wrapper.element as HTMLElement).style.display).toBe('none')
    wrapper.unmount()
  })

  it('accepts an array and allows any matching code', () => {
    state.codes = new Set(['admin:order:view'])
    const wrapper = mountButton(['admin:order:ship', 'admin:order:view'])
    expect((wrapper.element as HTMLElement).style.display).toBe('')
    wrapper.unmount()
  })
})
