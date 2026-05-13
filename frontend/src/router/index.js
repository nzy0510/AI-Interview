import { createRouter, createWebHistory } from 'vue-router'
import { trackPageView } from '@/utils/analytics'

const viewModules = import.meta.glob('../views/*.vue')
const commonModules = import.meta.glob('../components/common/*.vue')

const loadView = (name, fallback = 'RoutePlaceholder') => {
  return (
    viewModules[`../views/${name}.vue`] ||
    commonModules[`../components/common/${fallback}.vue`]
  )
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    name: 'Home',
    component: loadView('Home'),
    meta: { title: '工作台' }
  },
  {
    path: '/interview/setup',
    name: 'InterviewSetup',
    component: loadView('InterviewSetup'),
    meta: { title: '面试准备' }
  },
  {
    path: '/interview',
    name: 'Interview',
    component: loadView('Interview'),
    meta: { title: '文字面试' }
  },
  {
    path: '/history',
    name: 'History',
    component: loadView('History'),
    meta: { title: '历史报告' }
  },
  {
    path: '/resume',
    name: 'Resume',
    component: loadView('Resume'),
    meta: { title: '简历画像' }
  },
  {
    path: '/video-interview',
    name: 'VideoInterview',
    component: loadView('VideoInterview'),
    meta: { title: '视频面试' }
  },
  {
    path: '/mentor',
    name: 'Mentor',
    component: loadView('Mentor'),
    meta: { title: 'AI Mentor' }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: loadView('Settings'),
    meta: { title: '设置' }
  },
  {
    path: '/admin/analytics',
    name: 'AdminAnalytics',
    component: loadView('AdminAnalytics'),
    meta: { title: '运营统计' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation Guard
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

router.afterEach((to) => {
  trackPageView(to)
})

export default router
