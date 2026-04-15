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

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const token = getStoredAccessToken();
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  const text = await response.text();
  const data: unknown = text ? JSON.parse(text) : null;

  if (!response.ok) {
    if (isApiError(data)) {
      throw data;
    }

    throw {
      timestamp: new Date().toISOString(),
      path: url,
      code: 'HTTP_ERROR',
      message: `request failed with status ${response.status}`,
      traceId: 'N/A',
    } satisfies ApiErrorResponse;
  }

  return data as T;
}

async function requestForm<T>(url: string, body: FormData): Promise<T> {
  const token = getStoredAccessToken();
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body,
  });

  const text = await response.text();
  const data: unknown = text ? JSON.parse(text) : null;

  if (!response.ok) {
    if (isApiError(data)) {
      throw data;
    }

    throw {
      timestamp: new Date().toISOString(),
      path: url,
      code: 'HTTP_ERROR',
      message: `request failed with status ${response.status}`,
      traceId: 'N/A',
    } satisfies ApiErrorResponse;
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
