import { createRouter, createWebHashHistory } from 'vue-router'

// 用 hash 模式：生产环境由网关托管静态资源，hash 路由不需要服务端 SPA 回退配置
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('./views/Login.vue') },
    { path: '/', component: () => import('./views/Dashboard.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

// 全局守卫：未登录跳登录页（前端体验层拦截，真正的安全边界在网关 JWT）
router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('em_token')) return '/login'
  if (to.path === '/login' && localStorage.getItem('em_token')) return '/'
  return true
})

export default router
