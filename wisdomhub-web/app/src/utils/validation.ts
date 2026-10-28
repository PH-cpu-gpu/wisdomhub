export const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
export const PHONE_PATTERN = /^1[3-9]\d{9}$/
export const CODE_PATTERN = /^\d{6}$/

export function isEmail(value: string): boolean {
  return EMAIL_PATTERN.test(value)
}

export function isPhone(value: string): boolean {
  return PHONE_PATTERN.test(value)
}
