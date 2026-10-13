import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import '../styles.css'
import PortalApp from './PortalApp.vue'
import { createPortalRouter } from './create-portal-router'
import type { PortalId } from './portal-config'

export function mountPortal(portal: PortalId) {
  createApp(PortalApp, { portal })
    .use(createPinia())
    .use(createPortalRouter(portal))
    .mount('#app')
}
