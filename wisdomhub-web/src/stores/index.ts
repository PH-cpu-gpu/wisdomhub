import { createPinia } from 'pinia'

/**
 * Pinia 实例。在 src/main.ts 里 app.use(pinia)。
 *
 * 只有一个实例、且不存在「为每个请求创建独立 store」的需求，
 * 所以直接用 createPinia()；将来要做 SSR 时改成在应用工厂里创建即可。
 */
export const pinia = createPinia()
