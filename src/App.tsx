import React, { useEffect, useMemo, useState } from 'react';
import { FileText } from 'lucide-react';
import Layout from './components/Layout';
import DocumentList from './components/DocumentList';
import DocumentDetail from './components/DocumentDetail';
import DashboardCards from './components/DashboardCards';
import { createAnalysisJob, createDocument, getAnalysisJob, getDocumentById, listDocuments } from './api';
import {
  AnalysisJobRecord,
  ApiErrorResponse,
  DashboardStats,
  DocumentCreateInput,
  DocumentRecord,
} from './types';

function toApiError(error: unknown): ApiErrorResponse {
  if (!error || typeof error !== 'object') {
    return {
      timestamp: new Date().toISOString(),
      path: '/unknown',
      code: 'UNKNOWN_ERROR',
      message: '알 수 없는 오류가 발생했습니다.',
      traceId: 'N/A',
    };
  }

  const candidate = error as Partial<ApiErrorResponse>;
  if (
    typeof candidate.timestamp === 'string' &&
    typeof candidate.path === 'string' &&
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    typeof candidate.traceId === 'string'
  ) {
    return candidate as ApiErrorResponse;
  }

  return {
    timestamp: new Date().toISOString(),
    path: '/unknown',
    code: 'UNKNOWN_ERROR',
    message: '알 수 없는 오류가 발생했습니다.',
    traceId: 'N/A',
  };
}

const POLLING_INTERVAL_MS = 2000;
const POLLING_COUNT_LIMIT = 5;

export default function App() {
  const [activeTab, setActiveTab] = useState('list');
  const [documents, setDocuments] = useState<DocumentRecord[]>([]);
  const [loadingDocuments, setLoadingDocuments] = useState(true);
  const [submittingDocument, setSubmittingDocument] = useState(false);

  const [selectedDoc, setSelectedDoc] = useState<DocumentRecord | null>(null);
  const [analysisByDocumentId, setAnalysisByDocumentId] = useState<Record<string, AnalysisJobRecord>>({});
  const [analysisPolling, setAnalysisPolling] = useState(false);

  const [apiError, setApiError] = useState<ApiErrorResponse | null>(null);

  const dashboardStats: DashboardStats = useMemo(() => {
    const reviewNeeded = documents.filter((doc) => doc.status !== 'RECEIVED').length;
    return {
      weeklyCount: documents.length,
      weeklyChange: 0,
      avgConfidence: 98.4,
      reviewNeeded,
    };
  }, [documents]);

  const loadDocuments = async () => {
    setLoadingDocuments(true);
    try {
      const response = await listDocuments();
      setDocuments(response);
      setApiError(null);

      if (selectedDoc) {
        const refreshedSelected = response.find((doc) => doc.documentId === selectedDoc.documentId);
        if (refreshedSelected) {
          setSelectedDoc(refreshedSelected);
        }
      }
    } catch (error) {
      setApiError(toApiError(error));
    } finally {
      setLoadingDocuments(false);
    }
  };

  useEffect(() => {
    void loadDocuments();
  }, []);

  const handleDocumentClick = async (doc: DocumentRecord) => {
    try {
      const detail = await getDocumentById(doc.documentId);
      setSelectedDoc(detail);
      setApiError(null);
      setActiveTab('detail');
    } catch (error) {
      setApiError(toApiError(error));
    }
  };

  const handleCreateDocument = async (input: DocumentCreateInput) => {
    setSubmittingDocument(true);
    try {
      const created = await createDocument(input);
      setDocuments((prev) => [created, ...prev]);
      setSelectedDoc(created);
      setActiveTab('detail');
      setApiError(null);
    } catch (error) {
      setApiError(toApiError(error));
    } finally {
      setSubmittingDocument(false);
    }
  };

  const handleBack = () => {
    setSelectedDoc(null);
    setActiveTab('list');
  };

  const handleStartAnalysis = async (documentId: string) => {
    try {
      setAnalysisPolling(true);
      const created = await createAnalysisJob(documentId);
      setAnalysisByDocumentId((prev) => ({ ...prev, [documentId]: created }));
      setApiError(null);

      let latest = created;
      let count = 0;
      while (count < POLLING_COUNT_LIMIT) {
        await new Promise((resolve) => window.setTimeout(resolve, POLLING_INTERVAL_MS));
        latest = await getAnalysisJob(latest.jobId);
        setAnalysisByDocumentId((prev) => ({ ...prev, [documentId]: latest }));
        count += 1;
      }
    } catch (error) {
      setApiError(toApiError(error));
    } finally {
      setAnalysisPolling(false);
    }
  };

  return (
    <Layout activeTab={activeTab} onTabChange={setActiveTab}>
      {activeTab === 'dashboard' && (
        <div className="flex flex-col gap-8">
          <header>
            <h1 className="text-3xl font-bold tracking-tight text-on-surface mb-1">대시보드</h1>
            <p className="text-on-surface-variant text-sm">전체 분석 현황을 한눈에 확인하세요.</p>
          </header>
          <DashboardCards stats={dashboardStats} />

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-surface-container-lowest p-6 rounded-xl border border-outline-variant/10 shadow-sm">
              <h3 className="text-lg font-bold mb-4">최근 분석 문서</h3>
              <div className="flex flex-col gap-3">
                {documents.slice(0, 3).map((doc) => (
                  <div
                    key={doc.documentId}
                    className="flex items-center justify-between p-3 hover:bg-surface-container-low rounded-lg transition-colors cursor-pointer"
                    onClick={() => void handleDocumentClick(doc)}
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-primary/5 flex items-center justify-center text-primary">
                        <FileText className="w-5 h-5" />
                      </div>
                      <div>
                        <p className="text-sm font-bold truncate">{doc.filename}</p>
                        <p className="text-xs text-on-surface-variant">{new Date(doc.createdAt).toLocaleDateString('ko-KR')}</p>
                      </div>
                    </div>
                    <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-primary/10 text-primary">{doc.status}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="bg-surface-container-lowest p-6 rounded-xl border border-outline-variant/10 shadow-sm">
              <h3 className="text-lg font-bold mb-4">AI 인사이트 요약</h3>
              <div className="space-y-4">
                <div className="p-4 bg-tertiary/5 rounded-lg border-l-4 border-tertiary">
                  <p className="text-sm font-semibold text-tertiary mb-1">분석 요청 연결 완료</p>
                  <p className="text-xs text-on-surface-variant">Document 생성부터 Analysis job 요청/상태조회까지 API 기반으로 연결되었습니다.</p>
                </div>
                <div className="p-4 bg-error/5 rounded-lg border-l-4 border-error">
                  <p className="text-sm font-semibold text-error mb-1">오류 모델 표준 반영</p>
                  <p className="text-xs text-on-surface-variant">code/message/traceId를 UI에서 그대로 표시합니다.</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'list' && (
        <div className="flex flex-col gap-8">
          <DocumentList
            documents={documents}
            loading={loadingDocuments}
            submitting={submittingDocument}
            onDocumentClick={(doc) => void handleDocumentClick(doc)}
            onCreateDocument={handleCreateDocument}
            latestError={apiError}
          />
          <DashboardCards stats={dashboardStats} />
        </div>
      )}

      {activeTab === 'detail' && selectedDoc && (
        <DocumentDetail
          document={selectedDoc}
          onBack={handleBack}
          onAnalyze={handleStartAnalysis}
          analysisJob={analysisByDocumentId[selectedDoc.documentId] ?? null}
          polling={analysisPolling}
          error={apiError}
        />
      )}
    </Layout>
  );
}
