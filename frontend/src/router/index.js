import { createRouter, createWebHistory } from 'vue-router'

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
    meta: { title: 'Dashboard' }
  },
  {
    path: '/interview/setup',
    name: 'InterviewSetup',
    component: loadView('InterviewSetup'),
    meta: { title: 'Interview Setup' }
  },
  {
    path: '/interview',
    name: 'Interview',
    component: loadView('Interview'),
    meta: { title: 'Text Interview' }
  },
  {
    path: '/history',
    name: 'History',
    component: loadView('History'),
    meta: { title: 'Reports' }
  },
  {
    path: '/resume',
    name: 'Resume',
    component: loadView('Resume'),
    meta: { title: 'Resume' }
  },
  {
    path: '/video-interview',
    name: 'VideoInterview',
    component: loadView('VideoInterview'),
    meta: { title: 'Video Interview' }
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
    meta: { title: 'Settings' }
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

export default router
