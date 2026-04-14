export interface ApiErrorResponse {
  timestamp: string;
  path: string;
  code: string;
  message: string;
  traceId: string;
}

export interface DocumentRecord {
  documentId: string;
  filename: string;
  fileKey: string;
  contentType?: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface AnalysisJobRecord {
  jobId: string;
  documentId: string;
  state: string;
  createdAt: string;
  analysisProvider: string;
}

export interface NotificationEventRecord {
  eventId: string;
  documentId: string;
  channel: string;
  message: string;
  status: string;
  createdAt: string;
}

export interface DashboardStats {
  weeklyCount: number;
  weeklyChange: number;
  avgConfidence: number;
  reviewNeeded: number;
}

export interface DocumentCreateInput {
  filename: string;
  fileKey: string;
  contentType?: string;
}

export interface NotificationDispatchInput {
  documentId: string;
  channel: string;
  message: string;
}
