export interface ApiErrorResponse {
  timestamp: string;
  path: string;
  code: string;
  message: string;
  traceId: string;
}

export interface AuthUser {
  userId: string;
  email: string;
  displayName: string;
  role: string;
}

export interface AuthSession {
  user: AuthUser;
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface SignupInput {
  email: string;
  password: string;
  displayName?: string;
}

export interface DocumentRecord {
  documentId: string;
  filename: string;
  fileKey: string;
  contentType?: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
  archivedAt?: string | null;
}

export interface AnalysisJobRecord {
  jobId: string;
  ownerUserId: string;
  documentId: string;
  state: string;
  createdAt: string;
  analysisProvider: string;
  resultSummary?: string | null;
  riskScore?: number | null;
  keywords: string[];
  errorCode?: string | null;
  errorMessage?: string | null;
  failedAt?: string | null;
}

export interface NotificationEventRecord {
  eventId: string;
  documentId: string;
  channel: string;
  message: string;
  status: string;
  createdAt: string;
}

export interface NotificationRuleRecord {
  ruleId: string;
  keyword: string;
  channel: string;
  enabled: boolean;
  createdAt: string;
}

export interface NotificationRuleCreateInput {
  keyword: string;
  channel: string;
  enabled: boolean;
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
  file?: File;
}

export interface NotificationDispatchInput {
  documentId: string;
  channel: string;
  message: string;
}
