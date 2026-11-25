import type { Catalog, FieldError, RunDetail, RunSpec, RunStatus } from '../types/run';

// Single client for the ERENO backend (`java -jar ... serve`).
// VITE_API_URL="/" means same origin (the Docker image, where nginx proxies /api).
const BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '');
const API_LABEL = BASE_URL || 'this server (/api)';
const TOKEN = import.meta.env.VITE_API_TOKEN || '';

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldError[];

  constructor(status: number, message: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function headers(extra?: Record<string, string>): Record<string, string> {
  const h: Record<string, string> = { ...extra };
  if (TOKEN) h.Authorization = `Bearer ${TOKEN}`;
  return h;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: headers(body === undefined ? undefined : { 'Content-Type': 'application/json' }),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, `Cannot reach the ERENO API at ${API_LABEL}. Is the backend running?`);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    throw new ApiError(res.status, data?.error || `Request failed (${res.status})`, data?.errors ?? []);
  }
  return data as T;
}

export const api = {
  baseUrl: BASE_URL,
  catalog: () => request<Catalog>('GET', '/api/catalog'),
  listRuns: () => request<{ runs: RunStatus[] }>('GET', '/api/runs').then(r => r.runs),
  getRun: (id: string) => request<RunDetail>('GET', `/api/runs/${encodeURIComponent(id)}`),
  createRun: (spec: RunSpec) => request<RunStatus>('POST', '/api/runs', spec),
  /** Cancels an active run or deletes a finished one. */
  deleteRun: (id: string) => request<void>('DELETE', `/api/runs/${encodeURIComponent(id)}`),
  fileUrl: (id: string, path: string) =>
    `${BASE_URL}/api/runs/${encodeURIComponent(id)}/files/${path.split('/').map(encodeURIComponent).join('/')}`,

  /** Downloads a run file. Uses a plain link unless a token must be sent. */
  async download(id: string, path: string) {
    const url = api.fileUrl(id, path);
    const name = path.split('/').pop() || 'download';
    if (!TOKEN) {
      triggerDownload(url, name);
      return;
    }
    const res = await fetch(url, { headers: headers() });
    if (!res.ok) throw new ApiError(res.status, `Download failed (${res.status})`);
    const blobUrl = URL.createObjectURL(await res.blob());
    triggerDownload(blobUrl, name);
    setTimeout(() => URL.revokeObjectURL(blobUrl), 10_000);
  },
};

function triggerDownload(href: string, name: string) {
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
}
