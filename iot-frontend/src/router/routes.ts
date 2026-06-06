import type { RouteRecordRaw } from 'vue-router'

/**
 * 全量路由表
 * <p>
 * meta.layout 决定使用的布局组件：auth / admin / dashboard
 * meta.roles 定义允许访问的角色列表
 * meta.title 设置页面标题
 * meta.guestOnly 仅未登录用户可访问（如登录页）
 * </p>
 */
const routes: RouteRecordRaw[] = [
  // ==================== 根路径 ====================
  {
    path: '/',
    redirect: '/admin/dashboard'
  },

  // ==================== 登录 ====================
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { layout: 'auth', title: '管理员登录', guestOnly: true }
  },

  // ==================== 管理端 ====================
  {
    path: '/admin',
    redirect: '/admin/dashboard',
    children: [
      // --- 大屏 ---
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { layout: 'admin', title: '运营数据大屏', roles: ['ROLE_ADMIN'] }
      },
      // --- 充电站管理 ---
      {
        path: 'station',
        name: 'AdminStation',
        component: () => import('@/views/admin/station/StationList.vue'),
        meta: { layout: 'admin', title: '充电站管理', roles: ['ROLE_ADMIN'] }
      },
      {
        path: 'station/:id',
        name: 'AdminStationDetail',
        component: () => import('@/views/admin/station/StationDetail.vue'),
        meta: { layout: 'admin', title: '充电站详情', roles: ['ROLE_ADMIN'] }
      },
      // --- 充电桩管理 ---
      {
        path: 'charger',
        name: 'AdminCharger',
        component: () => import('@/views/admin/charger/ChargerList.vue'),
        meta: { layout: 'admin', title: '充电桩管理', roles: ['ROLE_ADMIN'] }
      },
      {
        path: 'charger/:id',
        name: 'AdminChargerDetail',
        component: () => import('@/views/admin/charger/ChargerDetail.vue'),
        meta: { layout: 'admin', title: '充电桩详情', roles: ['ROLE_ADMIN'] }
      },
      // --- 订单管理 ---
      {
        path: 'order',
        name: 'AdminOrder',
        component: () => import('@/views/admin/order/OrderList.vue'),
        meta: { layout: 'admin', title: '订单管理', roles: ['ROLE_ADMIN'] }
      },
      // --- 计费规则 ---
      {
        path: 'pricing',
        name: 'AdminPricing',
        component: () => import('@/views/admin/pricing/PricingList.vue'),
        meta: { layout: 'admin', title: '计费规则', roles: ['ROLE_ADMIN'] }
      },
      // --- 告警管理 ---
      {
        path: 'alarm',
        name: 'AdminAlarm',
        component: () => import('@/views/admin/alarm/AlarmList.vue'),
        meta: { layout: 'admin', title: '告警管理', roles: ['ROLE_ADMIN'] }
      },
      // --- 系统管理 ---
      {
        path: 'system/user',
        name: 'AdminUser',
        component: () => import('@/views/admin/system/UserList.vue'),
        meta: { layout: 'admin', title: '用户管理', roles: ['ROLE_ADMIN'] }
      },
      {
        path: 'system/role',
        name: 'AdminRole',
        component: () => import('@/views/admin/system/RoleList.vue'),
        meta: { layout: 'admin', title: '角色管理', roles: ['ROLE_ADMIN'] }
      },
      {
        path: 'system/log',
        name: 'AdminOperationLog',
        component: () => import('@/views/admin/system/OperationLog.vue'),
        meta: { layout: 'admin', title: '操作日志', roles: ['ROLE_ADMIN'] }
      }
    ]
  },

  // ==================== 404 ====================
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { layout: 'auth', title: '页面不存在' }
  }
]

export default routes
