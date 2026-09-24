import {
  ChatDotRound,
  Collection,
  Connection,
  House,
  MagicStick,
  QuestionFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

import type { TechCategory } from './design-tokens'

/**
 * 站点导航结构。顶部导航（桌面）与移动端抽屉共用这一份，
 * 避免两处各写一遍、改一处漏一处。
 */

export interface NavItem {
  label: string
  to: string
  icon: Component
  /**
   * 这一项真正落地归哪个任务。
   * 阶段 0 里所有目标页都还是占位页，标出归属任务是为了知道
   * 「它什么时候才会有真内容」，而不是让人以为已经做好了。
   */
  task: string
}

/** 主导航。按技术文档 21 章的产品形态取：内容 → 问答 → 标签 → 图谱 → AI。 */
export const primaryNav: NavItem[] = [
  { label: '首页', to: '/', icon: House, task: 'F1.2' },
  { label: '问答', to: '/questions', icon: QuestionFilled, task: 'F1.7' },
  { label: '标签', to: '/tags', icon: Collection, task: 'F1.5' },
  { label: '知识图谱', to: '/graph', icon: Connection, task: 'F2.3' },
  { label: 'AI 导师', to: '/ai', icon: MagicStick, task: 'F3.1' },
]

/**
 * 侧边栏的「热门技术」。
 *
 * ⚠️ 这是**占位数据**，只为把布局撑起来并演示类别配色。
 * 真数据来自 B2.11 的图谱查询接口（热门技术按文章数排序），
 * B2.8 会先导入 Java/Spring/Redis 等种子节点 —— 届时整个数组删掉换接口调用。
 */
export const placeholderHotTech: { name: string; category: TechCategory }[] = [
  { name: 'Java', category: 'LANGUAGE' },
  { name: 'Spring Boot', category: 'FRAMEWORK' },
  { name: 'MyBatis', category: 'FRAMEWORK' },
  { name: 'Redis', category: 'MIDDLEWARE' },
  { name: 'RabbitMQ', category: 'MIDDLEWARE' },
  { name: 'MySQL', category: 'DATABASE' },
  { name: 'Elasticsearch', category: 'DATABASE' },
  { name: '分布式锁', category: 'CONCEPT' },
]

/** 侧边栏的「我的」分组。都要登录，未登录时整组隐藏。 */
export const accountNav: NavItem[] = [
  { label: '我的文章', to: '/me/articles', icon: Collection, task: 'F1.8' },
  { label: '草稿箱', to: '/me/drafts', icon: ChatDotRound, task: 'F1.8' },
]
