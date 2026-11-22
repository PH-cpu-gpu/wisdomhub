<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    particleCount?: number
    connectionDistance?: number
    particleColor?: string
    lineColor?: string
    mouseRadius?: number
    speed?: number
  }>(),
  {
    particleCount: 60,
    connectionDistance: 150,
    particleColor: '#c9ff2e',
    lineColor: '#c9ff2e',
    mouseRadius: 120,
    speed: 0.5,
  },
)

const canvasRef = ref<HTMLCanvasElement | null>(null)
let animationId = 0
let particles: Particle[] = []
const mouse = { x: -1000, y: -1000 }

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  opacity: number
}

function init(): void {
  const canvas = canvasRef.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  function resize(): void {
    if (!canvas || !ctx) return
    const dpr = window.devicePixelRatio || 1
    const rect = canvas.getBoundingClientRect()
    canvas.width = rect.width * dpr
    canvas.height = rect.height * dpr
    ctx.scale(dpr, dpr)
  }

  function createParticles(): void {
    if (!canvas) return
    particles = []
    const rect = canvas.getBoundingClientRect()
    for (let i = 0; i < props.particleCount; i++) {
      particles.push({
        x: Math.random() * rect.width,
        y: Math.random() * rect.height,
        vx: (Math.random() - 0.5) * props.speed,
        vy: (Math.random() - 0.5) * props.speed,
        radius: Math.random() * 2 + 1,
        opacity: Math.random() * 0.5 + 0.2,
      })
    }
  }

  function drawParticle(p: Particle): void {
    if (!ctx) return
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2)
    ctx.fillStyle = props.particleColor
    ctx.globalAlpha = p.opacity
    ctx.fill()
    ctx.globalAlpha = 1
  }

  function drawConnections(): void {
    if (!ctx) return
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const pi = particles[i]!
        const pj = particles[j]!
        const dx = pi.x - pj.x
        const dy = pi.y - pj.y
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < props.connectionDistance) {
          const opacity = 1 - dist / props.connectionDistance
          ctx.beginPath()
          ctx.moveTo(pi.x, pi.y)
          ctx.lineTo(pj.x, pj.y)
          ctx.strokeStyle = props.lineColor
          ctx.globalAlpha = opacity * 0.3
          ctx.lineWidth = 0.5
          ctx.stroke()
          ctx.globalAlpha = 1
        }
      }

      const pi = particles[i]!
      const mdx = pi.x - mouse.x
      const mdy = pi.y - mouse.y
      const mDist = Math.sqrt(mdx * mdx + mdy * mdy)
      if (mDist < props.mouseRadius) {
        const opacity = 1 - mDist / props.mouseRadius
        ctx.beginPath()
        ctx.moveTo(pi.x, pi.y)
        ctx.lineTo(mouse.x, mouse.y)
        ctx.strokeStyle = props.lineColor
        ctx.globalAlpha = opacity * 0.6
        ctx.lineWidth = 1
        ctx.stroke()
        ctx.globalAlpha = 1
      }
    }
  }

  function update(): void {
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()

    for (const p of particles) {
      p.x += p.vx
      p.y += p.vy

      if (p.x < 0 || p.x > rect.width) p.vx *= -1
      if (p.y < 0 || p.y > rect.height) p.vy *= -1

      const dx = p.x - mouse.x
      const dy = p.y - mouse.y
      const dist = Math.sqrt(dx * dx + dy * dy)
      if (dist < props.mouseRadius && dist > 0) {
        const force = (props.mouseRadius - dist) / props.mouseRadius
        p.vx += (dx / dist) * force * 0.02
        p.vy += (dy / dist) * force * 0.02
      }

      p.vx *= 0.99
      p.vy *= 0.99
    }
  }

  function animate(): void {
    if (!canvas || !ctx) return
    const rect = canvas.getBoundingClientRect()
    ctx.clearRect(0, 0, rect.width, rect.height)

    update()
    for (const p of particles) drawParticle(p)
    drawConnections()

    animationId = requestAnimationFrame(animate)
  }

  function onMouseMove(e: MouseEvent): void {
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    mouse.x = e.clientX - rect.left
    mouse.y = e.clientY - rect.top
  }

  function onMouseLeave(): void {
    mouse.x = -1000
    mouse.y = -1000
  }

  resize()
  createParticles()
  animate()

  window.addEventListener('resize', () => {
    resize()
    createParticles()
  })
  canvas.addEventListener('mousemove', onMouseMove)
  canvas.addEventListener('mouseleave', onMouseLeave)
}

onMounted(() => {
  init()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
})
</script>

<template>
  <canvas ref="canvasRef" class="particle-canvas" aria-hidden="true" />
</template>

<style scoped>
.particle-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: auto;
  z-index: 0;
}
</style>
