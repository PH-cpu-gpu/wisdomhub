import {
  ChatDotRound,
  Collection,
  Connection,
  House,
  MagicStick,
  QuestionFilled,
  Star,
  User,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

/**
 * 站点导航结构。顶部导航（桌面）与移动端抽屉共用这一份，
 * 避免两处各写一遍、改一处漏一处。
 *
 * ⚠️ 这里只有面向用户的字段（名称/路径/图标）。**不要加任务号、开发阶段** ——
 * 导航是给访问者看的，开发进度归任务表。
 */

export interface NavItem {
  label: string
  to: string
  icon: Component
}

/** 主导航。按技术文档 21 章的产品形态取：内容 → 问答 → 标签 → 图谱 → AI。 */
export const primaryNav: NavItem[] = [
  { label: '首页', to: '/', icon: House },
  { label: '问答', to: '/questions', icon: QuestionFilled },
  { label: '标签', to: '/tags', icon: Collection },
  { label: '知识图谱', to: '/graph', icon: Connection },
  { label: 'AI 导师', to: '/ai', icon: MagicStick },
]

/** 侧边栏的「我的」分组。都要登录（路由上有 requiresAuth），未登录时整组隐藏。 */
export const accountNav: NavItem[] = [
  { label: '我的资料', to: '/me/profile', icon: User },
  { label: '我的文章', to: '/me/articles', icon: Collection },
  { label: '草稿箱', to: '/me/drafts', icon: ChatDotRound },
  { label: '我的收藏', to: '/me/favorites', icon: Star },
]
