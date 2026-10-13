import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { useAuthStore } from '../stores/auth'
import router from '../router'
import LoginView from './LoginView.vue'

describe('LoginView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders blank credentials without exposing demo accounts', () => {
    const wrapper = mount(LoginView, { global: { plugins: [ElementPlus, router] } })

    const username = wrapper.find('input[autocomplete="username"]')
    const password = wrapper.find('input[type="password"]')
    expect(username.exists()).toBe(true)
    expect(password.exists()).toBe(true)
    expect((username.element as HTMLInputElement).value).toBe('')
    expect((password.element as HTMLInputElement).value).toBe('')
    expect(wrapper.find('[data-test="demo-account"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('demo-admin')
  })

  it('shows a clear inline message when the backend cannot be reached', async () => {
    const wrapper = mount(LoginView, { global: { plugins: [ElementPlus, router] } })
    const auth = useAuthStore()
    vi.spyOn(auth, 'login').mockRejectedValue(Object.assign(new Error('Network Error'), { isNetworkError: true }))

    await wrapper.get('input[autocomplete="username"]').setValue('local-account')
    await wrapper.get('input[autocomplete="current-password"]').setValue('local-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(auth.login).toHaveBeenCalledWith('local-account', 'local-password')
    expect(wrapper.get('[role="alert"]').text()).toContain('无法连接人事服务')
  })
})
