import { describe, expect, it } from 'vitest'
import type { FormItemRule } from 'element-plus'
import { notAfter, notBefore, sameAs } from './live-validation'

function validate(rule: FormItemRule, value: unknown, source: Record<string, unknown>) {
  return new Promise<void>((resolve, reject) => {
    if (!rule.validator) return reject(new Error('validator is missing'))
    const validator = rule.validator as unknown as (...args: unknown[]) => void
    validator(rule, value, (error?: unknown) => error ? reject(error) : resolve(), source)
  })
}

describe('cross-field validation', () => {
  it('compares confirmation against the current password value', async () => {
    let password = 'secret123'
    const rule = sameAs(() => password, '两次输入的密码不一致。')

    await expect(validate(rule, 'secret123', { passwordConfirm: 'secret123' })).resolves.toBeUndefined()
    password = 'updated123'
    await expect(validate(rule, 'updated123', { passwordConfirm: 'updated123' })).resolves.toBeUndefined()
    await expect(validate(rule, 'secret123', { passwordConfirm: 'secret123' })).rejects.toThrow('两次输入的密码不一致。')
  })

  it('rejects end dates before the start and start dates after the end', async () => {
    const startDate = '2026-06-10'
    const endDate = '2026-06-20'

    await expect(validate(
      notBefore(() => startDate, '结束日期不能早于开始日期。'),
      '2026-06-09',
      { endDate: '2026-06-09' },
    )).rejects.toThrow('结束日期不能早于开始日期。')
    await expect(validate(
      notAfter(() => endDate, '开始日期不能晚于结束日期。'),
      '2026-06-21',
      { startDate: '2026-06-21' },
    )).rejects.toThrow('开始日期不能晚于结束日期。')
  })
})
