/** 文章选中文本的一次性交接键：跳转前写入，工作台读取后立即删除。 */
export const AI_TEXT_SELECTION_KEY = 'wisdomhub:ai.text-selection'

/** 与后端 TextQaChatRequest.text 上限保持一致 */
export const AI_TEXT_MAX_LENGTH = 2000

export function consumeTextSelection(): string {
  try {
    const raw = window.sessionStorage.getItem(AI_TEXT_SELECTION_KEY)
    if (raw === null) return ''
    window.sessionStorage.removeItem(AI_TEXT_SELECTION_KEY)
    return raw.trim().slice(0, AI_TEXT_MAX_LENGTH)
  } catch {
    return ''
  }
}
