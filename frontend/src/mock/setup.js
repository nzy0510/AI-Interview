export const interviewSetupDefaults = {
  roleOptions: [
    'Java 后端开发',
    'Web 前端开发',
    '测试开发',
    '算法工程师',
    '产品经理'
  ],
  experienceLevels: [
    { label: '应届 / 0-1 年', value: 'junior', hint: '基础题与项目追问' },
    { label: '1-3 年', value: 'mid', hint: '业务与架构并重' },
    { label: '3-5 年', value: 'senior', hint: '系统设计与取舍' },
    { label: '5 年以上', value: 'principal', hint: '复杂场景与方案评审' }
  ],
  focusOptions: [
    { label: '项目经历', value: 'projects' },
    { label: '技术深度', value: 'depth' },
    { label: '系统设计', value: 'architecture' },
    { label: '算法基础', value: 'algorithm' },
    { label: '表达与沟通', value: 'communication' },
    { label: '压力应对', value: 'pressure' }
  ],
  modeOptions: [
    {
      value: 'text',
      title: '文字面试',
      description: '适合先做预热和题目梳理，后续可无缝接到文字问答。',
      tag: '基础模式'
    },
    {
      value: 'video',
      title: '视频面试',
      description: '适合进行更接近真实场景的对话训练，当前先保留入口。',
      tag: '进阶模式'
    }
  ],
  checklist: [
    '确认目标岗位是否准确',
    '检查简历画像是否已加载',
    '勾选本次最想强化的能力',
    '先选择文字或视频模式',
    '点击开始后进入对应面试页'
  ],
  placeholderModules: [
    {
      title: '题库联动',
      status: '待接入',
      description: '后续可把岗位题库与个人画像动态拼装。'
    },
    {
      title: '情境模拟',
      status: '待接入',
      description: '后续可加入压力面、追问和白板题流程。'
    },
    {
      title: '准备建议',
      status: '待接入',
      description: '后续可按岗位、简历和经验等级输出建议。'
    }
  ]
}

export const buildSetupSnapshot = (storedResume, token) => {
  const hasResume = Boolean(storedResume)
  const resumeSource = storedResume ? '本地缓存画像' : token ? '已登录，等待画像' : '未登录'

  return {
    resumeSource,
    hasResume,
    resumeLabel: hasResume ? '已就绪' : token ? '待接入' : '未接入',
    resumeTone: hasResume ? '系统已读取你的简历画像，可以直接开始配置。' : token ? '你已经登录，但当前还没有可用的简历画像。' : '请先登录，再加载简历画像。'
  }
}
