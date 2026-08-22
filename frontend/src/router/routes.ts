import type { RouteRecordRaw } from 'vue-router'

import ChatView from '../views/ChatView.vue'
import KnowledgeBasesView from '../views/KnowledgeBasesView.vue'
import AdminView from '../views/AdminView.vue'
import RegistrationView from '../views/RegistrationView.vue'
import SettingsView from '../views/SettingsView.vue'

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'chat' } },
  {
    path: '/chat',
    name: 'chat',
    component: ChatView,
    meta: { title: 'Chat' },
  },
  {
    path: '/kbs/:logicalKbId?',
    name: 'knowledge-bases',
    component: KnowledgeBasesView,
    meta: { title: 'Knowledge bases' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: SettingsView,
    meta: { title: 'Settings' },
  },
  {
    path: '/register',
    name: 'registration',
    component: RegistrationView,
    meta: { title: 'Owner wizard' },
  },
  {
    path: '/admin',
    name: 'admin',
    component: AdminView,
    meta: { title: 'Admin governance' },
  },
]
