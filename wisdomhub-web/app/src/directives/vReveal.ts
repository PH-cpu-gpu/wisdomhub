import type { Directive } from 'vue'

interface RevealBinding {
  animation?: 'up' | 'down' | 'left' | 'right' | 'scale' | 'fade'
  delay?: number
  distance?: string
  duration?: string
  threshold?: number
}

const defaultOptions: Required<RevealBinding> = {
  animation: 'up',
  delay: 0,
  distance: '40px',
  duration: '600ms',
  threshold: 0.15,
}

const animationMap = {
  up: { from: 'translateY(var(--reveal-distance))', to: 'translateY(0)' },
  down: { from: 'translateY(calc(var(--reveal-distance) * -1))', to: 'translateY(0)' },
  left: { from: 'translateX(var(--reveal-distance))', to: 'translateX(0)' },
  right: { from: 'translateX(calc(var(--reveal-distance) * -1))', to: 'translateX(0)' },
  scale: { from: 'scale(0.9)', to: 'scale(1)' },
  fade: { from: 'none', to: 'none' },
}

function getInitialStyle(animation: keyof typeof animationMap, distance: string): Record<string, string> {
  const anim = animationMap[animation]
  return {
    opacity: '0',
    transform: anim.from === 'none' ? '' : anim.from,
    '--reveal-distance': distance,
    transition: `opacity var(--reveal-duration) var(--reveal-easing) var(--reveal-delay), transform var(--reveal-duration) var(--reveal-easing) var(--reveal-delay)`,
  }
}

function getVisibleStyle(): Record<string, string> {
  return {
    opacity: '1',
    transform: 'none',
  }
}

function applyStyles(el: HTMLElement, styles: Record<string, string>): void {
  for (const [key, value] of Object.entries(styles)) {
    if (key.startsWith('--')) {
      el.style.setProperty(key, value)
    } else {
      ;(el.style as unknown as Record<string, string>)[key] = value
    }
  }
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/** 动画结束后清掉内联 transform / transition，避免覆盖元素自身的 hover 效果 */
function releaseInlineStyles(el: HTMLElement): void {
  el.style.removeProperty('transform')
  el.style.removeProperty('transition')
}

function parseDuration(value: string): number {
  const amount = Number.parseFloat(value)
  if (Number.isNaN(amount)) return 600
  return value.trim().endsWith('ms') ? amount : amount * 1000
}

export const vReveal: Directive<HTMLElement, RevealBinding | undefined> = {
  mounted(el, binding) {
    const options = { ...defaultOptions, ...binding.value }

    if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined') {
      applyStyles(el, getVisibleStyle())
      return
    }

    el.style.setProperty('--reveal-duration', options.duration)
    el.style.setProperty('--reveal-delay', `${options.delay}ms`)
    el.style.setProperty('--reveal-easing', 'cubic-bezier(0.16, 1, 0.3, 1)')

    applyStyles(el, getInitialStyle(options.animation, options.distance))

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue

          applyStyles(el, getVisibleStyle())
          observer.unobserve(el)

          const target = el as RevealElement
          if (target.__revealFallback !== undefined) {
            window.clearTimeout(target.__revealFallback)
            target.__revealFallback = undefined
          }

          const timer = window.setTimeout(
            () => releaseInlineStyles(el),
            options.delay + parseDuration(options.duration) + 80,
          )
          target.__revealTimer = timer
        }
      },
      { threshold: options.threshold },
    )

    observer.observe(el)
    ;(el as RevealElement).__revealObserver = observer

    // 页面隐藏时 IntersectionObserver 不会回调，兜底保证内容最终可见
    ;(el as RevealElement).__revealFallback = window.setTimeout(() => {
      if (document.visibilityState !== 'hidden') return
      observer.disconnect()
      applyStyles(el, getVisibleStyle())
      releaseInlineStyles(el)
    }, 1500)
  },

  unmounted(el) {
    const target = el as RevealElement
    target.__revealObserver?.disconnect()
    if (target.__revealTimer !== undefined) window.clearTimeout(target.__revealTimer)
    if (target.__revealFallback !== undefined) window.clearTimeout(target.__revealFallback)
    delete target.__revealObserver
    delete target.__revealTimer
    delete target.__revealFallback
  },
}

interface RevealElement extends HTMLElement {
  __revealObserver?: IntersectionObserver
  __revealTimer?: number
  __revealFallback?: number
}
