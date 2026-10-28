import type {
  ArticleBriefVO,
  ArticleColumnVO,
  ArticleDetailVO,
  ArticleListItemVO,
  ColumnVO,
  CommentVO,
  HotTagVO,
  PurchasedColumnVO,
  TagVO,
} from '@/types/content'
import type { RecommendationItemVO } from '@/types/recommendation'
import type { QuestionListItemVO, AnswerVO } from '@/types/qa'
import type { LoginVO, UserBriefVO, UserProfileVO } from '@/types/user'

const PUBLISHED = '2026-10-20T08:30:00Z'

export const MOCK_AVATAR_URL =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' fill='%23c9ff2e'/%3E%3Ctext x='32' y='43' font-family='monospace' font-size='26' fill='%230b0b0e' text-anchor='middle'%3EU%3C/text%3E%3C/svg%3E"

export const mockProfile: UserProfileVO = {
  userId: '1001',
  username: '林深',
  avatar: null,
  bio: '后端工程师，写数据库与分布式系统',
  articleCount: 6,
  fansCount: 128,
  followCount: 36,
}

export const mockSocialUsers: UserBriefVO[] = [
  { userId: '3001', username: '陈默', avatar: null, bio: '写 Rust 与系统编程' },
  { userId: '3002', username: '苏晚', avatar: null, bio: '前端 / 交互与动效' },
  { userId: '3003', username: '顾北', avatar: null, bio: '数据库内核与查询优化' },
  { userId: '3004', username: '江离', avatar: null, bio: '分布式系统与消息队列' },
  { userId: '3005', username: '沈知', avatar: null, bio: '算法与数据结构' },
  { userId: '3006', username: '陆沉', avatar: null, bio: '云原生与 Kubernetes' },
  { userId: '3007', username: '宋野', avatar: null, bio: '安全研究与逆向' },
  { userId: '3008', username: '温言', avatar: null, bio: '产品设计与写作' },
  { userId: '3009', username: '许照', avatar: null, bio: '开源维护者' },
  { userId: '3010', username: '程野', avatar: null, bio: 'Go / 微服务' },
  { userId: '3011', username: '白露', avatar: null, bio: '数据可视化' },
  { userId: '3012', username: '韩川', avatar: null, bio: '计算机图形学' },
]

export const mockTags: TagVO[] = [
  { tagId: '1', name: 'Java', category: '编程语言', description: 'JVM 与语言基础' },
  { tagId: '2', name: 'Spring Boot', category: '后端与框架', description: '服务端应用开发' },
  { tagId: '3', name: 'Vue', category: '前端', description: 'Vue 3 与组合式 API' },
  { tagId: '4', name: 'Redis', category: '中间件与运维', description: '缓存与数据结构' },
  { tagId: '5', name: '全文搜索', category: '人工智能与数据', description: '倒排索引与相关性' },
  { tagId: '6', name: '知识图谱', category: '人工智能与数据', description: '实体关系与学习路径' },
  { tagId: '7', name: '机器学习', category: '人工智能与数据', description: '特征、训练与评估' },
  { tagId: '8', name: 'TypeScript', category: '编程语言', description: '类型系统与工程化' },
  { tagId: '9', name: 'Docker', category: '中间件与运维', description: '容器、镜像与编排' },
  { tagId: '10', name: '消息队列', category: '中间件与运维', description: '异步解耦与削峰' },
  { tagId: '11', name: 'MySQL', category: '数据库', description: '索引、事务与执行计划' },
  { tagId: '12', name: '分布式事务', category: '架构与分布式', description: '一致性协议与补偿' },
  { tagId: '13', name: '微服务', category: '架构与分布式', description: '服务拆分与治理' },
  { tagId: '14', name: 'Android', category: '移动与嵌入式', description: '客户端与性能' },
  { tagId: '15', name: '操作系统', category: '计算机基础', description: '进程、内存与文件系统' },
  { tagId: '16', name: '数据结构', category: '计算机基础', description: '线性表、树与图' },
  { tagId: '17', name: '代码规范', category: '开发实践', description: '可维护性与协作' },
]

export const mockHotTags: HotTagVO[] = [
  { tagId: '4', name: 'Redis', articleCount: 128 },
  { tagId: '3', name: 'Vue', articleCount: 96 },
  { tagId: '1', name: 'Java', articleCount: 88 },
  { tagId: '2', name: 'Spring Boot', articleCount: 76 },
  { tagId: '6', name: '知识图谱', articleCount: 52 },
  { tagId: '11', name: 'MySQL', articleCount: 48 },
  { tagId: '9', name: 'Docker', articleCount: 41 },
  { tagId: '12', name: '分布式事务', articleCount: 35 },
]

/** Neo4j 关系名 → 邻居 tagId；out = 我指向对方，in = 对方指向我 */
export const mockTagRelations: Record<string, Record<string, { out: string[]; in: string[] }>> = {
  '2': {
    BASED_ON: { out: ['1'], in: [] },
    USES: { out: ['4', '11', '10'], in: [] },
    PREREQUISITE: { out: ['1'], in: ['13'] },
    SIMILAR_TO: { out: ['13'], in: ['13'] },
    BELONGS_TO: { out: [], in: ['13'] },
    ABOUT: { out: ['5'], in: [] },
  },
  '13': {
    BASED_ON: { out: ['2'], in: [] },
    USES: { out: ['4', '10'], in: [] },
    PREREQUISITE: { out: ['2'], in: [] },
    SIMILAR_TO: { out: ['2'], in: ['2'] },
    BELONGS_TO: { out: [], in: [] },
    ABOUT: { out: [], in: [] },
  },
}

export const mockPrerequisites: Record<
  string,
  { depth: number; levels: { depth: number; tags: string[] }[]; truncated: boolean }
> = {
  '2': {
    depth: 2,
    levels: [
      { depth: 1, tags: ['1'] },
      { depth: 2, tags: ['15', '16'] },
    ],
    truncated: false,
  },
  '13': {
    depth: 2,
    levels: [
      { depth: 1, tags: ['2'] },
      { depth: 2, tags: ['1'] },
    ],
    truncated: true,
  },
  '3': {
    depth: 1,
    levels: [{ depth: 1, tags: ['8'] }],
    truncated: false,
  },
}

export const mockAuthors: UserBriefVO[] = [
  {
    userId: '1001',
    username: '林深',
    avatar: null,
    bio: '后端工程师，写数据库与分布式系统',
  },
  {
    userId: '2001',
    username: '林深',
    avatar: null,
    bio: '后端工程师，写数据库与分布式系统',
  },
  {
    userId: '2002',
    username: '周野',
    avatar: null,
    bio: '前端工程师，关注交互与性能',
  },
]

export const mockArticles: ArticleListItemVO[] = [
  {
    articleId: '1001',
    authorId: '2001',
    title: '把 Redis 当成缓存之前，先想清楚这四件事',
    summary: '缓存穿透、击穿、雪崩只是表象，真正要先回答的是数据一致性边界。',
    publishTime: PUBLISHED,
    viewCount: 3204,
    likeCount: 186,
    commentCount: 24,
    favoriteCount: 97,
  },
  {
    articleId: '1002',
    authorId: '2002',
    title: 'Vue 3 响应式系统的边界：哪些写法会让性能悄悄变差',
    summary: '从 ref、reactive 到 effect scope，拆开看响应式追踪的成本。',
    publishTime: '2026-10-18T03:20:00Z',
    viewCount: 2811,
    likeCount: 163,
    commentCount: 18,
    favoriteCount: 88,
  },
  {
    articleId: '1003',
    authorId: '2001',
    title: '从一次线上事故看 Spring Boot 事务失效的六个原因',
    summary: '同类调用、异常吞掉、只读事务、异步线程，每一个都足以让事务安静失效。',
    publishTime: '2026-10-15T11:05:00Z',
    viewCount: 4120,
    likeCount: 241,
    commentCount: 36,
    favoriteCount: 152,
  },
  {
    articleId: '1004',
    authorId: '2001',
    title: '写给后端工程师的全文搜索入门：倒排索引到底在做什么',
    summary: '从分词、倒排表到相关性打分，用一张图理解搜索引擎的最小闭环。',
    publishTime: '2026-10-12T06:40:00Z',
    viewCount: 1980,
    likeCount: 102,
    commentCount: 12,
    favoriteCount: 63,
  },
  {
    articleId: '1005',
    authorId: '2002',
    title: '知识图谱不是万能药：什么时候该用，什么时候不该用',
    summary: '关系查询、推荐、学习路径，三类场景的收益与维护成本并不相同。',
    publishTime: '2026-10-08T09:15:00Z',
    viewCount: 1566,
    likeCount: 94,
    commentCount: 9,
    favoriteCount: 51,
  },
  {
    articleId: '1006',
    authorId: '2001',
    title: 'AI 助手接入知识库后，真正难的是评估而不是生成',
    summary: '召回率、引用准确率、回答可追溯性，才是知识库问答的验收线。',
    publishTime: '2026-10-05T02:00:00Z',
    viewCount: 2340,
    likeCount: 128,
    commentCount: 15,
    favoriteCount: 79,
  },
  {
    articleId: '1007',
    authorId: '2001',
    title: 'MySQL 索引失效的七个常见场景',
    summary: '隐式转换、前导模糊、函数包裹列、联合索引最左前缀，逐条对照执行计划。',
    publishTime: '2026-10-02T05:30:00Z',
    viewCount: 1876,
    likeCount: 112,
    commentCount: 14,
    favoriteCount: 68,
  },
  {
    articleId: '1008',
    authorId: '2002',
    title: '从 SSR 到 Islands：前端渲染模式怎么选',
    summary: '首屏、交互与部署成本三者的权衡，没有一种模式能通吃所有场景。',
    publishTime: '2026-09-28T03:10:00Z',
    viewCount: 1420,
    likeCount: 86,
    commentCount: 11,
    favoriteCount: 55,
  },
  {
    articleId: '1009',
    authorId: '2001',
    title: '消息队列选型：Kafka、RocketMQ 与 RabbitMQ 的边界',
    summary: '吞吐、延迟、顺序与生态，先看业务约束，再谈中间件偏好。',
    publishTime: '2026-09-24T08:45:00Z',
    viewCount: 2650,
    likeCount: 158,
    commentCount: 22,
    favoriteCount: 110,
  },
  {
    articleId: '1010',
    authorId: '2002',
    title: 'CSS 布局的现代解法：Grid、容器查询与子网格',
    summary: '从页面级布局到组件级自适应，新的能力正在改变响应式的写法。',
    publishTime: '2026-09-20T02:25:00Z',
    viewCount: 1180,
    likeCount: 72,
    commentCount: 8,
    favoriteCount: 45,
  },
  {
    articleId: '1011',
    authorId: '2001',
    title: '从零实现一个可观测的缓存层',
    summary: '命中率只是起点，失效、回源与热点分布才决定缓存是否可靠。',
    publishTime: '2026-09-16T07:15:00Z',
    viewCount: 1730,
    likeCount: 96,
    commentCount: 13,
    favoriteCount: 61,
  },
  {
    articleId: '1012',
    authorId: '2002',
    title: '知识图谱落地前，先回答这三个问题',
    summary: '实体边界、关系维护与评估口径，决定了图谱是资产还是负担。',
    publishTime: '2026-09-12T04:00:00Z',
    viewCount: 1310,
    likeCount: 78,
    commentCount: 7,
    favoriteCount: 49,
  },
  {
    articleId: '1014',
    authorId: '1001',
    title: '数据库连接池耗尽排查手册',
    summary: '从连接泄漏到慢查询，按链路定位连接池耗尽的真正原因。',
    publishTime: '2026-10-19T09:20:00Z',
    viewCount: 1860,
    likeCount: 132,
    commentCount: 14,
    favoriteCount: 76,
  },
  {
    articleId: '1015',
    authorId: '1001',
    title: '分布式锁的三种实现与失效场景',
    summary: 'Redis、ZooKeeper 与数据库锁，各自的边界与误用代价。',
    publishTime: '2026-10-17T06:40:00Z',
    viewCount: 1520,
    likeCount: 108,
    commentCount: 11,
    favoriteCount: 64,
  },
  {
    articleId: '1016',
    authorId: '1001',
    title: '一次 JVM 调优记录：从 Full GC 到稳定 P99',
    summary: '从 GC 日志、堆外内存到线程池，逐层排除延迟抖动。',
    publishTime: '2026-10-15T11:10:00Z',
    viewCount: 1340,
    likeCount: 88,
    commentCount: 9,
    favoriteCount: 52,
  },
  {
    articleId: '1017',
    authorId: '1001',
    title: '消息积压的定位与回滚',
    summary: '先判断是消费慢、分区倾斜还是生产突增，再决定回滚策略。',
    publishTime: '2026-10-13T03:30:00Z',
    viewCount: 980,
    likeCount: 74,
    commentCount: 6,
    favoriteCount: 41,
  },
  {
    articleId: '1018',
    authorId: '1001',
    title: '接口幂等设计的四个层次',
    summary: '从数据库唯一键到业务状态机，幂等不是加一把锁。',
    publishTime: '2026-10-11T08:00:00Z',
    viewCount: 1220,
    likeCount: 91,
    commentCount: 10,
    favoriteCount: 58,
  },
  {
    articleId: '1019',
    authorId: '1001',
    title: 'MyBatis 批量写入的坑与优化',
    summary: 'ExecutorType、事务边界与 SQL 长度，决定了批量写入的真实吞吐。',
    publishTime: '2026-10-09T09:45:00Z',
    viewCount: 1100,
    likeCount: 83,
    commentCount: 8,
    favoriteCount: 50,
  },
]

const articleTagIds: string[][] = [
  ['4'],
  ['3'],
  ['1', '2'],
  ['5'],
  ['6'],
  ['6'],
  ['1'],
  ['3'],
  ['2'],
  ['3'],
  ['4'],
  ['6'],
]

export const mockRecommendations: RecommendationItemVO[] = mockArticles.map((article, index) => ({
  ...article,
  tags: mockTags
    .filter((tag) => (articleTagIds[index] ?? []).includes(tag.tagId))
    .map((tag) => ({ tagId: tag.tagId, name: tag.name, category: tag.category })),
}))

export const mockQuestions: QuestionListItemVO[] = [
  {
    questionId: '4001',
    userId: '2001',
    title: 'Spring Boot 事务在同类方法调用里为什么会失效？',
    answerCount: 6,
    favoriteCount: 21,
    solved: true,
    createTime: '2026-10-21T07:10:00Z',
  },
  {
    questionId: '4002',
    userId: '2002',
    title: 'Vue 3 中 ref 与 reactive 的选型边界是什么？',
    answerCount: 4,
    favoriteCount: 18,
    solved: true,
    createTime: '2026-10-19T04:30:00Z',
  },
  {
    questionId: '4003',
    userId: '2001',
    title: 'Redis 缓存和数据库一致性，先更新谁更合理？',
    answerCount: 8,
    favoriteCount: 35,
    solved: false,
    createTime: '2026-10-16T09:45:00Z',
  },
  {
    questionId: '4004',
    userId: '2002',
    title: 'Elasticsearch 的 from + size 为什么有上限？',
    answerCount: 3,
    favoriteCount: 12,
    solved: false,
    createTime: '2026-10-13T02:20:00Z',
  },
  {
    questionId: '4005',
    userId: '2001',
    title: '知识图谱的实体关系应该怎么建模？',
    answerCount: 5,
    favoriteCount: 26,
    solved: false,
    createTime: '2026-10-10T11:00:00Z',
  },
  {
    questionId: '4006',
    userId: '2002',
    title: 'AI 知识库问答的引用准确率怎么评估？',
    answerCount: 2,
    favoriteCount: 9,
    solved: false,
    createTime: '2026-10-06T06:15:00Z',
  },
]

export const mockQuestionContents: Record<string, string> = {
  '4001':
    '在同一个 Service 类里，方法 A 直接调用方法 B，调用走的是 this，不经过 Spring 代理，因此 @Transactional 不会生效。\n\n我已经确认方法都是 public，也试过把注解挪到类上，但仍然没有回滚。请问除了拆到另一个 Bean，还有别的办法吗？',
  '4002':
    '我在一个表单页面里用 reactive 管理整个对象，但拆分组件后解构传参总是丢响应性。ref 和 reactive 的边界到底应该怎么判断？',
  '4003':
    '缓存和数据库双写时，删除缓存还是更新缓存？先操作数据库还是先操作缓存？希望结合失败重试与延迟双删讲一下。',
  '4004':
    'Elasticsearch 查询时 from + size 超过 10000 会报错，deep pagination 应该用 search_after 还是 scroll？各自的适用场景是什么？',
  '4005':
    '想给技术知识做一张可演进的图谱，实体和关系的粒度应该怎么定？如何避免后面每加一个技术就要重构模型？',
  '4006': 'RAG 问答给出的引用经常看起来相关但答非所问，引用准确率和答案准确率应该分别怎么评估？',
}

export const mockAnswers: AnswerVO[] = [
  {
    answerId: '6001',
    questionId: '4001',
    userId: '2002',
    content:
      '同类方法调用走的是 this，不经过 Spring 代理，事务注解自然不生效。最稳妥的做法是把事务方法拆到另一个 Bean，或者通过 AopContext 暴露代理后调用。\n\n如果只是查询或单条写入，先确认是否真的需要事务；不要为了“看起来一致”给所有方法都加注解。',
    likeCount: 18,
    commentCount: 2,
    createTime: '2026-10-21T09:00:00Z',
  },
  {
    answerId: '6002',
    questionId: '4001',
    userId: '1001',
    content: '还有一个常见坑：异常被 catch 后没有继续抛出，默认只回滚 RuntimeException。',
    likeCount: 9,
    commentCount: 0,
    createTime: '2026-10-21T10:20:00Z',
  },
  {
    answerId: '6003',
    questionId: '4001',
    userId: '2002',
    content: '可以打开事务日志确认代理是否生效，先定位再改结构。',
    likeCount: 5,
    commentCount: 0,
    createTime: '2026-10-21T12:10:00Z',
  },
  {
    answerId: '6004',
    questionId: '4001',
    userId: '1001',
    content: '拆 Bean 后记得确认新 Bean 也被 Spring 管理，否则代理仍然不存在。',
    likeCount: 4,
    commentCount: 0,
    createTime: '2026-10-22T02:30:00Z',
  },
  {
    answerId: '6005',
    questionId: '4001',
    userId: '2002',
    content: '如果使用 AopContext，需要开启 exposeProxy，并且调用方必须在代理内。',
    likeCount: 3,
    commentCount: 0,
    createTime: '2026-10-22T06:00:00Z',
  },
  {
    answerId: '6006',
    questionId: '4001',
    userId: '1001',
    content: '补充：嵌套事务要确认传播级别，REQUIRES_NEW 和外层事务是两回事。',
    likeCount: 2,
    commentCount: 0,
    createTime: '2026-10-22T08:15:00Z',
  },
  {
    answerId: '6101',
    questionId: '4002',
    userId: '2001',
    content:
      'reactive 适合一组相关字段的整体状态，ref 适合基础值和需要整体替换的对象。解构会丢响应性，需要 toRefs。',
    likeCount: 12,
    commentCount: 1,
    createTime: '2026-10-19T06:00:00Z',
  },
  {
    answerId: '6102',
    questionId: '4002',
    userId: '1001',
    content: '如果一个状态会被整体替换，直接用 ref 更简单。',
    likeCount: 6,
    commentCount: 0,
    createTime: '2026-10-19T08:30:00Z',
  },
  {
    answerId: '6103',
    questionId: '4002',
    userId: '2002',
    content: '跨组件传 reactive 对象时注意保持引用，不要在中间层复制。',
    likeCount: 3,
    commentCount: 0,
    createTime: '2026-10-19T11:00:00Z',
  },
  {
    answerId: '6104',
    questionId: '4002',
    userId: '1001',
    content: '团队内约定表单用 reactive + toRefs，异步数据用 ref 即可。',
    likeCount: 2,
    commentCount: 0,
    createTime: '2026-10-20T01:20:00Z',
  },
  {
    answerId: '6201',
    questionId: '4003',
    userId: '2001',
    content: '先更新数据库再删除缓存是常见选择，但删除失败要有重试与补偿。',
    likeCount: 22,
    commentCount: 3,
    createTime: '2026-10-16T11:00:00Z',
  },
  {
    answerId: '6202',
    questionId: '4003',
    userId: '1001',
    content: '延迟双删不是银弹，关键还是缓存过期时间和回源策略。',
    likeCount: 15,
    commentCount: 1,
    createTime: '2026-10-16T13:20:00Z',
  },
  {
    answerId: '6203',
    questionId: '4003',
    userId: '2002',
    content: '如果读多写少，Cache Aside 足够；强一致场景要考虑旁路缓存之外的方案。',
    likeCount: 9,
    commentCount: 0,
    createTime: '2026-10-17T02:00:00Z',
  },
  {
    answerId: '6204',
    questionId: '4003',
    userId: '1001',
    content: '先定义一致性边界，再决定失效策略，不要把所有数据套同一个模板。',
    likeCount: 8,
    commentCount: 0,
    createTime: '2026-10-17T05:10:00Z',
  },
  {
    answerId: '6205',
    questionId: '4003',
    userId: '2001',
    content: '删除缓存失败时，消息队列补偿比无限重试更可控。',
    likeCount: 6,
    commentCount: 0,
    createTime: '2026-10-17T08:40:00Z',
  },
  {
    answerId: '6206',
    questionId: '4003',
    userId: '2002',
    content: '最好用监控把缓存命中率和回源耗时一起看，单看命中率会误判。',
    likeCount: 4,
    commentCount: 0,
    createTime: '2026-10-18T01:00:00Z',
  },
  {
    answerId: '6207',
    questionId: '4003',
    userId: '1001',
    content: '热点 key 的失效要单独处理，避免同一时刻大量回源。',
    likeCount: 3,
    commentCount: 0,
    createTime: '2026-10-18T04:30:00Z',
  },
  {
    answerId: '6208',
    questionId: '4003',
    userId: '2001',
    content: '如果业务允许短暂不一致，弱一致加过期时间通常最省事。',
    likeCount: 2,
    commentCount: 0,
    createTime: '2026-10-18T08:00:00Z',
  },
  {
    answerId: '6301',
    questionId: '4004',
    userId: '2001',
    content: 'from + size 要保留排序窗口，深分页优先 search_after。',
    likeCount: 7,
    commentCount: 0,
    createTime: '2026-10-13T05:00:00Z',
  },
  {
    answerId: '6302',
    questionId: '4004',
    userId: '1001',
    content: 'scroll 更适合离线导出，不适合实时交互。',
    likeCount: 5,
    commentCount: 0,
    createTime: '2026-10-13T07:20:00Z',
  },
  {
    answerId: '6303',
    questionId: '4004',
    userId: '2002',
    content: '用 search_after 时排序字段必须唯一且稳定，通常要加 _id 兜底。',
    likeCount: 3,
    commentCount: 0,
    createTime: '2026-10-13T09:40:00Z',
  },
  {
    answerId: '6401',
    questionId: '4005',
    userId: '2001',
    content: '先定实体边界，再用版本化关系避免一次性建模过重。',
    likeCount: 10,
    commentCount: 0,
    createTime: '2026-10-10T13:00:00Z',
  },
  {
    answerId: '6402',
    questionId: '4005',
    userId: '1001',
    content: '把技术、框架、问题都作为实体，关系只保留最常用的几种。',
    likeCount: 6,
    commentCount: 0,
    createTime: '2026-10-10T15:30:00Z',
  },
  {
    answerId: '6403',
    questionId: '4005',
    userId: '2002',
    content: '图谱落地前先确认查询场景，否则很容易过度建模。',
    likeCount: 4,
    commentCount: 0,
    createTime: '2026-10-11T02:00:00Z',
  },
  {
    answerId: '6404',
    questionId: '4005',
    userId: '1001',
    content: '实体名称要和其他模块的标签体系对齐，减少映射成本。',
    likeCount: 3,
    commentCount: 0,
    createTime: '2026-10-11T05:20:00Z',
  },
  {
    answerId: '6405',
    questionId: '4005',
    userId: '2001',
    content: '给关系加来源与置信度，后面评估和纠错会轻松很多。',
    likeCount: 2,
    commentCount: 0,
    createTime: '2026-10-11T08:00:00Z',
  },
  {
    answerId: '6501',
    questionId: '4006',
    userId: '2002',
    content: '引用准确率看引用是否真的支撑结论，答案准确率看最终结论是否正确。',
    likeCount: 5,
    commentCount: 0,
    createTime: '2026-10-06T08:00:00Z',
  },
  {
    answerId: '6502',
    questionId: '4006',
    userId: '1001',
    content: '可以分别做人工抽检和自动引用覆盖率，两个指标不要混在一起。',
    likeCount: 3,
    commentCount: 0,
    createTime: '2026-10-06T10:30:00Z',
  },
]

export const mockComments: CommentVO[] = [
  {
    commentId: '5001',
    userId: '2002',
    parentId: null,
    content: '把一致性边界先写清楚，这个顺序我认同。',
    createTime: '2026-10-22T02:10:00Z',
    replies: [
      {
        commentId: '5002',
        userId: '2001',
        parentId: '5001',
        content: '补充一点：回源失败时要有降级策略，否则缓存反而放大故障。',
        createTime: '2026-10-22T03:05:00Z',
        replies: [],
      },
      {
        commentId: '5003',
        userId: '1001',
        parentId: '5001',
        content: '受教了，准备把这套检查清单用到项目里。',
        createTime: '2026-10-22T04:20:00Z',
        replies: [],
      },
    ],
  },
  {
    commentId: '5004',
    userId: '2001',
    parentId: null,
    content: '可以再补一节讲一下缓存预热，冷启动的场景也很常见。',
    createTime: '2026-10-23T01:30:00Z',
    replies: [
      {
        commentId: '5005',
        userId: '2002',
        parentId: '5004',
        content: '预热和失效策略最好一起设计，不然预热完很快就过期了。',
        createTime: '2026-10-23T02:15:00Z',
        replies: [],
      },
    ],
  },
  {
    commentId: '5006',
    userId: '1001',
    parentId: null,
    content: '这篇的目录结构很清晰，排查线上问题时按章节对照很快。',
    createTime: '2026-10-24T06:40:00Z',
    replies: [
      {
        commentId: '5007',
        userId: '2001',
        parentId: '5006',
        content: '谢谢，目录是从正文标题自动生成的。',
        createTime: '2026-10-24T07:10:00Z',
        replies: [],
      },
    ],
  },
  {
    commentId: '5008',
    userId: '2002',
    parentId: null,
    content: '代码示例如果能加上异常处理就更完整了。',
    createTime: '2026-10-25T03:25:00Z',
    replies: [],
  },
  {
    commentId: '5009',
    userId: '2001',
    parentId: null,
    content: '后面会单独写一篇缓存可观测性，重点聊命中和回源指标。',
    createTime: '2026-10-25T08:00:00Z',
    replies: [],
  },
  {
    commentId: '5010',
    userId: '1001',
    parentId: null,
    content: '收藏了，周末再精读一遍。',
    createTime: '2026-10-26T02:35:00Z',
    replies: [],
  },
]

const MOCK_ARTICLE_HTML = `
<h2>先看结论</h2>
<p>缓存不是「加一层 Redis」这么简单。你需要先回答：哪些数据允许短暂不一致，哪些数据必须强一致，以及缓存失效时谁来回源。</p>
<h2>缓存策略怎么选</h2>
<p>先按读写比例与一致性要求分组，再决定是 Cache Aside、读穿还是写穿；不要把所有数据塞进同一套策略。</p>
<pre><code class="language-java">public class CacheDemo {
    public String load(String key) {
        String cached = redis.get(key);
        if (cached != null) {
            return cached;
        }
        return repository.findById(key);
    }
}</code></pre>
<blockquote>缓存一致性是业务问题，不是中间件问题。</blockquote>
<p>把一致性边界写清楚，再谈缓存策略，很多线上问题会提前消失。</p>
<p>延伸阅读：<a href="https://example.com/cache" target="_self">缓存设计的外部资料</a></p>
<script>window.__wisdomhubXss = true</script>
`

export const MOCK_ARTICLE_MARKDOWN = `## 先看结论

缓存不是「加一层 Redis」这么简单。你需要先回答：哪些数据允许短暂不一致，哪些数据必须强一致，以及缓存失效时谁来回源。

## 缓存策略怎么选

先按读写比例与一致性要求分组，再决定是 Cache Aside、读穿还是写穿；不要把所有数据塞进同一套策略。

> 缓存一致性是业务问题，不是中间件问题。

把一致性边界写清楚，再谈缓存策略，很多线上问题会提前消失。
`

interface MockArticleReaction {
  liked: boolean
  collected: boolean
  likeCount: number
  favoriteCount: number
}

const mockArticleReactions = new Map<string, MockArticleReaction>()

function reactionFor(articleId: string, base: ArticleListItemVO): MockArticleReaction {
  const current = mockArticleReactions.get(articleId)
  if (current) return current

  const created: MockArticleReaction = {
    liked: false,
    collected: false,
    likeCount: base.likeCount,
    favoriteCount: base.favoriteCount,
  }
  mockArticleReactions.set(articleId, created)
  return created
}

export const mockPaidColumns: ArticleColumnVO[] = [
  {
    columnId: '7001',
    title: '高并发系统设计',
    price: 9900,
    purchased: false,
    purchasable: true,
  },
  {
    columnId: '7002',
    title: '缓存与存储实战',
    price: 4900,
    purchased: false,
    purchasable: false,
  },
  {
    columnId: '7003',
    title: '后端工程师的阅读清单',
    price: 0,
    purchased: true,
    purchasable: true,
  },
]

/** Mock 专栏额外带一个「是否已有购买记录」开关，用来验证删除 / 移出的 409 */
export interface MockColumn extends ColumnVO {
  hasPurchase: boolean
}

export const mockColumns: MockColumn[] = [
  {
    columnId: '8001',
    userId: '1001',
    title: '可观测缓存实战',
    description: '从缓存穿透讲到指标、日志与链路追踪，一次只解决一个真问题。',
    cover: null,
    price: 9900,
    articleCount: 3,
    purchased: null,
    createTime: '2026-10-18T09:00:00Z',
    hasPurchase: false,
  },
  {
    columnId: '8002',
    userId: '1001',
    title: '微服务事故复盘',
    description: '已经卖出过的专栏，用来验证删除与移出的 409 文案。',
    cover: null,
    price: 12900,
    articleCount: 2,
    purchased: null,
    createTime: '2026-10-12T14:30:00Z',
    hasPurchase: true,
  },
  {
    columnId: '8003',
    userId: '2001',
    title: '数据库连接池笔记',
    description: '连接池耗尽、慢查询与索引失效的排查路径。',
    cover: null,
    price: 4900,
    articleCount: 2,
    purchased: false,
    createTime: '2026-10-10T11:20:00Z',
    hasPurchase: false,
  },
  {
    columnId: '8004',
    userId: '2001',
    title: '高并发系统设计',
    description: '从限流、熔断到容量评估，把高并发拆成可以验证的小问题。',
    cover: null,
    price: 9900,
    articleCount: 2,
    purchased: true,
    createTime: '2026-09-28T10:00:00Z',
    hasPurchase: true,
  },
  {
    columnId: '8005',
    userId: '2001',
    title: '缓存与存储实战',
    description: '缓存一致性、热点 key 与持久化选型的工程取舍。',
    cover: null,
    price: 11900,
    articleCount: 1,
    purchased: true,
    createTime: '2026-09-20T15:10:00Z',
    hasPurchase: true,
  },
  {
    columnId: '8006',
    userId: '2002',
    title: '后端工程师的阅读清单',
    description: '已经下架的专栏，购买记录仍然保留并能继续阅读。',
    cover: null,
    price: 4900,
    articleCount: 2,
    purchased: true,
    createTime: '2026-09-12T09:40:00Z',
    hasPurchase: true,
  },
  {
    columnId: '8007',
    userId: '2001',
    title: 'Spring 事务排查笔记',
    description: '事务失效的常见现场与最小复现。',
    cover: null,
    price: 2900,
    articleCount: 1,
    purchased: true,
    createTime: '2026-09-05T18:20:00Z',
    hasPurchase: true,
  },
  {
    columnId: '8008',
    userId: '2002',
    title: '分布式系统入门',
    description: '把一致性、可用性与分区容错放回真实系统里理解。',
    cover: null,
    price: 6900,
    articleCount: 1,
    purchased: true,
    createTime: '2026-08-26T11:00:00Z',
    hasPurchase: true,
  },
  {
    columnId: '8009',
    userId: '2001',
    title: '检索与排序实战',
    description: '已经下架的检索专栏，用于验证已购列表的下架标记。',
    cover: null,
    price: 3900,
    articleCount: 1,
    purchased: true,
    createTime: '2026-08-18T16:30:00Z',
    hasPurchase: true,
  },
]

/** columnId → articleId[]，数组顺序即归入时间升序 */
export const mockColumnArticleIds: Record<string, string[]> = {
  '8001': ['1014', '1015', '1017'],
  '8002': ['1018', '1019'],
  '8003': ['1001', '1003'],
  '8004': ['1001', '1003'],
  '8005': ['1006'],
  '8006': ['1008', '1010'],
  '8007': ['1004'],
  '8008': ['1012'],
  '8009': ['1009'],
}

/** 1001 已购专栏，price 是实付价快照 */
export const mockPurchasedColumns: PurchasedColumnVO[] = [
  {
    columnId: '8004',
    userId: '2001',
    title: '高并发系统设计',
    description: '从限流、熔断到容量评估，把高并发拆成可以验证的小问题。',
    cover: null,
    articleCount: 2,
    state: 1,
    price: 9900,
    orderId: '20261021000001',
    purchaseTime: '2026-10-21T09:30:00Z',
  },
  {
    columnId: '8005',
    userId: '2001',
    title: '缓存与存储实战',
    description: '缓存一致性、热点 key 与持久化选型的工程取舍。',
    cover: null,
    articleCount: 1,
    state: 1,
    price: 11900,
    orderId: '20261018000002',
    purchaseTime: '2026-10-18T20:15:00Z',
  },
  {
    columnId: '8006',
    userId: '2002',
    title: '后端工程师的阅读清单',
    description: '已经下架的专栏，购买记录仍然保留并能继续阅读。',
    cover: null,
    articleCount: 2,
    state: 2,
    price: 4900,
    orderId: '20261009000003',
    purchaseTime: '2026-10-09T13:05:00Z',
  },
  {
    columnId: '8007',
    userId: '2001',
    title: 'Spring 事务排查笔记',
    description: '事务失效的常见现场与最小复现。',
    cover: null,
    articleCount: 1,
    state: 1,
    price: 2900,
    orderId: '20261002000004',
    purchaseTime: '2026-10-02T08:40:00Z',
  },
  {
    columnId: '8008',
    userId: '2002',
    title: '分布式系统入门',
    description: '把一致性、可用性与分区容错放回真实系统里理解。',
    cover: null,
    articleCount: 1,
    state: 1,
    price: 6900,
    orderId: '20260925000005',
    purchaseTime: '2026-09-25T19:20:00Z',
  },
  {
    columnId: '8009',
    userId: '2001',
    title: '检索与排序实战',
    description: '已经下架的检索专栏，用于验证已购列表的下架标记。',
    cover: null,
    articleCount: 1,
    state: 2,
    price: 3900,
    orderId: '20260916000006',
    purchaseTime: '2026-09-16T11:10:00Z',
  },
]

export function mockArticleDetail(articleId: string, locked = false): ArticleDetailVO {
  const base = mockArticles.find((article) => article.articleId === articleId) ?? mockArticles[0]
  if (!base) throw new Error('mockArticles is empty')
  const reaction = reactionFor(articleId, base)

  return {
    ...base,
    articleId,
    likeCount: reaction.likeCount,
    favoriteCount: reaction.favoriteCount,
    html: locked ? null : MOCK_ARTICLE_HTML,
    locked,
    columns: locked ? mockPaidColumns : [],
    liked: reaction.liked,
    collected: reaction.collected,
  }
}

export function mockToggleArticleReaction(
  articleId: string,
  kind: 'like' | 'collect',
  active: boolean,
): number {
  const base = mockArticles.find((article) => article.articleId === articleId) ?? mockArticles[0]
  if (!base) return 0

  const reaction = reactionFor(articleId, base)
  if (kind === 'like') {
    if (reaction.liked !== active) {
      reaction.liked = active
      reaction.likeCount = Math.max(0, reaction.likeCount + (active ? 1 : -1))
    }
    return reaction.likeCount
  }

  if (reaction.collected !== active) {
    reaction.collected = active
    reaction.favoriteCount = Math.max(0, reaction.favoriteCount + (active ? 1 : -1))
  }
  return reaction.favoriteCount
}

export function mockArticleBriefs(articleIds?: string[]): ArticleBriefVO[] {
  const source =
    articleIds && articleIds.length > 0
      ? mockArticles.filter((article) => articleIds.includes(article.articleId))
      : mockArticles.slice(0, 3)

  return source.map(({ articleId, title, summary }) => ({ articleId, title, summary }))
}

function base64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function createMockJwt(userId: number): string {
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = base64Url(
    JSON.stringify({
      userId,
      exp: Math.floor(Date.now() / 1000) + 60 * 60,
    }),
  )
  return `${header}.${payload}.mock-signature`
}

export function mockLogin(hasEmail = true): LoginVO {
  return {
    accessToken: createMockJwt(1001),
    refreshToken: 'mock-refresh-token',
    hasEmail,
  }
}
