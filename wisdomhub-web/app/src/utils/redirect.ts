/** 只接受站内绝对路径，避免登录/注册后的 open redirect。 */
export function normalizeRedirect(value: string): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/home'

  const [path = ''] = value.split(/[?#]/)
  if (path === '/' || path === '/login' || path === '/register') return '/home'
  return value
}
