import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import RegisterView from './RegisterView.vue'

function mountRegisterView() {
  const emptyRoute = { render: () => null }
  const testRouter = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: emptyRoute },
      { path: '/login', component: emptyRoute },
      { path: '/workspaces', component: emptyRoute },
    ],
  })
  return mount(RegisterView, { global: { plugins: [ElementPlus, testRouter] } })
}

describe('RegisterView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('asks employees for four fields and keeps employee number system-generated', () => {
    const wrapper = mountRegisterView()

    expect(wrapper.findAll('input')).toHaveLength(4)
    expect(wrapper.find('input[autocomplete="username"]').exists()).toBe(false)
    expect(wrapper.findAll('input[autocomplete="new-password"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('员工编号由系统生成')
  })

  it('submits only the profile details and password', async () => {
    const wrapper = mountRegisterView()
    const auth = useAuthStore()
    const register = vi.spyOn(auth, 'register').mockResolvedValue(undefined)
    const passwordInputs = wrapper.findAll('input[autocomplete="new-password"]')

    await wrapper.get('input[autocomplete="name"]').setValue(' 王敏 ')
    await wrapper.get('input[autocomplete="tel"]').setValue('138 0013-8000')
    await passwordInputs[0].setValue('ValidPass-2026')
    await passwordInputs[1].setValue('ValidPass-2026')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(register).toHaveBeenCalledWith({
      name: '王敏',
      phone: '13800138000',
      password: 'ValidPass-2026',
    })
    expect(register.mock.calls[0][0]).not.toHaveProperty('employeeNo')
  })
})
