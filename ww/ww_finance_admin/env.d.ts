/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DEV_MOCK_LOGIN: string
  readonly VITE_MOCK_ADMIN_USERNAME?: string
  readonly VITE_MOCK_ADMIN_PASSWORD?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
