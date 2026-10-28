import { post, type RequestOptions } from '@/api/http'
import type {
  LoginPayload,
  LoginVO,
  LogoutPayload,
  RefreshPayload,
  RegisterPayload,
} from '@/types/user'

export function register(payload: RegisterPayload, options: RequestOptions = {}): Promise<LoginVO> {
  return post<LoginVO>('/api/auth/register', payload, { ...options, skipAuth: true })
}

export function login(payload: LoginPayload, options: RequestOptions = {}): Promise<LoginVO> {
  return post<LoginVO>('/api/auth/login', payload, { ...options, skipAuth: true })
}

export function refresh(payload: RefreshPayload): Promise<LoginVO> {
  return post<LoginVO>('/api/auth/refresh', payload, { skipAuth: true })
}

export function logout(payload: LogoutPayload, options: RequestOptions = {}): Promise<void> {
  return post<void>('/api/auth/logout', payload, options)
}

export function sendEmailCode(email: string, options: RequestOptions = {}): Promise<void> {
  return post<void>('/api/auth/code/email', undefined, {
    ...options,
    params: { email },
    skipAuth: true,
  })
}

export function sendPhoneCode(phone: string, options: RequestOptions = {}): Promise<void> {
  return post<void>('/api/auth/code/phone', undefined, {
    ...options,
    params: { phone },
    skipAuth: true,
  })
}
