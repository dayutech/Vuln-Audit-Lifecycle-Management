import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import router from './router'
import { I18nProvider } from './i18n'
import './index.css'

/**
 * 应用入口。
 *
 * ConfigProvider 已移到 `I18nProvider` 内部：语言切换时 antd 的 locale
 * （分页 / 空态 / 日期等内置文案）与 dayjs locale 会和前端文案一起即时切换。
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <RouterProvider router={router} />
    </I18nProvider>
  </StrictMode>,
)
