import { createApp } from 'vue'

import App from './App.vue'
import { router } from './router'
import './style.css'

// The browser may hold only the opaque Atlas session cookie. Provider and
// model credentials must never be written to web storage, URLs, or logs.
createApp(App).use(router).mount('#app')
