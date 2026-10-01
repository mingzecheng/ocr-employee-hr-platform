import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, beforeEach } from 'vitest'
import ElementPlus from 'element-plus'
import router from '../router'
import LoginView from './LoginView.vue'

describe('LoginView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders the credential form and demo account hint', () => {
    const wrapper = mount(LoginView, { global: { plugins: [ElementPlus, router] } })
    expect(wrapper.find('input[autocomplete="username"]').exists()).toBe(true)
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('demo-admin')
  })
})
