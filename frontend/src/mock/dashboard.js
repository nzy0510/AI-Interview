export const dashboardMock = {
  user: {
    name: 'InterWise 用户',
    title: 'Java 后端 / 前端训练路径',
    status: '今日建议：先补一轮简历定制面试，再进入文字模式热身',
    lastActive: '今天 09:24'
  },
  overview: [
    { label: '面试准备度', value: '82', unit: '%', tone: 'primary' },
    { label: '简历画像', value: '已解析', unit: '', tone: 'success' },
    { label: '最近面试', value: '3', unit: '场', tone: 'neutral' },
    { label: '可用路径', value: '2', unit: '条', tone: 'accent' }
  ],
  recommendation: {
    title: '先完成面试前置配置',
    description: '补齐简历画像后，系统可以把问题收敛到项目经历、技术选择和架构权衡上。',
    primaryAction: '进入面试准备',
    secondaryAction: '直接开始文字面试',
    pathHint: '将优先接入 /interview/setup'
  },
  mentor: {
    title: 'AI Mentor',
    summary: '当前更适合从“项目复盘 + 技术深挖”切入，而不是直接刷题。',
    nextFocus: '建议先练 15 分钟自我介绍，再进入追问模式。'
  },
  maturity: [
    { label: '项目表达', score: 88, delta: '+6' },
    { label: '技术选型', score: 72, delta: '+4' },
    { label: '问题拆解', score: 64, delta: '+2' },
    { label: '现场应答', score: 79, delta: '+5' }
  ],
  skills: [
    { name: 'Spring Boot', value: 88 },
    { name: 'Vue 3', value: 81 },
    { name: 'MySQL', value: 74 },
    { name: '系统设计', value: 69 }
  ],
  recentInterviews: [
    {
      title: 'Java 后端开发 - 文字面试',
      time: '今天 08:15',
      result: '完成 16 轮追问',
      tag: '文字'
    },
    {
      title: 'Web 前端开发 - 视频模拟',
      time: '昨天 19:40',
      result: '重点覆盖组件设计',
      tag: '视频'
    },
    {
      title: '简历画像更新',
      time: '昨天 18:05',
      result: '解析到 3 段项目经历',
      tag: '简历'
    }
  ],
  shortcuts: [
    { label: '进入准备页', hint: '先完成角色、模式和简历', action: 'setup' },
    { label: '文字面试', hint: '保留原有文字入口', action: 'text' },
    { label: '视频面试', hint: '保留原有视频入口', action: 'video' },
    { label: '简历管理', hint: '查看或更新画像', action: 'resume' }
  ],
  roadmap: [
    { title: '职业路径规划', status: 'Coming soon', description: '后续接入岗位成长建议与能力补齐建议。' },
    { title: '学习建议', status: '待接入', description: '后续根据薄弱项自动生成训练清单。' },
    { title: '面试复盘室', status: '预留', description: '后续接入语音、文字和视频复盘摘要。' }
  ]
}
