import { computed, ref } from 'vue'

/**
 * 全局请求计数（F1.9 的「全局 loading」）。
 *
 * 为什么用计数而不是布尔：并发请求下布尔会被「最后一个回来的」抢先关掉，
 * 而先回来的那个把它关掉时其他请求还在飞 —— 顶栏就会提前消失。
 *
 * 计数只在 `api/http.ts` 的拦截器里增减，别在业务代码里手动调：
 * 手写必然出现「忘了减一」→ 顶栏永远亮着。
 */
const pending = ref(0)

export function beginRequest(): void {
  pending.value += 1
}

export function endRequest(): void {
  // 防御性取 0：万一有一次异常路径多减了，也不该把计数打成负数
  pending.value = Math.max(0, pending.value - 1)
}

/** 是否有请求在飞。对外只暴露 computed，外部改不动这个计数 */
export const isBusy = computed(() => pending.value > 0)
export const pendingCount = computed(() => pending.value)
