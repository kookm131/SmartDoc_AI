import React from 'react';
import {
  ArrowLeft,
  Download,
  Bolt,
  Share2,
  FileText,
  LayoutGrid,
  BarChart3,
  Lightbulb,
  MessageSquare,
  LoaderCircle,
} from 'lucide-react';
import { motion } from 'motion/react';
import { AnalysisJobRecord, ApiErrorResponse, DocumentRecord } from '../types';

interface DocumentDetailProps {
  document: DocumentRecord;
  onBack: () => void;
  onAnalyze: (documentId: string) => Promise<void>;
  analysisJob: AnalysisJobRecord | null;
  polling: boolean;
  error: ApiErrorResponse | null;
}

export default function DocumentDetail({
  document,
  onBack,
  onAnalyze,
  analysisJob,
  polling,
  error,
}: DocumentDetailProps) {
  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="fixed inset-0 z-[60] bg-background flex flex-col h-screen overflow-hidden"
    >
      <header className="h-16 px-6 flex items-center justify-between bg-surface-container-lowest border-b border-outline-variant/15">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <FileText className="text-primary w-6 h-6" />
            <span className="text-xl font-bold tracking-tight text-on-surface">SmartDoc AI</span>
          </div>
        </div>
      </header>

      <main className="flex-1 flex overflow-hidden">
        <section className="flex-1 bg-surface-container-low flex flex-col min-w-0">
          <div className="h-12 px-6 flex items-center justify-between bg-white/50 backdrop-blur-sm border-b border-outline-variant/10">
            <div className="flex items-center gap-3">
              <button onClick={onBack} className="p-1.5 hover:bg-surface-container-high rounded-md transition-colors">
                <ArrowLeft className="w-4 h-4" />
              </button>
              <span className="font-semibold text-on-surface">{document.filename}</span>
              <span className="bg-primary/10 text-primary px-2 py-0.5 rounded text-[11px] font-bold">{document.status}</span>
            </div>
            <div className="flex items-center gap-2">
              <button className="p-1.5 hover:bg-surface-container-high rounded-md transition-colors">
                <Download className="w-4 h-4" />
              </button>
            </div>
          </div>
          <div className="flex-1 overflow-y-auto p-8 flex justify-center items-start custom-scrollbar">
            <div className="w-full max-w-3xl bg-white shadow-sm rounded-lg overflow-hidden relative p-6 space-y-4">
              <h3 className="text-lg font-bold">문서 메타데이터</h3>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <MetaRow label="Document ID" value={document.documentId} />
                <MetaRow label="파일명" value={document.filename} />
                <MetaRow label="파일 키" value={document.fileKey} />
                <MetaRow label="콘텐츠 타입" value={document.contentType || '-'} />
                <MetaRow label="생성일" value={new Date(document.createdAt).toLocaleString('ko-KR')} />
                <MetaRow label="수정일" value={new Date(document.updatedAt).toLocaleString('ko-KR')} />
              </div>
            </div>
          </div>
        </section>

        <aside className="w-[480px] bg-surface-container-lowest flex flex-col border-l border-outline-variant/10">
          <div className="p-6 pb-2">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold text-on-surface">AI 분석 결과</h2>
              {analysisJob && (
                <div className="flex items-center gap-1.5 text-xs text-on-tertiary-fixed-variant bg-tertiary-fixed/30 px-2 py-1 rounded-full">
                  provider: {analysisJob.analysisProvider}
                </div>
              )}
            </div>
            <div className="flex gap-2 mb-4">
              <button
                onClick={() => onAnalyze(document.documentId)}
                className="flex-1 flex items-center justify-center gap-2 bg-primary text-white py-2.5 rounded-xl text-sm font-medium hover:opacity-90 transition-all"
              >
                {polling ? <LoaderCircle className="w-4 h-4 animate-spin" /> : <Bolt className="w-4 h-4" />}
                분석 실행
              </button>
              <button className="flex items-center justify-center gap-2 border border-outline-variant/30 text-on-surface-variant px-4 py-2.5 rounded-xl text-sm font-medium hover:bg-surface-container-low transition-all">
                <Share2 className="w-4 h-4" />
              </button>
            </div>

            {error && (
              <div className="bg-error/5 border border-error/20 rounded-lg p-3 text-sm mb-4">
                <p className="font-bold text-error">{error.code}</p>
                <p className="text-on-surface mt-1">{error.message}</p>
                <p className="text-on-surface-variant mt-1">traceId: {error.traceId}</p>
              </div>
            )}
          </div>

          <div className="flex-1 overflow-y-auto px-6 pb-8 space-y-6 custom-scrollbar">
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <LayoutGrid className="text-primary w-5 h-5" />
                <h3 className="font-bold text-sm text-on-surface">분석 Job 상태</h3>
              </div>
              <div className="bg-surface-container-low p-4 rounded-xl text-sm border border-outline-variant/5 space-y-2">
                <MetaRow label="jobId" value={analysisJob?.jobId || '-'} />
                <MetaRow label="documentId" value={analysisJob?.documentId || document.documentId} />
                <MetaRow label="state" value={analysisJob?.state || 'NOT_STARTED'} />
                <MetaRow label="createdAt" value={analysisJob ? new Date(analysisJob.createdAt).toLocaleString('ko-KR') : '-'} />
                <MetaRow label="analysisProvider" value={analysisJob?.analysisProvider || '-'} />
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <BarChart3 className="text-primary w-5 h-5" />
                <h3 className="font-bold text-sm text-on-surface">상태 표시</h3>
              </div>
              <div className="bg-surface-container-low p-4 rounded-xl border border-outline-variant/5">
                <p className="text-xs text-on-surface-variant mb-2">현재 상태</p>
                <p className="text-sm font-bold text-primary">{analysisJob?.state || '대기'}</p>
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Lightbulb className="text-primary w-5 h-5" />
                <h3 className="font-bold text-sm text-on-surface">다음 액션</h3>
              </div>
              <div className="space-y-2">
                <InsightCard title="분석 요청" content="분석 실행 버튼으로 /api/v1/analysis/jobs 호출" type="primary" />
                <InsightCard title="상태 조회" content="백그라운드 폴링으로 최신 state 반영" type="tertiary" />
              </div>
            </div>
          </div>

          <div className="p-6 border-t border-outline-variant/10 bg-white/80 backdrop-blur-md">
            <button className="w-full flex items-center justify-center gap-2 bg-[#4A154B] text-white py-3 rounded-xl text-sm font-bold shadow-lg hover:shadow-xl active:scale-[0.98] transition-all">
              <MessageSquare className="w-5 h-5" />
              Slack으로 알림 보내기
            </button>
            <p className="mt-2 text-[10px] text-center text-on-surface-variant">긴급 채널로 분석 리포트가 즉시 공유됩니다.</p>
          </div>
        </aside>
      </main>
    </motion.div>
  );
}

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-surface-container p-3 rounded-lg flex flex-col">
      <span className="text-[11px] text-on-surface-variant mb-1">{label}</span>
      <span className="font-semibold text-sm break-all">{value}</span>
    </div>
  );
}

function InsightCard({ title, content, type }: { title: string; content: string; type: 'primary' | 'tertiary' }) {
  return (
    <div
      className={`group p-3 border-l-4 rounded-r-lg hover:translate-x-1 transition-transform ${
        type === 'primary' ? 'bg-secondary-container/30 border-primary' : 'bg-tertiary/5 border-tertiary'
      }`}
    >
      <h4 className={`text-xs font-bold mb-1 ${type === 'primary' ? 'text-primary' : 'text-tertiary'}`}>{title}</h4>
      <p className="text-xs text-on-surface-variant">{content}</p>
    </div>
  );
}
