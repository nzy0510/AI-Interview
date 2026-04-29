export const reportCenterConfig = {
  hero: {
    kicker: 'Report Center',
    title: '把面试结果变成可追踪的成长记录',
    description: '这里会把历史面试、能力分布、趋势变化和反馈摘要放在同一个观察面里，方便后续继续迭代。',
    tags: ['历史归档', '趋势观察', '能力画像', '后续接入']
  },
  filters: [
    { label: '全部', value: 'all' },
    { label: '文字', value: 'text' },
    { label: '视频', value: 'video' }
  ],
  emptyStates: {
    all: '还没有完成过面试，先完成一次训练后，这里会自动生成报告中心。',
    filtered: '没有匹配到结果，试试清空筛选或者换一个关键词。'
  },
  settingsSections: [
    {
      key: 'account',
      kicker: 'Account',
      title: '账号偏好',
      description: '基础账号与展示信息的预留位，后续接真实配置接口。',
      badge: '待接入'
    },
    {
      key: 'interview',
      kicker: 'Interview',
      title: '面试偏好',
      description: '默认角色、模式、难度和节奏相关的设置入口。',
      badge: '前端占位'
    },
    {
      key: 'notification',
      kicker: 'Notification',
      title: '通知与隐私',
      description: '消息提醒、数据保存时长与可见性策略。',
      badge: '占位模块'
    }
  ]
}
