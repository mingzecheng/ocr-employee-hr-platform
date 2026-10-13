import type { FormItemRule } from 'element-plus'

const live = (rule: FormItemRule): FormItemRule => ({ ...rule, trigger: 'change' })

export function requiredText(message: string): FormItemRule {
  return live({ required: true, whitespace: true, message })
}

export function requiredValue(message: string): FormItemRule {
  return live({
    validator: (_rule, value, callback) => value === '' || value === null || value === undefined
      ? callback(new Error(message))
      : callback(),
  })
}

export function positiveValue(message = '请选择有效项目。'): FormItemRule {
  return live({
    validator: (_rule, value, callback) => Number.isInteger(Number(value)) && Number(value) > 0
      ? callback()
      : callback(new Error(message)),
  })
}

export function requiredList(message: string): FormItemRule {
  return live({
    validator: (_rule, value, callback) => Array.isArray(value) && value.length > 0
      ? callback()
      : callback(new Error(message)),
  })
}

export function maxLength(max: number, message = `不能超过 ${max} 个字符`): FormItemRule {
  return live({
    validator: (_rule, value, callback) => String(value ?? '').length <= max
      ? callback()
      : callback(new Error(message)),
  })
}

export function patternRule(pattern: RegExp, message: string, optional = false): FormItemRule {
  return live({
    validator: (_rule, value, callback) => {
      const text = String(value ?? '')
      if (optional && !text) return callback()
      return pattern.test(text) ? callback() : callback(new Error(message))
    },
  })
}

export function positiveId(message = '请输入正整数 ID', optional = true): FormItemRule {
  return live({
    validator: (_rule, value, callback) => {
      const text = String(value ?? '').trim()
      if (optional && !text) return callback()
      return /^[1-9]\d*$/.test(text) ? callback() : callback(new Error(message))
    },
  })
}

export function boundedNumber(min: number, max: number, message: string): FormItemRule {
  return live({
    validator: (_rule, value, callback) => {
      const number = Number(value)
      return Number.isInteger(number) && number >= min && number <= max
        ? callback()
        : callback(new Error(message))
    },
  })
}

export function passwordRule(optional = false): FormItemRule {
  return live({
    validator: (_rule, value, callback) => {
      const text = String(value ?? '')
      if (optional && !text) return callback()
      if (text.length < 8 || text.length > 72) return callback(new Error('密码长度必须为 8-72 位'))
      if (new TextEncoder().encode(text).length > 72) return callback(new Error('密码按 UTF-8 编码后不能超过 72 字节'))
      return callback()
    },
  })
}

export function sameAs(expectedValue: () => unknown, message: string, optional = false): FormItemRule {
  return live({
    validator: (_rule, value, callback) => {
      const text = String(value ?? '')
      const expected = String(expectedValue() ?? '')
      if (optional && !text && !expected) return callback()
      return text && text === expected
        ? callback()
        : callback(new Error(message))
    },
  })
}

export function notBefore(previousValue: () => unknown, message: string): FormItemRule {
  return live({
    validator: (_rule, value, callback) => {
      const current = String(value ?? '')
      const previous = String(previousValue() ?? '')
      if (!current || !previous || current >= previous) return callback()
      return callback(new Error(message))
    },
  })
}

export function notAfter(followingValue: () => unknown, message: string): FormItemRule {
  return live({
    validator: (_rule, value, callback) => {
      const current = String(value ?? '')
      const following = String(followingValue() ?? '')
      if (!current || !following || current <= following) return callback()
      return callback(new Error(message))
    },
  })
}

export function ocrFieldError(fieldCode: string, rawValue: string): string {
  const value = rawValue.trim()
  if (!value) return '修订值不能为空。'
  if (value.length > 512) return '修订值不能超过 512 个字符。'
  if (['employee_name'].includes(fieldCode) && !/^[\u4e00-\u9fffA-Za-z· ]+$/.test(value)) {
    return '姓名仅支持中文、英文字母、间隔点和空格。'
  }
  if (fieldCode === 'employee_id' && !/^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$/.test(value)) {
    return '员工编号需为 2-32 位字母、数字、下划线或连字符。'
  }
  if (fieldCode === 'phone' && !/^1[3-9]\d{9}$/.test(value)) return '请输入 11 位有效手机号。'
  if (['hire_date', 'effective_date', 'leave_date'].includes(fieldCode)) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
    if (!match) return '日期格式应为 YYYY-MM-DD。'
    const date = new Date(`${value}T00:00:00Z`)
    if (date.getUTCFullYear() !== Number(match[1]) || date.getUTCMonth() + 1 !== Number(match[2]) || date.getUTCDate() !== Number(match[3])) {
      return '请输入真实存在的日期。'
    }
  }
  return ''
}
