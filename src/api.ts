import {
  AnalysisJobRecord,
  ApiErrorResponse,
  DocumentCreateInput,
  DocumentRecord,
  NotificationDispatchInput,
  NotificationEventRecord,
} from './types';

const DOCUMENT_API_BASE = '/api/document';
const ANALYSIS_API_BASE = '/api/analysis';
const NOTIFICATION_API_BASE = '/api/notification';

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
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
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

export async function listDocuments(): Promise<DocumentRecord[]> {
  return request<DocumentRecord[]>(`${DOCUMENT_API_BASE}/documents`);
}

export async function getDocumentById(id: string): Promise<DocumentRecord> {
  return request<DocumentRecord>(`${DOCUMENT_API_BASE}/documents/${id}`);
}

export async function createDocument(input: DocumentCreateInput): Promise<DocumentRecord> {
  return request<DocumentRecord>(`${DOCUMENT_API_BASE}/documents`, {
    method: 'POST',
    body: JSON.stringify({
      filename: input.filename,
      fileKey: input.fileKey,
      contentType: input.contentType?.trim() || undefined,
    }),
  });
}

export async function createAnalysisJob(documentId: string): Promise<AnalysisJobRecord> {
  return request<AnalysisJobRecord>(`${ANALYSIS_API_BASE}/analysis/jobs`, {
    method: 'POST',
    body: JSON.stringify({ documentId }),
  });
}

export async function getAnalysisJob(jobId: string): Promise<AnalysisJobRecord> {
  return request<AnalysisJobRecord>(`${ANALYSIS_API_BASE}/analysis/jobs/${jobId}`);
}

export async function dispatchNotification(input: NotificationDispatchInput): Promise<NotificationEventRecord> {
  return request<NotificationEventRecord>(`${NOTIFICATION_API_BASE}/notifications/dispatch`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export async function listNotificationEvents(): Promise<NotificationEventRecord[]> {
  return request<NotificationEventRecord[]>(`${NOTIFICATION_API_BASE}/notifications/events`);
}
