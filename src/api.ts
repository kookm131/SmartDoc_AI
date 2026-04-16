import {
  AnalysisJobRecord,
  ApiErrorResponse,
  AuthSession,
  AuthUser,
  DocumentCreateInput,
  DocumentRecord,
  LoginInput,
  NotificationDispatchInput,
  NotificationEventRecord,
  NotificationRuleCreateInput,
  NotificationRuleRecord,
  SignupInput,
} from './types';

const GATEWAY_API_BASE = '/api/gateway';
const ACCESS_TOKEN_STORAGE_KEY = 'smartdoc.accessToken';
const TRACE_ID_HEADER = 'X-SmartDoc-Trace-Id';

export function getStoredAccessToken(): string | null {
  return window.localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
}

function setStoredAccessToken(token: string): void {
  window.localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
}

export function clearStoredAccessToken(): void {
  window.localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
}

function isApiError(value: unknown): value is ApiErrorResponse {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const maybe = value as Record<string, unknown>;
  return (
    typeof maybe.code === 'string' &&
    typeof maybe.message === 'string' &&
    typeof maybe.traceId === 'string' &&
    typeof maybe.path === 'string' &&
    typeof maybe.timestamp === 'string'
  );
}

function createTraceId(): string {
  if (typeof window !== 'undefined' && typeof window.crypto?.randomUUID === 'function') {
    return window.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function parseJsonSafely(text: string): unknown {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function toApiErrorFallback(params: {
  url: string;
  code: string;
  message: string;
  traceId: string;
}): ApiErrorResponse {
  return {
    timestamp: new Date().toISOString(),
    path: params.url,
    code: params.code,
    message: params.message,
    traceId: params.traceId,
  };
}

function buildHeaders(initHeaders: RequestInit['headers'], defaults: Record<string, string>): Headers {
  const headers = new Headers(initHeaders);
  Object.entries(defaults).forEach(([key, value]) => {
    if (!headers.has(key)) {
      headers.set(key, value);
    }
  });
  return headers;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = getStoredAccessToken();
  const traceId = createTraceId();
  const headers = buildHeaders(init?.headers, {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    [TRACE_ID_HEADER]: traceId,
  });

  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      headers,
    });
  } catch {
    throw toApiErrorFallback({
      url,
      code: 'NETWORK_ERROR',
      message: 'network request failed',
      traceId,
    });
  }

  const text = await response.text();
  const data: unknown = parseJsonSafely(text);

  if (!response.ok) {
    if (isApiError(data)) {
      throw data;
    }

    const responseTraceId = response.headers.get(TRACE_ID_HEADER) ?? traceId;
    const snippet = typeof data === 'string' ? data.slice(0, 160) : '';
    const message =
      response.status === 413
        ? 'file size exceeds server limit'
        : snippet
          ? `request failed with status ${response.status}: ${snippet}`
          : `request failed with status ${response.status}`;

    throw toApiErrorFallback({
      url,
      code: response.status === 413 ? 'VALIDATION_ERROR' : 'HTTP_ERROR',
      message,
      traceId: responseTraceId,
    });
  }

  return data as T;
}

async function requestForm<T>(url: string, body: FormData): Promise<T> {
  const token = getStoredAccessToken();
  const traceId = createTraceId();
  const headers = buildHeaders(undefined, {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    [TRACE_ID_HEADER]: traceId,
  });

  let response: Response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers,
      body,
    });
  } catch {
    throw toApiErrorFallback({
      url,
      code: 'NETWORK_ERROR',
      message: 'network request failed',
      traceId,
    });
  }

  const text = await response.text();
  const data: unknown = parseJsonSafely(text);

  if (!response.ok) {
    if (isApiError(data)) {
      throw data;
    }

    const responseTraceId = response.headers.get(TRACE_ID_HEADER) ?? traceId;
    const snippet = typeof data === 'string' ? data.slice(0, 160) : '';
    const message =
      response.status === 413
        ? 'file size exceeds server limit'
        : snippet
          ? `request failed with status ${response.status}: ${snippet}`
          : `request failed with status ${response.status}`;

    throw toApiErrorFallback({
      url,
      code: response.status === 413 ? 'VALIDATION_ERROR' : 'HTTP_ERROR',
      message,
      traceId: responseTraceId,
    });
  }

  return data as T;
}

export async function login(input: LoginInput): Promise<AuthSession> {
  const session = await request<AuthSession>(`${GATEWAY_API_BASE}/auth/login`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
  setStoredAccessToken(session.accessToken);
  return session;
}

export async function signup(input: SignupInput): Promise<AuthSession> {
  const session = await request<AuthSession>(`${GATEWAY_API_BASE}/auth/signup`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
  setStoredAccessToken(session.accessToken);
  return session;
}

export async function getCurrentUser(): Promise<AuthUser> {
  return request<AuthUser>(`${GATEWAY_API_BASE}/auth/me`);
}

export async function logout(): Promise<void> {
  try {
    await request<void>(`${GATEWAY_API_BASE}/auth/logout`, { method: 'POST' });
  } finally {
    clearStoredAccessToken();
  }
}

export async function listDocuments(): Promise<DocumentRecord[]> {
  return request<DocumentRecord[]>(`${GATEWAY_API_BASE}/documents`);
}

export async function listArchivedDocuments(): Promise<DocumentRecord[]> {
  return request<DocumentRecord[]>(`${GATEWAY_API_BASE}/documents/archived`);
}

export async function getDocumentById(id: string): Promise<DocumentRecord> {
  return request<DocumentRecord>(`${GATEWAY_API_BASE}/documents/${id}`);
}

export async function archiveDocument(id: string): Promise<DocumentRecord> {
  return request<DocumentRecord>(`${GATEWAY_API_BASE}/documents/${id}/archive`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

export async function createDocument(input: DocumentCreateInput): Promise<DocumentRecord> {
  if (input.file) {
    const body = new FormData();
    body.append('file', input.file);
    if (input.fileKey.trim()) {
      body.append('fileKey', input.fileKey.trim());
    }
    return requestForm<DocumentRecord>(`${GATEWAY_API_BASE}/documents/upload`, body);
  }

  return request<DocumentRecord>(`${GATEWAY_API_BASE}/documents`, {
    method: 'POST',
    body: JSON.stringify({
      filename: input.filename,
      fileKey: input.fileKey,
      contentType: input.contentType?.trim() || undefined,
    }),
  });
}

export async function createAnalysisJob(documentId: string): Promise<AnalysisJobRecord> {
  return request<AnalysisJobRecord>(`${GATEWAY_API_BASE}/analysis/jobs`, {
    method: 'POST',
    body: JSON.stringify({ documentId }),
  });
}

export async function getAnalysisJob(jobId: string): Promise<AnalysisJobRecord> {
  return request<AnalysisJobRecord>(`${GATEWAY_API_BASE}/analysis/jobs/${jobId}`);
}

export async function retryAnalysisJob(jobId: string): Promise<AnalysisJobRecord> {
  return request<AnalysisJobRecord>(`${GATEWAY_API_BASE}/analysis/jobs/${jobId}/retry`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

export async function dispatchNotification(input: NotificationDispatchInput): Promise<NotificationEventRecord> {
  return request<NotificationEventRecord>(`${GATEWAY_API_BASE}/notifications/dispatch`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export async function listNotificationEvents(): Promise<NotificationEventRecord[]> {
  return request<NotificationEventRecord[]>(`${GATEWAY_API_BASE}/notifications/events`);
}

export async function listNotificationRules(): Promise<NotificationRuleRecord[]> {
  return request<NotificationRuleRecord[]>(`${GATEWAY_API_BASE}/notifications/rules`);
}

export async function createNotificationRule(input: NotificationRuleCreateInput): Promise<NotificationRuleRecord> {
  return request<NotificationRuleRecord>(`${GATEWAY_API_BASE}/notifications/rules`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
