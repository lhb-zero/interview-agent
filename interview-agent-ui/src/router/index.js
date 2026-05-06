import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/ChatView.vue')
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('../views/KnowledgeView.vue')
  },
  {
    path: '/eval',
    name: 'Eval',
    component: () => import('../views/EvalView.vue')
  },
  {
    path: '/eval/:experimentId',
    name: 'EvalDetail',
    component: () => import('../views/EvalDetailView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
