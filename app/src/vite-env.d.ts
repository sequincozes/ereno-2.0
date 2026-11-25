/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** ERENO API base URL (default http://localhost:8080). */
  readonly VITE_API_URL?: string;
  /** Bearer token, only when the API runs with ERENO_API_TOKEN. */
  readonly VITE_API_TOKEN?: string;
  /** "false" turns Firebase login and presets off even if configured. */
  readonly VITE_AUTH_ENABLED?: string;
  readonly VITE_APIKEY?: string;
  readonly VITE_AUTHDOMAIN?: string;
  readonly VITE_DATABASEURL?: string;
  readonly VITE_PROJECTID?: string;
  readonly VITE_STORAGEBUCKET?: string;
  readonly VITE_MESSAGINGSENDERID?: string;
  readonly VITE_APPID?: string;
  readonly VITE_MEASUREMENTID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
