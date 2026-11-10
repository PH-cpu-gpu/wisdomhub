<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type Hls from 'hls.js'

import { getAccessToken } from '@/api/auth-token'
import { resolveApiUrl } from '@/api/http'

const props = defineProps<{
  /** 后端返回的相对路径 /api/video/{id}/playlist */
  src: string
  poster?: string
  title?: string
  /** mock 模式不加载真实流，只展示播放器外壳 */
  mockMode?: boolean
}>()

const videoRef = ref<HTMLVideoElement | null>(null)
const started = ref(false)
const loading = ref(false)
const error = ref('')
const mockNotice = ref(false)

let hls: Hls | null = null
let networkRetried = false
let mediaRetried = false

function destroyHls(): void {
  if (!hls) return
  hls.destroy()
  hls = null
}

function teardownVideo(): void {
  destroyHls()
  const video = videoRef.value
  if (!video) return
  video.pause()
  video.removeAttribute('src')
  video.load()
}

function fail(message: string): void {
  started.value = false
  loading.value = false
  error.value = message
  teardownVideo()
}

async function play(): Promise<void> {
  if (props.mockMode) {
    mockNotice.value = true
    return
  }
  if (loading.value) return

  const video = videoRef.value
  if (!video) return

  started.value = true
  loading.value = true
  error.value = ''
  mockNotice.value = false
  networkRetried = false
  mediaRetried = false

  const url = resolveApiUrl(props.src)
  try {
    const module = await import('hls.js')
    const HlsPlayer = module.default

    if (HlsPlayer.isSupported()) {
      destroyHls()
      const instance = new HlsPlayer({
        // 仅播放列表走业务网关时需要身份；COS 分片是绝对 URL，不带业务 token
        xhrSetup: (xhr, requestUrl) => {
          const token = getAccessToken()
          if (token && requestUrl.includes('/api/')) {
            xhr.setRequestHeader('Authorization', `Bearer ${token}`)
          }
        },
      })
      hls = instance

      instance.on(HlsPlayer.Events.MANIFEST_PARSED, () => {
        void video.play().catch((err: unknown) => {
          if ((err as { name?: string })?.name === 'NotAllowedError') {
            loading.value = false
            return
          }
          fail('自动播放被拦截，请点击播放器上的播放键')
        })
      })

      instance.on(HlsPlayer.Events.ERROR, (_event, data) => {
        if (!data.fatal) return
        if (data.type === HlsPlayer.ErrorTypes.NETWORK_ERROR && !networkRetried) {
          networkRetried = true
          instance.startLoad()
          return
        }
        if (data.type === HlsPlayer.ErrorTypes.MEDIA_ERROR && !mediaRetried) {
          mediaRetried = true
          instance.recoverMediaError()
          return
        }
        fail('视频加载失败，请稍后重试')
      })

      instance.loadSource(url)
      instance.attachMedia(video)
      return
    }

    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url
      await video.play()
      return
    }

    fail('当前浏览器不支持 HLS 播放，请升级浏览器或换用 Safari')
  } catch {
    fail('播放器加载失败，请刷新重试')
  } finally {
    loading.value = false
  }
}

function onPlaying(): void {
  loading.value = false
  error.value = ''
}

function onWaiting(): void {
  if (started.value) loading.value = true
}

function onVideoError(): void {
  if (!started.value) return
  fail('视频播放出错，请稍后重试')
}

watch(
  () => props.src,
  () => {
    teardownVideo()
    started.value = false
    loading.value = false
    error.value = ''
    mockNotice.value = false
  },
)

onBeforeUnmount(teardownVideo)
</script>

<template>
  <div class="video-player" :class="{ 'is-started': started }">
    <video
      ref="videoRef"
      class="video-player__media"
      :poster="poster"
      controls
      playsinline
      preload="metadata"
      :aria-label="title ? `${title} 播放器` : '视频播放器'"
      @playing="onPlaying"
      @waiting="onWaiting"
      @error="onVideoError"
    ></video>

    <div v-if="!started" class="video-player__cover" :class="{ 'has-poster': Boolean(poster) }">
      <button class="video-player__play" type="button" :disabled="loading" @click="play">
        <span class="video-player__icon" aria-hidden="true">▶</span>
        <span>{{ error ? '重试' : '播放' }}</span>
      </button>
      <p v-if="mockNotice" class="video-player__notice">
        mock 模式不加载真实视频流；播放能力请在联调环境验证。
      </p>
      <p v-if="error" class="video-player__error" role="alert">{{ error }}</p>
    </div>

    <p v-if="started && loading" class="video-player__loading" aria-live="polite">加载中…</p>
  </div>
</template>

<style scoped>
.video-player {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: #0b0b0b;
  border: 1px solid var(--ink);
}

.video-player__media {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0b0b0b;
}

.video-player__cover {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.25), rgba(0, 0, 0, 0.6));
  text-align: center;
}

.video-player__cover:not(.has-poster) {
  background: #14161c;
}

.video-player__play {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 12px 20px;
  border: 1px solid var(--acid);
  background: rgba(0, 0, 0, 0.55);
  color: var(--acid);
  font-family: var(--mono);
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  cursor: pointer;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.video-player__play:hover:not(:disabled) {
  background: var(--acid);
  color: var(--ink);
  transform: translate(-1px, -1px);
}

.video-player__play:disabled {
  opacity: 0.6;
  cursor: wait;
}

.video-player__icon {
  font-size: 14px;
  line-height: 1;
}

.video-player__notice,
.video-player__error {
  max-width: 46ch;
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.video-player__notice {
  color: rgba(255, 255, 255, 0.78);
}

.video-player__error {
  color: var(--verm);
}

.video-player__loading {
  position: absolute;
  right: var(--space-3);
  bottom: var(--space-3);
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-family: var(--mono);
  font-size: var(--text-2xs);
}
</style>
