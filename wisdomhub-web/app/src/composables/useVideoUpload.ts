import { onScopeDispose, ref } from 'vue'

import { isApiError } from '@/api/error'
import { completeUpload, getAsset, requestUploadTicket } from '@/api/modules/video'
import { putObjectToStorage } from '@/api/upload'
import type { ID } from '@/types/common'
import {
  acceptedMimeOf,
  MAX_VIDEO_DURATION_SECONDS,
  MAX_VIDEO_SIZE_BYTES,
  MEDIA_ASSET_STATUS,
  type MediaAssetStatusVO,
  type PendingUpload,
  type UploadTicketVO,
} from '@/types/video'

export type UploadPhase =
  | 'idle'
  | 'validating'
  | 'ticketing'
  | 'uploading'
  | 'paused'
  | 'completing'
  | 'processing'
  | 'ready'
  | 'failed'

const PENDING_KEY = 'wisdomhub:video.upload.pending'
const POLL_INTERVAL = 3000
const MAX_POLL_FAILURES = 3
const TICKET_SAFETY_MS = 5000
const ACTIVE_PHASES: UploadPhase[] = ['ticketing', 'uploading', 'completing']

function fingerprintOf(file: File): string {
  return `${file.name}|${file.size}|${file.lastModified}`
}

/** 后端给的是 UTC；缺少时区标识时按 UTC 解析 */
function parseUtc(value: string): number {
  return Date.parse(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`)
}

function isTicketUsable(expiresAt: string): boolean {
  const at = parseUtc(expiresAt)
  return Number.isFinite(at) && at - Date.now() > TICKET_SAFETY_MS
}

function isCancel(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const record = error as { name?: string; code?: string }
  return record.name === 'AbortError' || record.code === 'ERR_CANCELED'
}

function readPending(): PendingUpload | null {
  try {
    const raw = localStorage.getItem(PENDING_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PendingUpload
    return parsed && parsed.assetId ? parsed : null
  } catch {
    return null
  }
}

function writePending(pending: PendingUpload): void {
  localStorage.setItem(PENDING_KEY, JSON.stringify(pending))
}

function clearPending(): void {
  localStorage.removeItem(PENDING_KEY)
}

function readDuration(file: File): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    let settled = false

    const finish = (value: number | null): void => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      video.removeAttribute('src')
      URL.revokeObjectURL(url)
      resolve(value)
    }

    const timer = setTimeout(() => finish(null), 4000)
    video.preload = 'metadata'
    video.onloadedmetadata = () =>
      finish(Number.isFinite(video.duration) ? Math.round(video.duration * 1000) : null)
    video.onerror = () => finish(null)
    video.src = url
  })
}

/**
 * 视频上传状态机：
 * 选文件校验 → 取凭证 → 直传 COS → complete → 轮询转码/审核 → ready。
 *
 * 断点续传的边界：后端只签发单次 PUT，所以「续传」= 复用未过期的凭证重新 PUT；
 * 凭证过期或换文件就必须重新申请（会消耗每日上传额度）。
 */
export function useVideoUpload() {
  const file = ref<File | null>(null)
  const mimeType = ref('')
  const durationMs = ref<number | null>(null)
  const phase = ref<UploadPhase>('idle')
  const progress = ref(0)
  const speedBps = ref(0)
  const error = ref('')
  const asset = ref<MediaAssetStatusVO | null>(null)
  const resumeHint = ref(false)

  let controller: AbortController | null = null
  let pollTimer: ReturnType<typeof setTimeout> | null = null
  let pollFailures = 0
  let ticket: UploadTicketVO | null = null
  let uploaded = false
  let lastTick = { at: 0, percent: 0 }

  const active = () => ACTIVE_PHASES.includes(phase.value)

  function clearPollTimer(): void {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  function abortCurrent(): void {
    controller?.abort()
    controller = null
  }

  function resetRuntime(): void {
    clearPollTimer()
    abortCurrent()
    progress.value = 0
    speedBps.value = 0
    error.value = ''
    speedReset()
    pollFailures = 0
  }

  function speedReset(): void {
    lastTick = { at: 0, percent: 0 }
    speedBps.value = 0
  }

  function handleProgress(percent: number): void {
    progress.value = percent
    const now = Date.now()
    const size = file.value?.size ?? 0
    if (percent > lastTick.percent && lastTick.at > 0 && now > lastTick.at) {
      const deltaBytes = ((percent - lastTick.percent) / 100) * size
      speedBps.value = Math.max(0, Math.round(deltaBytes / ((now - lastTick.at) / 1000)))
    }
    lastTick = { at: now, percent }
  }

  function applyStatus(status: MediaAssetStatusVO): void {
    asset.value = status
    if (status.status === MEDIA_ASSET_STATUS.READY) {
      phase.value = 'ready'
      progress.value = 100
      clearPending()
      return
    }
    if (
      status.status === MEDIA_ASSET_STATUS.FAILED ||
      status.status === MEDIA_ASSET_STATUS.REJECTED
    ) {
      phase.value = 'failed'
      error.value = status.failReason || '视频处理失败，请重新上传'
      clearPending()
      return
    }
    phase.value = 'processing'
    progress.value = 100
    schedulePoll(status.assetId)
  }

  function schedulePoll(assetId: ID): void {
    clearPollTimer()
    pollTimer = setTimeout(() => {
      void poll(assetId)
    }, POLL_INTERVAL)
  }

  async function poll(assetId: ID): Promise<void> {
    pollTimer = null
    try {
      const status = await getAsset(assetId, { skipErrorToast: true })
      pollFailures = 0
      applyStatus(status)
    } catch (err) {
      pollFailures += 1
      if (pollFailures >= MAX_POLL_FAILURES) {
        phase.value = 'failed'
        error.value = isApiError(err) ? err.message : '状态查询失败，请稍后重试'
        return
      }
      schedulePoll(assetId)
    }
  }

  async function selectFile(next: File): Promise<void> {
    resetRuntime()
    resumeHint.value = false
    asset.value = null
    uploaded = false
    ticket = null
    file.value = next
    mimeType.value = ''

    const mime = acceptedMimeOf(next)
    if (!mime) {
      phase.value = 'idle'
      error.value = '仅支持 MP4 / MOV / WebM / MKV 格式'
      file.value = null
      return
    }
    if (next.size <= 0) {
      phase.value = 'idle'
      error.value = '文件为空，请重新选择'
      file.value = null
      return
    }
    if (next.size > MAX_VIDEO_SIZE_BYTES) {
      phase.value = 'idle'
      error.value = '视频不能超过 2 GB'
      file.value = null
      return
    }
    mimeType.value = mime

    phase.value = 'validating'
    const duration = await readDuration(next)
    if (file.value !== next) return

    durationMs.value = duration
    if (duration !== null && duration > MAX_VIDEO_DURATION_SECONDS * 1000) {
      phase.value = 'idle'
      error.value = '视频时长不能超过 30 分钟'
      file.value = null
      return
    }

    const pending = readPending()
    if (pending && pending.fingerprint === fingerprintOf(next) && isTicketUsable(pending.expiresAt)) {
      ticket = {
        assetId: pending.assetId,
        objectKey: pending.objectKey,
        uploadUrl: pending.uploadUrl,
        method: 'PUT',
        headers: pending.headers,
        expiresAt: pending.expiresAt,
      }
      uploaded = pending.uploaded
      resumeHint.value = true
      phase.value = 'paused'
      return
    }
    if (pending) clearPending()
    phase.value = 'idle'
  }

  async function start(): Promise<void> {
    if (!file.value) {
      error.value = '请先选择视频文件'
      return
    }
    if (active() || phase.value === 'processing') return

    resetRuntime()
    try {
      if (!ticket || !isTicketUsable(ticket.expiresAt)) {
        phase.value = 'ticketing'
        ticket = await requestUploadTicket(
          { fileSize: file.value.size, mimeType: mimeType.value },
          { skipErrorToast: true },
        )
        uploaded = false
        writePending(buildPending(ticket, file.value, false))
      }

      if (!uploaded) {
        phase.value = 'uploading'
        controller = new AbortController()
        await putObjectToStorage({
          url: ticket.uploadUrl,
          file: file.value,
          headers: ticket.headers,
          signal: controller.signal,
          onProgress: handleProgress,
        })
        controller = null
        uploaded = true
        writePending(buildPending(ticket, file.value, true))
      }

      phase.value = 'completing'
      const status = await completeUpload(ticket.assetId, { skipErrorToast: true })
      resumeHint.value = false
      applyStatus(status)
    } catch (err) {
      controller = null
      if (isCancel(err)) {
        phase.value = 'paused'
        return
      }
      phase.value = 'failed'
      error.value = isApiError(err)
        ? err.message
        : err instanceof Error
          ? err.message
          : '上传失败，请稍后重试'
    }
  }

  function buildPending(
    current: UploadTicketVO,
    currentFile: File,
    isUploaded: boolean,
  ): PendingUpload {
    return {
      fingerprint: fingerprintOf(currentFile),
      assetId: current.assetId,
      objectKey: current.objectKey,
      uploadUrl: current.uploadUrl,
      headers: current.headers,
      expiresAt: current.expiresAt,
      fileSize: currentFile.size,
      mimeType: mimeType.value,
      uploaded: isUploaded,
    }
  }

  function pause(): void {
    if (phase.value !== 'uploading' && phase.value !== 'ticketing') return
    abortCurrent()
    phase.value = 'paused'
  }

  async function retry(): Promise<void> {
    if (phase.value !== 'failed' && phase.value !== 'paused') return
    if (!file.value) {
      reset()
      return
    }
    error.value = ''
    if (!uploaded) progress.value = 0
    await start()
  }

  function reset(): void {
    resetRuntime()
    clearPending()
    abortCurrent()
    file.value = null
    mimeType.value = ''
    durationMs.value = null
    asset.value = null
    ticket = null
    uploaded = false
    resumeHint.value = false
    progress.value = 0
    phase.value = 'idle'
  }

  onScopeDispose(() => {
    clearPollTimer()
    abortCurrent()
  })

  return {
    file,
    durationMs,
    phase,
    progress,
    speedBps,
    error,
    asset,
    resumeHint,
    active,
    selectFile,
    start,
    pause,
    retry,
    reset,
  }
}
