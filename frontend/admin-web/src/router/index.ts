import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '../stores/session'
import { featureModules } from './modules'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/password', name: 'password', component: () => import('../views/PasswordChangeView.vue') },
    { path: '/forbidden', component: () => import('../views/ForbiddenView.vue') },
    {
      path: '/',
      component: () => import('../components/AdminLayout.vue'),
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue'),
          meta: { title: '工作台' },
        },
        ...featureModules.map((module) => ({
          path: module.path,
          component:
            module.path === 'notifications'
              ? () => import('../views/NotificationsView.vue')
              : module.path === 'accounts'
              ? () => import('../views/AdminAccountsView.vue')
              : module.path === 'health'
              ? () => import('../views/HealthView.vue')
              : module.path === 'elders'
              ? () => import('../views/EldersView.vue')
              : module.path === 'services'
                ? () => import('../views/ServicesView.vue')
                : module.path === 'consent'
                  ? () => import('../views/BookingAccessView.vue')
                  : module.path === 'bookings' || module.path === 'fulfillment'
                    ? () => import('../views/BookingsView.vue')
                    : () => import('../views/ModuleView.vue'),
          meta: {
            title: module.title,
            description: module.description,
            requiresHealthAssist: module.path === 'health',
            requiresAccountAdmin: module.path === 'accounts',
            requiresNotifications: module.path === 'notifications',
            requiresElderAccess: [
              'elders',
              'services',
              'consent',
              'bookings',
              'fulfillment',
            ].includes(module.path),
          },
        })),
      ],
    },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') },
  ],
})

// 先处理强制改密，再判断各业务入口；未完成改密的账号不会挂载后台布局。
router.beforeEach(async (to) => {
  const session = useSessionStore()
  if (!session.initialized) {
    try {
      await session.restore()
    } catch {
      return to.path === '/login' ? true : '/login'
    }
  }
  if (session.canAccessAdmin && session.account?.passwordChangeRequired) {
    return to.path === '/password' ? true : '/password'
  }
  if (to.path === '/login') return session.canAccessAdmin ? '/' : true
  if (!session.account) return '/login'
  if (!session.canAccessAdmin && to.path !== '/forbidden') return '/forbidden'
  if (to.meta.requiresElderAccess && !session.canManageElders) return '/forbidden'
  if (to.meta.requiresHealthAssist && !session.canAssistHealth) return '/forbidden'
  if (to.meta.requiresAccountAdmin && !session.canManageAccounts) return '/forbidden'
  if (to.meta.requiresNotifications && !session.canViewNotifications) return '/forbidden'
  return true
})

// 权限或会话失效时卸载页面，防止继续展示上一会话的数据。
window.addEventListener('session-invalidated', () => {
  useSessionStore().clear()
  if (router.currentRoute.value.path !== '/login') void router.replace('/login')
})

// 强制改密属于受限登录态，保留身份并立即转向独立页面卸载业务数据。
window.addEventListener('password-change-required', () => {
  const session = useSessionStore()
  if (session.account) session.account = { ...session.account, passwordChangeRequired: true }
  if (router.currentRoute.value.path !== '/password') void router.replace('/password')
})
