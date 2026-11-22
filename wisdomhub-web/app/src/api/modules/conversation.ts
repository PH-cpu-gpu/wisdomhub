import { del, get, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type { ConversationDetailVO, ConversationItemVO } from '@/types/conversation'

export function listConversations(
  query: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<ConversationItemVO>> {
  return get<PageResponse<ConversationItemVO>>('/api/agent/conversations', query, options)
}

export function getConversation(
  conversationId: ID,
  options: RequestOptions = {},
): Promise<ConversationDetailVO> {
  return get<ConversationDetailVO>(`/api/agent/conversations/${conversationId}`, undefined, options)
}

export function deleteConversation(
  conversationId: ID,
  options: RequestOptions = {},
): Promise<void> {
  return del<void>(`/api/agent/conversations/${conversationId}`, options)
}
