<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    stagger?: number
    animation?: 'up' | 'down' | 'left' | 'right' | 'scale' | 'fade'
    threshold?: number
  }>(),
  {
    stagger: 80,
    animation: 'up',
    threshold: 0.1,
  },
)

const containerRef = ref<HTMLElement | null>(null)

const transformMap = {
  up: 'translateY(28px)',
  down: 'translateY(-28px)',
  left: 'translateX(28px)',
  right: 'translateX(-28px)',
  scale: 'scale(0.94)',
  fade: 'none',
}

const duration = 520
const easing = 'cubic-bezier(0.16, 1, 0.3, 1)'

const prepared = new WeakSet<HTMLElement>()
const timers = new Set<number>()
let observer: IntersectionObserver | null = null
let mutation: MutationObserver | null = null
let fallbackTimer: number | undefined
let visible = false

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function items(): HTMLElement[] {
  const root = containerRef.value
  if (!root) return []
  return Array.from(root.children).filter(
    (node): node is HTMLElement => node instanceof HTMLElement,
  )
}

function prepare(el: HTMLElement): void {
  if (prepared.has(el)) return
  prepared.add(el)
  el.style.transition = 'none'
  el.style.opacity = '0'
  if (transformMap[props.animation] !== 'none') {
    el.style.transform = transformMap[props.animation]
  }
}

function play(el: HTMLElement, index: number): void {
  const delay = index * props.stagger
  // 先提交初始样式，确保动态插入的节点也能播放过渡
  void el.offsetHeight
  el.style.transition = `opacity ${duration}ms ${easing} ${delay}ms, transform ${duration}ms ${easing} ${delay}ms`
  el.style.opacity = '1'
  el.style.transform = 'none'

  const timer = window.setTimeout(() => {
    // 动画结束后释放内联样式，避免覆盖元素自身的 hover / 过渡效果
    el.style.removeProperty('transition')
    el.style.removeProperty('transform')
    el.style.removeProperty('opacity')
    timers.delete(timer)
  }, delay + duration + 80)
  timers.add(timer)
}

function revealAll(): void {
  if (visible) return
  visible = true
  if (fallbackTimer !== undefined) {
    window.clearTimeout(fallbackTimer)
    fallbackTimer = undefined
  }
  items().forEach((el, index) => {
    prepare(el)
    play(el, index)
  })
}

function attachObserver(): void {
  const root = containerRef.value
  if (!root || observer) return
  // 容器是 display: contents，自身没有布局盒，需要观察第一个真实子元素
  const target = items()[0]
  if (!target) return

  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        revealAll()
        observer?.disconnect()
        observer = null
      }
    },
    { threshold: props.threshold },
  )
  observer.observe(target)
}

onMounted(() => {
  const root = containerRef.value
  if (!root || prefersReducedMotion() || typeof IntersectionObserver === 'undefined') return

  items().forEach(prepare)
  attachObserver()

  mutation = new MutationObserver(() => {
    const list = items()
    if (visible) {
      list.forEach((el, index) => {
        if (!prepared.has(el)) {
          prepare(el)
          play(el, index)
        }
      })
      return
    }

    list.forEach(prepare)
    attachObserver()
  })
  mutation.observe(root, { childList: true })

  // 页面隐藏时 IntersectionObserver 不会回调，兜底保证内容最终可见
  fallbackTimer = window.setTimeout(() => {
    if (!visible && document.visibilityState === 'hidden') revealAll()
  }, 1500)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  mutation?.disconnect()
  observer = null
  mutation = null
  if (fallbackTimer !== undefined) window.clearTimeout(fallbackTimer)
  fallbackTimer = undefined
  timers.forEach((timer) => window.clearTimeout(timer))
  timers.clear()
})
</script>

<template>
  <div ref="containerRef" class="stagger-container">
    <slot />
  </div>
</template>

<style scoped>
.stagger-container {
  display: contents;
}
</style>
