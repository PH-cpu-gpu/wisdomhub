<template>
  <component :is="tag" class="typewriter">
    <span class="typewriter__text" v-html="formattedText"></span>
    <span
      v-if="showCursor"
      class="typewriter__cursor"
      :class="{ 'is-typing': isTyping }"
      aria-hidden="true"
    >|</span>
  </component>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    text: string
    speed?: number
    delay?: number
    loop?: boolean
    showCursor?: boolean
    tag?: string
  }>(),
  {
    speed: 60,
    delay: 300,
    loop: false,
    showCursor: true,
    tag: 'span',
  },
)

const displayText = ref('')
const isTyping = ref(false)

let timeoutId: ReturnType<typeof setTimeout> | null = null

const formattedText = computed(() => {
  return displayText.value.replace(/\n/g, '<br>')
})

async function typeText(): Promise<void> {
  displayText.value = ''
  isTyping.value = true

  await new Promise<void>((resolve) => {
    timeoutId = setTimeout(resolve, props.delay)
  })

  for (let i = 0; i < props.text.length; i++) {
    if (!isTyping.value) return
    displayText.value += props.text[i]
    await new Promise<void>((resolve) => {
      timeoutId = setTimeout(resolve, props.speed)
    })
  }

  isTyping.value = false

  if (props.loop) {
    await new Promise<void>((resolve) => {
      timeoutId = setTimeout(resolve, 2000)
    })
    if (props.loop) void typeText()
  }
}

function stopTyping(): void {
  isTyping.value = false
  if (timeoutId) clearTimeout(timeoutId)
}

onMounted(() => {
  void typeText()
})

onBeforeUnmount(() => {
  stopTyping()
})

watch(() => props.text, () => {
  stopTyping()
  void typeText()
})
</script>

<style scoped>
.typewriter {
  display: inline;
}

.typewriter__text {
  white-space: pre-wrap;
}

.typewriter__cursor {
  display: inline;
  color: var(--verm);
  font-weight: var(--weight-black);
  animation: cursor-blink 1s step-end infinite;
}

.typewriter__cursor.is-typing {
  animation: none;
  opacity: 1;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
